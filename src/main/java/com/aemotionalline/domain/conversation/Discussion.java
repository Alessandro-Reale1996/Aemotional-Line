package com.aemotionalline.domain.conversation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.negotiation.DiscussionTitle;
import com.aemotionalline.domain.user.UserId;

/**
 * An ordered exchange of messages inside a conversation. A specific discussion (a branch) keeps the title and subtitle
 * the partners settled on; the conversation's main discussion has none. It enforces turn-taking,
 * so the partners are forced to answer each other rather than write in bursts.
 * A discussion can be opened to expand a topic exposed in a paragraph (its origin): the paragraphs of its
 * first message then all refer to that paragraph, so a topic can continue across discussions.
 * <p>
 * A specific discussion can be closed, never by one partner alone: a partner sends a closing request and the other
 * accepts it, or, after at least one failed attempt, the therapist confirms a special request. A closed discussion
 * accepts no new messages; each partner then declares whether its topic was resolved or stalled. Every request, with its
 * instants, stays in the discussion's history. The roles (partner, therapist, main discussion) are checked by
 * {@link Conversation}; the state rules are checked here.
 */
public class Discussion
{
	private final DiscussionId id;
	private final List<Message> messages;
	
	// When the recipient first read each message. Only the other partner's reads matter (the sender wrote it).
	private final Map<MessageId, Instant> readAt = new HashMap<>();
	
	private UserId lastSender;
	// Title and subtitle settled with a negotiation, or null for the main discussion.
	private final DiscussionTitle title;
	
	// The paragraph this discussion expands, or null when it doesn't start from one (e.g. the conversation's first).
	private final ParagraphId originParagraphId;
	
	// Closing: every request in the order it was sent, then the closure (null while open) and the declarations.
	private final List<ClosingRequestRecord> closingRequests = new ArrayList<>();
	private final List<TherapistClosingRequestRecord> therapistClosingRequests = new ArrayList<>();
	private DiscussionClosure.Way closedWay;
	private Instant closedAt;
	private final List<OutcomeDeclaration> declarations = new ArrayList<>();
	
	public Discussion(DiscussionId id, DiscussionTitle title)
	{
		this(id, title, null);
	}
	
	public Discussion(DiscussionId id, DiscussionTitle title, ParagraphId originParagraphId)
	{
		this.id = id;
		this.title = title;
		this.originParagraphId = originParagraphId;
		
		this.messages = new ArrayList<Message>();
	}

	// Reserved to Conversation.restore: rebuilds the discussion from its stored facts, without replaying them.
	static Discussion restore(DiscussionSnapshot snapshot)
	{
		Discussion discussion = new Discussion(snapshot.id(), snapshot.title().orElse(null), snapshot.originParagraphId().orElse(null));
		
		discussion.messages.addAll(snapshot.messages());
		discussion.lastSender = snapshot.messages().isEmpty() ? null : snapshot.messages().getLast().getSenderId();
		discussion.readAt.putAll(snapshot.readAt());
		discussion.closingRequests.addAll(snapshot.closingRequests());
		discussion.therapistClosingRequests.addAll(snapshot.therapistClosingRequests());
		
		snapshot.closure().ifPresent(closure ->
		{
			discussion.closedWay = closure.way();
			discussion.closedAt = closure.closedAt();
			discussion.declarations.addAll(closure.declarations());
		});
		
		return discussion;
	}

	public DiscussionId getId()
	{
		return id;
	}

	public List<Message> getMessages()
	{
		return List.copyOf(messages);
	}
	
	public UserId getLastSender() 
	{
		return lastSender;
	}
	
	public Optional<DiscussionTitle> getTitle() 
	{
		return Optional.ofNullable(title);
	}
	
	public Optional<ParagraphId> getOriginParagraphId()
	{
		return Optional.ofNullable(originParagraphId);
	}

	public Optional<Instant> getReadAt(MessageId messageId)
	{
		return Optional.ofNullable(readAt.get(messageId));
	}
	
	// Only the first read is kept: later reads of the same message don't move it.
	void markRead(MessageId messageId, Instant when)
	{
		readAt.putIfAbsent(messageId, when);
	}

	public List<ClosingRequestRecord> getClosingRequests()
	{
		return List.copyOf(closingRequests);
	}
	
	public List<TherapistClosingRequestRecord> getTherapistClosingRequests()
	{
		return List.copyOf(therapistClosingRequests);
	}
	
	public boolean isClosed()
	{
		return closedAt != null;
	}
	
	public Optional<DiscussionClosure> getClosure()
	{
		return isClosed() ? Optional.of(new DiscussionClosure(closedWay, closedAt, declarations)) : Optional.empty();
	}
	
	void ensureOpen()
	{
		if (isClosed())
		{
			throw new DomainException("The discussion is closed.");
		}
	}
	
	// One pending closing procedure at a time, of either kind: two ways of closing at once would compete.
	private void ensureNothingPending()
	{
		if (closingRequests.stream().anyMatch(ClosingRequestRecord::isPending)
				|| therapistClosingRequests.stream().anyMatch(TherapistClosingRequestRecord::isPending))
		{
			throw new DomainException("A closing request is already pending for this discussion.");
		}
	}
	
