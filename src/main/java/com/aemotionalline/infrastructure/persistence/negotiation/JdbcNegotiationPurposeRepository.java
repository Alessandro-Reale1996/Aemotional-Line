package com.aemotionalline.infrastructure.persistence.negotiation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.aemotionalline.application.NegotiationPurpose;
import com.aemotionalline.application.NegotiationPurposeRepository;
import com.aemotionalline.domain.conversation.NegotiationRecord;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.negotiation.NegotiationStatus;

/** The application's NegotiationPurposeRepository on MariaDB. A purpose is deleted with its negotiation (ON DELETE CASCADE). */
@Repository
public class JdbcNegotiationPurposeRepository implements NegotiationPurposeRepository
{
	private final JdbcClient jdbc;

	JdbcNegotiationPurposeRepository(JdbcClient jdbc)
	{
		this.jdbc = jdbc;
	}

	@Override
	public void save(NegotiationPurpose purpose)
	{
		jdbc.sql("""
				INSERT INTO negotiation_purpose (negotiation_id, purpose, origin_paragraph_id) VALUES (?, ?, ?)
				ON DUPLICATE KEY UPDATE purpose = VALUES(purpose), origin_paragraph_id = VALUES(origin_paragraph_id)""")
				.params(purpose.negotiationId().value(), purpose.purpose().name(), purpose.origin().map(ParagraphId::value).orElse(null))
				.update();
	}

	@Override
	public Optional<NegotiationPurpose> findByNegotiation(NegotiationId negotiationId)
	{
		return jdbc.sql("SELECT * FROM negotiation_purpose WHERE negotiation_id = ?")
				.param(negotiationId.value())
				.query((rs, row) -> purpose(rs))
				.optional();
	}

	@Override
	public List<NegotiationPurpose> findInProgressOf(CoupleId coupleId)
	{
		return jdbc.sql("""
				SELECT p.* FROM negotiation_purpose p JOIN negotiation n ON n.id = p.negotiation_id
				WHERE n.couple_id = ? AND n.status IN (?, ?) ORDER BY n.started_at""")
				.params(coupleId.value(), NegotiationStatus.DRAFT.name(), NegotiationStatus.ACCEPTED.name())
				.query((rs, row) -> purpose(rs))
				.list();
	}

	private static NegotiationPurpose purpose(ResultSet rs) throws SQLException
	{
		return new NegotiationPurpose(new NegotiationId(uuid(rs, "negotiation_id")), NegotiationRecord.Purpose.valueOf(rs.getString("purpose")),
				Optional.ofNullable(uuid(rs, "origin_paragraph_id")).map(ParagraphId::new));
	}

	private static UUID uuid(ResultSet rs, String column) throws SQLException
	{
		Object value = rs.getObject(column);

		return value == null ? null : value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
	}
}
