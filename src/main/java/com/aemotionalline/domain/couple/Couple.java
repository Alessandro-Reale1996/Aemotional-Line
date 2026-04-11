package com.aemotionalline.domain.couple;

import java.util.Objects;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.user.UserId;

public class Couple
{
    private final Long id;
    private final UserId partnerOneId;
    private final UserId partnerTwoId;
    private final UserId therapistId;
    

    
    public Couple(Long id, UserId partnerOneId, UserId partnerTwoId, UserId therapistId)
    {

        this.id = Objects.requireNonNull(id, "Couple id cannot be null");
        this.partnerOneId = Objects.requireNonNull(partnerOneId, "Partner one cannot be null");
        this.partnerTwoId = Objects.requireNonNull(partnerTwoId, "Partner two cannot be null");
        this.therapistId = Objects.requireNonNull(therapistId, "Therapist cannot be null");

        if (partnerOneId.equals(partnerTwoId)) 
        {
            throw new DomainException("Partners must be different");
        }
    }
    
    public Long getId()
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
    
    
}
