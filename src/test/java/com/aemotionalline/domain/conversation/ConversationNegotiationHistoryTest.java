package com.aemotionalline.domain.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.Ids;
import com.aemotionalline.domain.MutableClock;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.conversation.NegotiationRecord.Purpose;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.SimpleParagraph;
import com.aemotionalline.domain.negotiation.AgreementProposal;
import com.aemotionalline.domain.negotiation.AgreementText;
import com.aemotionalline.domain.negotiation.DiscussionProposal;
import com.aemotionalline.domain.negotiation.Negotiation;
import com.aemotionalline.domain.negotiation.Proposal;
import com.aemotionalline.domain.user.UserId;

/**
 * Every negotiation that takes effect is recorded in order, with what it was used for and when (spec 1.3.1):
 * the archive shows the agreement before and after each change, and the title of each discussion.
 */
public class ConversationNegotiationHistoryTest
{
	private static final Couple COUPLE = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

	private static final UserId A = COUPLE.getPartnerOneId();
	private static final UserId B = COUPLE.getPartnerTwoId();

	private static final DiscussionId FIRST = new DiscussionId(Ids.conversation(321L), 0);
	private static final DiscussionId SECOND = new DiscussionId(Ids.conversation(321L), 1);

	private static final Instant T0 = Instant.parse("2026-10-28T10:00:00Z");

	private final MutableClock clock = new MutableClock(T0);

	private static Negotiation settled(Proposal initial, long id)
	{
		Negotiation negotiation = Negotiation.start(Ids.negotiation(id), COUPLE, initial, Clock.systemUTC());
		negotiation.acceptNegotiation(B);
		negotiation.sendProposal(A, "JUSTIFICATION " + id);
		negotiation.acceptProposal(B);

		return negotiation;
	}

	private static Negotiation agreement(long id)
	{
		return settled(new AgreementProposal(Ids.proposal(id), A, "AGREEMENT " + id), id);
	}

	private static Negotiation discussion(long id)
	{
		return settled(new DiscussionProposal(Ids.proposal(id), A, "TITLE " + id, "SUBTITLE " + id), id);
	}

	@Test
	void shouldRecordTheNegotiationThatStartedTheConversation()
	{
		Negotiation first = agreement(60L);

		Conversation conversation = Conversation.start(Ids.conversation(321L), COUPLE, first, clock);

		assertEquals(List.of(new NegotiationRecord(first, Purpose.STARTED_CONVERSATION, Optional.of(FIRST), T0)),
				conversation.getNegotiationHistory());
	}

	@Test
	void shouldRecordEveryNegotiationInTheOrderItTookEffect()
	{
		Negotiation first = agreement(60L);
		Negotiation change = agreement(70L);
		Negotiation branch = discussion(80L);
		Conversation conversation = Conversation.start(Ids.conversation(321L), COUPLE, first, clock);

		clock.advance(Duration.ofDays(1));
		conversation.modifyAgreement(change);
		clock.advance(Duration.ofDays(1));
		conversation.openDiscussion(branch);

		assertEquals(List.of(
				new NegotiationRecord(first, Purpose.STARTED_CONVERSATION, Optional.of(FIRST), T0),
				new NegotiationRecord(change, Purpose.MODIFIED_AGREEMENT, Optional.empty(), T0.plus(Duration.ofDays(1))),
				new NegotiationRecord(branch, Purpose.OPENED_DISCUSSION, Optional.of(SECOND), T0.plus(Duration.ofDays(2)))),
				conversation.getNegotiationHistory());
	}

	@Test
	void shouldRecordADiscussionOpenedFromAParagraph()
	{
		Conversation conversation = Conversation.start(Ids.conversation(321L), COUPLE, agreement(60L), clock);
		Message message = new Message(Ids.message(1L), A);
		SimpleParagraph origin = new SimpleParagraph(Ids.paragraph(1L), "Title", "Subtitle", "Body");
		message.addParagraph(origin);
		conversation.sendMessage(message, FIRST, null);
		Negotiation branch = discussion(80L);

		conversation.openDiscussion(branch, origin.getId());

		assertEquals(new NegotiationRecord(branch, Purpose.OPENED_DISCUSSION, Optional.of(SECOND), T0),
				conversation.getNegotiationHistory().getLast());
	}

	@Test
	void shouldShowTheAgreementBeforeAndAfterEachChange()
	{
		Conversation conversation = Conversation.start(Ids.conversation(321L), COUPLE, agreement(60L), clock);
		conversation.modifyAgreement(agreement(70L));
		conversation.openDiscussion(discussion(80L));
		conversation.modifyAgreement(agreement(90L));

		List<AgreementText> agreements = conversation.getNegotiationHistory().stream()
				.filter(record -> record.purpose() != Purpose.OPENED_DISCUSSION)
				.map(record -> (AgreementText) record.negotiation().getLastProposal().getContent())
				.toList();

		assertEquals(List.of(new AgreementText("AGREEMENT 60"), new AgreementText("AGREEMENT 70"), new AgreementText("AGREEMENT 90")), agreements);
		// The justification of each step is kept with its proposal.
		assertEquals("JUSTIFICATION 70", conversation.getNegotiationHistory().get(1).negotiation().getLastProposal().getJustification());
	}

	@Test
	void shouldNotRecordARejectedNegotiationNorOneUsedTwice()
	{
		Negotiation first = agreement(60L);
		Conversation conversation = Conversation.start(Ids.conversation(321L), COUPLE, first, clock);

		assertThrows(DomainException.class, () -> conversation.modifyAgreement(first));
		assertThrows(DomainException.class, () -> conversation.openDiscussion(agreement(70L)));

		assertEquals(1, conversation.getNegotiationHistory().size());
	}

	@Test
	void shouldNotLetTheHistoryBeChangedFromOutside()
	{
		Conversation conversation = Conversation.start(Ids.conversation(321L), COUPLE, agreement(60L), clock);

		assertThrows(UnsupportedOperationException.class, () -> conversation.getNegotiationHistory().clear());
		assertEquals(1, conversation.getNegotiationHistory().size());
	}
}
