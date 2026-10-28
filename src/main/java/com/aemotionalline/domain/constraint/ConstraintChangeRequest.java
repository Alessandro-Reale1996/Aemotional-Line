package com.aemotionalline.domain.constraint;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.user.UserId;

/**
 * A therapist's proposal to change the constraints (spec 1.3.2: the "descriptive notification"). Only the therapist may
 * create it, but it changes nothing on its own: both partners must approve first, because the constraints limit them,
 * not the therapist. It belongs to one couple for its whole life, and it can be applied only once. The conversation
 * owns its requests and keeps every one of them, however it ends; the instants are given by the conversation's clock.
 */
public class ConstraintChangeRequest
{

    private final ConstraintChangeRequestId id;
    private final Couple couple;
    private final UserId proposedBy;
    private final List<ConstraintAssignment> assignments;
    private final Instant proposedAt;
    private final Set<UserId> approvals = new HashSet<>();

    private ConstraintChangeRequestStatus status = ConstraintChangeRequestStatus.PENDING;
    private Instant closedAt;
    private UserId rejectedBy;
    private List<ConstraintAssignment> previousAssignments;

    public ConstraintChangeRequest(ConstraintChangeRequestId id, UserId proposedBy, List<ConstraintAssignment> assignments, Couple couple, Instant proposedAt)
    {
        if (id == null || proposedAt == null)
        {
            throw new DomainException("A request needs an id and the instant it was proposed.");
        }

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

        // A constraint only ever checks a partner: one assigned to anybody else would never apply.
        for (ConstraintAssignment assignment : assignments)
        {
            if (!couple.isPartner(assignment.getUserId()))
            {
                throw new DomainException("Constraints can only be assigned to the partners of the couple.");
            }
        }

        this.id = id;
        this.couple = couple;
        this.proposedBy = proposedBy;
        this.assignments = List.copyOf(assignments);
        this.proposedAt = proposedAt;
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
    public void reject(UserId userId, Instant at)
    {
        ensurePending();

        if (!couple.isPartner(userId))
        {
            throw new DomainException("Only partners can reject");
        }

        close(ConstraintChangeRequestStatus.REJECTED, at);
        rejectedBy = userId;
    }

    /** The therapist takes back their own request before it is applied. */
    public void withdraw(UserId userId, Instant at)
    {
        ensurePending();

        if (!proposedBy.equals(userId))
        {
            throw new DomainException("Only the therapist who proposed the constraints can withdraw them");
        }

        close(ConstraintChangeRequestStatus.WITHDRAWN, at);
    }

    public boolean isFullyApproved()
    {
        return approvals.contains(couple.getPartnerOneId())  &&  approvals.contains(couple.getPartnerTwoId());
    }

    /**
     * Puts the approved assignments into the given set, keeps the ones they replace and closes the request. This is the
     * only way to change a {@link ConstraintSet}, so a request cannot be applied twice, unapproved, or after it was closed.
     */
    public void applyTo(ConstraintSet constraintSet, Instant at)
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

        // Checked here too, so a failure leaves both the set and the request as they were.
        if (at == null)
        {
            throw new DomainException("The instant the request is closed can't be null.");
        }

        List<ConstraintAssignment> previous = constraintSet.getAssignments();

        constraintSet.replaceWith(assignments);

        previousAssignments = previous;
        close(ConstraintChangeRequestStatus.APPLIED, at);
    }

    public ConstraintChangeRequestId id()
    {
        return id;
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

    public boolean isPending()
    {
        return status == ConstraintChangeRequestStatus.PENDING;
    }

    /** The picture of this request for the archive: reading it can't change the request. */
    public ConstraintRequestRecord toRecord()
    {
        return new ConstraintRequestRecord(id, proposedBy, assignments, status, approvals, proposedAt,
                Optional.ofNullable(closedAt), Optional.ofNullable(rejectedBy), Optional.ofNullable(previousAssignments));
    }

    private void close(ConstraintChangeRequestStatus finalStatus, Instant at)
    {
        if (at == null)
        {
            throw new DomainException("The instant the request is closed can't be null.");
        }

        status = finalStatus;
        closedAt = at;
    }

    private void ensurePending()
    {
        if (status != ConstraintChangeRequestStatus.PENDING)
        {
            throw new DomainException("The request is already " + status + ".");
        }
    }
}
