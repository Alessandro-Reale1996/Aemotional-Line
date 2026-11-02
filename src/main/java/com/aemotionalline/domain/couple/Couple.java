package com.aemotionalline.domain.couple;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.user.UserId;

/**
 * The two partners and the therapist who supervises them. It is the authority on who may do what:
 * partners talk and approve, the therapist proposes constraints, and nobody else takes part.
 * A couple is active until its therapist ends it, at the end of the therapy; that is final, and its conversation then
 * becomes read-only (everything pending stays frozen; messages can still be opened).
 */
public class Couple
{
    private final CoupleId id;
    private final UserId partnerOneId;
    private final UserId partnerTwoId;
    private final UserId therapistId;

    // Null while the couple is active.
    private Instant endedAt;
    

    
    public Couple(CoupleId id, UserId partnerOneId, UserId partnerTwoId, UserId therapistId)
    {
        if (id == null || partnerOneId == null || partnerTwoId == null || therapistId == null)
        {
            throw new DomainException("A couple needs an id, two partners and a therapist.");
        }

        if (partnerOneId.equals(partnerTwoId)) 
        {
            throw new DomainException("Partners must be different");
        }
        
        if (therapistId.equals(partnerOneId) || therapistId.equals(partnerTwoId))
        {
            throw new DomainException("The therapist can't also be a partner of the couple.");
        }
        
        this.id = id;
        this.partnerOneId = partnerOneId;
        this.partnerTwoId = partnerTwoId;
        this.therapistId = therapistId;
    }
    
    /** Reserved to the persistence layer: rebuilds a couple, ended or not. */
    public static Couple restore(CoupleId id, UserId partnerOneId, UserId partnerTwoId, UserId therapistId, Instant endedAt)
    {
        Couple couple = new Couple(id, partnerOneId, partnerTwoId, therapistId);
        couple.endedAt = endedAt;

        return couple;
    }

    /** Only the couple's therapist ends it, once. */
    public void end(UserId therapist, Instant at)
    {
        if (!isTherapist(therapist))
        {
            throw new DomainException("Only the couple's therapist can end it.");
        }

        if (!isActive())
        {
            throw new DomainException("The couple has already ended.");
        }

        this.endedAt = Objects.requireNonNull(at);
    }

    public boolean isActive()
    {
        return endedAt == null;
    }

    public Optional<Instant> getEndedAt()
    {
        return Optional.ofNullable(endedAt);
    }

    public CoupleId getId()
	{
		return id;
	}

	public UserId getPartnerOneId()
	{
		return partnerOneId;
	}

	public UserId getPartnerTwoId()
	{
		return partnerTwoId;
	}

	public UserId getTherapistId()
	{
		return therapistId;
	}

	public boolean isPartner(UserId userId)
    {
    	return partnerOneId.equals(userId) || partnerTwoId.equals(userId);
    }
    
    public boolean isTherapist(UserId userId)
    {
    	return therapistId.equals(userId);
    }
    
    
    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }

        if (!(o instanceof Couple other))
        {
            return false;
        }

        return id.equals(other.id);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id);
    }
}
