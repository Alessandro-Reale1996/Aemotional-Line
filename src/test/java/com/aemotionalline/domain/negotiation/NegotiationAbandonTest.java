package com.aemotionalline.domain.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.Ids;
import com.aemotionalline.domain.MutableClock;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.user.UserId;

/**
 * Either partner may abandon an open negotiation at any time, whoever's turn it is; refusing the request and
 * abandoning are the two ways to end without an agreement, and both record who ended it and when.
 */
public class NegotiationAbandonTest
{
	private static final Couple COUPLE = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

	private static final UserId A = COUPLE.getPartnerOneId();
	private static final UserId B = COUPLE.getPartnerTwoId();

	private static final Instant T0 = Instant.parse("2026-10-29T10:00:00Z");

	private final MutableClock clock = new MutableClock(T0);

	// A asks to talk; B has not answered yet.
	private Negotiation requestedByA()
	{
		return Negotiation.start(Ids.negotiation(100L), COUPLE, new AgreementProposal(Ids.proposal(0L), A, "TEXT"), clock);
	}

	// B accepted to talk, A sent the first proposal: it is B's turn.
	private Negotiation exchangeWaitingForB()
	{
		Negotiation negotiation = requestedByA();
		negotiation.acceptNegotiation(B);
		negotiation.sendProposal(A, "JUSTIFICATION");

		return negotiation;
	}

	@Test
	void shouldRecordWhenTheNegotiationStarted()
	{
		assertEquals(T0, requestedByA().getStartedAt());
	}

	@Test
	void shouldLetTheRequesterWithdrawBeforeTheAnswer()
	{
		Negotiation negotiation = requestedByA();
		clock.advance(Duration.ofHours(1));

		negotiation.abandon(A);

		assertEquals(NegotiationStatus.ABANDONED, negotiation.getNegotiationStatus());
		assertEquals(Optional.of(A), negotiation.getClosedBy());
		assertEquals(Optional.of(T0.plus(Duration.ofHours(1))), negotiation.getClosedAt());
		assertTrue(negotiation.isFailed());
	}

	@Test
	void shouldLetTheOtherPartnerAbandonBeforeAnswering()
	{
		Negotiation negotiation = requestedByA();

		negotiation.abandon(B);

		assertEquals(Optional.of(B), negotiation.getClosedBy());
	}

	@Test
	void shouldLetEitherPartnerAbandonDuringTheExchangeWhateverTheTurn()
	{
		Negotiation waiting = exchangeWaitingForB();
		waiting.abandon(A);
		assertEquals(NegotiationStatus.ABANDONED, waiting.getNegotiationStatus());

		Negotiation onTurn = exchangeWaitingForB();
		onTurn.abandon(B);
		assertEquals(NegotiationStatus.ABANDONED, onTurn.getNegotiationStatus());
	}

	@Test
	void shouldNotLetTheTherapistOrAStrangerAbandon()
	{
		Negotiation negotiation = exchangeWaitingForB();

		assertThrows(DomainException.class, () -> negotiation.abandon(COUPLE.getTherapistId()));
		assertThrows(DomainException.class, () -> negotiation.abandon(Ids.user(99L)));
		assertThrows(DomainException.class, () -> negotiation.abandon(null));
		assertEquals(NegotiationStatus.ACCEPTED, negotiation.getNegotiationStatus());
		assertEquals(Optional.empty(), negotiation.getClosedBy());
	}

	@Test
	void shouldNotAbandonANegotiationThatAlreadyEnded()
	{
		Negotiation agreed = exchangeWaitingForB();
		agreed.acceptProposal(B);
		assertThrows(DomainException.class, () -> agreed.abandon(A));
		assertFalse(agreed.isFailed());

		Negotiation refused = requestedByA();
		refused.refuseNegotiation(B);
		assertThrows(DomainException.class, () -> refused.abandon(A));

		Negotiation abandoned = requestedByA();
		abandoned.abandon(A);
		assertThrows(DomainException.class, () -> abandoned.abandon(B));
		assertEquals(Optional.of(A), abandoned.getClosedBy());
	}

	@Test
	void shouldBlockEveryActionAfterTheNegotiationWasAbandoned()
	{
		Negotiation beforeAnswer = requestedByA();
		beforeAnswer.abandon(A);
		assertThrows(DomainException.class, () -> beforeAnswer.acceptNegotiation(B));
		assertThrows(DomainException.class, () -> beforeAnswer.refuseNegotiation(B));

		Negotiation duringExchange = exchangeWaitingForB();
		duringExchange.abandon(A);
		assertThrows(DomainException.class, () -> duringExchange.acceptProposal(B));
		assertThrows(DomainException.class, () -> duringExchange.refuseProposal(B, Ids.proposal(1L)));
		assertThrows(DomainException.class, () -> duringExchange.editCurrentProposal(B, new AgreementText("OTHER")));
		assertThrows(DomainException.class, () -> duringExchange.sendProposal(B, "JUSTIFICATION"));
		assertEquals(1, duringExchange.getProposals().size());
	}

	@Test
	void shouldRecordWhoRefusedTheRequestAndWhen()
	{
		Negotiation negotiation = requestedByA();
		clock.advance(Duration.ofMinutes(30));

		negotiation.refuseNegotiation(B);

		assertEquals(Optional.of(B), negotiation.getClosedBy());
		assertEquals(Optional.of(T0.plus(Duration.ofMinutes(30))), negotiation.getClosedAt());
		assertTrue(negotiation.isFailed());
	}

	@Test
	void shouldKeepTheRequestAsTheOtherPartnerReceivedIt()
	{
		Negotiation negotiation = requestedByA();
		negotiation.acceptNegotiation(B);

		// After the acceptance the requester may still edit the first draft before sending it.
		negotiation.editCurrentProposal(A, new AgreementText("EDITED"));

		assertEquals(new AgreementText("TEXT"), negotiation.getRequest().getContent());
		assertEquals(Ids.proposal(0L), negotiation.getRequest().getId());
		assertEquals(A, negotiation.getRequest().getAuthor());

		// Nobody can start another negotiation with it, and change it.
		assertThrows(DomainException.class, () -> Negotiation.start(Ids.negotiation(200L), COUPLE, negotiation.getRequest(), clock));
	}
}
