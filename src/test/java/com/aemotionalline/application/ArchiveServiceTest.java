package com.aemotionalline.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.application.view.ArchiveEntry;
import com.aemotionalline.application.view.TopicHistoryView;
import com.aemotionalline.domain.MutableClock;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.conversation.ConversationRepository;
import com.aemotionalline.domain.conversation.FullConversation;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.user.UserId;

/**
 * The archive for a partner applies the closed-envelope rule: in FullConversation, B hasn't opened A's last message of the
 * main discussion, A's message in the closed branch, nor A's message in the pending branch.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class ArchiveServiceTest
{
	private static final Instant EVER = Instant.parse("2000-01-01T00:00:00Z");
	private static final Instant NEVER = Instant.parse("2100-01-01T00:00:00Z");

	@Autowired
	private ArchiveService archive;

	@Autowired
	private ConversationRepository conversations;

	@Autowired
	private CoupleRepository couples;

	private FullConversation full;
	private ConversationId id;

	@BeforeEach
	void saveAFullConversation()
	{
		Couple couple = new Couple(CoupleId.random(), UserId.random(), UserId.random(), UserId.random());
		couples.save(couple);
		full = new FullConversation(couple, ConversationId.random(), new MutableClock(Instant.parse("2026-11-02T10:00:00Z")));
		conversations.save(full.conversation);
		id = full.conversation.getId();
	}

	// The messages B hasn't opened: A's, without a recorded read.
	private Set<MessageId> unopenedByB()
	{
		return full.conversation.getDiscussions().stream()
				.flatMap(discussion -> discussion.getMessages().stream()
						.filter(message -> message.getSenderId().equals(full.a) && discussion.getReadAt(message.getId()).isEmpty()))
				.map(Message::getId)
				.collect(Collectors.toSet());
	}

	private Set<ParagraphId> paragraphsOf(Set<MessageId> messages)
	{
		return full.conversation.getDiscussions().stream()
				.flatMap(discussion -> discussion.getMessages().stream())
				.filter(message -> messages.contains(message.getId()))
				.flatMap(message -> message.getParagraphs().stream())
				.map(Paragraph::getId)
				.collect(Collectors.toSet());
	}

	private static Set<ParagraphId> ids(List<ArchiveEntry> entries)
	{
		return entries.stream().filter(entry -> !entry.closedEnvelope()).map(entry -> entry.paragraphId().orElseThrow()).collect(Collectors.toSet());
	}

	@Test
	void shouldLeaveTheUnopenedMessagesOutOfAKeywordSearch()
	{
		Set<ParagraphId> hidden = paragraphsOf(unopenedByB());
		assertFalse(hidden.isEmpty());

		Set<ParagraphId> forTheTherapist = ids(archive.search(full.therapist, id, "aeiou").paragraphs());
		Set<ParagraphId> forB = ids(archive.search(full.b, id, "aeiou").paragraphs());

		assertTrue(forTheTherapist.stream().anyMatch(hidden::contains));
		assertTrue(forB.stream().noneMatch(hidden::contains));
		assertEquals(forTheTherapist.stream().filter(paragraph -> !hidden.contains(paragraph)).collect(Collectors.toSet()), forB);
		// Proposals are always searchable.
		assertEquals(1, archive.search(full.b, id, "b's agreement").proposals().size());
	}

	@Test
	void shouldShowEachUnopenedMessageAsOneClosedEnvelopeWhenBrowsingByTime()
	{
		List<ArchiveEntry> forB = archive.messagesBetween(full.b, id, EVER, NEVER);

		List<ArchiveEntry> envelopes = forB.stream().filter(ArchiveEntry::closedEnvelope).toList();
		assertEquals(unopenedByB(), envelopes.stream().map(ArchiveEntry::messageId).collect(Collectors.toSet()));
		assertEquals(unopenedByB().size(), envelopes.size());
		assertTrue(envelopes.stream().allMatch(envelope -> envelope.paragraphId().isEmpty() && envelope.body() == null && envelope.type() == null));
		assertTrue(ids(forB).stream().noneMatch(paragraphsOf(unopenedByB())::contains));

		assertTrue(archive.messagesBetween(full.therapist, id, EVER, NEVER).stream().noneMatch(ArchiveEntry::closedEnvelope));
	}

	@Test
	void shouldShowTheTopicHistoryWithClosedEnvelopesInPlace()
	{
		// P's topic runs through A's last main message (s, t) and, through s, the closed branch with A's unopened message.
		TopicHistoryView forTheTherapist = archive.topicHistory(full.therapist, id, full.p.getId());
		TopicHistoryView forB = archive.topicHistory(full.b, id, full.p.getId());

		assertTrue(forTheTherapist.entries().stream().noneMatch(ArchiveEntry::closedEnvelope));
		assertEquals(2, forB.entries().stream().filter(ArchiveEntry::closedEnvelope).count());
		assertTrue(forB.entries().stream().filter(ArchiveEntry::closedEnvelope).allMatch(envelope -> unopenedByB().contains(envelope.messageId())));
		assertTrue(ids(forB.entries()).stream().noneMatch(paragraphsOf(unopenedByB())::contains));
		// One envelope per unopened message, never one per paragraph.
		List<MessageId> envelopes = forB.entries().stream().filter(ArchiveEntry::closedEnvelope).map(ArchiveEntry::messageId).toList();
		assertEquals(Set.copyOf(envelopes).size(), envelopes.size());
		assertEquals(forTheTherapist.rule(), forB.rule());
	}

	@Test
	void shouldNotStartATopicHistoryFromAParagraphTheViewerHasNotOpened()
	{
		assertThrows(InvalidRequestException.class, () -> archive.topicHistory(full.b, id, full.s.getId()));
		assertEquals(full.s.getId(), archive.topicHistory(full.a, id, full.s.getId()).paragraphId());
	}

	@Test
	void shouldListTheUnansweredQuestionsTheViewerCanSee()
	{
		var forB = archive.unansweredQuestions(full.b, id);
		var forTheTherapist = archive.unansweredQuestions(full.therapist, id);

		assertEquals(ids(forTheTherapist.leftUnanswered()), ids(forB.leftUnanswered()));
		assertEquals(1, forB.leftUnanswered().size());
		assertEquals("And then?", forB.leftUnanswered().getFirst().title());
		assertTrue(forB.stillOpen().stream().noneMatch(ArchiveEntry::closedEnvelope));
	}

	@Test
	void shouldTellOutsidersNothingExistsAndRejectMalformedSearches()
	{
		UserId stranger = UserId.random();

		assertThrows(NotFoundException.class, () -> archive.search(stranger, id, "body"));
		assertThrows(NotFoundException.class, () -> archive.messagesBetween(stranger, id, EVER, NEVER));
		assertThrows(NotFoundException.class, () -> archive.topicHistory(stranger, id, full.q.getId()));
		assertThrows(NotFoundException.class, () -> archive.unansweredQuestions(stranger, id));
		assertThrows(NotFoundException.class, () -> archive.search(full.a, ConversationId.random(), "body"));

		assertThrows(InvalidRequestException.class, () -> archive.search(full.a, id, "  "));
		assertThrows(InvalidRequestException.class, () -> archive.messagesBetween(full.a, id, NEVER, EVER));
	}
}
