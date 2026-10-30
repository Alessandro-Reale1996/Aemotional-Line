package com.aemotionalline.domain.conversation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.aemotionalline.domain.constraint.ConstraintAssignment;
import com.aemotionalline.domain.constraint.ConstraintRequestRecord;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.message.PointedParagraph;
import com.aemotionalline.domain.message.QuestionParagraph;
import com.aemotionalline.domain.message.SimpleParagraph;
import com.aemotionalline.domain.negotiation.StoredFacts;

/**
 * Test helper that plays the persistence layer for conversations: it reads a conversation's facts through its getters
 * and rebuilds every message, paragraph and proposal from them (paragraphs in sending order, so each reference points to
 * a paragraph already rebuilt), so a restored conversation shares no mutable object with the original. A fingerprint
 * lists every observable fact, to compare an original with its restored copy.
 */
final class ConversationFacts
{
	private ConversationFacts()
	{
	}

	static ConversationSnapshot snapshot(Conversation conversation)
	{
		List<MessageId> sendingOrder = sendingOrder(conversation);
		Map<MessageId, Message> rebuilt = rebuildMessages(conversation, sendingOrder);

		List<DiscussionSnapshot> discussions = conversation.getDiscussions().stream()
				.map(discussion -> snapshot(discussion, rebuilt))
				.toList();

		List<NegotiationRecord> history = conversation.getNegotiationHistory().stream()
				.map(record -> new NegotiationRecord(record.negotiationId(), record.purpose(), record.discussionId(), record.archivedAt(),
						StoredFacts.copy(record.proposals())))
				.toList();

		List<FailedNegotiationRecord> failed = conversation.getFailedNegotiations().stream()
				.map(record -> new FailedNegotiationRecord(record.negotiationId(), record.attemptedPurpose(), record.outcome(), record.closedBy(),
						record.startedAt(), record.closedAt(), StoredFacts.copy(record.proposals())))
				.toList();

		return new ConversationSnapshot(conversation.getId(), conversation.getCouple(), discussions, sendingOrder, history, failed,
				conversation.getConstraintRequestHistory());
	}

	// The graph lists the paragraphs in sending order: the messages they belong to, once each, are in sending order too.
	private static List<MessageId> sendingOrder(Conversation conversation)
	{
		Map<ParagraphId, MessageId> messageOf = new HashMap<>();
		conversation.getDiscussions().forEach(discussion -> discussion.getMessages()
				.forEach(message -> message.getParagraphs().forEach(paragraph -> messageOf.put(paragraph.getId(), message.getId()))));

		Set<MessageId> order = new LinkedHashSet<>();
		conversation.getConversationGraph().getParagraphs().forEach(paragraph -> order.add(messageOf.get(paragraph.getId())));

		return List.copyOf(order);
	}

	private static Map<MessageId, Message> rebuildMessages(Conversation conversation, List<MessageId> sendingOrder)
	{
		Map<MessageId, Message> originals = new HashMap<>();
		conversation.getDiscussions().forEach(discussion -> discussion.getMessages().forEach(message -> originals.put(message.getId(), message)));

		Map<ParagraphId, Paragraph> paragraphs = new HashMap<>();
		Map<MessageId, Message> rebuilt = new HashMap<>();

		for (MessageId messageId : sendingOrder)
		{
			Message original = originals.get(messageId);
			Message copy = new Message(original.getId(), original.getSenderId());

			for (Paragraph paragraph : original.getParagraphs())
			{
				Paragraph paragraphCopy = rebuild(paragraph, paragraphs);
				paragraphs.put(paragraphCopy.getId(), paragraphCopy);
				copy.addParagraph(paragraphCopy);
			}

			copy.seal(original.getQuestionsLeftUnanswered(), original.getSentAt());
			rebuilt.put(messageId, copy);
		}

		return rebuilt;
	}

	private static Paragraph rebuild(Paragraph paragraph, Map<ParagraphId, Paragraph> alreadyRebuilt)
	{
		Paragraph copy = switch (paragraph.getType())
		{
			case SIMPLE -> new SimpleParagraph(paragraph.getId(), paragraph.getTitle(), paragraph.getSubtitle(), paragraph.getBody());
			case QUESTION -> new QuestionParagraph(paragraph.getId(), paragraph.getTitle(), paragraph.getBody());
			case POINTED -> new PointedParagraph(paragraph.getId(),
					(QuestionParagraph) alreadyRebuilt.get(((PointedParagraph) paragraph).getQuestion().getId()), paragraph.getBody());
		};

		// A pointed paragraph's one reference, its question, is set by its constructor.
		if (!(copy instanceof PointedParagraph))
		{
			paragraph.getReferences().forEach(reference -> copy.addReference(alreadyRebuilt.get(reference.getId())));
		}

		paragraph.getSelfCitations().forEach(cited -> copy.addSelfCitation(alreadyRebuilt.get(cited.getId())));

		return copy;
	}

