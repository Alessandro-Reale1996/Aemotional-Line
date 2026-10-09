package com.aemotionalline.domain.conversation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.negotiation.AgreementProposal;
import com.aemotionalline.domain.negotiation.DiscussionProposal;
import com.aemotionalline.domain.negotiation.DiscussionTitle;
import com.aemotionalline.domain.negotiation.Negotiation;
import com.aemotionalline.domain.negotiation.Proposal;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.Ids;

/**
 * How a conversation takes in the negotiations that open a discussion or change the agreement:
 * only a settled negotiation counts, each one only once, and a rejected one leaves the conversation untouched.
 */
public class ConversationAgreementTest
{
	private static final Couple COUPLE = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

	private static final UserId A = COUPLE.getPartnerOneId();
	private static final UserId B = COUPLE.getPartnerTwoId();

	private static Negotiation draftNegotiation(long id)
	{
		Proposal proposal = new AgreementProposal(Ids.proposal(id), A, "TEXT " + id);

		return Negotiation.start(Ids.negotiation(id), COUPLE, proposal, Clock.systemUTC());
	}

	// The request to talk is accepted and a proposal was sent, but the partner has not answered it yet.
	private static Negotiation negotiationWithPendingProposal(long id)
	{
		Negotiation negotiation = draftNegotiation(id);

		negotiation.acceptNegotiation(B);
		negotiation.sendProposal(A, "JUSTIFICATION");

		return negotiation;
	}

	private static Negotiation settledNegotiation(long id)
	{
		Negotiation negotiation = negotiationWithPendingProposal(id);

		negotiation.acceptProposal(B);

		return negotiation;
	}

	private static Negotiation draftDiscussionNegotiation(long id)
	{
		Proposal proposal = new DiscussionProposal(Ids.proposal(id), A, "TITLE " + id, "SUBTITLE " + id);

		return Negotiation.start(Ids.negotiation(id), COUPLE, proposal, Clock.systemUTC());
	}

	private static Negotiation discussionWithPendingProposal(long id)
	{
		Negotiation negotiation = draftDiscussionNegotiation(id);

		negotiation.acceptNegotiation(B);
		negotiation.sendProposal(A, "JUSTIFICATION");

		return negotiation;
	}

	private static Negotiation settledDiscussionNegotiation(long id)
	{
		Negotiation negotiation = discussionWithPendingProposal(id);

		negotiation.acceptProposal(B);

		return negotiation;
	}

	private static Conversation startConversation()
	{
		return Conversation.start(Ids.conversation(321L), COUPLE, settledNegotiation(60L));
	}

	// MODIFY AGREEMENT:

	@Test
	void shouldReplaceTheAgreementWithTheLastProposalOfTheNewNegotiation()
	{
		Conversation conversation = startConversation();
		Negotiation negotiation = settledNegotiation(70L);

		conversation.modifyAgreement(negotiation);

		assertSame(negotiation.getLastProposal(), conversation.getAgreement());
	}

	@Test
	void shouldNotModifyTheAgreementWithAnUnacceptedNegotiation()
	{
		Conversation conversation = startConversation();
		Proposal before = conversation.getAgreement();

		assertThrows(DomainException.class, () -> conversation.modifyAgreement(draftNegotiation(70L)));
		assertSame(before, conversation.getAgreement());
	}

	@Test
	void shouldNotModifyTheAgreementWhenTheProposalIsNotAccepted()
	{
		Conversation conversation = startConversation();
		Proposal before = conversation.getAgreement();

		assertThrows(DomainException.class, () -> conversation.modifyAgreement(negotiationWithPendingProposal(70L)));
		assertSame(before, conversation.getAgreement());
	}

	@Test
	void shouldNotModifyTheAgreementTwiceWithTheSameNegotiation()
	{
		Conversation conversation = startConversation();
		Negotiation negotiation = settledNegotiation(70L);
		conversation.modifyAgreement(negotiation);
		Proposal afterFirst = conversation.getAgreement();

		assertThrows(DomainException.class, () -> conversation.modifyAgreement(negotiation));
		assertSame(afterFirst, conversation.getAgreement());
	}

	@Test
	void shouldNotModifyTheAgreementWithTheNegotiationThatStartedTheConversation()
	{
		Negotiation first = settledNegotiation(60L);
		Conversation conversation = Conversation.start(Ids.conversation(321L), COUPLE, first);
		conversation.modifyAgreement(settledNegotiation(70L));
		Proposal current = conversation.getAgreement();

		assertThrows(DomainException.class, () -> conversation.modifyAgreement(first));
		assertSame(current, conversation.getAgreement());
	}

	// OPEN DISCUSSION:

	@Test
	void shouldNotOpenADiscussionWithAnUnacceptedNegotiation()
	{
		Conversation conversation = startConversation();

		assertThrows(DomainException.class, () -> conversation.openDiscussion(draftDiscussionNegotiation(70L)));
		assertEquals(1, conversation.getDiscussions().size());
	}

