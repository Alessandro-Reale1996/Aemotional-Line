package com.aemotionalline.domain.couple;

import java.util.Objects;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.user.UserId;

/**
 * The two partners and the therapist who supervises them. It is the authority on who may do what:
 * partners talk and approve, the therapist proposes constraints, and nobody else takes part.
 */
public class Couple
{
    private final CoupleId id;
    private final UserId partnerOneId;
    private final UserId partnerTwoId;
    private final UserId therapistId;
    

    
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
