package com.aemotionalline.domain.constraint;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.user.UserId;

public class ConstraintChangeRequest 
{

    private final UserId proposedBy;
    private final List<ConstraintAssignment> assignments;
    private final Set<UserId> approvals = new HashSet<>();

    public ConstraintChangeRequest(UserId proposedBy, List<ConstraintAssignment> assignments, Couple couple) 
    {

        if (!couple.isTherapist(proposedBy)) 
        {
            throw new DomainException("Only therapist can propose constraints");
        }

        this.proposedBy = proposedBy;
        this.assignments = assignments;
    }

    public void approve(UserId userId, Couple couple) 
    {
        if (!couple.isPartner(userId)) 
        {
            throw new DomainException("Only partners can approve");
        }

        approvals.add(userId);
    }

    public boolean isFullyApproved(Couple couple) 
    {
        return approvals.contains(couple.getPartnerOneId())  &&  approvals.contains(couple.getPartnerTwoId());
    }

    public List<ConstraintAssignment> assignments() 
    {
        return assignments;
    }
}
