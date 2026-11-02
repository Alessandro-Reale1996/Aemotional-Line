package com.aemotionalline.infrastructure.persistence.couple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleInvitation;
import com.aemotionalline.domain.couple.CoupleInvitationId;
import com.aemotionalline.domain.couple.CoupleInvitationRepository;
import com.aemotionalline.domain.couple.CoupleInvitationStatus;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.user.UserId;

/** Couple invitations and ended couples on MariaDB. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class JdbcCoupleInvitationRepositoryTest
{
	private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

	@Autowired
	private CoupleInvitationRepository invitations;

	@Autowired
	private CoupleRepository couples;

	private final UserId therapist = UserId.random();
	private final UserId a = UserId.random();
	private final UserId b = UserId.random();

	private CoupleInvitation invitation()
	{
		return CoupleInvitation.create(CoupleInvitationId.random(), therapist, a, b, NOW, NOW.plus(Duration.ofDays(7)));
	}

	@Test
	void shouldSaveAnInvitationWithItsAcceptances()
	{
		CoupleInvitation invitation = invitation();
		invitation.accept(a, NOW.plusSeconds(60));
		invitations.save(invitation);

		CoupleInvitation loaded = invitations.findById(invitation.getId()).orElseThrow();
		assertEquals(Set.of(a), loaded.getAcceptedBy());
		assertEquals(CoupleInvitationStatus.PENDING, loaded.getStatus());
		assertEquals(NOW.plus(Duration.ofDays(7)), loaded.getExpiresAt());

		Couple couple = loaded.accept(b, NOW.plusSeconds(120)).orElseThrow();
		invitations.save(loaded);

		CoupleInvitation accepted = invitations.findById(invitation.getId()).orElseThrow();
		assertEquals(CoupleInvitationStatus.ACCEPTED, accepted.getStatus());
		assertEquals(Optional.of(couple.getId()), accepted.getCoupleId());
		assertEquals(Set.of(a, b), accepted.getAcceptedBy());
	}

	@Test
	void shouldFindThePendingInvitationsOfAUser()
	{
		CoupleInvitation pending = invitation();
		CoupleInvitation declined = invitation();
		declined.decline(b, NOW);
		invitations.save(pending);
		invitations.save(declined);

		assertEquals(List.of(pending.getId()), invitations.findPendingInvolving(a).stream().map(CoupleInvitation::getId).toList());
		assertEquals(List.of(pending.getId()), invitations.findPendingInvolving(therapist).stream().map(CoupleInvitation::getId).toList());
		assertEquals(Optional.of(b), invitations.findById(declined.getId()).orElseThrow().getDeclinedBy());
		assertTrue(invitations.findPendingInvolving(UserId.random()).isEmpty());
	}

	@Test
	void shouldKeepWhetherACoupleEndedAndFindAPatientsActiveCouple()
	{
		Couple couple = new Couple(CoupleId.random(), a, b, therapist);
		couples.save(couple);
		assertEquals(Optional.of(couple.getId()), couples.findActiveCoupleOf(a).map(Couple::getId));

		couple.end(therapist, NOW);
		couples.save(couple);

		Couple loaded = couples.findById(couple.getId()).orElseThrow();
		assertFalse(loaded.isActive());
		assertEquals(Optional.of(NOW), loaded.getEndedAt());
		assertTrue(couples.findActiveCoupleOf(a).isEmpty());
		assertTrue(couples.findActiveCoupleOf(b).isEmpty());
	}
}
