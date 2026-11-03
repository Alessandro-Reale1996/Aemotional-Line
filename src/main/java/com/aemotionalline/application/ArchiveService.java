package com.aemotionalline.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.aemotionalline.application.view.ArchiveEntry;
import com.aemotionalline.application.view.ArchiveSearchResult;
import com.aemotionalline.application.view.TopicHistoryView;
import com.aemotionalline.application.view.UnansweredQuestionsView;
import com.aemotionalline.application.view.Views;
import com.aemotionalline.domain.conversation.Analyzer;
import com.aemotionalline.domain.conversation.Conversation;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.conversation.ConversationRepository;
import com.aemotionalline.domain.conversation.Discussion;
import com.aemotionalline.domain.conversation.TopicHistory;
import com.aemotionalline.domain.conversation.TopicHistoryEntry;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.message.QuestionParagraph;
import com.aemotionalline.domain.user.UserId;

/**
 * The archive (spec 1.3.4), consultable by every member of the couple at any time: history by topic, browsing by time,
 * keyword search and unanswered questions. Everything applies the closed-envelope rule: a partner never sees the content
 * of the other partner's messages they haven't opened, and a keyword search doesn't even reveal that they match.
 */
@Service
@ReadOnlyTransaction
public class ArchiveService
{
	private final ConversationRepository conversations;
	private final CoupleRepository couples;
	private final ArchiveSearch search;

	ArchiveService(ConversationRepository conversations, CoupleRepository couples, ArchiveSearch search)
	{
		this.conversations = conversations;
		this.couples = couples;
		this.search = search;
	}

	/** The history of the topic of a paragraph the viewer can see; unopened paragraphs in it are closed envelopes. */
	public TopicHistoryView topicHistory(UserId viewer, ConversationId id, ParagraphId paragraphId)
	{
		Conversation conversation = ConversationAccess.readable(conversations, viewer, id);
		Paragraphs.visibleTo(viewer, conversation, paragraphId);

		TopicHistory history = TopicHistory.of(conversation, paragraphId);
		List<ArchiveEntry> entries = new ArrayList<>();

		for (TopicHistoryEntry entry : history.entries())
		{
			Discussion discussion = conversation.findDiscussion(entry.discussionId());
			Message message = discussion.getMessages().stream().filter(sent -> sent.getId().equals(entry.messageId())).findFirst().orElseThrow();

			if (Views.canSeeContent(viewer, conversation.getCouple(), discussion, message))
			{
				entries.add(entry(entry.paragraph(), discussion, message));
			}
			// One envelope per unopened message, even when several of its paragraphs are in the topic: their number stays hidden.
			else if (entries.isEmpty() || !entries.getLast().closedEnvelope() || !entries.getLast().messageId().equals(message.getId()))
			{
				entries.add(ArchiveEntry.envelope(discussion.getId(), message.getId(), message.getSenderId(), message.getSentAt()));
			}
		}

		return new TopicHistoryView(paragraphId, history.rule(), entries);
	}

	/** The messages sent in {@code [from, to)}: visible ones paragraph by paragraph, unopened ones as closed envelopes. */
	public List<ArchiveEntry> messagesBetween(UserId viewer, ConversationId id, Instant from, Instant to)
	{
		boolean therapist = membership(viewer, id).isTherapist(viewer);

		return translated(() -> search.messagesBetween(id, viewer, therapist, from, to));
	}

	/** Paragraphs and proposals containing the text; the other partner's unopened messages are left out entirely. */
	public ArchiveSearchResult search(UserId viewer, ConversationId id, String text)
	{
		boolean therapist = membership(viewer, id).isTherapist(viewer);

		return translated(() -> search.search(id, viewer, therapist, text));
	}

	/** The questions the viewer can see that have no answer, still open or skipped for good. */
	public UnansweredQuestionsView unansweredQuestions(UserId viewer, ConversationId id)
	{
		Conversation conversation = ConversationAccess.readable(conversations, viewer, id);

		return new UnansweredQuestionsView(visibleEntries(viewer, conversation, Analyzer.conversationNotAnsweredQuestions(conversation)),
				visibleEntries(viewer, conversation, Analyzer.questionsLeftUnanswered(conversation)));
	}

	private List<ArchiveEntry> visibleEntries(UserId viewer, Conversation conversation, List<QuestionParagraph> questions)
	{
		return questions.stream()
				.map(question -> Paragraphs.locate(conversation, question.getId()).orElseThrow())
				.filter(located -> Views.canSeeContent(viewer, conversation.getCouple(), located.discussion(), located.message()))
				.map(located -> entry(located.paragraph(), located.discussion(), located.message()))
				.toList();
	}

	private static ArchiveEntry entry(Paragraph paragraph, Discussion discussion, Message message)
	{
		return new ArchiveEntry(Optional.of(paragraph.getId()), paragraph.getType(), paragraph.getTitle(), paragraph.getSubtitle(), paragraph.getBody(),
				discussion.getId(), message.getId(), message.getSenderId(), message.getSentAt(), false);
	}

	// Checks membership without loading the conversation.
	private Couple membership(UserId viewer, ConversationId id)
	{
		Couple couple = conversations.findCoupleIdOf(id).flatMap(couples::findById)
				.orElseThrow(() -> new NotFoundException("Conversation not found."));
		Access.ensureMember(couple, viewer, "Conversation");

		return couple;
	}

	private static <T> T translated(Supplier<T> query)
	{
		try
		{
			return query.get();
		}
		catch (IllegalArgumentException malformed)
		{
			throw new InvalidRequestException(malformed.getMessage(), malformed);
		}
	}
}
