package com.aemotionalline.infrastructure.persistence.conversation;

import static com.aemotionalline.infrastructure.persistence.conversation.Columns.instant;
import static com.aemotionalline.infrastructure.persistence.conversation.Columns.integer;
import static com.aemotionalline.infrastructure.persistence.conversation.Columns.uuid;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.aemotionalline.domain.constraint.ConstraintAssignment;
import com.aemotionalline.domain.constraint.ConstraintChangeRequestId;
import com.aemotionalline.domain.constraint.ConstraintChangeRequestStatus;
import com.aemotionalline.domain.constraint.ConstraintRequestRecord;
import com.aemotionalline.domain.conversation.ClosingRequestRecord;
import com.aemotionalline.domain.conversation.Conversation;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.conversation.ConversationSnapshot;
import com.aemotionalline.domain.conversation.DiscussionClosure;
import com.aemotionalline.domain.conversation.DiscussionId;
import com.aemotionalline.domain.conversation.DiscussionSnapshot;
import com.aemotionalline.domain.conversation.FailedNegotiationRecord;
import com.aemotionalline.domain.conversation.NegotiationRecord;
import com.aemotionalline.domain.conversation.OutcomeDeclaration;
import com.aemotionalline.domain.conversation.TherapistClosingRequestRecord;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.message.PointedParagraph;
import com.aemotionalline.domain.message.QuestionParagraph;
import com.aemotionalline.domain.message.SimpleParagraph;
import com.aemotionalline.domain.negotiation.DiscussionTitle;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.negotiation.Proposal;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.infrastructure.persistence.negotiation.ProposalRowRepository;
import com.aemotionalline.infrastructure.persistence.negotiation.ProposalRows;

/**
 * Loads a whole conversation: one query per table, then the facts are assembled into a ConversationSnapshot and the
 * domain rebuilds the conversation with Conversation.restore. Messages and paragraphs are rebuilt in sending order
 * (message.sequence), so every reference points to a paragraph already rebuilt. Derived here: an applied constraint
 * request's previous constraints (those of the applied request before it) and the therapist's snapshot (the first
 * previous_count closing requests of the discussion).
 */
@Component
class ConversationReader
{
	private record MessageRow(UUID id, int discussion, UUID sender, Instant sentAt)
	{
	}

	private record ParagraphRow(UUID id, UUID messageId, String type, String title, String subtitle, String body)
	{
	}

	private record DiscussionRow(int number, String title, String subtitle, UUID origin, String closedWay, Instant closedAt)
	{
	}

	private record Keyed<K, V>(K key, V value)
	{
	}

	private final JdbcClient jdbc;
	private final CoupleRepository couples;
	private final ProposalRowRepository proposals;
	private final Clock clock;

	ConversationReader(JdbcClient jdbc, CoupleRepository couples, ProposalRowRepository proposals, Clock clock)
	{
		this.jdbc = jdbc;
		this.couples = couples;
		this.proposals = proposals;
		this.clock = clock;
	}

	Optional<Conversation> load(UUID id)
	{
		Optional<UUID> coupleId = jdbc.sql("SELECT couple_id FROM conversation WHERE id = ?").param(id)
				.query((rs, row) -> uuid(rs, "couple_id")).optional();

		if (coupleId.isEmpty())
		{
			return Optional.empty();
		}

		Couple couple = couples.findById(new CoupleId(coupleId.get()))
				.orElseThrow(() -> new IllegalStateException("Conversation " + id + " refers to a missing couple."));
		ConversationId conversationId = new ConversationId(id);

		List<MessageRow> messageRows = messageRows(id);
		Map<UUID, Message> messages = rebuildMessages(id, messageRows);
		Map<UUID, Instant> reads = reads(id);

		List<DiscussionSnapshot> discussions = discussions(conversationId, messageRows, messages, reads);
		List<MessageId> sendingOrder = messageRows.stream().map(row -> new MessageId(row.id())).toList();

		ConversationSnapshot snapshot = new ConversationSnapshot(conversationId, couple, discussions, sendingOrder,
				negotiationHistory(conversationId), failedNegotiations(id), constraintRequests(id));

		return Optional.of(Conversation.restore(snapshot, clock));
	}

	// MESSAGES AND PARAGRAPHS

	private List<MessageRow> messageRows(UUID id)
	{
		return jdbc.sql("SELECT id, discussion_number, sender_id, sent_at FROM message WHERE conversation_id = ? ORDER BY sequence")
				.param(id)
				.query((rs, row) -> new MessageRow(uuid(rs, "id"), rs.getInt("discussion_number"), uuid(rs, "sender_id"), instant(rs, "sent_at")))
				.list();
	}

