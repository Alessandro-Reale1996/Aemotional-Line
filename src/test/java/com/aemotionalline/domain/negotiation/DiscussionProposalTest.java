package com.aemotionalline.domain.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.Ids;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;

// The two kinds of proposal hold different content; Negotiation must work with both without knowing which.
public class DiscussionProposalTest
{
	private final Couple couple = new Couple(Ids.couple(1L), Ids.user(10L), Ids.user(20L), Ids.user(30L));

	private Negotiation startAccepted(Proposal initial)
	{
		Negotiation negotiation = Negotiation.start(Ids.negotiation(100L), couple, initial, Clock.systemUTC());
		negotiation.acceptNegotiation(couple.getPartnerTwoId());
		return negotiation;
	}

	@Test
	void shouldRejectBlankTitleOrSubtitle()
	{
		assertThrows(DomainException.class, () -> new DiscussionProposal(Ids.proposal(0L), couple.getPartnerOneId(), " ", "sub"));
		assertThrows(DomainException.class, () -> new DiscussionProposal(Ids.proposal(0L), couple.getPartnerOneId(), "title", ""));
		assertThrows(DomainException.class, () -> new DiscussionProposal(Ids.proposal(0L), couple.getPartnerOneId(), null, "sub"));
	}

	@Test
	void shouldEnforceTheLengthLimitsOfTitleAndSubtitle()
	{
		assertDoesNotThrow(() -> new DiscussionTitle("t".repeat(DiscussionTitle.MAX_TITLE_LENGTH), "s".repeat(DiscussionTitle.MAX_SUBTITLE_LENGTH)));
		assertThrows(DomainException.class, () -> new DiscussionTitle("t".repeat(DiscussionTitle.MAX_TITLE_LENGTH + 1), "sub"));
		assertThrows(DomainException.class, () -> new DiscussionTitle("title", "s".repeat(DiscussionTitle.MAX_SUBTITLE_LENGTH + 1)));
	}

	@Test
	void shouldNotAcceptContentOfAnotherKind()
	{
		Negotiation negotiation = startAccepted(new DiscussionProposal(Ids.proposal(0L), couple.getPartnerOneId(), "Title", "Sub"));

		assertThrows(DomainException.class, () -> negotiation.editCurrentProposal(couple.getPartnerOneId(), new AgreementText("TEXT")));
		assertThrows(DomainException.class, () -> negotiation.editCurrentProposal(couple.getPartnerOneId(), null));
	}

	@Test
	void shouldNotAcceptAgreementProposalContentOfADiscussion()
	{
		Negotiation negotiation = startAccepted(new AgreementProposal(Ids.proposal(0L), couple.getPartnerOneId(), "TEXT"));

		assertThrows(DomainException.class, () -> negotiation.editCurrentProposal(couple.getPartnerOneId(), new DiscussionTitle("Title", "Sub")));
	}

	@Test
	void shouldEditAndSendADiscussionProposal()
	{
		Negotiation negotiation = startAccepted(new DiscussionProposal(Ids.proposal(0L), couple.getPartnerOneId(), "Title", "Sub"));

		negotiation.editCurrentProposal(couple.getPartnerOneId(), new DiscussionTitle("New title", "New sub"));
		negotiation.sendProposal(couple.getPartnerOneId(), "because");

		DiscussionProposal sent = (DiscussionProposal) negotiation.getLastProposal();
		assertEquals("New title", sent.getTitle());
		assertEquals("New sub", sent.getSubtitle());
	}

	@Test
	void shouldAnswerARefusedDiscussionProposalWithADraftOfTheSameKind()
	{
		Negotiation negotiation = startAccepted(new DiscussionProposal(Ids.proposal(0L), couple.getPartnerOneId(), "Title", "Sub"));
		negotiation.sendProposal(couple.getPartnerOneId(), "because");

		negotiation.refuseProposal(couple.getPartnerTwoId(), Ids.proposal(1L));

		DiscussionProposal draft = assertInstanceOf(DiscussionProposal.class, negotiation.getCurrentProposal());
		assertEquals(new DiscussionTitle("Title", "Sub"), draft.getContent());
		assertEquals(couple.getPartnerTwoId(), draft.getAuthor());
	}
}
