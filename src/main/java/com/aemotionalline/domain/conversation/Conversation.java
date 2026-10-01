package com.aemotionalline.domain.conversation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.Paragraph;
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
	 * The single entry point for sending. Checks run cheapest and most fundamental first
	 * (message shape, sender permission, constraints) so that a rejected message leaves the discussion untouched.
	 */
	public void sendMessage(Message message,DiscussionId discussionid, ConstraintContext context) 
	{
		if (message == null)
		{
			throw new DomainException("Message can't be null.");
		}
		
		UserId sender = message.getSenderId();
		
		message.ensureReadyToSend();

	    ensureCanSendMessage(sender);

	    constraintSet.ensureSatisfiedBy(message.getSenderId(), context);
	    
	    Discussion discussion = findDiscussion(discussionid);
	    
	    // Turn-taking is checked by the discussion itself: the same partner cannot write twice without a reply.
	    discussion.setLastSender(sender);
	    discussion.addMessage(message);
	    
	    conversationGraph.addAllParagraphsInMessage(message);
	    
	    if (!discussion.getMessages().contains(message))
		{
			throw new DomainException("Message was not added to relative discussion.");
		}
	    
	    verifyParagraphsWereAddedToGraph(message);
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