	private Map<UUID, Message> rebuildMessages(UUID id, List<MessageRow> messageRows)
	{
		Map<UUID, List<ParagraphRow>> paragraphsByMessage = jdbc.sql("""
				SELECT p.id, p.message_id, p.type, p.title, p.subtitle, p.body
				FROM paragraph p JOIN message m ON m.id = p.message_id
				WHERE m.conversation_id = ? ORDER BY m.sequence, p.position""")
				.param(id)
				.query((rs, row) -> new ParagraphRow(uuid(rs, "id"), uuid(rs, "message_id"), rs.getString("type"), rs.getString("title"),
						rs.getString("subtitle"), rs.getString("body")))
				.list().stream()
				.collect(Collectors.groupingBy(ParagraphRow::messageId, LinkedHashMap::new, Collectors.toList()));

		Map<UUID, List<UUID>> references = links(id, "paragraph_reference", "referenced_id");
		Map<UUID, List<UUID>> citations = links(id, "paragraph_self_citation", "cited_id");
		Map<UUID, List<UUID>> skipped = grouped(jdbc.sql("""
				SELECT s.message_id, s.question_id FROM message_skipped_question s JOIN message m ON m.id = s.message_id
				WHERE m.conversation_id = ? ORDER BY m.sequence, s.position""")
				.param(id)
				.query((rs, row) -> new Keyed<>(uuid(rs, "message_id"), uuid(rs, "question_id")))
				.list());

		Map<UUID, Paragraph> rebuilt = new HashMap<>();
		Map<UUID, Message> messages = new HashMap<>();

		for (MessageRow row : messageRows)
		{
			Message message = new Message(new MessageId(row.id()), new UserId(row.sender()));

			for (ParagraphRow paragraphRow : paragraphsByMessage.getOrDefault(row.id(), List.of()))
			{
				List<UUID> referenced = references.getOrDefault(paragraphRow.id(), List.of());
				ParagraphId paragraphId = new ParagraphId(paragraphRow.id());

				Paragraph paragraph = switch (paragraphRow.type())
				{
					case "SIMPLE" -> new SimpleParagraph(paragraphId, paragraphRow.title(), paragraphRow.subtitle(), paragraphRow.body());
					case "QUESTION" -> new QuestionParagraph(paragraphId, paragraphRow.title(), paragraphRow.body());
					// A pointed paragraph's one reference is its question.
					case "POINTED" -> new PointedParagraph(paragraphId, (QuestionParagraph) rebuilt.get(referenced.getFirst()), paragraphRow.body());
					default -> throw new IllegalStateException("Unknown paragraph type in the database: " + paragraphRow.type());
				};

				if (!(paragraph instanceof PointedParagraph))
				{
					referenced.forEach(target -> paragraph.addReference(rebuilt.get(target)));
				}

				citations.getOrDefault(paragraphRow.id(), List.of()).forEach(cited -> paragraph.addSelfCitation(rebuilt.get(cited)));

				rebuilt.put(paragraphRow.id(), paragraph);
				message.addParagraph(paragraph);
			}

			message.seal(skipped.getOrDefault(row.id(), List.of()).stream().map(ParagraphId::new).toList(), row.sentAt());
			messages.put(row.id(), message);
		}

		return messages;
	}

	private Map<UUID, List<UUID>> links(UUID id, String table, String targetColumn)
	{
		return grouped(jdbc.sql("SELECT l.paragraph_id, l." + targetColumn + " FROM " + table + " l"
				+ " JOIN paragraph p ON p.id = l.paragraph_id JOIN message m ON m.id = p.message_id"
				+ " WHERE m.conversation_id = ? ORDER BY l.paragraph_id, l.position")
				.param(id)
				.query((rs, row) -> new Keyed<>(uuid(rs, "paragraph_id"), uuid(rs, targetColumn)))
				.list());
	}

	private Map<UUID, Instant> reads(UUID id)
	{
		Map<UUID, Instant> reads = new HashMap<>();

		jdbc.sql("SELECT r.message_id, r.read_at FROM message_read r JOIN message m ON m.id = r.message_id WHERE m.conversation_id = ?")
				.param(id)
				.query((rs, row) -> new Keyed<>(uuid(rs, "message_id"), instant(rs, "read_at")))
				.list()
				.forEach(read -> reads.put(read.key(), read.value()));

		return reads;
	}

	// DISCUSSIONS AND THEIR CLOSING

