package com.aemotionalline.domain.conversation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.negotiation.Negotiation;
import com.aemotionalline.domain.negotiation.NegotiationArchive;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.negotiation.Proposal;
import com.aemotionalline.domain.user.UserId;
import com.sun.tools.javac.comp.ThisEscapeAnalyzer;
import com.aemotionalline.domain.constraint.ConstraintChangeRequest;
import com.aemotionalline.domain.constraint.ConstraintContext;
import com.aemotionalline.domain.constraint.ConstraintSet;

public class Conversation
{
	private final Long id;
	private final Couple couple ;
	private final ConversationGraph conversationGraph;
	private final NegotiationArchive NegotiationArchive;
	
	private final Set<Discussion> discussions;
	
	private Proposal agreement;
	private ConversationStatus status;
	private ConstraintSet constraintSet;
	
	public Conversation(Long id, Couple couple, Proposal agreement)
	{
		
        if (id == null) 
        {
            throw new DomainException("Conversation id cannot be null");
        }

        if (couple == null) 
        {
            throw new DomainException("Couple id cannot be null");
        }

        if (agreement == null)
        {
            throw new DomainException("Agreement cannot be null");
        }
		
		this.id = id;
		this.couple = couple;
		this.agreement = agreement;
		
		this.status = ConversationStatus.PENDING_AGREEMENT;
		this.conversationGraph = new ConversationGraph();
		this.constraintSet = new ConstraintSet();
		this.discussions = new HashSet<>();
		this.NegotiationArchive = new NegotiationArchive();
		
		opernDiscussion();
		
		if (constraintSet == null)
        {
            throw new DomainException("ConstraintSet cannot be null");
        }
		
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


	public ConversationStatus getStatus()
	{
		return status;
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
	
	
 
	public void opernDiscussion()
	{
		Proposal initialProposal = Proposal.create(null, null, null, null, null)
		
		Negotiation negotiation = 
				Negotiation.start
							(
							generateNegotiationId(), 
							couple, 
							initialProposal
							);
		
		Discussion discussion = new Discussion(generateDiscussionId(), negotiation.getLastProposal());
		
		discussions.add(discussion);
		
		NegotiationArchive.add(negotiation);
	}

	public boolean canSendMessage(UserId userId)
	{
		return  couple.isPartner(userId) && status == ConversationStatus.ACTIVE && agreement.isAccepted();	
	}
	
	public void ensureCanSendMessage(UserId userId)
	{
		if (!canSendMessage(userId))
		{
			throw new DomainException("User cannot send messages in the current conversation state");
		}
	}
	
	public void applyConstraints(ConstraintChangeRequest request) 
	{
	    if (!request.isFullyApproved(couple)) 
	    {
	        throw new DomainException("Constraints not fully approved");
	    }

	    constraintSet.replaceWith(request.assignments());
	}
	
	
	
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
	    
	    discussion.setLastSender(sender);
	    discussion.addMessage(message);
	    
	    conversationGraph.addAllParagraphsInMessage(message);
	    
	    if (!discussion.getMessages().contains(message))
		{
			throw new DomainException("Message was not added to relative discussion.");
		}
	    
	    verifyParagrapshWasAddedToGraph(message);
	}
	
	public Discussion findDiscussion(DiscussionId discussionId)
	{
		return	discussions.stream()
				.filter(d -> d.getId().equals(discussionId))
				.findFirst()
				.orElseThrow(() -> new DomainException("Discussion not found"));
	}
	
	private void verifyParagrapshWasAddedToGraph(Message message)
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
			throw new DomainException("Discussion alredy exist in conversation.");
		};
		
		if(!discussions.contains(discussion))
		{
			throw new DomainException("DIscussion was not added to the conversation.");
		}
	}
	
	private NegotiationId generateNegotiationId()
	{
		return new NegotiationId(this.id, this.NegotiationArchive.getNegotiations().size());
	}
	
	private DiscussionId generateDiscussionId()
	{
		return new DiscussionId(this.id, this.NegotiationArchive.getNegotiations().size());
	}
	
}
