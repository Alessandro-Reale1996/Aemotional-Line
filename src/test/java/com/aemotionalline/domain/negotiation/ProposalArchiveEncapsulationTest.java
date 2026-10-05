package com.aemotionalline.domain.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.Ids;

// Adding to the archive from outside would skip the turn and phase checks of Negotiation.sendProposal.
public class ProposalArchiveEncapsulationTest
{
	private Negotiation negotiationWithOneSentProposal(Couple couple)
	{
		Proposal proposal = new Proposal(Ids.proposal(0L), couple.getPartnerOneId(), "TEXT");
		Negotiation negotiation = Negotiation.start(Ids.negotiation(100L), couple, proposal, Clock.systemUTC());

		negotiation.acceptNegotiation(couple.getPartnerTwoId());
		negotiation.sendProposal(couple.getPartnerOneId(), "JUSTIFICATION");

		return negotiation;
	}

	@Test
	void shouldNotAllowChangingTheArchiveThroughGetProposals()
	{
		Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));
		Negotiation negotiation = negotiationWithOneSentProposal(couple);
		Proposal intruder = new Proposal(Ids.proposal(9L), couple.getPartnerTwoId(), "INTRUDER");

		assertThrows(UnsupportedOperationException.class, () -> negotiation.getProposals().add(intruder));
		assertEquals(1, negotiation.getProposals().size());
	}

	@Test
	void shouldReturnASnapshotOfTheProposals()
	{
		Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));
		Negotiation negotiation = negotiationWithOneSentProposal(couple);

		List<Proposal> before = negotiation.getProposals();

		negotiation.refuseProposal(couple.getPartnerTwoId(), Ids.proposal(1L));
		negotiation.editCurrentProposal(couple.getPartnerTwoId(), "EDITED");
		negotiation.sendProposal(couple.getPartnerTwoId(), "WHY");

		assertEquals(1, before.size());
		assertEquals(2, negotiation.getProposals().size());
	}

	@Test
	void shouldNotExposeArchiveAddOutsideThePackage()
	{
		Method[] adds = Arrays.stream(ProposalArchive.class.getDeclaredMethods())
				.filter(method -> method.getName().equals("add"))
				.toArray(Method[]::new);

		assertFalse(adds.length == 0, "ProposalArchive.add not found");

		for (Method add : adds)
		{
			assertFalse(Modifier.isPublic(add.getModifiers()), "ProposalArchive.add must not be public");
		}
	}
}
