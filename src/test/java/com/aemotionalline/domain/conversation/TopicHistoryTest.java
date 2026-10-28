package com.aemotionalline.domain.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.Ids;
import com.aemotionalline.domain.MutableClock;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.constraint.ConstraintContext;
import com.aemotionalline.domain.conversation.TopicHistory.Rule;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.message.SimpleParagraph;
import com.aemotionalline.domain.negotiation.AgreementProposal;
import com.aemotionalline.domain.negotiation.DiscussionProposal;
import com.aemotionalline.domain.negotiation.Negotiation;
import com.aemotionalline.domain.negotiation.Proposal;
import com.aemotionalline.domain.user.UserId;

/**
 * The archive's history by topic (spec 1.3.4). The conversation built for every test:
 * <pre>
 * main (0):   A: [Q]            B: [R1->Q, R2->Q]        A: [S1->R1, S2->R2, N]
 * second (1), opened from R1:   B: [X1->R1, X2->R1]      A: [Y1->X1, Z]          B: [W->Y1]
 * third (2), opened from Z:     A: [T1->Z]
 * fourth (3), no origin:        A: [F1]
 * </pre>
 * N and Z start new topics (no references).
 */
public class TopicHistoryTest
{
	private static final Couple COUPLE = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

	private static final UserId A = COUPLE.getPartnerOneId();
	private static final UserId B = COUPLE.getPartnerTwoId();

	private static final DiscussionId MAIN = new DiscussionId(Ids.conversation(321L), 0);
	private static final DiscussionId SECOND = new DiscussionId(Ids.conversation(321L), 1);
	private static final DiscussionId THIRD = new DiscussionId(Ids.conversation(321L), 2);
	private static final DiscussionId FOURTH = new DiscussionId(Ids.conversation(321L), 3);

	private static final Instant T0 = Instant.parse("2026-10-28T10:00:00Z");
	private static final ConstraintContext NOON = new ConstraintContext(LocalTime.NOON);

	private final MutableClock clock = new MutableClock(T0);

	private Conversation conversation;
	private Paragraph q, r1, r2, s1, s2, n, x1, x2, y1, z, w, t1, f1;
	private Message secondFirstMessage;

	private static Negotiation settled(Proposal initial, long id)
	{
		Negotiation negotiation = Negotiation.start(Ids.negotiation(id), COUPLE, initial, Clock.systemUTC());
		negotiation.acceptNegotiation(B);
		negotiation.sendProposal(A, "JUSTIFICATION");
		negotiation.acceptProposal(B);

		return negotiation;
	}

	private static Negotiation discussion(long id)
	{
		return settled(new DiscussionProposal(Ids.proposal(id), A, "TITLE " + id, "SUBTITLE " + id), id);
	}

	private static SimpleParagraph paragraph(long id, Paragraph... answered)
	{
		SimpleParagraph paragraph = new SimpleParagraph(Ids.paragraph(id), "Title " + id, "Subtitle", "Body");

		for (Paragraph reference : answered)
		{
			paragraph.addReference(reference);
		}

		return paragraph;
	}

	// Each message is sent one minute after the previous one.
	private Message send(DiscussionId discussion, long messageId, UserId sender, Paragraph... paragraphs)
	{
		Message message = new Message(Ids.message(messageId), sender);

		for (Paragraph paragraph : paragraphs)
		{
			message.addParagraph(paragraph);
		}

		clock.advance(Duration.ofMinutes(1));
		Reading.send(conversation, message, discussion, NOON);

		return message;
	}

	@BeforeEach
	void buildConversation()
	{
		conversation = Conversation.start(Ids.conversation(321L), COUPLE, settled(new AgreementProposal(Ids.proposal(60L), A, "TEXT"), 60L), clock);

		q = paragraph(1L);
		send(MAIN, 1L, A, q);
		r1 = paragraph(2L, q);
		r2 = paragraph(3L, q);
		send(MAIN, 2L, B, r1, r2);
		s1 = paragraph(4L, r1);
		s2 = paragraph(5L, r2);
		n = paragraph(6L);
		send(MAIN, 3L, A, s1, s2, n);

		conversation.openDiscussion(discussion(70L), r1.getId());
		x1 = paragraph(7L, r1);
		x2 = paragraph(8L, r1);
		secondFirstMessage = send(SECOND, 4L, B, x1, x2);
		y1 = paragraph(9L, x1);
		z = paragraph(10L);
		send(SECOND, 5L, A, y1, z);
		w = paragraph(11L, y1);
		send(SECOND, 6L, B, w);

		conversation.openDiscussion(discussion(80L), z.getId());
		t1 = paragraph(12L, z);
		send(THIRD, 7L, A, t1);

		conversation.openDiscussion(discussion(90L));
		f1 = paragraph(13L);
		send(FOURTH, 8L, A, f1);
	}

