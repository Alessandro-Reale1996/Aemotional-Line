package com.aemotionalline.domain.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.Ids;
import com.aemotionalline.domain.MutableClock;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.user.UserId;

/** Once the therapist ends the couple, no negotiation can start and the ones in progress stay frozen as they were. */
public class NegotiationOfAnEndedCoupleTest
{
	private static final Instant T0 = Instant.parse("2026-11-02T10:00:00Z");

	private final Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));
	private final UserId a = couple.getPartnerOneId();
	private final UserId b = couple.getPartnerTwoId();
	private final MutableClock clock = new MutableClock(T0);

	@Test
	void shouldNotStartANegotiation()
	{
		couple.end(couple.getTherapistId(), T0);

		assertThrows(DomainException.class,
				() -> Negotiation.start(Ids.negotiation(100L), couple, new AgreementProposal(Ids.proposal(0L), a, "TEXT"), clock));
	}

	@Test
	void shouldFreezeARequestWaitingForTheAnswer()
	{
		Negotiation negotiation = Negotiation.start(Ids.negotiation(100L), couple, new AgreementProposal(Ids.proposal(0L), a, "TEXT"), clock);
		couple.end(couple.getTherapistId(), T0);

		assertFrozen(negotiation, List.of(
				n -> n.acceptNegotiation(b),
				n -> n.refuseNegotiation(b),
				n -> n.abandon(a),
				n -> n.abandon(b)));
	}

	@Test
	void shouldFreezeAnExchangeInProgress()
	{
		Negotiation negotiation = Negotiation.start(Ids.negotiation(100L), couple, new AgreementProposal(Ids.proposal(0L), a, "TEXT"), clock);
		negotiation.acceptNegotiation(b);
		negotiation.sendProposal(a, "FIRST");
		couple.end(couple.getTherapistId(), T0);

		assertFrozen(negotiation, List.of(
				n -> n.acceptProposal(b),
				n -> n.refuseProposal(b, Ids.proposal(1L)),
				n -> n.abandon(a),
				n -> n.abandon(b)));
	}

	@Test
	void shouldFreezeADraftBeingWritten()
	{
		Negotiation negotiation = Negotiation.start(Ids.negotiation(100L), couple, new AgreementProposal(Ids.proposal(0L), a, "TEXT"), clock);
		negotiation.acceptNegotiation(b);
		negotiation.sendProposal(a, "FIRST");
		negotiation.refuseProposal(b, Ids.proposal(1L));
		couple.end(couple.getTherapistId(), T0);

		assertFrozen(negotiation, List.of(
				n -> n.editCurrentProposal(b, new AgreementText("ANOTHER TEXT")),
				n -> n.sendProposal(b, "SECOND"),
				n -> n.abandon(a)));
	}

	// Every action is refused and nothing changes.
	private static void assertFrozen(Negotiation negotiation, List<Consumer<Negotiation>> actions)
	{
		NegotiationStatus status = negotiation.getNegotiationStatus();
		UserId responder = negotiation.getCurrentResponder();
		List<Proposal> proposals = negotiation.getProposals();
		List<ProposalStatus> statuses = proposals.stream().map(Proposal::getProposalStatus).toList();

		for (Consumer<Negotiation> action : actions)
		{
			assertThrows(DomainException.class, () -> action.accept(negotiation));
		}

		assertEquals(status, negotiation.getNegotiationStatus());
		assertEquals(responder, negotiation.getCurrentResponder());
		assertEquals(proposals, negotiation.getProposals());
		assertEquals(statuses, negotiation.getProposals().stream().map(Proposal::getProposalStatus).toList());
	}
}
