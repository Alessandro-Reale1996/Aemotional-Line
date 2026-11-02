package com.aemotionalline.infrastructure.persistence.couple;

import static com.aemotionalline.infrastructure.persistence.Rows.instant;
import static com.aemotionalline.infrastructure.persistence.Rows.uuid;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.aemotionalline.application.CoupleDeletion;
import com.aemotionalline.application.CoupleErasure;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.infrastructure.persistence.Utc;

/**
 * The application's CoupleErasure on MariaDB: explicit deletes, children before parents as the foreign keys require.
 * Every table holding a couple's data is listed here; the test checks that none is forgotten.
 */
@Repository
public class JdbcCoupleErasure implements CoupleErasure
{
	private static final String MESSAGES = "SELECT id FROM message WHERE conversation_id = ?";
	private static final String PARAGRAPHS = "SELECT p.id FROM paragraph p JOIN message m ON m.id = p.message_id WHERE m.conversation_id = ?";
	private static final String CONSTRAINT_REQUESTS = "SELECT id FROM constraint_request WHERE conversation_id = ?";

	// Each statement takes the conversation id.
	private static final List<String> CONVERSATION = List.of(
			// The proposals of the negotiations recorded in the conversation (they stay in proposal, by negotiation id).
			"DELETE FROM proposal WHERE negotiation_id IN (SELECT negotiation_id FROM negotiation_record WHERE conversation_id = ?)",
			"DELETE FROM proposal WHERE negotiation_id IN (SELECT negotiation_id FROM failed_negotiation_record WHERE conversation_id = ?)",
			"DELETE FROM negotiation_record WHERE conversation_id = ?",
			"DELETE FROM failed_negotiation_record WHERE conversation_id = ?",
			"DELETE FROM paragraph_reference WHERE paragraph_id IN (" + PARAGRAPHS + ")",
			"DELETE FROM paragraph_self_citation WHERE paragraph_id IN (" + PARAGRAPHS + ")",
			"DELETE FROM paragraph WHERE message_id IN (" + MESSAGES + ")",
			"DELETE FROM message_skipped_question WHERE message_id IN (" + MESSAGES + ")",
			"DELETE FROM message_read WHERE message_id IN (" + MESSAGES + ")",
			"DELETE FROM message WHERE conversation_id = ?",
			"DELETE FROM outcome_declaration WHERE conversation_id = ?",
			"DELETE FROM therapist_closing_request WHERE conversation_id = ?",
			"DELETE FROM closing_request WHERE conversation_id = ?",
			"DELETE FROM discussion WHERE conversation_id = ?",
			"DELETE FROM constraint_assignment WHERE request_id IN (" + CONSTRAINT_REQUESTS + ")",
			"DELETE FROM constraint_request_approval WHERE request_id IN (" + CONSTRAINT_REQUESTS + ")",
			"DELETE FROM constraint_request WHERE conversation_id = ?",
			"DELETE FROM conversation WHERE id = ?");

	// Each statement takes the couple id.
	private static final List<String> COUPLE = List.of(
			// The negotiations still kept at couple level: in progress, or failed attempts to start the conversation (their
			// negotiation_purpose rows follow by ON DELETE CASCADE).
			"DELETE FROM proposal WHERE negotiation_id IN (SELECT id FROM negotiation WHERE couple_id = ?)",
			"DELETE FROM negotiation WHERE couple_id = ?",
			"DELETE FROM couple_invitation_acceptance WHERE invitation_id IN (SELECT id FROM couple_invitation WHERE couple_id = ?)",
			"DELETE FROM couple_invitation WHERE couple_id = ?",
			"DELETE FROM couple WHERE id = ?");

	private final JdbcClient jdbc;

	JdbcCoupleErasure(JdbcClient jdbc)
	{
		this.jdbc = jdbc;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void erase(CoupleDeletion deletion)
	{
		UUID couple = deletion.couple().value();

		jdbc.sql("SELECT id FROM conversation WHERE couple_id = ?").param(couple).query((rs, row) -> uuid(rs, "id")).optional()
				.ifPresent(conversation -> CONVERSATION.forEach(statement -> jdbc.sql(statement).param(conversation).update()));
		COUPLE.forEach(statement -> jdbc.sql(statement).param(couple).update());

		jdbc.sql("""
				INSERT INTO deleted_couple (couple_id, therapist_id, ended_at, deleted_at, cause, deleted_by) VALUES (?, ?, ?, ?, ?, ?)""")
				.params(couple, deletion.therapist().value(), Utc.toColumn(deletion.endedAt()), Utc.toColumn(deletion.deletedAt()),
						deletion.cause().name(), deletion.deletedBy().map(UserId::value).orElse(null))
				.update();
	}

	@Override
	public Optional<CoupleDeletion> findDeletion(CoupleId couple)
	{
		return jdbc.sql("SELECT * FROM deleted_couple WHERE couple_id = ?").param(couple.value())
				.query((rs, row) -> new CoupleDeletion(new CoupleId(uuid(rs, "couple_id")), new UserId(uuid(rs, "therapist_id")), instant(rs, "ended_at"),
						instant(rs, "deleted_at"), CoupleDeletion.Cause.valueOf(rs.getString("cause")),
						Optional.ofNullable(uuid(rs, "deleted_by")).map(UserId::new)))
				.optional();
	}
}
