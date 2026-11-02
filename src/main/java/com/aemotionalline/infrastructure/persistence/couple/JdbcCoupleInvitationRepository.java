package com.aemotionalline.infrastructure.persistence.couple;

import static com.aemotionalline.infrastructure.persistence.Rows.instant;
import static com.aemotionalline.infrastructure.persistence.Rows.uuid;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleInvitation;
import com.aemotionalline.domain.couple.CoupleInvitationId;
import com.aemotionalline.domain.couple.CoupleInvitationRepository;
import com.aemotionalline.domain.couple.CoupleInvitationStatus;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.infrastructure.persistence.Utc;

/** The domain's CoupleInvitationRepository on MariaDB: the invitation and the partners who accepted it. */
@Repository
public class JdbcCoupleInvitationRepository implements CoupleInvitationRepository
{
	private final JdbcClient jdbc;

	JdbcCoupleInvitationRepository(JdbcClient jdbc)
	{
		this.jdbc = jdbc;
	}

	// The invitation's own row, read before its acceptances so that no query runs while a result set is open.
	private record InvitationRow(UUID id, UUID therapist, UUID partnerOne, UUID partnerTwo, java.time.Instant createdAt, java.time.Instant expiresAt,
			String status, java.time.Instant closedAt, UUID declinedBy, UUID couple)
	{
	}

	@Override
	public Optional<CoupleInvitation> findById(CoupleInvitationId id)
	{
		return jdbc.sql("SELECT * FROM couple_invitation WHERE id = ?").param(id.value()).query((rs, row) -> row(rs)).optional().map(this::invitation);
	}

	/** The lock lasts until the end of the caller's transaction, so there must be one. */
	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<CoupleInvitation> findByIdForUpdate(CoupleInvitationId id)
	{
		return jdbc.sql("SELECT * FROM couple_invitation WHERE id = ? FOR UPDATE").param(id.value()).query((rs, row) -> row(rs)).optional()
				.map(this::invitation);
	}

	@Override
	public List<CoupleInvitation> findPendingInvolving(UserId user)
	{
		return jdbc.sql("""
				SELECT * FROM couple_invitation WHERE status = ? AND (therapist_id = ? OR partner_one_id = ? OR partner_two_id = ?)
				ORDER BY created_at""")
				.params(CoupleInvitationStatus.PENDING.name(), user.value(), user.value(), user.value())
				.query((rs, row) -> row(rs))
				.list().stream()
				.map(this::invitation)
				.toList();
	}

	@Override
	@Transactional
	public void save(CoupleInvitation invitation)
	{
		UUID id = invitation.getId().value();

		jdbc.sql("""
				INSERT INTO couple_invitation (id, therapist_id, partner_one_id, partner_two_id, created_at, expires_at, status, closed_at, declined_by, couple_id)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON DUPLICATE KEY UPDATE status = VALUES(status), closed_at = VALUES(closed_at), declined_by = VALUES(declined_by), couple_id = VALUES(couple_id)""")
				.params(id, invitation.getTherapistId().value(), invitation.getPartnerOneId().value(), invitation.getPartnerTwoId().value(),
						Utc.toColumn(invitation.getCreatedAt()), Utc.toColumn(invitation.getExpiresAt()), invitation.getStatus().name(),
						Utc.toColumn(invitation.getClosedAt().orElse(null)), invitation.getDeclinedBy().map(UserId::value).orElse(null),
						invitation.getCoupleId().map(CoupleId::value).orElse(null))
				.update();

		for (UserId partner : invitation.getAcceptedBy())
		{
			jdbc.sql("INSERT INTO couple_invitation_acceptance (invitation_id, partner_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE partner_id = partner_id")
					.params(id, partner.value())
					.update();
		}
	}

	private static InvitationRow row(ResultSet rs) throws SQLException
	{
		return new InvitationRow(uuid(rs, "id"), uuid(rs, "therapist_id"), uuid(rs, "partner_one_id"), uuid(rs, "partner_two_id"),
				instant(rs, "created_at"), instant(rs, "expires_at"), rs.getString("status"), instant(rs, "closed_at"), uuid(rs, "declined_by"),
				uuid(rs, "couple_id"));
	}

	private CoupleInvitation invitation(InvitationRow row)
	{
		Set<UserId> accepted = new HashSet<>(jdbc.sql("SELECT partner_id FROM couple_invitation_acceptance WHERE invitation_id = ?").param(row.id())
				.query((acceptance, number) -> new UserId(uuid(acceptance, "partner_id"))).list());

		return CoupleInvitation.restore(new CoupleInvitationId(row.id()), new UserId(row.therapist()), new UserId(row.partnerOne()),
				new UserId(row.partnerTwo()), row.createdAt(), row.expiresAt(), accepted, CoupleInvitationStatus.valueOf(row.status()), row.closedAt(),
				row.declinedBy() == null ? null : new UserId(row.declinedBy()), row.couple() == null ? null : new CoupleId(row.couple()));
	}
}
