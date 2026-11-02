package com.aemotionalline.infrastructure.persistence.archive;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.aemotionalline.application.ArchiveSearch;
import com.aemotionalline.application.view.ArchiveEntry;
import com.aemotionalline.application.view.ArchiveProposal;
import com.aemotionalline.application.view.ArchiveSearchResult;
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
 * checked by the application layer; the closed-envelope rule is applied here, for the viewer, in SQL.
 */
@Component
@Transactional(readOnly = true)
public class ArchiveQueries implements ArchiveSearch
{
	private static final String PARAGRAPH_COLUMNS = """
			SELECT p.id, p.type, p.title, p.subtitle, p.body, p.position, m.discussion_number, m.id AS message_id, m.sender_id, m.sent_at, m.sequence
			FROM paragraph p JOIN message m ON m.id = p.message_id
			""";

	// The closed-envelope rule, for the viewer: the therapist sees everything; otherwise their own messages and opened ones.
	// Parameters: seesEverything, viewer.
	private static final String VISIBLE = "(? OR m.sender_id = ? OR EXISTS (SELECT 1 FROM message_read r WHERE r.message_id = m.id))";

	private final JdbcClient jdbc;

	ArchiveQueries(JdbcClient jdbc)
	{
		this.jdbc = jdbc;
	}

	/**
	 * The messages sent from {@code from} (included) to {@code to} (excluded), in sending order: the visible ones paragraph by
	 * paragraph, each unopened one as a single closed envelope.
	 */
	@Override
	public List<ArchiveEntry> messagesBetween(ConversationId conversation, UserId viewer, boolean seesEverything, Instant from, Instant to)
	{
		if (conversation == null || viewer == null || from == null || to == null || from.isAfter(to))
		{
			throw new IllegalArgumentException("A conversation, a viewer and an interval whose start isn't after its end are required.");
		}

		record Ordered(int sequence, int position, ArchiveEntry entry)
		{
		}

		List<Ordered> visible = jdbc.sql(PARAGRAPH_COLUMNS + " WHERE m.conversation_id = ? AND m.sent_at >= ? AND m.sent_at < ? AND " + VISIBLE)
				.params(conversation.value(), Utc.toColumn(from), Utc.toColumn(to), seesEverything, viewer.value())
				.query((rs, row) -> new Ordered(rs.getInt("sequence"), rs.getInt("position"), paragraph(rs, conversation)))
				.list();

		List<Ordered> envelopes = jdbc.sql("SELECT m.id AS message_id, m.discussion_number, m.sender_id, m.sent_at, m.sequence FROM message m"
				+ " WHERE m.conversation_id = ? AND m.sent_at >= ? AND m.sent_at < ? AND NOT " + VISIBLE)
				.params(conversation.value(), Utc.toColumn(from), Utc.toColumn(to), seesEverything, viewer.value())
				.query((rs, row) -> new Ordered(rs.getInt("sequence"), -1, ArchiveEntry.envelope(new DiscussionId(conversation, rs.getInt("discussion_number")),
						new MessageId(uuid(rs, "message_id")), new UserId(uuid(rs, "sender_id")), Utc.fromColumn(rs.getObject("sent_at", LocalDateTime.class)))))
				.list();

		return java.util.stream.Stream.concat(visible.stream(), envelopes.stream())
				.sorted(java.util.Comparator.comparingInt(Ordered::sequence).thenComparingInt(Ordered::position))
				.map(Ordered::entry)
				.toList();
	}

	/**
	 * The paragraphs whose title, subtitle or body contains the text, and the proposals (agreement texts, discussion titles)
	 * of the negotiations recorded in the conversation, took effect or failed, that contain it. The text is matched
	 * literally: % and _ are not wildcards.
	 */
	@Override
	public ArchiveSearchResult search(ConversationId conversation, UserId viewer, boolean seesEverything, String text)
	{
		if (conversation == null || viewer == null || text == null || text.isBlank())
		{
			throw new IllegalArgumentException("A conversation, a viewer and a text to search are required.");
		}

		String pattern = "%" + escapeLike(text.strip()) + "%";
		UUID id = conversation.value();

		// Unopened messages are left out entirely: even a closed envelope would reveal that their content matches.
		List<ArchiveEntry> paragraphs = jdbc.sql(PARAGRAPH_COLUMNS
				+ " WHERE m.conversation_id = ? AND (p.title LIKE ? OR p.subtitle LIKE ? OR p.body LIKE ?) AND " + VISIBLE + " ORDER BY m.sequence, p.position")
				.params(id, pattern, pattern, pattern, seesEverything, viewer.value())
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

	private static ArchiveEntry paragraph(ResultSet rs, ConversationId conversation) throws SQLException
	{
		return new ArchiveEntry(Optional.of(new ParagraphId(uuid(rs, "id"))), ParagraphType.valueOf(rs.getString("type")), rs.getString("title"),
				rs.getString("subtitle"), rs.getString("body"), new DiscussionId(conversation, rs.getInt("discussion_number")),
				new MessageId(uuid(rs, "message_id")), new UserId(uuid(rs, "sender_id")), Utc.fromColumn(rs.getObject("sent_at", LocalDateTime.class)), false);
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
