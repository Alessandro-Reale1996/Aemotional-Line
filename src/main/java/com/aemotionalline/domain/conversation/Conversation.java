package com.aemotionalline.domain.conversation;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.constraint.ConstraintScope;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.message.ParagraphType;
import com.aemotionalline.domain.message.QuestionParagraph;
import com.aemotionalline.domain.negotiation.Negotiation;
import com.aemotionalline.domain.negotiation.NegotiationStatus;
import com.aemotionalline.domain.negotiation.AgreementProposal;
import com.aemotionalline.domain.negotiation.DiscussionProposal;
import com.aemotionalline.domain.negotiation.DiscussionTitle;
import com.aemotionalline.domain.negotiation.Proposal;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.constraint.ConstraintAssignment;
import com.aemotionalline.domain.constraint.ConstraintChangeRequest;
import com.aemotionalline.domain.constraint.ConstraintChangeRequestStatus;
import com.aemotionalline.domain.constraint.ConstraintChangeRequestId;
import com.aemotionalline.domain.constraint.ConstraintRequestRecord;
import com.aemotionalline.domain.constraint.ConstraintContext;
import com.aemotionalline.domain.constraint.ConstraintSet;

/**
 * Aggregate root of the couple's supervised exchange. Every state change goes through this class
 * so that the agreement, the therapist-approved constraints and the turn-taking rules
 * are always evaluated together and can never be bypassed by touching a {@link Discussion} directly.
 */
public class Conversation
{
	private final ConversationId id;
	private final Couple couple ;
	// The server's time: the caller can't fake when a message was sent or read (a reply delay depends on it).
	private final Clock clock;
	private final ConversationGraph conversationGraph;
	// Every negotiation that took effect, in order (spec 1.3.1); a negotiation can be used only once.
	private final List<NegotiationRecord> negotiationHistory;
	// Negotiations that ended without an agreement, in their own section of the archive.
	private final List<FailedNegotiationRecord> failedNegotiations;
	
	private final Set<Discussion> discussions;
	
	private Proposal agreement;
	private ConstraintSet constraintSet;
	private final List<ConstraintChangeRequest> constraintRequests;
	
	private Conversation(ConversationId id, Couple couple, Clock clock)
	{
		
		this.id = id;
		this.couple = couple;
		this.clock = clock;
		
		this.conversationGraph = new ConversationGraph();
		this.constraintSet = new ConstraintSet();
		this.constraintRequests = new ArrayList<>();
		this.discussions = new LinkedHashSet<>();
		this.negotiationHistory = new ArrayList<>();
		this.failedNegotiations = new ArrayList<>();
		
	}
	
	public static Conversation start(ConversationId id, Couple couple, Negotiation negotiation, Clock clock)
	{
		return start(id, couple, negotiation, clock, List.of());
	}
	
	/**
	 * Starts the conversation and records, in its section of failed negotiations, the couple's earlier attempts to start
	 * it that ended without an agreement: until the conversation existed there was no archive to keep them in. They are
	 * recorded in the order they were started, with the purpose of starting the conversation.
	 */
	public static Conversation start(ConversationId id, Couple couple, Negotiation negotiation, Clock clock, List<Negotiation> earlierFailedStarts)
	{
		Objects.requireNonNull(clock);
		Objects.requireNonNull(id);
		Objects.requireNonNull(couple);
		Objects.requireNonNull(negotiation);
		
		if (earlierFailedStarts == null || earlierFailedStarts.stream().anyMatch(Objects::isNull))
		{
			throw new DomainException("The earlier failed starts can't be null.");
		}
		
		Conversation conversation = new Conversation(id, couple, clock);
		
		// A conversation is never empty: the negotiation that authorised it also opens its first discussion,
		// the main one, which has no title because it is not a branch.
		conversation.open(negotiation, AgreementProposal.class, null);
		
		conversation.setAgreement(negotiation.getLastProposal());
		
		// The conversation is new: if one of them is rejected, nothing outside it has changed.
		List<Negotiation> inStartOrder = earlierFailedStarts.stream()
				.sorted(Comparator.comparing(Negotiation::getStartedAt))
				.toList();
		
		for (Negotiation failed : inStartOrder)
		{
			if (!(failed.getRequest() instanceof AgreementProposal))
			{
				throw new DomainException("Only a negotiation of the agreement could have started the conversation.");
			}
			
			conversation.recordFailed(failed, NegotiationRecord.Purpose.STARTED_CONVERSATION);
		}
		
		return conversation;
	}
	
	
	