	private static DiscussionSnapshot snapshot(Discussion discussion, Map<MessageId, Message> rebuilt)
	{
		Map<MessageId, Instant> readAt = new HashMap<>();
		discussion.getMessages().forEach(message -> discussion.getReadAt(message.getId()).ifPresent(at -> readAt.put(message.getId(), at)));

		return new DiscussionSnapshot(discussion.getId(), discussion.getTitle(), discussion.getOriginParagraphId(),
				discussion.getMessages().stream().map(message -> rebuilt.get(message.getId())).toList(), readAt,
				discussion.getClosingRequests(), discussion.getTherapistClosingRequests(), discussion.getClosure());
	}

	// FINGERPRINT:

	static String fingerprint(Conversation conversation)
	{
		List<String> lines = new ArrayList<>();

		lines.add("id " + conversation.getId() + " couple " + conversation.getCouple().getId());
		lines.add("agreement " + StoredFacts.fingerprint(conversation.getAgreement()));
		lines.add("constraints " + describe(conversation.getConstraintAssignments()));
		lines.add("graph " + conversation.getConversationGraph().getParagraphs().stream().map(Paragraph::getId).toList());

		for (Discussion discussion : conversation.getDiscussions())
		{
			lines.add("discussion " + discussion.getId() + " " + discussion.getTitle() + " origin " + discussion.getOriginParagraphId()
					+ " last sender " + discussion.getLastSender() + " closed " + discussion.isClosed());
			lines.add("  closing " + discussion.getClosingRequests());
			lines.add("  therapist " + discussion.getTherapistClosingRequests());
			lines.add("  closure " + discussion.getClosure());

			for (Message message : discussion.getMessages())
			{
				lines.add("  message " + message.getId() + " by " + message.getSenderId() + " at " + message.getSentAt() + " sealed "
						+ message.isSealed() + " skipped " + message.getQuestionsLeftUnanswered() + " read " + discussion.getReadAt(message.getId()));

				for (Paragraph paragraph : message.getParagraphs())
				{
					lines.add("    " + paragraph.getType() + " " + paragraph.getId() + " [" + paragraph.getTitle() + " | " + paragraph.getSubtitle()
							+ " | " + paragraph.getBody() + "] refs " + paragraph.getReferences().stream().map(Paragraph::getId).toList()
							+ " cites " + paragraph.getSelfCitations().stream().map(Paragraph::getId).toList() + " sealed " + paragraph.isSealed());
				}
			}
		}

		conversation.getNegotiationHistory().forEach(record -> lines.add("negotiation " + record.negotiationId() + " " + record.purpose() + " "
				+ record.discussionId() + " " + record.archivedAt() + " " + StoredFacts.fingerprint(record.proposals())));

		conversation.getFailedNegotiations().forEach(record -> lines.add("failed " + record.negotiationId() + " " + record.attemptedPurpose() + " "
				+ record.outcome() + " " + record.closedBy() + " " + record.startedAt() + " " + record.closedAt() + " "
				+ StoredFacts.fingerprint(record.proposals())));

		conversation.getConstraintRequestHistory().forEach(record -> lines.add("constraint request " + describe(record)));

		return String.join("\n", lines);
	}

	private static String describe(ConstraintRequestRecord record)
	{
		return record.id() + " by " + record.proposedBy() + " " + record.status() + " approvals " + record.approvals().stream().map(String::valueOf).sorted().toList()
				+ " " + record.proposedAt() + " " + record.closedAt() + " rejected by " + record.rejectedBy()
				+ " assignments " + describe(record.assignments()) + " previous " + record.previousAssignments().map(ConversationFacts::describe);
	}

	// Assignments have no equals: they are compared through what they say.
	private static String describe(List<ConstraintAssignment> assignments)
	{
		return assignments.stream()
				.map(assignment -> assignment.getUserId() + ":" + assignment.getConstraint().getClass().getSimpleName() + ":"
						+ assignment.getConstraint().getDescription() + ":" + assignment.getConstraint().getScope())
				.collect(Collectors.joining(", ", "[", "]"));
	}
}