	private ClosingRequestRecord pendingClosingRequest()
	{
		if (closingRequests.isEmpty() || !closingRequests.getLast().isPending())
		{
			throw new DomainException("There is no pending closing request.");
		}
		
		return closingRequests.getLast();
	}
	
	private TherapistClosingRequestRecord pendingTherapistRequest()
	{
		if (therapistClosingRequests.isEmpty() || !therapistClosingRequests.getLast().isPending())
		{
			throw new DomainException("There is no pending request to the therapist.");
		}
		
		return therapistClosingRequests.getLast();
	}
	
	void requestClosing(UserId requester, Instant at)
	{
		ensureOpen();
		ensureNothingPending();
		
		closingRequests.add(new ClosingRequestRecord(requester, at, ClosingRequestRecord.Status.PENDING, Optional.empty()));
	}
	
	// Only the other partner answers: the requester can withdraw, not accept their own request.
	void answerClosing(UserId responder, boolean accepted, Instant at)
	{
		ClosingRequestRecord pending = pendingClosingRequest();
		
		if (pending.requester().equals(responder))
		{
			throw new DomainException("Only the other partner can answer a closing request.");
		}
		
		closingRequests.set(closingRequests.size() - 1,
				pending.end(accepted ? ClosingRequestRecord.Status.ACCEPTED : ClosingRequestRecord.Status.REFUSED, at));
		
		if (accepted)
		{
			close(DiscussionClosure.Way.AGREEMENT, at);
		}
	}
	
	void withdrawClosing(UserId requester, Instant at)
	{
		ClosingRequestRecord pending = pendingClosingRequest();
		
		if (!pending.requester().equals(requester))
		{
			throw new DomainException("Only the partner who sent the closing request can withdraw it.");
		}
		
		closingRequests.set(closingRequests.size() - 1, pending.end(ClosingRequestRecord.Status.WITHDRAWN, at));
	}
	
	/**
	 * Allowed only after a failed attempt (a refused or withdrawn closing request of the requester) that came after the
	 * requester's last request to the therapist: after a refusal from the therapist, the partners must try again
	 * between themselves before going back to the therapist.
	 */
	void requestTherapistClosing(UserId requester, String justification, Instant at)
	{
		ensureOpen();
		ensureNothingPending();
		
		int firstCountingRequest = therapistClosingRequests.stream()
				.filter(request -> request.requester().equals(requester))
				.mapToInt(request -> request.previousRequests().size())
				.max()
				.orElse(0);
		
		boolean failedAttempt = closingRequests.subList(firstCountingRequest, closingRequests.size()).stream()
				.anyMatch(request -> request.requester().equals(requester) && request.isFailedAttempt());
		
		if (!failedAttempt)
		{
			throw new DomainException("The therapist can be asked only after a failed attempt to close the discussion with the other partner.");
		}
		
		therapistClosingRequests.add(new TherapistClosingRequestRecord(requester, justification, at, closingRequests,
				TherapistClosingRequestRecord.Status.PENDING, Optional.empty()));
	}
	
	void decideTherapistClosing(boolean confirmed, Instant at)
	{
		TherapistClosingRequestRecord pending = pendingTherapistRequest();
		
		therapistClosingRequests.set(therapistClosingRequests.size() - 1,
				pending.decide(confirmed ? TherapistClosingRequestRecord.Status.CONFIRMED : TherapistClosingRequestRecord.Status.REFUSED, at));
		
		if (confirmed)
		{
			close(DiscussionClosure.Way.THERAPIST, at);
		}
	}
	
	void declareOutcome(UserId partner, OutcomeDeclaration.Outcome outcome, String reason, Instant at)
	{
		if (!isClosed())
		{
			throw new DomainException("The outcome is declared once the discussion is closed.");
		}
		
		if (declarations.stream().anyMatch(declaration -> declaration.partner().equals(partner)))
		{
			throw new DomainException("This partner already declared the outcome.");
		}
		
		declarations.add(new OutcomeDeclaration(partner, outcome, reason, at));
	}
	
	private void close(DiscussionClosure.Way way, Instant at)
	{
		closedWay = way;
		closedAt = at;
	}

	// Rejecting the same sender twice in a row is what makes the exchange turn-based.
	void setLastSender(UserId lastSender)
	{
		if (lastSender.equals(this.lastSender)) 
		{
			throw new DomainException("A new message can't be sent if an answer wasn't received.");
		}
		
		this.lastSender = lastSender;
	}
	
	

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}

		if (!(o instanceof Discussion other))
		{
			return false;
		}

		return Objects.equals(id, other.id);
	}
	
	@Override
	public int hashCode()
	{
		return Objects.hash(id);
	}
	
	void addMessage(Message message)
	{
		if (message == null)
		{
			throw new DomainException("Message can't be null.");
		}
		
		message.ensureReadyToSend();
		this.messages.add(message);
	}
	
}
