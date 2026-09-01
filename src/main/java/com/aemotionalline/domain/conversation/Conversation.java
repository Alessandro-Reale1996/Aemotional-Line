package com.aemotionalline.domain.conversation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.aemotionalline.domain.agreement.Agreement;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.constraint.ConstraintAssignment;
import com.aemotionalline.domain.constraint.ConstraintChangeRequest;
import com.aemotionalline.domain.constraint.ConstraintContext;
import com.aemotionalline.domain.constraint.ConstraintSet;

public class Conversation
{
	private final Long id;
	private final Long coupleId;
	private final Agreement agreement;
	private ConversationStatus status;
	private final ConversationGraph conversationGraph;
	private final ConstraintSet constraintSet;
	private final Set<Discussion> discussions;
	
	public Conversation(Long id, Long coupleId, Agreement agreement)
	{
		
        if (id == null) 
        {
            throw new DomainException("Conversation id cannot be null");
        }

        if (coupleId == null) 
        {
            throw new DomainException("Couple id cannot be null");
        }

        if (agreement == null)
        {
            throw new DomainException("Agreement cannot be null");
        }
		
		this.id = id;
		this.coupleId = coupleId;
		this.agreement = agreement;
		this.status = ConversationStatus.PENDING_AGREEMENT;
		this.conversationGraph = new ConversationGraph();
		this.constraintSet = new ConstraintSet();
		this.discussions = new HashSet<>();
		
		discussions.add(new Discussion(new DiscussionId(0L)));
		
		if (constraintSet == null)
        {
            throw new DomainException("ConstraintSet cannot be null");
        }
		
	}
	
	
	
	public Long getId()
	{
		return id;
	}


	public Long getCoupleId()
	{
		return coupleId;
	}


	public Agreement getAgreement()
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
 
	public void addDiscussion(Discussion discussion)
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


	public void acceptAgrement(UserId userId, Couple couple )
	{
		this.agreement.accept(userId, couple);
		
		if (agreement.isAccepted())
		{
			this.status = ConversationStatus.ACTIVE;
			this.discussions.add(new Discussion(new DiscussionId(0L)));
		}
	}
	
	public boolean canSendMessage(UserId userId, Couple couple)
	{
		return  couple.isPartner(userId) && status == ConversationStatus.ACTIVE && agreement.isAccepted();	
	}
	
	public void ensureCanSendMessage(UserId userId, Couple couple)
	{
		if (!canSendMessage(userId, couple))
		{
			throw new DomainException("User cannot send messages in the current conversation state");
		}
	}
	
	public void applyConstraints(ConstraintChangeRequest request, Couple couple) 
	{
	    if (!request.isFullyApproved(couple)) 
	    {
	        throw new DomainException("Constraints not fully approved");
	    }

	    constraintSet.replaceWith(request.assignments());
	}
	
	
	
	public void sendMessage(Message message, Couple couple,DiscussionId discussionid, ConstraintContext context) 
	{
		if (message == null)
		{
			throw new DomainException("Message can't be null.");
		}
		
		UserId sender = message.getSenderId();
		
		message.ensureReadyToSend();

	    ensureCanSendMessage(sender, couple);

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
	
}
