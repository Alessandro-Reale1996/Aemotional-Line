package com.aemotionalline.domain.conversation;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.message.ParagraphType;
import com.aemotionalline.domain.negotiation.Negotiation;
import com.aemotionalline.domain.negotiation.NegotiationArchive;
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
		
		// A conversation is never empty: the negotiation that authorised it also opens its first discussion.
		conversation.openDiscussion(negotiation);	
		
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
		
		if (negotiation.getLastProposal().isAccepted())
		{
			
		// Archiving first rejects a negotiation that was already used before the agreement is touched.
		this.negotiationArchive.add(negotiation);
		
		setAgreement(negotiation.getLastProposal());
		
		}
		else
		{
			throw new DomainException("Can't modify agreement if the negotiation's proposal is not accepted.");
		}
	}
	

	/**
	 * Opens a new discussion that doesn't start from a paragraph. Like a change of agreement, it requires
	 * a negotiation the partners have both settled, so no branch can start on a text one of them has not accepted.
	 */
	public void openDiscussion(Negotiation negotiation)
	{
		open(negotiation, null);
	}
	
	/**
	 * Opens a new topic-specific branch ("discorso specifico") that expands the topic exposed in a paragraph already
	 * sent. The origin is checked before anything changes, so a rejected request leaves the conversation untouched.
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
		
		open(negotiation, originParagraphId);
	}
	
	private void open(Negotiation negotiation, ParagraphId originParagraphId)
	{
		ensureNegotiationOfThisCouple(negotiation);

	    if (!negotiation.isAccepted())
	    {
	        throw new DomainException("Cannot open new discussion with an unaccepted negotiation.");
	    }
		
		if (negotiation.getLastProposal().isAccepted())
		{
		
		// Archiving first rejects a negotiation that was already used before any discussion is added.
		negotiationArchive.add(negotiation);
		
		addDiscussion(new Discussion(generateDiscussionId(), negotiation.getLastProposal(), originParagraphId));
		}
		else
		{
			throw new DomainException("Can't open a new discussion if the negotiation's proposal is not accepted.");
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

	    constraintSet.ensureSatisfiedBy(sender, context);
	    
	    ensureValidParagraphs(message, discussion);
	    
	    // From here on nothing can fail: the message is valid and the conversation can change.
	    discussion.setLastSender(sender);
	    discussion.addMessage(message);
	    
	    conversationGraph.addAllParagraphsInMessage(message);
	    
	    message.seal();
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
	 * to the question the discussion expands); a pointed paragraph must answer a question, and each question
	 * receives at most one pointed answer. As answers can only target the message being answered, counting them
	 * inside this message is enough.
	 * A self-citation must point to a paragraph its sender already sent, within the topic of the citing paragraph,
	 * i.e. the topic of the paragraphs it refers to: a paragraph that answers nothing has no topic to cite from.
	 */
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
			
			// "In risposta a ...": a pointed paragraph without a question has nothing to answer.
			if (paragraph.getType() == ParagraphType.POINTED && paragraph.getReferences().isEmpty())
			{
				throw new DomainException("Pointed paragraph " + paragraph.getId().value() + " must answer a question.");
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
