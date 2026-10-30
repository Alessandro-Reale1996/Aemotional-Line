package com.aemotionalline.infrastructure.persistence.conversation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.aemotionalline.domain.constraint.ConstraintAssignment;
import com.aemotionalline.domain.constraint.ConstraintRequestRecord;
import com.aemotionalline.domain.conversation.ClosingRequestRecord;
import com.aemotionalline.domain.conversation.Conversation;
import com.aemotionalline.domain.conversation.Discussion;
import com.aemotionalline.domain.conversation.DiscussionClosure;
import com.aemotionalline.domain.conversation.FailedNegotiationRecord;
import com.aemotionalline.domain.conversation.NegotiationRecord;
import com.aemotionalline.domain.conversation.OutcomeDeclaration;
import com.aemotionalline.domain.conversation.TherapistClosingRequestRecord;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.message.ParagraphType;
import com.aemotionalline.domain.negotiation.Proposal;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.infrastructure.persistence.Utc;
import com.aemotionalline.infrastructure.persistence.negotiation.ProposalRow;
import com.aemotionalline.infrastructure.persistence.negotiation.ProposalRows;

/**
 * Saves a conversation. What is only ever added (messages with their paragraphs, references, self-citations and skipped
 * questions; negotiation records) is inserted when it isn't stored yet, and never rewritten. The few rows that change
 * state (discussions and their closing requests, reads, declarations, constraint requests) are written with
 * INSERT ... ON DUPLICATE KEY UPDATE on their key. A record's proposals are upserted too, so it doesn't matter whether the
 * consumed negotiation was deleted before or after (its unsent draft may share its id with the request a failed record keeps).
 */
@Component
class ConversationWriter
{
	private final JdbcClient jdbc;

	ConversationWriter(JdbcClient jdbc)
	{
		this.jdbc = jdbc;
	}

	void save(Conversation conversation)
	{
		UUID id = conversation.getId().value();

		jdbc.sql("INSERT INTO conversation (id, couple_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE id = id")
				.params(id, conversation.getCouple().getId().value())
				.update();

		conversation.getDiscussions().forEach(discussion -> saveDiscussion(id, discussion));
		saveNewMessages(id, conversation);
		conversation.getDiscussions().forEach(discussion -> saveDiscussionState(id, discussion));
		saveNegotiationHistory(id, conversation);
		saveConstraintRequests(id, conversation);
	}

	// DISCUSSIONS

	private void saveDiscussion(UUID id, Discussion discussion)
	{
		DiscussionClosure closure = discussion.getClosure().orElse(null);

		jdbc.sql("""
				INSERT INTO discussion (conversation_id, number, title, subtitle, origin_paragraph_id, closed_way, closed_at)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				ON DUPLICATE KEY UPDATE closed_way = VALUES(closed_way), closed_at = VALUES(closed_at)""")
				.params(id, discussion.getId().discussionNumber(),
						discussion.getTitle().map(title -> title.title()).orElse(null),
						discussion.getTitle().map(title -> title.subtitle()).orElse(null),
						discussion.getOriginParagraphId().map(ParagraphId::value).orElse(null),
						closure == null ? null : closure.way().name(),
						closure == null ? null : Utc.toColumn(closure.closedAt()))
				.update();
	}

	// Reads, closing requests and declarations, after the messages they refer to.
	private void saveDiscussionState(UUID id, Discussion discussion)
	{
		int number = discussion.getId().discussionNumber();

		for (Message message : discussion.getMessages())
		{
			discussion.getReadAt(message.getId()).ifPresent(readAt -> jdbc.sql(
					"INSERT INTO message_read (message_id, read_at) VALUES (?, ?) ON DUPLICATE KEY UPDATE message_id = message_id")
					.params(message.getId().value(), Utc.toColumn(readAt))
					.update());
		}

		List<ClosingRequestRecord> closing = discussion.getClosingRequests();

		for (int position = 0; position < closing.size(); position++)
		{
			ClosingRequestRecord request = closing.get(position);

			jdbc.sql("""
					INSERT INTO closing_request (conversation_id, discussion_number, position, requester, sent_at, status, ended_at)
					VALUES (?, ?, ?, ?, ?, ?, ?)
					ON DUPLICATE KEY UPDATE status = VALUES(status), ended_at = VALUES(ended_at)""")
					.params(id, number, position, request.requester().value(), Utc.toColumn(request.sentAt()), request.status().name(),
							Utc.toColumn(request.endedAt().orElse(null)))
					.update();
		}

		List<TherapistClosingRequestRecord> therapist = discussion.getTherapistClosingRequests();

		for (int position = 0; position < therapist.size(); position++)
		{
			TherapistClosingRequestRecord request = therapist.get(position);

			jdbc.sql("""
					INSERT INTO therapist_closing_request
					(conversation_id, discussion_number, position, requester, justification, sent_at, previous_count, status, decided_at)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
					ON DUPLICATE KEY UPDATE status = VALUES(status), decided_at = VALUES(decided_at)""")
					.params(id, number, position, request.requester().value(), request.justification(), Utc.toColumn(request.sentAt()),
							request.previousRequests().size(), request.status().name(), Utc.toColumn(request.decidedAt().orElse(null)))
					.update();
		}

		List<OutcomeDeclaration> declarations = discussion.getClosure().map(DiscussionClosure::declarations).orElse(List.of());

		for (int position = 0; position < declarations.size(); position++)
		{
			OutcomeDeclaration declaration = declarations.get(position);

			jdbc.sql("""
					INSERT INTO outcome_declaration (conversation_id, discussion_number, partner_id, position, outcome, reason, declared_at)
					VALUES (?, ?, ?, ?, ?, ?, ?)
					ON DUPLICATE KEY UPDATE partner_id = partner_id""")
					.params(id, number, declaration.partner().value(), position, declaration.outcome().name(), declaration.reason(),
							Utc.toColumn(declaration.declaredAt()))
					.update();
		}
	}

