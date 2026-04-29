package com.aemotionalline.domain.agreement;

import java.util.HashSet;
import java.util.Set;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.user.UserId;

public class Agreement
{
	private final Long id;
	private final String text;
	private final Set<UserId> approvals;
	private AgreementStatus status;
	
	public Agreement(Long id, String text)
	{
		
        if (id == null) 
        {
            throw new DomainException("Agreement id cannot be null");
        }

        if (text == null || text.isBlank()) 
        {
            throw new DomainException("Agreement text cannot be blank");
        }
		
		this.id = id;
		this.text = text;
		this.status = AgreementStatus.DRAFT;
        this.approvals = new HashSet<>();
	}
	
	
	
	public Long getId()
	{
		return id;
	}



	public String getText()
	{
		return text;
	}



	public AgreementStatus getStatus()
	{
		return status;
	}

	public Set<UserId> getApprovals()
	{
		return approvals;
	}


	public void accept(UserId userId, Couple couple)
	{
		if (!couple.isPartner(userId))
		{
			 throw new DomainException("Only partners can accept the agreement");
		}
		
		this.approvals.add(userId);
		
		if (!approvals.contains(userId))
		{
			throw new DomainException("userId was not added to approvals.");
		}
		
		  if (approvals.contains(couple.getPartnerOneId()) && approvals.contains(couple.getPartnerTwoId())) 
		  {
	            this.status = AgreementStatus.ACCEPTED;
	        }
	}
	
	 public boolean isAccepted() {
	        return status == AgreementStatus.ACCEPTED;
	    }
	 
	 
	
}