	/**
	 * Reserved to the persistence layer: rebuilds a conversation from its stored facts, without replaying them. Those
	 * facts were validated when they were produced, so the rules aren't checked again; only the shape is (a main
	 * discussion, a first negotiation that started the conversation, every message in the sending order once). The
	 * graph, the agreement in force and the constraints in force are derived. The conversation then goes on as before.
	 */
	public static Conversation restore(ConversationSnapshot snapshot, Clock clock)
	{
		if (snapshot == null || clock == null)
		{
			throw new DomainException("A snapshot and a clock are required to restore a conversation.");
		}
		
		if (snapshot.discussions().isEmpty() || snapshot.negotiationHistory().isEmpty()
				|| snapshot.negotiationHistory().getFirst().purpose() != NegotiationRecord.Purpose.STARTED_CONVERSATION)
		{
			throw new DomainException("A conversation has a main discussion and the negotiation that started it.");
		}
		
		Conversation conversation = new Conversation(snapshot.id(), snapshot.couple(), clock);
		
		snapshot.discussions().forEach(discussion -> conversation.discussions.add(Discussion.restore(discussion)));
		
		Map<MessageId, Message> sent = new HashMap<>();
		conversation.discussions.forEach(discussion -> discussion.getMessages().forEach(message -> sent.put(message.getId(), message)));
		
		if (snapshot.sendingOrder().size() != sent.size() || !sent.keySet().containsAll(snapshot.sendingOrder()))
		{
			throw new DomainException("The sending order must list every message of the conversation once.");
		}
		
		snapshot.sendingOrder().forEach(messageId -> conversation.conversationGraph.addAllParagraphsInMessage(sent.get(messageId)));
		
		conversation.negotiationHistory.addAll(snapshot.negotiationHistory());
		conversation.failedNegotiations.addAll(snapshot.failedNegotiations());
		
		conversation.agreement = snapshot.negotiationHistory().stream()
				.filter(record -> record.purpose() != NegotiationRecord.Purpose.OPENED_DISCUSSION)
				.reduce((first, second) -> second)
				.get()
				.finalProposal();
		
		snapshot.constraintRequests().forEach(record -> conversation.constraintRequests.add(ConstraintChangeRequest.restore(record, snapshot.couple())));
		
		snapshot.constraintRequests().stream()
				.filter(record -> record.status() == ConstraintChangeRequestStatus.APPLIED)
				.reduce((first, second) -> second)
				.ifPresent(last -> conversation.constraintSet = ConstraintSet.restore(last.assignments()));
		
		return conversation;
	}
	
	public ConversationId getId()
	{
		return id;
	}


	public Couple getCouple()
	{
		return couple;
	}


	public Proposal getAgreement()
	{
		return agreement;
	}	
		
	public ConversationGraph getConversationGraph()
	{
		return conversationGraph;
	}


	// A copy: the live set is changed only by a request the conversation applies.
	public List<ConstraintAssignment> getConstraintAssignments() 
	{
		return constraintSet.getAssignments();
	}
	
	public List<Discussion> getDiscussions()
	{
		return List.copyOf(discussions);
	}
	
	private void setAgreement(Proposal agreement) 
	{
		this.agreement = agreement;
	}
	
	public void modifyAgreement(Negotiation negotiation)
	{	
		ensureNegotiationOfThisCouple(negotiation);

	    if (!negotiation.isAccepted())
	    {
	        throw new DomainException("Cannot modify agreement with an unaccepted negotiation.");
	    }
		
		if (!negotiation.getLastProposal().isAccepted())
		{
			throw new DomainException("Can't modify agreement if the negotiation's proposal is not accepted.");
		}
		
		ensureProposalKind(negotiation, AgreementProposal.class);
		
		// Archiving first rejects a negotiation that was already used before the agreement is touched.
		archive(negotiation, NegotiationRecord.Purpose.MODIFIED_AGREEMENT, null);
		
		setAgreement(negotiation.getLastProposal());
	}
	

