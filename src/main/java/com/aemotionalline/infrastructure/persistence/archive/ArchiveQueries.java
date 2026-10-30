package com.aemotionalline.infrastructure.persistence.archive;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.conversation.DiscussionId;
import com.aemotionalline.domain.conversation.NegotiationRecord;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.message.ParagraphType;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.negotiation.Proposal;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.infrastructure.persistence.Utc;
import com.aemotionalline.infrastructure.persistence.negotiation.ProposalRow;
import com.aemotionalline.infrastructure.persistence.negotiation.ProposalRows;

/**
 * The archive's searches by time and by keyword (spec 1.3.4), as read-only queries on the tables: they don't load the
 * conversation, and they are always limited to one conversation. The keyword search is a substring search, case- and
 * accent-insensitive through the schema's collation ("perche" finds "Perché"); a conversation is small enough not to need
 * a full-text index, which would also miss Italian variants. Who may search (the couple's partners and therapist) is
 * checked by the application layer.
 */
@Component
@Transactional(readOnly = true)
public class ArchiveQueries
{
	private static final String PARAGRAPH_COLUMNS = """
			SELECT p.id, p.type, p.title, p.subtitle, p.body, m.discussion_number, m.id AS message_id, m.sender_id, m.sent_at
			FROM paragraph p JOIN message m ON m.id = p.message_id
			""";

	private final JdbcClient jdbc;

	ArchiveQueries(JdbcClient jdbc)
	{
		this.jdbc = jdbc;
	}

	/** The paragraphs of the messages sent from {@code from} (included) to {@code to} (excluded), in sending order. */
	public List<ArchiveParagraph> messagesBetween(ConversationId conversation, Instant from, Instant to)
	{
		if (conversation == null || from == null || to == null || from.isAfter(to))
		{
			throw new IllegalArgumentException("A conversation and an interval whose start isn't after its end are required.");
		}

		return jdbc.sql(PARAGRAPH_COLUMNS + " WHERE m.conversation_id = ? AND m.sent_at >= ? AND m.sent_at < ? ORDER BY m.sequence, p.position")
				.params(conversation.value(), Utc.toColumn(from), Utc.toColumn(to))
				.query((rs, row) -> paragraph(rs, conversation))
				.list();
	}

	/**
	 * The paragraphs whose title, subtitle or body contains the text, and the proposals (agreement texts, discussion titles)
	 * of the negotiations recorded in the conversation, took effect or failed, that contain it. The text is matched
	 * literally: % and _ are not wildcards.
	 */
	public ArchiveSearchResult search(ConversationId conversation, String text)
	{
		if (conversation == null || text == null || text.isBlank())
		{
			throw new IllegalArgumentException("A conversation and a text to search are required.");
		}

		String pattern = "%" + escapeLike(text.strip()) + "%";
		UUID id = conversation.value();

		List<ArchiveParagraph> paragraphs = jdbc.sql(PARAGRAPH_COLUMNS
				+ " WHERE m.conversation_id = ? AND (p.title LIKE ? OR p.subtitle LIKE ? OR p.body LIKE ?) ORDER BY m.sequence, p.position")
				.params(id, pattern, pattern, pattern)
				.query((rs, row) -> paragraph(rs, conversation))
				.list();

		List<ArchiveProposal> tookEffect = proposals("negotiation_record", "purpose", ArchiveProposal.Section.TOOK_EFFECT, id, pattern);
		List<ArchiveProposal> failed = proposals("failed_negotiation_record", "attempted_purpose", ArchiveProposal.Section.FAILED, id, pattern);

		return new ArchiveSearchResult(paragraphs, java.util.stream.Stream.concat(tookEffect.stream(), failed.stream()).toList());
	}

	private List<ArchiveProposal> proposals(String recordTable, String purposeColumn, ArchiveProposal.Section section, UUID conversation, String pattern)
	{
		return jdbc.sql("SELECT r.negotiation_id AS record_negotiation, r." + purposeColumn + " AS purpose, pr.* FROM " + recordTable + " r"
				+ " JOIN proposal pr ON pr.negotiation_id = r.negotiation_id AND pr.position IS NOT NULL"
				+ " WHERE r.conversation_id = ? AND (pr.text LIKE ? OR pr.title LIKE ? OR pr.subtitle LIKE ?)"
				+ " ORDER BY r.position, pr.position")
				.params(conversation, pattern, pattern, pattern)
				.query((rs, row) ->
				{
					Proposal proposal = ProposalRows.toDomain(new ProposalRow(uuid(rs, "id"), uuid(rs, "negotiation_id"), rs.getInt("position"),
							uuid(rs, "author_id"), rs.getString("kind"), rs.getString("text"), rs.getString("title"), rs.getString("subtitle"),
							rs.getString("justification"), rs.getObject("sent_at", LocalDateTime.class), rs.getString("status")));

					return new ArchiveProposal(new NegotiationId(uuid(rs, "record_negotiation")), NegotiationRecord.Purpose.valueOf(rs.getString("purpose")),
							section, proposal.getId(), proposal.getAuthor(), proposal.getContent(), proposal.getSentAt(), proposal.getProposalStatus());
				})
				.list();
	}

	private static ArchiveParagraph paragraph(ResultSet rs, ConversationId conversation) throws SQLException
	{
		return new ArchiveParagraph(new ParagraphId(uuid(rs, "id")), ParagraphType.valueOf(rs.getString("type")), rs.getString("title"),
				rs.getString("subtitle"), rs.getString("body"), new DiscussionId(conversation, rs.getInt("discussion_number")),
				new MessageId(uuid(rs, "message_id")), new UserId(uuid(rs, "sender_id")), Utc.fromColumn(rs.getObject("sent_at", LocalDateTime.class)));
	}

	// The backslash is MariaDB's default LIKE escape character.
	static String escapeLike(String text)
	{
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private static UUID uuid(ResultSet rs, String column) throws SQLException
	{
		Object value = rs.getObject(column);

		return value == null ? null : value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
	}
}