	@Test
	void shouldNotOpenADiscussionWhenTheProposalIsNotAccepted()
	{
		Conversation conversation = startConversation();

		assertThrows(DomainException.class, () -> conversation.openDiscussion(discussionWithPendingProposal(70L)));
		assertEquals(1, conversation.getDiscussions().size());
	}

	@Test
	void shouldNotOpenADiscussionWithAnAcceptedNegotiationThatHasNoProposal()
	{
		Conversation conversation = startConversation();
		Negotiation negotiation = draftDiscussionNegotiation(70L);
		negotiation.acceptNegotiation(B);

		assertThrows(DomainException.class, () -> conversation.openDiscussion(negotiation));
		assertEquals(1, conversation.getDiscussions().size());
	}

	@Test
	void shouldLeaveTheConversationUsableWhenANegotiationIsReused()
	{
		Conversation conversation = startConversation();
		Negotiation negotiation = settledDiscussionNegotiation(70L);
		conversation.openDiscussion(negotiation);

		// A rejected reuse must not leave a half-opened discussion behind, or the next discussion could never be opened.
		assertThrows(DomainException.class, () -> conversation.openDiscussion(negotiation));
		assertEquals(2, conversation.getDiscussions().size());

		conversation.openDiscussion(settledDiscussionNegotiation(80L));

		assertEquals(3, conversation.getDiscussions().size());
	}

	@Test
	void shouldNotOpenADiscussionWithTheNegotiationThatStartedTheConversation()
	{
		Negotiation first = settledNegotiation(60L);
		Conversation conversation = Conversation.start(Ids.conversation(321L), COUPLE, first);

		assertThrows(DomainException.class, () -> conversation.openDiscussion(first));
		assertEquals(1, conversation.getDiscussions().size());
	}

	// DISCUSSION NUMBERS:

	@Test
	void shouldNumberDiscussionsWithoutGapsEvenWhenTheAgreementChangesInBetween()
	{
		Conversation conversation = startConversation();

		conversation.modifyAgreement(settledNegotiation(70L));
		conversation.openDiscussion(settledDiscussionNegotiation(80L));
		conversation.modifyAgreement(settledNegotiation(90L));
		conversation.openDiscussion(settledDiscussionNegotiation(100L));

		assertEquals(3, conversation.getDiscussions().size());
		assertEquals(new DiscussionId(Ids.conversation(321L), 0), conversation.findDiscussion(new DiscussionId(Ids.conversation(321L), 0)).getId());
		assertEquals(new DiscussionId(Ids.conversation(321L), 1), conversation.findDiscussion(new DiscussionId(Ids.conversation(321L), 1)).getId());
		assertEquals(new DiscussionId(Ids.conversation(321L), 2), conversation.findDiscussion(new DiscussionId(Ids.conversation(321L), 2)).getId());
		assertThrows(DomainException.class, () -> conversation.findDiscussion(new DiscussionId(Ids.conversation(321L), 3)));
	}

	@Test
	void shouldListDiscussionsInTheOrderTheyWereOpened()
	{
		Conversation conversation = startConversation();

		for (long i = 1; i < 20; i++)
		{
			conversation.openDiscussion(settledDiscussionNegotiation(100L + i));
		}

		List<Discussion> discussions = conversation.getDiscussions();

		assertEquals(20, discussions.size());

		for (int number = 0; number < 20; number++)
		{
			assertEquals(new DiscussionId(Ids.conversation(321L), number), discussions.get(number).getId());
		}
	}

	@Test
	void shouldGiveTheMainDiscussionNoTitleAndEachBranchTheTitleItWasSettledWith()
	{
		Conversation conversation = startConversation();

		conversation.openDiscussion(settledDiscussionNegotiation(80L));

		assertEquals(Optional.empty(), conversation.findDiscussion(new DiscussionId(Ids.conversation(321L), 0)).getTitle());
		assertEquals(Optional.of(new DiscussionTitle("TITLE 80", "SUBTITLE 80")), conversation.findDiscussion(new DiscussionId(Ids.conversation(321L), 1)).getTitle());
	}

	// KIND OF PROPOSAL:

	@Test
	void shouldNotOpenADiscussionWithAnAgreementNegotiation()
	{
		Conversation conversation = startConversation();
		Negotiation agreement = settledNegotiation(70L);

		assertThrows(DomainException.class, () -> conversation.openDiscussion(agreement));
		assertEquals(1, conversation.getDiscussions().size());

		// The rejected negotiation was not used up.
		assertDoesNotThrow(() -> conversation.modifyAgreement(agreement));
	}

	@Test
	void shouldNotModifyTheAgreementWithADiscussionNegotiation()
	{
		Conversation conversation = startConversation();
		Negotiation discussion = settledDiscussionNegotiation(70L);
		Proposal before = conversation.getAgreement();

		assertThrows(DomainException.class, () -> conversation.modifyAgreement(discussion));
		assertSame(before, conversation.getAgreement());

		assertDoesNotThrow(() -> conversation.openDiscussion(discussion));
	}

	@Test
	void shouldNotStartAConversationWithADiscussionNegotiation()
	{
		assertThrows(DomainException.class, () -> Conversation.start(Ids.conversation(321L), COUPLE, settledDiscussionNegotiation(60L)));
	}
}