	/**
	 * Opens a new discussion that doesn't start from a paragraph. It requires a negotiation of title and subtitle
	 * ({@link DiscussionProposal}) the partners have both settled, so no branch can start on a text one of them has not accepted.
	 */
	public void openDiscussion(Negotiation negotiation)
	{
		open(negotiation, DiscussionProposal.class, null);
	}
	
	/**
	 * Opens a new topic-specific branch ("discorso specifico") that expands the topic exposed in a paragraph already
	 * sent and not expanded yet (one discussion per paragraph). The origin is checked before anything changes, so a rejected
	 * request leaves the conversation untouched.
	 */
	public void openDiscussion(Negotiation negotiation, ParagraphId originParagraphId)
	{
		if (originParagraphId == null)
		{
			throw new DomainException("The paragraph to expand can't be null.");
		}
		
		if (!conversationGraph.containsParagraph(originParagraphId))
		{
			throw new DomainException("A discussion can only expand a paragraph that was already sent.");
		}
		
		if (discussions.stream().anyMatch(other -> other.getOriginParagraphId().equals(Optional.of(originParagraphId))))
		{
			throw new DomainException("A paragraph can be expanded by only one discussion.");
		}
		
		open(negotiation, DiscussionProposal.class, originParagraphId);
	}
	
	// The kind of proposal tells what the negotiation was for: an agreement text can't title a discussion, nor the reverse.
	private void open(Negotiation negotiation, Class<? extends Proposal> kind, ParagraphId originParagraphId)
	{
		ensureNegotiationOfThisCouple(negotiation);

	    if (!negotiation.isAccepted())
	    {
	        throw new DomainException("Cannot open new discussion with an unaccepted negotiation.");
	    }
		
		if (!negotiation.getLastProposal().isAccepted())
		{
			throw new DomainException("Can't open a new discussion if the negotiation's proposal is not accepted.");
		}
		
		ensureProposalKind(negotiation, kind);
		
		DiscussionId discussionId = generateDiscussionId();
		boolean mainDiscussion = kind == AgreementProposal.class;
		
		// Archiving first rejects a negotiation that was already used before any discussion is added.
		archive(negotiation, mainDiscussion ? NegotiationRecord.Purpose.STARTED_CONVERSATION : NegotiationRecord.Purpose.OPENED_DISCUSSION, discussionId);
		
		DiscussionTitle title = mainDiscussion ? null : (DiscussionTitle) negotiation.getLastProposal().getContent();
		
		addDiscussion(new Discussion(discussionId, title, originParagraphId));
	}
	
	private void archive(Negotiation negotiation, NegotiationRecord.Purpose purpose, DiscussionId discussionId)
	{
		ensureNotRecorded(negotiation);
		
		negotiationHistory.add(new NegotiationRecord(negotiation.getId(), purpose, Optional.ofNullable(discussionId), clock.instant(),
				negotiation.getProposals()));
	}
	
	private void ensureNotRecorded(Negotiation negotiation)
	{
		boolean recorded = negotiationHistory.stream().anyMatch(record -> record.negotiationId().equals(negotiation.getId()))
				|| failedNegotiations.stream().anyMatch(record -> record.negotiationId().equals(negotiation.getId()));
		
		if (recorded)
		{
			throw new DomainException(negotiation.getId() + " was already used in this conversation.");
		}
	}
	
	/**
	 * Keeps, in its own section of the archive, a negotiation of this conversation that ended without an agreement
	 * (refused or abandoned): a failed attempt to change the agreement or to open a discussion. Only the history is
	 * kept: the proposals that were sent, or the request when none was. The negotiation that should have started the
	 * conversation can't get here (the conversation doesn't exist): the persistence layer keeps it.
	 */
	public void recordFailedNegotiation(Negotiation negotiation)
	{
		ensureNegotiationOfThisCouple(negotiation);
		
		recordFailed(negotiation, negotiation.getRequest() instanceof AgreementProposal
				? NegotiationRecord.Purpose.MODIFIED_AGREEMENT
				: NegotiationRecord.Purpose.OPENED_DISCUSSION);
	}
	