	private List<Paragraph> paragraphsOf(TopicHistory history)
	{
		return history.entries().stream().map(TopicHistoryEntry::paragraph).toList();
	}

	// MAIN CONVERSATION:

	@Test
	void shouldGiveTheWholeComponentInTheMainConversationAndTheExpandingDiscussionsWhole()
	{
		TopicHistory history = TopicHistory.of(conversation, s2.getId());

		assertEquals(Rule.MAIN_CONVERSATION, history.rule());
		assertEquals(s2, history.paragraph());
		// S2's sibling branch (R1, S1) is in; the second discussion comes with every paragraph of every message, the
		// new sub-topic Z included, and the third discussion, opened from Z, follows in a chain. N and F1 are unrelated.
		assertEquals(List.of(q, r1, r2, s1, s2, x1, x2, y1, z, w, t1), paragraphsOf(history));
	}

	@Test
	void shouldGiveAParagraphWithoutLinksAlone()
	{
		TopicHistory history = TopicHistory.of(conversation, n.getId());

		assertEquals(Rule.MAIN_CONVERSATION, history.rule());
		assertEquals(List.of(n), paragraphsOf(history));
	}

	// SPECIFIC DISCUSSION:

	@Test
	void shouldReachTheMainConversationThroughTheOriginParagraphByParagraph()
	{
		TopicHistory history = TopicHistory.of(conversation, y1.getId());

		assertEquals(Rule.SPECIFIC_DISCUSSION, history.rule());
		// Paragraph by paragraph: Z (a new sub-topic) and what follows it are not linked to Y1.
		assertEquals(List.of(q, r1, r2, s1, s2, x1, x2, y1, w), paragraphsOf(history));
	}

	@Test
	void shouldFollowASubTopicIntoTheDiscussionOpenedFromIt()
	{
		assertEquals(List.of(z, t1), paragraphsOf(TopicHistory.of(conversation, z.getId())));
		assertEquals(Rule.SPECIFIC_DISCUSSION, TopicHistory.of(conversation, t1.getId()).rule());
		assertEquals(List.of(z, t1), paragraphsOf(TopicHistory.of(conversation, t1.getId())));
	}

	@Test
	void shouldKeepADiscussionWithoutOriginToItself()
	{
		TopicHistory history = TopicHistory.of(conversation, f1.getId());

		assertEquals(Rule.SPECIFIC_DISCUSSION, history.rule());
		assertEquals(List.of(f1), paragraphsOf(history));
	}

	// ENTRIES:

	@Test
	void shouldTellWhereWhenAndByWhomEachParagraphWasWritten()
	{
		TopicHistoryEntry entry = TopicHistory.of(conversation, s2.getId()).entries().get(5);

		assertEquals(new TopicHistoryEntry(x1, SECOND, secondFirstMessage.getId(), B, T0.plus(Duration.ofMinutes(4))), entry);
	}

	@Test
	void shouldListEachParagraphOnceInSendingOrder()
	{
		List<TopicHistoryEntry> entries = TopicHistory.of(conversation, q.getId()).entries();

		assertEquals(entries.size(), entries.stream().map(TopicHistoryEntry::paragraph).distinct().count());

		for (int i = 1; i < entries.size(); i++)
		{
			assertEquals(false, entries.get(i).sentAt().isBefore(entries.get(i - 1).sentAt()));
		}
	}

	@Test
	void shouldNotLetTheHistoryBeChangedFromOutside()
	{
		TopicHistory history = TopicHistory.of(conversation, q.getId());

		assertThrows(UnsupportedOperationException.class, () -> history.entries().clear());
	}

	@Test
	void shouldRejectAnUnknownParagraphOrMissingArguments()
	{
		assertThrows(DomainException.class, () -> TopicHistory.of(conversation, Ids.paragraph(99L)));
		assertThrows(DomainException.class, () -> TopicHistory.of(conversation, null));
		assertThrows(DomainException.class, () -> TopicHistory.of(null, q.getId()));
	}
}
