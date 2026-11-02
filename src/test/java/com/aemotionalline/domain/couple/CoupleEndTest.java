package com.aemotionalline.domain.couple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.user.UserId;

public class CoupleEndTest
{
	private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

	private final UserId therapist = UserId.random();
	private final Couple couple = new Couple(CoupleId.random(), UserId.random(), UserId.random(), therapist);

	@Test
	void shouldBeEndedOnlyByItsTherapistAndOnce()
	{
		assertTrue(couple.isActive());
		assertThrows(DomainException.class, () -> couple.end(couple.getPartnerOneId(), NOW));
		assertThrows(DomainException.class, () -> couple.end(UserId.random(), NOW));

		couple.end(therapist, NOW);

		assertFalse(couple.isActive());
		assertEquals(Optional.of(NOW), couple.getEndedAt());
		assertThrows(DomainException.class, () -> couple.end(therapist, NOW));
	}

	@Test
	void shouldBeRestoredEndedOrActive()
	{
		assertFalse(Couple.restore(CoupleId.random(), UserId.random(), UserId.random(), therapist, NOW).isActive());
		assertTrue(Couple.restore(CoupleId.random(), UserId.random(), UserId.random(), therapist, null).isActive());
	}
}
