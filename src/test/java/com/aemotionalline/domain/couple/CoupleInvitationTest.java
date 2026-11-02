package com.aemotionalline.domain.couple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.user.UserId;

public class CoupleInvitationTest
{
	private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
	private static final Instant EXPIRY = NOW.plus(Duration.ofDays(7));

	private final UserId therapist = UserId.random();
	private final UserId a = UserId.random();
	private final UserId b = UserId.random();

	private CoupleInvitation invitation()
	{
		return CoupleInvitation.create(CoupleInvitationId.random(), therapist, a, b, NOW, EXPIRY);
	}

	@Test
	void shouldCreateTheCoupleOnlyWhenBothPartnersAccept()
	{
		CoupleInvitation invitation = invitation();

		assertTrue(invitation.accept(a, NOW.plusSeconds(60)).isEmpty());
		assertEquals(CoupleInvitationStatus.PENDING, invitation.getStatus());

		Couple couple = invitation.accept(b, NOW.plusSeconds(120)).orElseThrow();

		assertEquals(CoupleInvitationStatus.ACCEPTED, invitation.getStatus());
		assertEquals(Optional.of(couple.getId()), invitation.getCoupleId());
		assertEquals(a, couple.getPartnerOneId());
		assertEquals(b, couple.getPartnerTwoId());
		assertEquals(therapist, couple.getTherapistId());
		assertTrue(couple.isActive());
		assertEquals(Optional.of(NOW.plusSeconds(120)), invitation.getClosedAt());
	}

	@Test
	void shouldLetOnlyTheInvitedPartnersAnswerOnce()
	{
		CoupleInvitation invitation = invitation();
		invitation.accept(a, NOW);

		assertThrows(DomainException.class, () -> invitation.accept(a, NOW));
		assertThrows(DomainException.class, () -> invitation.accept(therapist, NOW));
		assertThrows(DomainException.class, () -> invitation.decline(UserId.random(), NOW));
		assertEquals(Set.of(a), invitation.getAcceptedBy());
	}

	@Test
	void shouldEndWhenAPartnerDeclines()
	{
		CoupleInvitation invitation = invitation();
		invitation.accept(a, NOW);

		invitation.decline(b, NOW.plusSeconds(60));

		assertEquals(CoupleInvitationStatus.DECLINED, invitation.getStatus());
		assertEquals(Optional.of(b), invitation.getDeclinedBy());
		assertThrows(DomainException.class, () -> invitation.accept(b, NOW));
		assertTrue(invitation.getCoupleId().isEmpty());
	}

	@Test
	void shouldLetOnlyItsTherapistWithdrawItWhilePending()
	{
		CoupleInvitation invitation = invitation();

		assertThrows(DomainException.class, () -> invitation.withdraw(a, NOW));
		invitation.withdraw(therapist, NOW);

		assertEquals(CoupleInvitationStatus.WITHDRAWN, invitation.getStatus());
		assertThrows(DomainException.class, () -> invitation.withdraw(therapist, NOW));
	}

	@Test
	void shouldExpire()
	{
		CoupleInvitation invitation = invitation();

		assertThrows(DomainException.class, () -> invitation.accept(a, EXPIRY));
		assertThrows(DomainException.class, () -> invitation.expire(EXPIRY.minusSeconds(1)));

		invitation.expire(EXPIRY.plusSeconds(60));

		assertEquals(CoupleInvitationStatus.EXPIRED, invitation.getStatus());
		assertEquals(Optional.of(EXPIRY), invitation.getClosedAt());
	}

	@Test
	void shouldRequireThreeDifferentPeopleAndAFutureExpiry()
	{
		assertThrows(DomainException.class, () -> CoupleInvitation.create(CoupleInvitationId.random(), therapist, a, a, NOW, EXPIRY));
		assertThrows(DomainException.class, () -> CoupleInvitation.create(CoupleInvitationId.random(), therapist, therapist, b, NOW, EXPIRY));
		assertThrows(DomainException.class, () -> CoupleInvitation.create(CoupleInvitationId.random(), therapist, a, b, NOW, NOW));
		assertThrows(DomainException.class, () -> CoupleInvitation.create(null, therapist, a, b, NOW, EXPIRY));
	}

	@Test
	void shouldBeRestoredAsItWas()
	{
		CoupleInvitation invitation = invitation();
		invitation.accept(a, NOW);

		CoupleInvitation restored = CoupleInvitation.restore(invitation.getId(), therapist, a, b, NOW, EXPIRY, invitation.getAcceptedBy(),
				invitation.getStatus(), null, null, null);

		Couple couple = restored.accept(b, NOW.plusSeconds(1)).orElseThrow();
		assertEquals(Set.of(a, b), Set.of(couple.getPartnerOneId(), couple.getPartnerTwoId()));
	}
}