	// MESSAGES: only the new ones, in sending order (the graph's order), so references point to stored paragraphs.

	private void saveNewMessages(UUID id, Conversation conversation)
	{
		Set<UUID> stored = new HashSet<>(jdbc.sql("SELECT id FROM message WHERE conversation_id = ?").param(id)
				.query((rs, row) -> Columns.uuid(rs, "id")).list());

		Map<ParagraphId, Message> messageOf = new HashMap<>();
		Map<Message, Integer> discussionOf = new HashMap<>();

		for (Discussion discussion : conversation.getDiscussions())
		{
			for (Message message : discussion.getMessages())
			{
				discussionOf.put(message, discussion.getId().discussionNumber());
				message.getParagraphs().forEach(paragraph -> messageOf.put(paragraph.getId(), message));
			}
		}

		Set<Message> sendingOrder = new LinkedHashSet<>();
		conversation.getConversationGraph().getParagraphs().forEach(paragraph -> sendingOrder.add(messageOf.get(paragraph.getId())));

		int sequence = 0;

		for (Message message : sendingOrder)
		{
			if (!stored.contains(message.getId().value()))
			{
				insertMessage(id, message, discussionOf.get(message), sequence);
			}

			sequence++;
		}
	}

	private void insertMessage(UUID id, Message message, int discussion, int sequence)
	{
		UUID messageId = message.getId().value();

		jdbc.sql("INSERT INTO message (id, conversation_id, discussion_number, sequence, sender_id, sent_at) VALUES (?, ?, ?, ?, ?, ?)")
				.params(messageId, id, discussion, sequence, message.getSenderId().value(), Utc.toColumn(message.getSentAt()))
				.update();

		List<Paragraph> paragraphs = message.getParagraphs();

		for (int position = 0; position < paragraphs.size(); position++)
		{
			Paragraph paragraph = paragraphs.get(position);
			boolean pointed = paragraph.getType() == ParagraphType.POINTED;

			// A pointed paragraph's title is computed from its question: not stored.
			jdbc.sql("INSERT INTO paragraph (id, message_id, position, type, title, subtitle, body) VALUES (?, ?, ?, ?, ?, ?, ?)")
					.params(paragraph.getId().value(), messageId, position, paragraph.getType().name(), pointed ? null : paragraph.getTitle(),
							paragraph.getSubtitle(), paragraph.getBody())
					.update();

			insertLinks("paragraph_reference", "referenced_id", paragraph.getId(), paragraph.getReferences());
			insertLinks("paragraph_self_citation", "cited_id", paragraph.getId(), paragraph.getSelfCitations());
		}

		List<ParagraphId> skipped = message.getQuestionsLeftUnanswered();

		for (int position = 0; position < skipped.size(); position++)
		{
			jdbc.sql("INSERT INTO message_skipped_question (message_id, position, question_id) VALUES (?, ?, ?)")
					.params(messageId, position, skipped.get(position).value())
					.update();
		}
	}

	private void insertLinks(String table, String targetColumn, ParagraphId from, List<Paragraph> targets)
	{
		for (int position = 0; position < targets.size(); position++)
		{
			jdbc.sql("INSERT INTO " + table + " (paragraph_id, position, " + targetColumn + ") VALUES (?, ?, ?)")
					.params(from.value(), position, targets.get(position).getId().value())
					.update();
		}
	}

	// NEGOTIATION HISTORY: only the new records, with their proposals.