	private void recordFailed(Negotiation negotiation, NegotiationRecord.Purpose attempted)
	{
		ensureNegotiationOfThisCouple(negotiation);
		
		if (!negotiation.isFailed())
		{
			throw new DomainException("Only a negotiation that ended without an agreement can be recorded as failed.");
		}
		
		ensureNotRecorded(negotiation);
		
		List<Proposal> proposals = negotiation.getProposals().isEmpty() ? List.of(negotiation.getRequest()) : negotiation.getProposals();
		
		FailedNegotiationRecord.Outcome outcome = negotiation.getNegotiationStatus() == NegotiationStatus.REFUSED
				? FailedNegotiationRecord.Outcome.REFUSED
				: FailedNegotiationRecord.Outcome.ABANDONED;
		
		failedNegotiations.add(new FailedNegotiationRecord(negotiation.getId(), attempted, outcome, negotiation.getClosedBy().get(),
				negotiation.getStartedAt(), negotiation.getClosedAt().get(), proposals));
	}
	
	/** The negotiations of this conversation that ended without an agreement, in the order they were recorded. */
	public List<FailedNegotiationRecord> getFailedNegotiations()
	{
		return List.copyOf(failedNegotiations);
	}
	
	/** The negotiations that took effect, in the order they did: the agreement's history and the discussions' titles. */
	public List<NegotiationRecord> getNegotiationHistory()
	{
		return List.copyOf(negotiationHistory);
	}
	
	private void ensureProposalKind(Negotiation negotiation, Class<? extends Proposal> kind)
	{
		if (!kind.isInstance(negotiation.getLastProposal()))
		{
			throw new DomainException("The negotiation doesn't carry the kind of proposal required here: " + kind.getSimpleName() + ".");
		}
	}

	private void ensureNegotiationOfThisCouple(Negotiation negotiation)
	{
		if (negotiation == null)
		{
			throw new DomainException("A negotiation is required.");
		}
		
		if (!couple.equals(negotiation.getCouple()))
		{
			throw new DomainException("The negotiation belongs to a different couple.");
		}
	}

	public boolean canSendMessage(UserId userId)
	{
		return  couple.isPartner(userId) && agreement.isAccepted();	
	}
	
	public void ensureCanSendMessage(UserId userId)
	{
		if (!canSendMessage(userId))
		{
			throw new DomainException("User cannot send messages in the current conversation state");
		}
	}
	
	/**
	 * Constraints are imposed by the therapist but bind the partners, so a request takes effect only once both partners
	 * have approved it (spec 1.3.2). The conversation owns the requests: it keeps every one, however it ends, so the
	 * archive shows each notification, and only one can be pending at a time (two requests each replacing the whole set
	 * would contradict each other).
	 */
	public ConstraintChangeRequestId proposeConstraints(UserId therapist, List<ConstraintAssignment> assignments)
	{
		if (constraintRequests.stream().anyMatch(ConstraintChangeRequest::isPending))
		{
			throw new DomainException("Another constraint change request is still pending.");
		}
		
		ConstraintChangeRequest request = new ConstraintChangeRequest(ConstraintChangeRequestId.random(), therapist, assignments, couple, clock.instant());
		
		constraintRequests.add(request);
		
		return request.id();
	}
	
	// The second approval is the consent of both partners: the constraints take effect at once.
	public void approveConstraints(ConstraintChangeRequestId requestId, UserId partner)
	{
		ConstraintChangeRequest request = findConstraintRequest(requestId);
		
		request.approve(partner);
		
		if (request.isFullyApproved())
		{
			request.applyTo(constraintSet, clock.instant());
		}
	}
	
	public void rejectConstraints(ConstraintChangeRequestId requestId, UserId partner)
	{
		findConstraintRequest(requestId).reject(partner, clock.instant());
	}
	
	public void withdrawConstraints(ConstraintChangeRequestId requestId, UserId therapist)
	{
		findConstraintRequest(requestId).withdraw(therapist, clock.instant());
	}
	
	/** Every constraint change request, in the order it was proposed, as immutable pictures. */
	public List<ConstraintRequestRecord> getConstraintRequestHistory()
	{
		return constraintRequests.stream().map(ConstraintChangeRequest::toRecord).toList();
	}
	
