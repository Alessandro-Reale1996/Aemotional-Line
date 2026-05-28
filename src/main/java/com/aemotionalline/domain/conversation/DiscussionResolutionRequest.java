package com.aemotionalline.domain.conversation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.user.UserId;

public class DiscussionResolutionRequest
{
	private final ParagraphId rootId;
	private final UserId proposedBy;
	private final Set<UserId> approvals;
	private DiscussionResolutionStatus status;
	
	public DiscussionResolutionRequest(ParagraphId rootId, UserId proposedBy, Couple couple)
	{
		this.rootId = Objects.requireNonNull(rootId, "Root paragraph id cannot be null");

        this.proposedBy = Objects.requireNonNull(proposedBy, "Proposer cannot be null");

        Objects.requireNonNull(couple, "Couple cannot be null");

        if (!couple.isPartner(proposedBy))
        {
            throw new DomainException("Only partners can propose discussion resolution");
        }

        this.approvals = new HashSet<>();
        this.approvals.add(proposedBy);
        this.status = DiscussionResolutionStatus.RESOLUTION_PROPOSED;
		
	}
	
	 
	
	public ParagraphId getRootId()
	{
		return rootId;
	}



	public UserId getProposedBy()
	{
		return proposedBy;
	}



	public Set<UserId> getApprovals()
	{
		return Set.copyOf(approvals);
	}



	public DiscussionResolutionStatus getStatus()
	{
		return status;
	}



	public void approve(UserId userId, Couple couple)
    {
        Objects.requireNonNull(userId, "User id cannot be null");
        Objects.requireNonNull(couple, "Couple cannot be null");

        if (!couple.isPartner(userId))
        {
            throw new DomainException("Only partners can approve discussion resolution");
        }

        if (status == DiscussionResolutionStatus.REJECTED)
        {
            throw new DomainException("Rejected resolution request cannot be approved");
        }

        if (status == DiscussionResolutionStatus.RESOLVED)
        {
            throw new DomainException("Discussion resolution request is already resolved");
        }

        approvals.add(userId);
        
        if (!approvals.contains(userId))
		{
			throw new DomainException("Approve was not added.");
		}
        

        if (approvals.contains(couple.getPartnerOneId()) && approvals.contains(couple.getPartnerTwoId()))
        {
            status = DiscussionResolutionStatus.RESOLVED;
        }
    }
	
   public void reject(UserId userId, Couple couple)
    {
        Objects.requireNonNull(userId, "User id cannot be null");
        Objects.requireNonNull(couple, "Couple cannot be null");

        if (!couple.isPartner(userId))
        {
            throw new DomainException("Only partners can reject discussion resolution");
        }

        if (status == DiscussionResolutionStatus.RESOLVED)
        {
            throw new DomainException("Resolved discussion cannot be rejected");
        }

        status = DiscussionResolutionStatus.REJECTED;
    }

    public boolean isResolved()
    {
        return status == DiscussionResolutionStatus.RESOLVED;
    }

   

	
}
