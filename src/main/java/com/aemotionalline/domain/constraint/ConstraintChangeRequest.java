package com.aemotionalline.domain.constraint;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.user.UserId;

/**
 * A therapist's proposal to change the constraints. Only the therapist may create it, but it changes nothing
 * on its own: both partners must approve first, because the constraints limit them, not the therapist.
 * It belongs to one couple for its whole life, and it can be applied only once.
 */
public class ConstraintChangeRequest
{

    private final Couple couple;
    private final UserId proposedBy;
    private final List<ConstraintAssignment> assignments;
    private final Set<UserId> approvals = new HashSet<>();

    private ConstraintChangeRequestStatus status = ConstraintChangeRequestStatus.PENDING;

    public ConstraintChangeRequest(UserId proposedBy, List<ConstraintAssignment> assignments, Couple couple)
    {
        if (couple == null)
        {
            throw new DomainException("Couple cannot be null.");
        }

        if (!couple.isTherapist(proposedBy))
        {
            throw new DomainException("Only therapist can propose constraints");
        }

        if (assignments == null)
        {
            throw new DomainException("Assignments cannot be null.");
        }

        // An empty list is allowed on purpose: it is how the therapist lifts every constraint.
        if (assignments.stream().anyMatch(Objects::isNull))
        {
            throw new DomainException("Assignments cannot contain null.");
        }

        // A constraint only ever checks the sender, who is always a partner: one assigned to anybody else would never apply.
        for (ConstraintAssignment assignment : assignments)
        {
            if (!couple.isPartner(assignment.getUserId()))
            {
                throw new DomainException("Constraints can only be assigned to the partners of the couple.");
            }
        }

        this.couple = couple;
        this.proposedBy = proposedBy;
        this.assignments = List.copyOf(assignments);
    }

    public void approve(UserId userId)
    {
        ensurePending();

        if (!couple.isPartner(userId))
        {
            throw new DomainException("Only partners can approve");
        }

        approvals.add(userId);
    }

    /** A partner refuses the request. It is final: the therapist has to create a new one. */
    public void reject(UserId userId)
    {
        ensurePending();

        if (!couple.isPartner(userId))
        {
            throw new DomainException("Only partners can reject");
        }

        status = ConstraintChangeRequestStatus.REJECTED;
    }

    /** The therapist takes back their own request before it is applied. */
    public void withdraw(UserId userId)
    {
        ensurePending();

        if (!proposedBy.equals(userId))
        {
            throw new DomainException("Only the therapist who proposed the constraints can withdraw them");
        }

        status = ConstraintChangeRequestStatus.REJECTED;
    }

    public boolean isFullyApproved()
    {
        return approvals.contains(couple.getPartnerOneId())  &&  approvals.contains(couple.getPartnerTwoId());
    }

    /**
     * Puts the approved assignments into the given set and closes the request. This is the only way to change a
     * {@link ConstraintSet}, so a request cannot be applied twice, unapproved, or after it was rejected.
     */
    public void applyTo(ConstraintSet constraintSet)
    {
        if (constraintSet == null)
        {
            throw new DomainException("Constraint set cannot be null.");
        }

        ensurePending();

        if (!isFullyApproved())
        {
            throw new DomainException("Constraints not fully approved");
        }

        constraintSet.replaceWith(assignments);

        status = ConstraintChangeRequestStatus.APPLIED;
    }

    public List<ConstraintAssignment> assignments()
    {
        return assignments;
    }

    public Couple couple()
    {
        return couple;
    }

    public ConstraintChangeRequestStatus status()
    {
        return status;
    }

    private void ensurePending()
    {
        if (status != ConstraintChangeRequestStatus.PENDING)
        {
            throw new DomainException("The request is already " + status + ".");
        }
    }
}