	private ConstraintChangeRequest findConstraintRequest(ConstraintChangeRequestId requestId)
	{
		return constraintRequests.stream()
				.filter(request -> request.id().equals(requestId))
				.findFirst()
				.orElseThrow(() -> new DomainException("No such constraint change request in this conversation."));
	}
	
	/**
	 * Reading is an operation of its own: a partner reads a message the other partner sent, the constraints that
	 * restrict reading are checked, and the instant of the first read is recorded (a reply needs it, and so does a
	 * reply-delay constraint). The therapist consults freely, with no constraint and no record; a partner's own
	 * messages are not "read". Nothing changes if a check fails. The instant comes from the conversation's clock;
	 * the context is needed only when the reader has constraints on reading.
	 */
	public void readMessage(UserId reader, DiscussionId discussionId, MessageId messageId, ConstraintContext context)
	{
		if (reader == null || messageId == null)
		{
			throw new DomainException("A reader and a message are required.");
		}
		
		Instant now = clock.instant();
		
		Discussion discussion = findDiscussion(discussionId);
		
		Message message = discussion.getMessages().stream()
				.filter(sent -> sent.getId().equals(messageId))
				.findFirst()
				.orElseThrow(() -> new DomainException("The message is not in this discussion."));
		
		if (couple.isTherapist(reader) || reader.equals(message.getSenderId()))
		{
			return;
		}
		
		if (!couple.isPartner(reader))
		{
			throw new DomainException("User does not belong to the couple");
		}
		
		constraintSet.ensureSatisfiedBy(reader, context == null ? null : context.withNow(now), ConstraintScope.READ);
		
		discussion.markRead(messageId, now);
	}
	
	/**
	 * The single entry point for sending. Every check runs before anything changes (message shape,
	 * sender permission, discussion, turn, constraints, then the paragraphs), so a rejected message
	 * leaves the discussion and the graph exactly as they were.
	 */
	public void sendMessage(Message message,DiscussionId discussionid, ConstraintContext context) 
	{
		if (message == null)
		{
			throw new DomainException("Message can't be null.");
		}
		
		if (message.isSealed())
		{
			throw new DomainException("The message was already sent.");
		}
		
		UserId sender = message.getSenderId();
		
		message.ensureReadyToSend();

	    ensureCanSendMessage(sender);
	    
	    Discussion discussion = findDiscussion(discussionid);
	    
	    // A closed discussion is read-only.
	    discussion.ensureOpen();
	    
	    // Turn-taking is checked here, before any change: the same partner cannot write twice without a reply.
	    if (sender.equals(discussion.getLastSender()))
	    {
	    	throw new DomainException("A new message can't be sent if an answer wasn't received.");
	    }
	    
	    // A reply requires having read the message being answered (the last of the discussion).
	    Instant now = clock.instant();
	    ConstraintContext checkedContext = context == null ? null : context.withNow(now);
	    List<Message> sentMessages = discussion.getMessages();
	    
	    if (!sentMessages.isEmpty())
	    {
	    	Optional<Instant> readAt = discussion.getReadAt(sentMessages.getLast().getId());
	    	
	    	if (readAt.isEmpty())
	    	{
	    		throw new DomainException("The message being answered must be read before replying.");
	    	}
	    	
	    	checkedContext = checkedContext == null ? null : checkedContext.withReadAt(readAt.get());
	    }

	    constraintSet.ensureSatisfiedBy(sender, checkedContext, ConstraintScope.WRITE);
	    
	    ensureValidParagraphs(message, discussion);
	    
	    // Computed before anything changes, so the draft's answers are counted from the message itself.
	    List<ParagraphId> skippedQuestions = questionsLeftUnanswered(message, discussion)
	    		.stream()
	    		.map(QuestionParagraph::getId)
	    		.toList();
	    
	    // From here on nothing can fail: the message is valid and the conversation can change.
	    discussion.setLastSender(sender);
	    discussion.addMessage(message);
	    
	    conversationGraph.addAllParagraphsInMessage(message);
	    
	    message.seal(skippedQuestions, now);
	}
	