	private void saveNegotiationHistory(UUID id, Conversation conversation)
	{
		Set<UUID> storedRecords = storedIds("SELECT negotiation_id FROM negotiation_record WHERE conversation_id = ?", id);
		List<NegotiationRecord> history = conversation.getNegotiationHistory();

		for (int position = 0; position < history.size(); position++)
		{
			NegotiationRecord record = history.get(position);

			if (!storedRecords.contains(record.negotiationId().value()))
			{
				jdbc.sql("INSERT INTO negotiation_record (negotiation_id, conversation_id, position, purpose, discussion_number, archived_at) VALUES (?, ?, ?, ?, ?, ?)")
						.params(record.negotiationId().value(), id, position, record.purpose().name(),
								record.discussionId().map(discussion -> discussion.discussionNumber()).orElse(null), Utc.toColumn(record.archivedAt()))
						.update();
				upsertProposals(record.negotiationId().value(), record.proposals());
			}
		}

		Set<UUID> storedFailures = storedIds("SELECT negotiation_id FROM failed_negotiation_record WHERE conversation_id = ?", id);
		List<FailedNegotiationRecord> failed = conversation.getFailedNegotiations();

		for (int position = 0; position < failed.size(); position++)
		{
			FailedNegotiationRecord record = failed.get(position);

			if (!storedFailures.contains(record.negotiationId().value()))
			{
				jdbc.sql("""
						INSERT INTO failed_negotiation_record
						(negotiation_id, conversation_id, position, attempted_purpose, outcome, closed_by, started_at, closed_at)
						VALUES (?, ?, ?, ?, ?, ?, ?, ?)""")
						.params(record.negotiationId().value(), id, position, record.attemptedPurpose().name(), record.outcome().name(),
								record.closedBy().value(), Utc.toColumn(record.startedAt()), Utc.toColumn(record.closedAt()))
						.update();
				upsertProposals(record.negotiationId().value(), record.proposals());
			}
		}
	}

	private void upsertProposals(UUID negotiationId, List<Proposal> proposals)
	{
		for (int position = 0; position < proposals.size(); position++)
		{
			ProposalRow row = ProposalRows.toRow(proposals.get(position), negotiationId, position);

			jdbc.sql("""
					INSERT INTO proposal (id, negotiation_id, position, author_id, kind, text, title, subtitle, justification, sent_at, status)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					ON DUPLICATE KEY UPDATE negotiation_id = VALUES(negotiation_id), position = VALUES(position), author_id = VALUES(author_id),
					kind = VALUES(kind), text = VALUES(text), title = VALUES(title), subtitle = VALUES(subtitle),
					justification = VALUES(justification), sent_at = VALUES(sent_at), status = VALUES(status)""")
					.params(row.id(), row.negotiationId(), row.position(), row.authorId(), row.kind(), row.text(), row.title(), row.subtitle(),
							row.justification(), row.sentAt(), row.status())
					.update();
		}
	}

	// CONSTRAINT REQUESTS: the header changes state; approvals and assignments are only added.

	private void saveConstraintRequests(UUID id, Conversation conversation)
	{
		List<ConstraintRequestRecord> requests = conversation.getConstraintRequestHistory();

		for (int position = 0; position < requests.size(); position++)
		{
			ConstraintRequestRecord request = requests.get(position);
			UUID requestId = request.id().value();

			jdbc.sql("""
					INSERT INTO constraint_request (id, conversation_id, position, proposed_by, status, proposed_at, closed_at, rejected_by)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?)
					ON DUPLICATE KEY UPDATE status = VALUES(status), closed_at = VALUES(closed_at), rejected_by = VALUES(rejected_by)""")
					.params(requestId, id, position, request.proposedBy().value(), request.status().name(), Utc.toColumn(request.proposedAt()),
							Utc.toColumn(request.closedAt().orElse(null)), request.rejectedBy().map(UserId::value).orElse(null))
					.update();

			for (UserId partner : request.approvals())
			{
				jdbc.sql("INSERT INTO constraint_request_approval (request_id, partner_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE request_id = request_id")
						.params(requestId, partner.value())
						.update();
			}

			List<ConstraintAssignment> assignments = request.assignments();

			for (int index = 0; index < assignments.size(); index++)
			{
				ConstraintAssignment assignment = assignments.get(index);
				ConstraintRows.Values values = ConstraintRows.values(assignment.getConstraint());

				jdbc.sql("""
						INSERT INTO constraint_assignment
						(request_id, position, user_id, type, scope, not_before, delay_millis, latitude, longitude, radius_metres, direction)
						VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						ON DUPLICATE KEY UPDATE request_id = request_id""")
						.params(requestId, index, assignment.getUserId().value(), values.type(), values.scope(), values.notBefore(),
								values.delayMillis(), values.latitude(), values.longitude(), values.radiusMetres(), values.direction())
						.update();
			}
		}
	}

	private Set<UUID> storedIds(String sql, UUID id)
	{
		return new HashSet<>(jdbc.sql(sql).param(id).query((rs, row) -> Columns.uuid(rs, 1)).list());
	}
}
