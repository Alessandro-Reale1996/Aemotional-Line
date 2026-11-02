package com.aemotionalline.infrastructure.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.application.CoupleDeletion;
import com.aemotionalline.application.CoupleErasure;
import com.aemotionalline.application.RetentionService;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.user.UserId;

/**
 * The retention period deletes the data of the couples ended longer ago than configured, and nothing when it is
 * disabled. The instants are years in the past, so the couples of the other tests in the shared database are untouched.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class RetentionJobTest
{
	private static final String TEN_YEARS = "P3650D";

	@Autowired
	private RetentionService retention;

	@Autowired
	private CoupleRepository couples;

	@Autowired
	private CoupleErasure erasure;

	@Autowired
	private Clock clock;

	@Test
	void shouldDeleteOnlyTheCouplesEndedBeforeThePeriod()
	{
		Instant now = clock.instant();
		CoupleId old = endedCouple(now.minus(Duration.ofDays(4000)));
		CoupleId recent = endedCouple(now.minus(Duration.ofDays(3000)));
		CoupleId active = endedCouple(null);

		int deleted = new RetentionJob(retention, TEN_YEARS, clock).run();

		assertTrue(deleted >= 1);
		assertTrue(couples.findById(old).isEmpty());
		assertTrue(couples.findById(recent).isPresent());
		assertTrue(couples.findById(active).isPresent());

		CoupleDeletion trace = erasure.findDeletion(old).orElseThrow();
		assertEquals(CoupleDeletion.Cause.RETENTION, trace.cause());
		assertEquals(Optional.empty(), trace.deletedBy());
	}

	@Test
	void shouldDoNothingWhenDisabled()
	{
		CoupleId old = endedCouple(clock.instant().minus(Duration.ofDays(4000)));

		assertEquals(0, new RetentionJob(retention, "", clock).run());
		assertEquals(0, new RetentionJob(retention, null, clock).run());
		assertTrue(couples.findById(old).isPresent());
	}

	@Test
	void shouldRefuseAPeriodThatIsNotAPositiveDuration()
	{
		assertEquals(Optional.empty(), RetentionJob.parse("  "));
		assertEquals(Optional.of(Duration.ofDays(365)), RetentionJob.parse(" P365D "));
		assertThrows(IllegalStateException.class, () -> RetentionJob.parse("one year"));
		assertThrows(IllegalStateException.class, () -> RetentionJob.parse("P0D"));
		assertThrows(IllegalStateException.class, () -> RetentionJob.parse("-P1D"));
	}

	private CoupleId endedCouple(Instant endedAt)
	{
		Couple couple = Couple.restore(CoupleId.random(), UserId.random(), UserId.random(), UserId.random(), endedAt);
		couples.save(couple);

		return couple.getId();
	}
}