	/**
	 * The rules on the paragraphs of a message being sent. A paragraph id identifies one paragraph of the
	 * conversation forever: it can't be reused by a later message, nor appear twice in the same one.
	 * A reference may only point to a paragraph of the message being answered, i.e. the last message of the
	 * discussion: never to an unsent paragraph, another discussion, an older message or the message itself.
	 * The one exception is the first message of a discussion opened from a paragraph: it answers that paragraph,
	 * so every paragraph of it must refer to it, and to nothing else.
	 * Since every reference then points to an older paragraph, references can't form a cycle.
	 * Only a pointed paragraph may answer a question (except in that first message, where any paragraph may refer
	 * to the question the discussion expands); a pointed paragraph always answers one question (PointedParagraph
	 * guarantees it at creation), and each question receives at most one pointed answer. As answers can only
	 * target the message being answered, counting them inside this message is enough.
	 * A self-citation must point to a paragraph its sender already sent, within the topic of the citing paragraph,
	 * i.e. the topic of the paragraphs it refers to: a paragraph that answers nothing has no topic to cite from.
	 */
	/**
	 * Preview for the "you are leaving questions unanswered" warning (spec 1.3.3): the questions of the message being
	 * answered that this draft would not answer. It changes nothing and does not validate the draft; sending it
	 * records the same list in the message. A question already answered by a discussion opened from it doesn't count.
	 */
	public List<QuestionParagraph> questionsLeftUnansweredBy(Message message, DiscussionId discussionId)
	{
		if (message == null)
		{
			throw new DomainException("Message can't be null.");
		}
		
		return questionsLeftUnanswered(message, findDiscussion(discussionId));
	}
	
	/**
	 * Preview for the "this paragraph has a specific discussion" pop-up (spec 1.3.3): the discussions that keep this draft
	 * from being sent in the given discussion, because a paragraph it refers to is the origin of a specific discussion
	 * (the draft must be written there instead). It changes nothing and does not validate the draft; sending it
	 * fails for the same reason.
	 */
	public List<DiscussionId> discussionsBlockingReplyTo(Message message, DiscussionId discussionId)
	{
		if (message == null)
		{
			throw new DomainException("Message can't be null.");
		}
		
		Discussion discussion = findDiscussion(discussionId);
		Set<DiscussionId> blocking = new LinkedHashSet<>();
		
		for (Paragraph paragraph : message.getParagraphs())
		{
			for (Paragraph reference : paragraph.getReferences())
			{
				blocking.addAll(discussionsBlockingReplyTo(reference.getId(), discussion));
			}
		}
		
		return List.copyOf(blocking);
	}
	
	// A paragraph that is the origin of a discussion is answered only there: the first message of that discussion
	// (and of any other opened from the same paragraph) is the one place where it may be referred to again.
	private List<DiscussionId> discussionsBlockingReplyTo(ParagraphId paragraphId, Discussion discussion)
	{
		boolean expandsIt = discussion.getMessages().isEmpty() && discussion.getOriginParagraphId().equals(Optional.of(paragraphId));
		
		if (expandsIt)
		{
			return List.of();
		}
		
		return discussions.stream()
				.filter(other -> other.getOriginParagraphId().equals(Optional.of(paragraphId)))
				.map(Discussion::getId)
				.toList();
	}
	
	private List<QuestionParagraph> questionsLeftUnanswered(Message message, Discussion discussion)
	{
		List<Message> previousMessages = discussion.getMessages();
		
		// The first message of a discussion answers no message (the origin, if any, is expanded, not skipped).
		if (previousMessages.isEmpty())
		{
			return List.of();
		}
		
		Set<ParagraphId> answeredByDraft = new HashSet<>();
		
		for (Paragraph paragraph : message.getParagraphs())
		{
			if (paragraph.getType() == ParagraphType.POINTED)
			{
				paragraph.getReferences().forEach(reference -> answeredByDraft.add(reference.getId()));
			}
		}
		
		return previousMessages.getLast().getParagraphs()
				.stream()
				.filter(paragraph -> paragraph.getType() == ParagraphType.QUESTION)
				.map(paragraph -> (QuestionParagraph) paragraph)
				.filter(question -> !answeredByDraft.contains(question.getId()))
				.filter(question -> !Analyzer.isAnswered(question, this))
				.toList();
	}
	
