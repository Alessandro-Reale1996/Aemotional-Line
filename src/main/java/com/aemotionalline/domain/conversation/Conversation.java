package com.aemotionalline.domain.conversation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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
	private final Long id;
	private final Couple couple ;
	private final ConversationGraph conversationGraph;
	private final NegotiationArchive negotiationArchive;
	
	private final Set<Discussion> discussions;
	
	private Proposal agreement;
	private ConstraintSet constraintSet;
	
	private Conversation(Long id, Couple couple)
	{
		
		this.id = id;
		this.couple = couple;
		
		this.conversationGraph = new ConversationGraph();
		this.constraintSet = new ConstraintSet();
		this.discussions = new HashSet<>();
		this.negotiationArchive = new NegotiationArchive();
		
	}
	
	public static Conversation start(Long id, Couple couple, Negotiation negotiation)
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
	
	
	
	public Long getId()
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
			
		setAgreement(negotiation.getLastProposal());
		
		this.negotiationArchive.add(negotiation);
		
		}
		else
		{
			throw new DomainException("Can't modify agreement if the negotiation's proposal is not accepted.");
		}
	}
	

	/**
	 * Opens a new topic-specific branch ("discorso specifico"). Like a change of agreement, it requires
	 * a negotiation the partners have both settled, so no branch can start on a text one of them has not accepted.
	 */
	public void openDiscussion(Negotiation negotiation)
	{
		ensureNegotiationOfThisCouple(negotiation);

	    if (!negotiation.isAccepted())
	    {
	        throw new DomainException("Cannot open new discussion with an unaccepted negotiation.");
	    }
		
		if (negotiation.getLastProposal().isAccepted())
		{
		
		// The id is derived from the archive size, so the negotiation must be archived only after the id is generated.
		Discussion discussion = new Discussion(generateDiscussionId(), negotiation.getLastProposal());
		
		addDiscussion(discussion);
		
		negotiationArchive.add(negotiation);
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
	    if (!request.isFullyApproved(couple)) 
	    {
	        throw new DomainException("Constraints not fully approved");
	    }

	    constraintSet.replaceWith(request.assignments());
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
	    
	    if (!discussion.getMessages().contains(message))
		{
			throw new DomainException("Message was not added to relative discussion.");
		}
	    
	    verifyParagraphsWereAddedToGraph(message);
	}
	
	/**
	 * The rules on the paragraphs of a message being sent. A paragraph id identifies one paragraph of the
	 * conversation forever: it can't be reused by a later message, nor appear twice in the same one.
	 * A reference may only point to a paragraph of the message being answered, i.e. the last message of the
	 * discussion: never to an unsent paragraph, another discussion, an older message or the message itself.
	 * Since every reference then points to an older paragraph, references can't form a cycle.
	 * A pointed paragraph must answer a question, and each question receives at most one pointed answer;
	 * as answers can only target the message being answered, counting them inside this message is enough.
	 */
	private void ensureValidParagraphs(Message message, Discussion discussion)
	{
		Set<ParagraphId> answerable = paragraphIdsOfLastMessage(discussion);
		
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
			
			for (Paragraph reference : paragraph.getReferences())
			{
				if (!answerable.contains(reference.getId()))
				{
					throw new DomainException("Paragraph " + paragraph.getId().value()
							+ " can only refer to paragraphs of the message being answered.");
				}
				
				if (paragraph.getType() == ParagraphType.POINTED && !questionsAnswered.add(reference.getId()))
				{
					throw new DomainException("Question " + reference.getId().value() + " can receive only one answer.");
				}
			}
			
			// Citations are exempt from the "message being answered" rule: they may point to any earlier paragraph,
			// as long as it was already sent and written by the sender.
			for (Paragraph cited : paragraph.getCitations())
			{
				if (!message.getSenderId().equals(authorOf(cited.getId())))
				{
					throw new DomainException("Paragraph " + paragraph.getId().value()
							+ " can only cite paragraphs its sender already sent.");
				}
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
	
	private void verifyParagraphsWereAddedToGraph(Message message)
	{	
		for(Paragraph paragraph : message.getParagraphs())
		{
			if(!this.conversationGraph.getParagraphs().contains(paragraph))
			{
				throw new DomainException("Message's paragraphs wasn't added to graph.");
			}
		}		
	}
	
	private void addDiscussion(Discussion discussion)
	{
		if(!this.discussions.add(discussion))
		{
			throw new DomainException("Discussion already exists in the conversation.");
		};
		
		if(!discussions.contains(discussion))
		{
			throw new DomainException("Discussion was not added to the conversation.");
		}
	}
	
	
	private DiscussionId generateDiscussionId()
	{
		return new DiscussionId(this.id, this.negotiationArchive.getNegotiations().size());
	}
	
}
