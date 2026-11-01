package com.aemotionalline.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.application.view.ConversationView;
import com.aemotionalline.application.view.DiscussionView;
import com.aemotionalline.application.view.MessageView;
import com.aemotionalline.domain.MutableClock;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.conversation.ConversationRepository;
import com.aemotionalline.domain.conversation.FullConversation;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.ParagraphType;
import com.aemotionalline.domain.user.UserId;

/**
 * The conversation view hides the content of the other partner's messages the viewer hasn't opened (closed envelopes),
 * shows everything to the therapist, and tells an outsider the conversation doesn't exist.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class ConversationViewTest
{
	@Autowired
	private ConversationService service;

	@Autowired
	private ConversationRepository conversations;

	@Autowired
	private CoupleRepository couples;

	private FullConversation full;

	@BeforeEach
	void saveAFullConversation()
	{
		Couple couple = new Couple(CoupleId.random(), UserId.random(), UserId.random(), UserId.random());
		couples.save(couple);
		full = new FullConversation(couple, ConversationId.random(), new MutableClock(Instant.parse("2026-11-01T10:00:00Z")));
		conversations.save(full.conversation);
	}

	private static DiscussionView discussion(ConversationView view, int number)
	{
		return view.discussions().get(number);
	}

	private static List<Boolean> envelopes(DiscussionView discussion)
	{
		return discussion.messages().stream().map(MessageView::closedEnvelope).toList();
	}

	@Test
	void shouldShowBTheMessagesBOpenedAndHideTheOnesBDidNot()
	{
		ConversationView view = service.view(full.b, full.conversation.getId());

		// Main: A's first message (opened by B), B's own reply, A's last message (not opened by B).
		assertEquals(List.of(false, false, true), envelopes(discussion(view, 0)));
		MessageView unopened = discussion(view, 0).messages().get(2);
		assertEquals(full.lastMainMessage, unopened.id());
		assertEquals(full.a, unopened.sender());
		assertTrue(unopened.paragraphs().isEmpty());
		assertTrue(unopened.questionsLeftUnanswered().isEmpty());
		assertTrue(unopened.readAt().isEmpty());

		// The pending branch has only A's message, which B never opened.
		assertEquals(List.of(true), envelopes(discussion(view, 2)));
	}

	@Test
	void shouldShowAEveryMessageAOpenedOrWrote()
	{
		ConversationView view = service.view(full.a, full.conversation.getId());

		view.discussions().forEach(discussion -> assertTrue(envelopes(discussion).stream().noneMatch(closed -> closed)));
		assertEquals("B'S AGREEMENT", view.agreement());
	}

	@Test
	void shouldShowTheTherapistEverything()
	{
		ConversationView view = service.view(full.therapist, full.conversation.getId());

		view.discussions().forEach(discussion -> assertTrue(envelopes(discussion).stream().noneMatch(closed -> closed)));

		// The pointed answer's computed title is in the view.
		var pointed = discussion(view, 0).messages().get(1).paragraphs().getFirst();
		assertEquals(ParagraphType.POINTED, pointed.type());
		assertEquals("In risposta a «Why?»", pointed.title());
		assertEquals(List.of(full.q.getId()), pointed.references());
	}

	@Test
	void shouldDescribeTheDiscussions()
	{
		ConversationView view = service.view(full.a, full.conversation.getId());

		assertTrue(discussion(view, 0).title().isEmpty());
		assertEquals("TITLE", discussion(view, 1).title().orElseThrow());
		assertEquals(full.s.getId(), discussion(view, 1).originParagraphId().orElseThrow());
		assertTrue(discussion(view, 1).closed());
		assertFalse(discussion(view, 2).closed());

		Message first = full.conversation.findDiscussion(full.main).getMessages().getFirst();
		assertEquals(first.getSentAt(), discussion(view, 0).messages().getFirst().sentAt());
	}

	@Test
	void shouldTellAnOutsiderOrAnUnknownIdThatTheConversationDoesNotExist()
	{
		assertThrows(NotFoundException.class, () -> service.view(UserId.random(), full.conversation.getId()));
		assertThrows(NotFoundException.class, () -> service.view(full.a, ConversationId.random()));
	}
}