	private void ensureValidParagraphs(Message message, Discussion discussion)
	{
		Optional<ParagraphId> origin = discussion.getOriginParagraphId();
		boolean answersTheOrigin = discussion.getMessages().isEmpty() && origin.isPresent();
		
		Set<ParagraphId> answerable = answersTheOrigin ? Set.of(origin.get()) : paragraphIdsOfLastMessage(discussion);
		
		Set<ParagraphId> idsInMessage = new HashSet<>();
		Set<ParagraphId> questionsAnswered = new HashSet<>();
		
		for (Paragraph paragraph : message.getParagraphs())
		{
			if (!idsInMessage.add(paragraph.getId()))
			{
				throw new DomainException("Paragraph " + paragraph.getId().value() + " appears twice in the message.");
			}
			
			if (conversationGraph.containsParagraph(paragraph.getId()))
			{
				throw new DomainException("Paragraph " + paragraph.getId().value() + " was already sent.");
			}
			
			if (answersTheOrigin && paragraph.getReferences().stream().noneMatch(reference -> reference.getId().equals(origin.get())))
			{
				throw new DomainException("Paragraph " + paragraph.getId().value()
						+ " must refer to the paragraph its discussion expands.");
			}
			
			for (Paragraph reference : paragraph.getReferences())
			{
				if (!answerable.contains(reference.getId()))
				{
					throw new DomainException("Paragraph " + paragraph.getId().value()
							+ " can only refer to paragraphs of the message being answered.");
				}
				
				List<DiscussionId> blocking = discussionsBlockingReplyTo(reference.getId(), discussion);
				
				if (!blocking.isEmpty())
				{
					throw new DomainException("Paragraph " + reference.getId().value() + " can't be answered here: it is expanded by discussion "
							+ blocking.getFirst().discussionNumber() + ".");
				}
				
				boolean expandsThisQuestion = answersTheOrigin && reference.getId().equals(origin.get());
				
				if (reference.getType() == ParagraphType.QUESTION && paragraph.getType() != ParagraphType.POINTED && !expandsThisQuestion)
				{
					throw new DomainException("Only pointed paragraphs can answer a question: paragraph "
							+ paragraph.getId().value() + " refers to question " + reference.getId().value() + ".");
				}
				
				if (paragraph.getType() == ParagraphType.POINTED && !questionsAnswered.add(reference.getId()))
				{
					throw new DomainException("Question " + reference.getId().value() + " can receive only one answer.");
				}
			}
			
			ensureValidSelfCitations(paragraph, message.getSenderId());
		}
	}
	
	// Self-citations are exempt from the "message being answered" rule: they may point to any earlier paragraph of the
	// sender, as long as it belongs to the topic of the citing paragraph (the topic of the paragraphs it refers to).
	private void ensureValidSelfCitations(Paragraph paragraph, UserId sender)
	{
		if (paragraph.getSelfCitations().isEmpty())
		{
			return;
		}
		
		Set<ParagraphId> topic = new HashSet<>();
		
		for (Paragraph reference : paragraph.getReferences())
		{
			for (Paragraph inTopic : conversationGraph.findConversationBranchContaining(reference))
			{
				topic.add(inTopic.getId());
			}
		}
		
		for (Paragraph cited : paragraph.getSelfCitations())
		{
			if (!sender.equals(authorOf(cited.getId())))
			{
				throw new DomainException("Paragraph " + paragraph.getId().value()
						+ " can only cite paragraphs its sender already sent.");
			}
			
			if (!topic.contains(cited.getId()))
			{
				throw new DomainException("Paragraph " + paragraph.getId().value()
						+ " can only cite paragraphs of its own topic.");
			}
		}
	}
	
	// The sender of the message that contains the paragraph, or null if no sent message contains it.
	private UserId authorOf(ParagraphId paragraphId)
	{
		for (Discussion discussion : discussions)
		{
			for (Message sent : discussion.getMessages())
			{
				for (Paragraph paragraph : sent.getParagraphs())
				{
					if (paragraph.getId().equals(paragraphId))
					{
						return sent.getSenderId();
					}
				}
			}
		}
		
		return null;
	}
	