	private List<DiscussionSnapshot> discussions(ConversationId conversationId, List<MessageRow> messageRows, Map<UUID, Message> messages,
			Map<UUID, Instant> reads)
	{
		UUID id = conversationId.value();

		Map<Integer, List<ClosingRequestRecord>> closing = grouped(jdbc.sql("""
				SELECT discussion_number, requester, sent_at, status, ended_at FROM closing_request
				WHERE conversation_id = ? ORDER BY discussion_number, position""")
				.param(id)
				.query((rs, row) -> new Keyed<>(rs.getInt("discussion_number"), new ClosingRequestRecord(new UserId(uuid(rs, "requester")),
						instant(rs, "sent_at"), ClosingRequestRecord.Status.valueOf(rs.getString("status")), Optional.ofNullable(instant(rs, "ended_at")))))
				.list());

		record TherapistRow(UserId requester, String justification, Instant sentAt, int previousCount, String status, Instant decidedAt)
		{
		}

		Map<Integer, List<TherapistRow>> therapist = grouped(jdbc.sql("""
				SELECT discussion_number, requester, justification, sent_at, previous_count, status, decided_at FROM therapist_closing_request
				WHERE conversation_id = ? ORDER BY discussion_number, position""")
				.param(id)
				.query((rs, row) -> new Keyed<>(rs.getInt("discussion_number"), new TherapistRow(new UserId(uuid(rs, "requester")),
						rs.getString("justification"), instant(rs, "sent_at"), rs.getInt("previous_count"), rs.getString("status"), instant(rs, "decided_at"))))
				.list());

		Map<Integer, List<OutcomeDeclaration>> declarations = grouped(jdbc.sql("""
				SELECT discussion_number, partner_id, outcome, reason, declared_at FROM outcome_declaration
				WHERE conversation_id = ? ORDER BY discussion_number, position""")
				.param(id)
				.query((rs, row) -> new Keyed<>(rs.getInt("discussion_number"), new OutcomeDeclaration(new UserId(uuid(rs, "partner_id")),
						OutcomeDeclaration.Outcome.valueOf(rs.getString("outcome")), rs.getString("reason"), instant(rs, "declared_at"))))
				.list());

		List<DiscussionRow> rows = jdbc.sql("""
				SELECT number, title, subtitle, origin_paragraph_id, closed_way, closed_at FROM discussion
				WHERE conversation_id = ? ORDER BY number""")
				.param(id)
				.query((rs, row) -> new DiscussionRow(rs.getInt("number"), rs.getString("title"), rs.getString("subtitle"),
						uuid(rs, "origin_paragraph_id"), rs.getString("closed_way"), instant(rs, "closed_at")))
				.list();

		List<DiscussionSnapshot> snapshots = new ArrayList<>();

		for (DiscussionRow row : rows)
		{
			List<Message> discussionMessages = messageRows.stream()
					.filter(message -> message.discussion() == row.number())
					.map(message -> messages.get(message.id()))
					.toList();

			Map<MessageId, Instant> readAt = new HashMap<>();
			discussionMessages.forEach(message -> Optional.ofNullable(reads.get(message.getId().value())).ifPresent(at -> readAt.put(message.getId(), at)));

			List<ClosingRequestRecord> closingRequests = closing.getOrDefault(row.number(), List.of());

			List<TherapistClosingRequestRecord> therapistRequests = therapist.getOrDefault(row.number(), List.of()).stream()
					.map(request -> new TherapistClosingRequestRecord(request.requester(), request.justification(), request.sentAt(),
							closingRequests.subList(0, request.previousCount()), TherapistClosingRequestRecord.Status.valueOf(request.status()),
							Optional.ofNullable(request.decidedAt())))
					.toList();

			Optional<DiscussionClosure> closure = row.closedAt() == null ? Optional.empty()
					: Optional.of(new DiscussionClosure(DiscussionClosure.Way.valueOf(row.closedWay()), row.closedAt(),
							declarations.getOrDefault(row.number(), List.of())));

			snapshots.add(new DiscussionSnapshot(new DiscussionId(conversationId, row.number()),
					Optional.ofNullable(row.title()).map(title -> new DiscussionTitle(title, row.subtitle())),
					Optional.ofNullable(row.origin()).map(ParagraphId::new), discussionMessages, readAt, closingRequests, therapistRequests, closure));
		}

		return snapshots;
	}

	// NEGOTIATION HISTORY

