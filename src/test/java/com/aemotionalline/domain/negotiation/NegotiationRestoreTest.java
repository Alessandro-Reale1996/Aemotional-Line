package com.aemotionalline.domain.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.Ids;
import com.aemotionalline.domain.MutableClock;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.user.UserId;

/** A negotiation rebuilt from its stored facts must be indistinguishable from the original and go on under the same rules. */
public class NegotiationRestoreTest
{
	private static final Couple COUPLE = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

	private static final UserId A = COUPLE.getPartnerOneId();
	private static final UserId B = COUPLE.getPartnerTwoId();

	private final MutableClock clock = new MutableClock(Instant.parse("2026-10-30T10:00:00Z"));

	private Negotiation requested()
	{
		return Negotiation.start(Ids.negotiation(100L), COUPLE, new AgreementProposal(Ids.proposal(0L), A, "TEXT"), clock);
	}

	private Negotiation restore(Negotiation negotiation)
	{
		Negotiation restored = Negotiation.restore(StoredFacts.snapshot(negotiation), clock);

		assertNotSame(negotiation, restored);
		assertEquals(StoredFacts.fingerprint(negotiation), StoredFacts.fingerprint(restored));

		return restored;
	}

	@Test
	void shouldGoOnFromAWaitingRequest()
	{
		Negotiation restored = restore(requested());

		restored.acceptNegotiation(B);
		restored.sendProposal(A, "JUSTIFICATION");

		assertEquals(Ids.proposal(0L), restored.getLastProposal().getId());
	}

	@Test
	void shouldGoOnFromAnExchangeWithADraftInProgress()
	{
		Negotiation negotiation = requested();
		negotiation.acceptNegotiation(B);
		clock.advance(Duration.ofHours(1));
		negotiation.sendProposal(A, "MINE");
		negotiation.refuseProposal(B, Ids.proposal(1L));
		negotiation.editCurrentProposal(B, new AgreementText("B'S DRAFT"));

		Negotiation restored = restore(negotiation);

		assertThrows(DomainException.class, () -> restored.editCurrentProposal(A, new AgreementText("NOT MY TURN")));
		restored.sendProposal(B, "BETTER");
		restored.acceptProposal(A);

		assertTrue(restored.getLastProposal().isAccepted());
		assertEquals(new AgreementText("B'S DRAFT"), restored.getLastProposal().getContent());
		assertEquals(2, restored.getProposals().size());
	}

	@Test
	void shouldStayEndedWhenItWasAbandoned()
	{
		Negotiation negotiation = requested();
		negotiation.abandon(A);

		Negotiation restored = restore(negotiation);

		assertTrue(restored.isFailed());
		assertThrows(DomainException.class, () -> restored.acceptNegotiation(B));
		assertThrows(DomainException.class, () -> restored.abandon(B));
	}

	@Test
	void shouldNotLetARestoredProposalStartAnotherNegotiation()
	{
		Negotiation restored = restore(requested());

		assertThrows(DomainException.class, () -> Negotiation.start(Ids.negotiation(200L), COUPLE, restored.getRequest(), clock));
		assertThrows(DomainException.class, () -> Negotiation.start(Ids.negotiation(200L), COUPLE, restored.getCurrentProposal(), clock));
	}

	@Test
	void shouldRejectAMissingSnapshotOrClock()
	{
		assertThrows(DomainException.class, () -> Negotiation.restore(null, clock));
		assertThrows(DomainException.class, () -> Negotiation.restore(StoredFacts.snapshot(requested()), null));
	}
}
