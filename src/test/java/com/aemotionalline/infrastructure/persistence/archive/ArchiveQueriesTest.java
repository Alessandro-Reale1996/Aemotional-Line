package com.aemotionalline.infrastructure.persistence.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.domain.MutableClock;
import com.aemotionalline.domain.constraint.ConstraintContext;
import com.aemotionalline.domain.constraint.GeoPosition;
import com.aemotionalline.domain.conversation.Conversation;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.conversation.ConversationRepository;
import com.aemotionalline.domain.conversation.FullConversation;
import com.aemotionalline.domain.conversation.NegotiationRecord;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.message.ParagraphType;
import com.aemotionalline.domain.message.SimpleParagraph;
import com.aemotionalline.domain.negotiation.AgreementText;
import com.aemotionalline.domain.negotiation.DiscussionTitle;
import com.aemotionalline.domain.user.UserId;

/** The archive's searches by time and keyword on a saved conversation holding every kind of state (FullConversation). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class ArchiveQueriesTest
{
	@Autowired
	private ArchiveQueries archive;

	@Autowired
	private ConversationRepository conversations;

	@Autowired
	private CoupleRepository couples;

	private FullConversation full;
	private ConversationId id;

	private FullConversation savedFullConversation()
	{
		Couple couple = new Couple(CoupleId.random(), UserId.random(), UserId.random(), UserId.random());
		couples.save(couple);
		FullConversation conversation = new FullConversation(couple, ConversationId.random(), new MutableClock(Instant.parse("2026-10-30T10:00:00Z")));
		conversations.save(conversation.conversation);

		return conversation;
	}

	@BeforeEach
	void saveAFullConversation()
	{
		full = savedFullConversation();
		id = full.conversation.getId();
	}

	private List<Message> mainMessages()
	{
		return full.conversation.findDiscussion(full.main).getMessages();
	}

	private static Set<ParagraphId> ids(List<ArchiveParagraph> paragraphs)
	{
		return paragraphs.stream().map(ArchiveParagraph::paragraphId).collect(Collectors.toSet());
	}

	// BY TIME:

	@Test
	void shouldGiveTheParagraphsOfTheMessagesSentFromTheStartIncludedToTheEndExcluded()
	{
		Message second = mainMessages().get(1);
		Message third = mainMessages().get(2);

		List<ArchiveParagraph> found = archive.messagesBetween(id, second.getSentAt(), third.getSentAt());

		assertEquals(second.getParagraphs().stream().map(Paragraph::getId).toList(), found.stream().map(ArchiveParagraph::paragraphId).toList());
		ArchiveParagraph pointed = found.getFirst();
		assertEquals(ParagraphType.POINTED, pointed.type());
		assertEquals(full.main, pointed.discussionId());
		assertEquals(second.getId(), pointed.messageId());
		assertEquals(full.b, pointed.sender());
		assertEquals(second.getSentAt(), pointed.sentAt());
	}

	@Test
	void shouldGiveNothingForAnEmptyIntervalAndRejectAReversedOne()
	{
		Instant sent = mainMessages().getFirst().getSentAt();

		assertTrue(archive.messagesBetween(id, sent.plusNanos(1_000), sent.plusNanos(1_000)).isEmpty());
		assertThrows(IllegalArgumentException.class, () -> archive.messagesBetween(id, sent, sent.minusSeconds(1)));
	}

	// BY KEYWORD:

	@Test
	void shouldFindTextIgnoringCaseAndAccents()
	{
		Set<ParagraphId> simple = full.conversation.getConversationGraph().getParagraphs().stream()
				.filter(paragraph -> paragraph.getType() == ParagraphType.SIMPLE)
				.map(Paragraph::getId)
				.collect(Collectors.toSet());

		// The simple paragraphs' bodies are "Body àèìòù".
		assertEquals(simple, ids(archive.search(id, "AEIOU").paragraphs()));
		assertEquals(Set.of(full.q.getId()), ids(archive.search(id, "tell ME").paragraphs()));
	}

	@Test
	void shouldStayWithinTheConversation()
	{
		FullConversation other = savedFullConversation();

		Set<MessageId> ours = full.conversation.getDiscussions().stream()
				.flatMap(discussion -> discussion.getMessages().stream())
				.map(Message::getId)
				.collect(Collectors.toSet());

		List<ArchiveParagraph> found = archive.search(id, "body").paragraphs();

		assertTrue(found.stream().allMatch(paragraph -> ours.contains(paragraph.messageId())));
		assertEquals(ids(archive.search(other.conversation.getId(), "body").paragraphs()).size(), found.size());
	}

	@Test
	void shouldMatchPercentAndUnderscoreLiterally()
	{
		Conversation loaded = conversations.findById(id).orElseThrow();
		loaded.readMessage(full.b, full.main, full.lastMainMessage, FullConversation.NOON);
		Message message = new Message(MessageId.random(), full.b);
		SimpleParagraph percent = new SimpleParagraph(ParagraphId.random(), "Half", "Of the time", "We agree 50% of the time.");
		percent.addReference(full.t);
		message.addParagraph(percent);
		loaded.sendMessage(message, full.main, new ConstraintContext(LocalTime.NOON, new GeoPosition(45.48, 9.19)));
		conversations.save(loaded);

		assertEquals(Set.of(percent.getId()), ids(archive.search(id, "50%").paragraphs()));
		assertTrue(archive.search(id, "%").paragraphs().stream().allMatch(paragraph -> paragraph.paragraphId().equals(percent.getId())));
		assertTrue(archive.search(id, "_").isEmpty());
	}

	@Test
	void shouldFindTheProposalsThatTookEffectAndThoseThatFailed()
	{
		List<ArchiveProposal> agreement = archive.search(id, "b's agreement").proposals();
		assertEquals(1, agreement.size());
		assertEquals(ArchiveProposal.Section.TOOK_EFFECT, agreement.getFirst().section());
		assertEquals(NegotiationRecord.Purpose.MODIFIED_AGREEMENT, agreement.getFirst().purpose());
		assertEquals(new AgreementText("B'S AGREEMENT"), agreement.getFirst().content());

		// The failed attempt to start the conversation, and an abandoned discussion request (only its request was kept).
		ArchiveProposal tooEarly = archive.search(id, "too early").proposals().getFirst();
		assertEquals(ArchiveProposal.Section.FAILED, tooEarly.section());
		assertEquals(NegotiationRecord.Purpose.STARTED_CONVERSATION, tooEarly.purpose());

		ArchiveProposal never = archive.search(id, "never").proposals().getFirst();
		assertEquals(ArchiveProposal.Section.FAILED, never.section());
		assertEquals(new DiscussionTitle("NEVER", "MIND"), never.content());
	}

	@Test
	void shouldFindNothingAndRejectABlankText()
	{
		assertTrue(archive.search(id, "zzzz").isEmpty());
		assertThrows(IllegalArgumentException.class, () -> archive.search(id, "  "));
		assertThrows(IllegalArgumentException.class, () -> archive.search(id, null));
	}
}