	private List<NegotiationRecord> negotiationHistory(ConversationId conversationId)
	{
		return jdbc.sql("SELECT negotiation_id, purpose, discussion_number, archived_at FROM negotiation_record WHERE conversation_id = ? ORDER BY position")
				.param(conversationId.value())
				.query((rs, row) ->
				{
					UUID negotiationId = uuid(rs, "negotiation_id");
					Integer discussion = integer(rs, "discussion_number");

					return new NegotiationRecord(new NegotiationId(negotiationId), NegotiationRecord.Purpose.valueOf(rs.getString("purpose")),
							Optional.ofNullable(discussion).map(number -> new DiscussionId(conversationId, number)), instant(rs, "archived_at"),
							sentProposals(negotiationId));
				})
				.list();
	}

	private List<FailedNegotiationRecord> failedNegotiations(UUID id)
	{
		return jdbc.sql("""
				SELECT negotiation_id, attempted_purpose, outcome, closed_by, started_at, closed_at FROM failed_negotiation_record
				WHERE conversation_id = ? ORDER BY position""")
				.param(id)
				.query((rs, row) ->
				{
					UUID negotiationId = uuid(rs, "negotiation_id");

					return new FailedNegotiationRecord(new NegotiationId(negotiationId), NegotiationRecord.Purpose.valueOf(rs.getString("attempted_purpose")),
							FailedNegotiationRecord.Outcome.valueOf(rs.getString("outcome")), new UserId(uuid(rs, "closed_by")), instant(rs, "started_at"),
							instant(rs, "closed_at"), sentProposals(negotiationId));
				})
				.list();
	}

	private List<Proposal> sentProposals(UUID negotiationId)
	{
		return proposals.findByNegotiationIdAndPositionIsNotNullOrderByPosition(negotiationId).stream().map(ProposalRows::toDomain).toList();
	}

	// CONSTRAINT REQUESTS

	private List<ConstraintRequestRecord> constraintRequests(UUID id)
	{
		record RequestRow(UUID id, UserId proposedBy, String status, Instant proposedAt, Instant closedAt, UUID rejectedBy)
		{
		}

		List<RequestRow> rows = jdbc.sql("""
				SELECT id, proposed_by, status, proposed_at, closed_at, rejected_by FROM constraint_request
				WHERE conversation_id = ? ORDER BY position""")
				.param(id)
				.query((rs, row) -> new RequestRow(uuid(rs, "id"), new UserId(uuid(rs, "proposed_by")), rs.getString("status"),
						instant(rs, "proposed_at"), instant(rs, "closed_at"), uuid(rs, "rejected_by")))
				.list();

		Map<UUID, List<UserId>> approvals = grouped(jdbc.sql("""
				SELECT a.request_id, a.partner_id FROM constraint_request_approval a JOIN constraint_request r ON r.id = a.request_id
				WHERE r.conversation_id = ?""")
				.param(id)
				.query((rs, row) -> new Keyed<>(uuid(rs, "request_id"), new UserId(uuid(rs, "partner_id"))))
				.list());

		Map<UUID, List<ConstraintAssignment>> assignments = grouped(jdbc.sql("""
				SELECT s.* FROM constraint_assignment s JOIN constraint_request r ON r.id = s.request_id
				WHERE r.conversation_id = ? ORDER BY r.position, s.position""")
				.param(id)
				.query((rs, row) -> new Keyed<>(uuid(rs, "request_id"), ConstraintRows.assignment(rs)))
				.list());

		List<ConstraintRequestRecord> records = new ArrayList<>();
		List<ConstraintAssignment> inForce = List.of();

		// The constraints change only through applied requests: the previous ones are those of the last applied request.
		for (RequestRow row : rows)
		{
			ConstraintChangeRequestStatus status = ConstraintChangeRequestStatus.valueOf(row.status());
			List<ConstraintAssignment> requestAssignments = assignments.getOrDefault(row.id(), List.of());
			Optional<List<ConstraintAssignment>> previous = status == ConstraintChangeRequestStatus.APPLIED ? Optional.of(inForce) : Optional.empty();

			records.add(new ConstraintRequestRecord(new ConstraintChangeRequestId(row.id()), row.proposedBy(), requestAssignments, status,
					Set.copyOf(approvals.getOrDefault(row.id(), List.of())), row.proposedAt(), Optional.ofNullable(row.closedAt()),
					Optional.ofNullable(row.rejectedBy()).map(UserId::new), previous));

			if (status == ConstraintChangeRequestStatus.APPLIED)
			{
				inForce = requestAssignments;
			}
		}

		return records;
	}

	private static <K, V> Map<K, List<V>> grouped(List<Keyed<K, V>> rows)
	{
		return rows.stream().collect(Collectors.groupingBy(Keyed::key, LinkedHashMap::new, Collectors.mapping(Keyed::value, Collectors.toList())));
	}
}
