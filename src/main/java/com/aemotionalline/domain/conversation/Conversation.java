package com.aemotionalline.domain.conversation;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
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
import com.aemotionalline.domain.negotiation.NegotiationArchive;
import com.aemotionalline.domain.negotiation.AgreementProposal;
import com.aemotionalline.domain.negotiation.DiscussionProposal;
import com.aemotionalline.domain.negotiation.DiscussionTitle;
import com.aemotionalline.domain.negotiation.Proposal;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.constraint.ConstraintChangeRequest;
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
	private final ConversationGraph conversationGraph;
	private final NegotiationArchive negotiationArchive;
	
	private final Set<Discussion> discussions;
	
	private Proposal agreement;
	private ConstraintSet constraintSet;
	
	private Conversation(ConversationId id, Couple couple)
	{
		
		this.id = id;
		this.couple = couple;
		
		this.conversationGraph = new ConversationGraph();
		this.constraintSet = new ConstraintSet();
		this.discussions = new LinkedHashSet<>();
		this.negotiationArchive = new NegotiationArchive();
		
	}
	
	public static Conversation start(ConversationId id, Couple couple, Negotiation negotiation)
	{
		Objects.requireNonNull(id);
		Objects.requireNonNull(couple);
		Objects.requireNonNull(negotiation);
		
		Conversation conversation = new Conversation(id, couple);
		
		// A conversation is never empty: the negotiation that authorised it also opens its first discussion,
		// the main one, which has no title because it is not a branch.
		conversation.open(negotiation, AgreementProposal.class, null);
		
		conversation.setAgreement(negotiation.getLastProposal());
		
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


	public ConstraintSet getConstraintSet() 
	{
		return constraintSet;
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
		this.negotiationArchive.add(negotiation);
		
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
		
		// Archiving first rejects a negotiation that was already used before any discussion is added.
		negotiationArchive.add(negotiation);
		
		DiscussionTitle title = kind == DiscussionProposal.class ? ((DiscussionTitle) negotiation.getLastProposal().getContent()) : null;
		
		addDiscussion(new Discussion(generateDiscussionId(), title, originParagraphId));
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
	 * Constraints are imposed by the therapist but bind the partners, so they take effect only
	 * once both partners have approved the request.
	 */
	public void applyConstraints(ConstraintChangeRequest request)
	{
	    if (request == null)
	    {
	        throw new DomainException("Constraint change request cannot be null.");
	    }

	    // The request was approved by its own couple's partners: it means nothing for another couple's conversation.
	    if (!couple.equals(request.couple()))
	    {
	        throw new DomainException("The constraint change request belongs to another couple.");
	    }

	    request.applyTo(constraintSet);
	}
	
	
	
	/**
	 * Reading is an operation of its own: a partner reads a message the other partner sent, the constraints that
	 * restrict reading are checked, and the instant of the first read is recorded (a reply needs it, and so does a
	 * reply-delay constraint). The therapist consults freely, with no constraint and no record; a partner's own
	 * messages are not "read". Nothing changes if a check fails. The context must carry {@code now}.
	 */
	public void readMessage(UserId reader, DiscussionId discussionId, MessageId messageId, ConstraintContext context)
	{
		if (reader == null || messageId == null || context == null || context.now() == null)
		{
			throw new DomainException("A reader, a message and a context with the current instant are required.");
		}
		
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
		
		constraintSet.ensureSatisfiedBy(reader, context, ConstraintScope.READ);
		
		discussion.markRead(messageId, context.now());
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
	    
	    // Turn-taking is checked here, before any change: the same partner cannot write twice without a reply.
	    if (sender.equals(discussion.getLastSender()))
	    {
	    	throw new DomainException("A new message can't be sent if an answer wasn't received.");
	    }
	    
	    // A reply requires having read the message being answered (the last of the discussion).
	    ConstraintContext checkedContext = context;
	    List<Message> sentMessages = discussion.getMessages();
	    
	    if (!sentMessages.isEmpty())
	    {
	    	Optional<Instant> readAt = discussion.getReadAt(sentMessages.getLast().getId());
	    	
	    	if (readAt.isEmpty())
	    	{
	    		throw new DomainException("The message being answered must be read before replying.");
	    	}
	    	
	    	checkedContext = context == null ? null : context.withReadAt(readAt.get());
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
	    
	    message.seal(skippedQuestions);
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
		// Discussions are never removed, so their count is the next number: no gaps, unlike the archive, which also records agreement changes.
		return new DiscussionId(this.id, this.discussions.size());
	}
	
}
