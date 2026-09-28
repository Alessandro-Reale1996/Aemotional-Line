package com.aemotionalline.domain.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.user.UserId;

public class ProposalTest 
{
	@Test
	public void shouldSetTheRightStatus()
	{
		Proposal proposalDraft = Proposal.create(new ProposalId(0L), new UserId(0L), "TEST TEXT", "TEST JUSTIFICATION", Clock.systemUTC());
		
		assertEquals(proposalDraft.getProposalStatus(), ProposalStatus.DRAFT);
		
		Proposal proposalWFR = Proposal.create(new ProposalId(0L), new UserId(0L), "TEST TEXT", "TEST JUSTIFICATION", Clock.systemUTC());

		// create() always starts as a draft; the status only moves on once the proposal is sent.
		proposalWFR.setProposalStatus(ProposalStatus.WAITING_FOR_RESPONSE);

		assertEquals(proposalWFR.getProposalStatus(), ProposalStatus.WAITING_FOR_RESPONSE);
		
		proposalWFR.setProposalStatus(ProposalStatus.ACCEPTED);
		
		assertEquals(proposalWFR.getProposalStatus(), ProposalStatus.ACCEPTED);
	}
}
