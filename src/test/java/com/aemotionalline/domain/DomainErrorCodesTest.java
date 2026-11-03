package com.aemotionalline.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.common.ErrorCode;
import com.aemotionalline.domain.constraint.ConstraintContext;
import com.aemotionalline.domain.constraint.GeoPosition;
import com.aemotionalline.domain.conversation.Conversation;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.conversation.DiscussionId;
import com.aemotionalline.domain.conversation.FullConversation;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleInvitation;
import com.aemotionalline.domain.couple.CoupleInvitationId;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.message.Paragraph;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.message.SimpleParagraph;
import com.aemotionalline.domain.negotiation.AgreementProposal;
import com.aemotionalline.domain.negotiation.Negotiation;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.negotiation.ProposalId;
import com.aemotionalline.domain.user.EmailAddress;
import com.aemotionalline.domain.user.Role;
import com.aemotionalline.domain.user.User;
import com.aemotionalline.domain.user.UserId;

/**
 * The stable codes of the rules a user meets through the interface (CLAUDE.md, R1): each refusal says which rule, so
 * the frontend can explain it in Italian; a programming guard says RULE_VIOLATED.
 */
public class DomainErrorCodesTest
{
	private static final Instant T0 = Instant.parse("2026-11-03T10:00:00Z");
	// B may write only away from home (FullConversation's location constraint).
	private static final ConstraintContext AWAY = new ConstraintContext(LocalTime.NOON, new GeoPosition(45.48, 9.19));
	private static final ConstraintContext HOME = new ConstraintContext(LocalTime.NOON, new GeoPosition(45.4642, 9.19));

	@Test
	void shouldSayWhyAMessageIsRefused()
	{
		Couple couple = new Couple(CoupleId.random(), UserId.random(), UserId.random(), UserId.random());
		FullConversation full = new FullConversation(couple, ConversationId.random(), new MutableClock(T0));
		Conversation conversation = full.conversation;

		assertCode(ErrorCode.NOT_YOUR_TURN, () -> conversation.sendMessage(message(full.a, FullConversation.simple(full.t)), full.main, FullConversation.NOON));
		assertCode(ErrorCode.NOT_A_PARTNER, () -> conversation.sendMessage(message(full.therapist, FullConversation.simple()), full.main, null));
		assertCode(ErrorCode.MESSAGE_NOT_OPENED, () -> conversation.sendMessage(message(full.b, FullConversation.simple(full.t)), full.main, AWAY));

		conversation.readMessage(full.b, full.main, full.lastMainMessage, FullConversation.NOON);

		assertCode(ErrorCode.CONSTRAINT_NOT_SATISFIED, () -> conversation.sendMessage(message(full.b, FullConversation.simple(full.t)), full.main, HOME));
		assertCode(ErrorCode.REFERENCE_NOT_ALLOWED, () -> conversation.sendMessage(message(full.b, FullConversation.simple(full.p)), full.main, AWAY));
		assertCode(ErrorCode.PARAGRAPH_EXPANDED_ELSEWHERE, () -> conversation.sendMessage(message(full.b, FullConversation.simple(full.s)), full.main, AWAY));
		assertCode(ErrorCode.MESSAGE_EMPTY, () -> conversation.sendMessage(new Message(MessageId.random(), full.b), full.main, AWAY));
		assertCode(ErrorCode.DISCUSSION_CLOSED, () -> conversation.sendMessage(message(full.b, FullConversation.simple()), full.closedBranch, AWAY));
		assertCode(ErrorCode.NOT_FOUND, () -> conversation.findDiscussion(new DiscussionId(conversation.getId(), 99)));

		couple.end(full.therapist, T0.plus(Duration.ofDays(60)));

		assertCode(ErrorCode.COUPLE_ENDED, () -> conversation.sendMessage(message(full.b, FullConversation.simple(full.t)), full.main, AWAY));
	}

	@Test
	void shouldSayWhyANegotiationStepIsRefused()
	{
		Couple couple = new Couple(CoupleId.random(), UserId.random(), UserId.random(), UserId.random());
		UserId a = couple.getPartnerOneId();
		UserId b = couple.getPartnerTwoId();
		Negotiation negotiation = Negotiation.start(NegotiationId.random(), couple, new AgreementProposal(ProposalId.random(), a, "TEXT"), new MutableClock(T0));

		assertCode(ErrorCode.NOT_YOUR_TURN, () -> negotiation.acceptNegotiation(a));
		assertCode(ErrorCode.NOT_A_PARTNER, () -> negotiation.acceptNegotiation(couple.getTherapistId()));
		negotiation.acceptNegotiation(b);
		assertCode(ErrorCode.NEGOTIATION_WRONG_PHASE, () -> negotiation.acceptProposal(a));
		negotiation.abandon(b);
		assertCode(ErrorCode.NEGOTIATION_ENDED, () -> negotiation.sendProposal(a, "Ready"));

		couple.end(couple.getTherapistId(), T0);
		assertCode(ErrorCode.COUPLE_ENDED,
				() -> Negotiation.start(NegotiationId.random(), couple, new AgreementProposal(ProposalId.random(), a, "TEXT"), new MutableClock(T0)));
	}

	@Test
	void shouldSayWhichTextOrValueIsWrong()
	{
		EmailAddress email = new EmailAddress("someone@example.org");

		assertCode(ErrorCode.TEXT_TOO_LONG, () -> new SimpleParagraph(ParagraphId.random(), "Title", null, "x".repeat(801)));
		assertCode(ErrorCode.TEXT_REQUIRED, () -> new SimpleParagraph(ParagraphId.random(), " ", null, "Body"));
		assertCode(ErrorCode.TEXT_REQUIRED, () -> User.pending(UserId.random(), email, " ", Role.PATIENT));
		assertCode(ErrorCode.TEXT_TOO_LONG, () -> User.pending(UserId.random(), email, "x".repeat(101), Role.PATIENT));
		assertCode(ErrorCode.INVALID_EMAIL, () -> new EmailAddress("not an email"));
	}

	@Test
	void shouldSayWhyAnInvitationCantBeAnswered()
	{
		UserId a = UserId.random();
		CoupleInvitation invitation = CoupleInvitation.create(CoupleInvitationId.random(), UserId.random(), a, UserId.random(), T0,
				T0.plus(Duration.ofDays(7)));

		assertCode(ErrorCode.INVITATION_EXPIRED, () -> invitation.accept(a, T0.plus(Duration.ofDays(8))));
		invitation.accept(a, T0);
		assertCode(ErrorCode.INVITATION_ALREADY_ACCEPTED, () -> invitation.accept(a, T0));
	}

	@Test
	void shouldCallAProgrammingGuardARuleViolation()
	{
		assertCode(ErrorCode.RULE_VIOLATED, () -> new Couple(null, UserId.random(), UserId.random(), UserId.random()));
		assertEquals(ErrorCode.RULE_VIOLATED, new DomainException("A guard.").getCode());
	}

	private static Message message(UserId sender, Paragraph... paragraphs)
	{
		Message message = new Message(MessageId.random(), sender);

		for (Paragraph paragraph : paragraphs)
		{
			message.addParagraph(paragraph);
		}

		return message;
	}

	private static void assertCode(ErrorCode code, Executable action)
	{
		assertEquals(code, assertThrows(DomainException.class, action).getCode());
	}
}