	// The message being answered is the last one of the discussion; the first message of a discussion answers nothing.
	private static Set<ParagraphId> paragraphIdsOfLastMessage(Discussion discussion)
	{
		Set<ParagraphId> ids = new HashSet<>();
		
		List<Message> messages = discussion.getMessages();
		
		if (!messages.isEmpty())
		{
			for (Paragraph paragraph : messages.getLast().getParagraphs())
			{
				ids.add(paragraph.getId());
			}
		}
		
		return ids;
	}
	
	// CLOSING A SPECIFIC DISCUSSION (nobody closes it alone; see Discussion for the state rules).
	
	/** A partner asks the other to close a specific discussion; any time, whatever the turn. */
	public void requestClosing(DiscussionId discussionId, UserId partner)
	{
		ensurePartner(partner);
		
		specificDiscussion(discussionId).requestClosing(partner, clock.instant());
	}
	
	/** The other partner accepts: the discussion closes now, and each partner then declares its outcome. */
	public void acceptClosing(DiscussionId discussionId, UserId partner)
	{
		ensurePartner(partner);
		
		specificDiscussion(discussionId).answerClosing(partner, true, clock.instant());
	}
	
	public void refuseClosing(DiscussionId discussionId, UserId partner)
	{
		ensurePartner(partner);
		
		specificDiscussion(discussionId).answerClosing(partner, false, clock.instant());
	}
	
	public void withdrawClosing(DiscussionId discussionId, UserId partner)
	{
		ensurePartner(partner);
		
		specificDiscussion(discussionId).withdrawClosing(partner, clock.instant());
	}
	
	/**
	 * The special request: a partner who already tried, without success, to close the discussion with the other asks the
	 * therapist, with a justification. The therapist sees every earlier closing request with its instants.
	 */
	public void requestTherapistClosing(DiscussionId discussionId, UserId partner, String justification)
	{
		ensurePartner(partner);
		
		specificDiscussion(discussionId).requestTherapistClosing(partner, justification, clock.instant());
	}
	
	public void confirmTherapistClosing(DiscussionId discussionId, UserId therapist)
	{
		ensureTherapist(therapist);
		
		specificDiscussion(discussionId).decideTherapistClosing(true, clock.instant());
	}
	
	public void refuseTherapistClosing(DiscussionId discussionId, UserId therapist)
	{
		ensureTherapist(therapist);
		
		specificDiscussion(discussionId).decideTherapistClosing(false, clock.instant());
	}
	
	/** Once the discussion is closed, each partner declares, once, whether its topic was resolved or stalled, and why. */
	public void declareOutcome(DiscussionId discussionId, UserId partner, OutcomeDeclaration.Outcome outcome, String reason)
	{
		ensurePartner(partner);
		
		specificDiscussion(discussionId).declareOutcome(partner, outcome, reason, clock.instant());
	}
	
	// The main discussion is the conversation itself: it is never closed.
	private Discussion specificDiscussion(DiscussionId discussionId)
	{
		Discussion discussion = findDiscussion(discussionId);
		
		if (discussion.equals(discussions.iterator().next()))
		{
			throw new DomainException("The main discussion can't be closed.");
		}
		
		return discussion;
	}
	
	private void ensurePartner(UserId user)
	{
		if (!couple.isPartner(user))
		{
			throw new DomainException("Only a partner of the couple can do this.");
		}
	}
	
	private void ensureTherapist(UserId user)
	{
		if (!couple.isTherapist(user))
		{
			throw new DomainException("Only the couple's therapist can do this.");
		}
	}
	
	public Discussion findDiscussion(DiscussionId discussionId)
	{
		return	discussions.stream()
				.filter(d -> d.getId().equals(discussionId))
				.findFirst()
				.orElseThrow(() -> new DomainException("Discussion not found"));
	}
	
	
	private void addDiscussion(Discussion discussion)
	{
		if(!this.discussions.add(discussion))
		{
			throw new DomainException("Discussion already exists in the conversation.");
		}
	}
	
	
	private DiscussionId generateDiscussionId()
	{
		// Discussions are never removed, so their count is the next number: no gaps, unlike the negotiation history, which also records agreement changes.
		return new DiscussionId(this.id, this.discussions.size());
	}
	
}
