package com.aemotionalline.domain.negotiation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.aemotionalline.domain.common.DomainException;

public class ProposalArchive 
{
	private final List<Proposal> proposals;

	public ProposalArchive() 
	{
		this.proposals = new ArrayList<Proposal>();
	}
	
	
	
	public List<Proposal> getProposals() 
	{
		return List.copyOf(proposals);
	}



	public void add(Proposal proposal)
	{
		Objects.requireNonNull(proposal);
		
		this.proposals.add(proposal);
		
		if(!proposals.contains(proposal))
		{
			throw new DomainException("Proposal wasn't added to the archive.");
		}
	}
	
	public Proposal getLast()
	{
		return proposals.getLast();
	}
	
	public boolean isEmpty()
	{
		return proposals.isEmpty();
	}
	
	
}
