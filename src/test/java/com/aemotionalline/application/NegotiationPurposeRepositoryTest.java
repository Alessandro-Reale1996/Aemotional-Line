package com.aemotionalline.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.domain.conversation.NegotiationRecord.Purpose;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.negotiation.AgreementProposal;
import com.aemotionalline.domain.negotiation.DiscussionProposal;
import com.aemotionalline.domain.negotiation.Negotiation;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.negotiation.NegotiationRepository;
import com.aemotionalline.domain.negotiation.ProposalId;
import com.aemotionalline.domain.user.UserId;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class NegotiationPurposeRepositoryTest
{
	@Autowired
	private NegotiationPurposeRepository purposes;

	@Autowired
	private NegotiationRepository negotiations;

	@Autowired
	private CoupleRepository couples;

	private Couple couple;

	@BeforeEach
	void saveACouple()
	{
		couple = new Couple(CoupleId.random(), UserId.random(), UserId.random(), UserId.random());
		couples.save(couple);
	}

	private Negotiation savedDiscussionRequest()
	{
		Negotiation negotiation = Negotiation.start(NegotiationId.random(), couple,
				new DiscussionProposal(ProposalId.random(), couple.getPartnerOneId(), "Holidays", "Where"), Clock.systemUTC());
		negotiations.save(negotiation);

		return negotiation;
	}

	@Test
	void shouldKeepThePurposeAndOriginOfANegotiation()
	{
		Negotiation negotiation = savedDiscussionRequest();
		NegotiationPurpose purpose = new NegotiationPurpose(negotiation.getId(), Purpose.OPENED_DISCUSSION, Optional.of(ParagraphId.random()));

		purposes.save(purpose);

		assertEquals(Optional.of(purpose), purposes.findByNegotiation(negotiation.getId()));
	}

	@Test
	void shouldListOnlyTheNegotiationsStillInProgress()
	{
		Negotiation open = savedDiscussionRequest();
		Negotiation abandoned = savedDiscussionRequest();
		abandoned.abandon(couple.getPartnerOneId());
		negotiations.save(abandoned);
		purposes.save(new NegotiationPurpose(open.getId(), Purpose.OPENED_DISCUSSION, Optional.empty()));
		purposes.save(new NegotiationPurpose(abandoned.getId(), Purpose.OPENED_DISCUSSION, Optional.empty()));

		assertEquals(List.of(open.getId()), purposes.findInProgressOf(couple.getId()).stream().map(NegotiationPurpose::negotiationId).toList());
	}

	@Test
	void shouldGoAwayWithItsNegotiation()
	{
		Negotiation negotiation = Negotiation.start(NegotiationId.random(), couple, new AgreementProposal(ProposalId.random(), couple.getPartnerOneId(), "TEXT"),
				Clock.systemUTC());
		negotiations.save(negotiation);
		purposes.save(new NegotiationPurpose(negotiation.getId(), Purpose.STARTED_CONVERSATION, Optional.empty()));

		negotiations.delete(negotiation.getId());

		assertTrue(purposes.findByNegotiation(negotiation.getId()).isEmpty());
	}

	@Test
	void shouldAllowAnOriginOnlyForADiscussion()
	{
		assertThrows(IllegalArgumentException.class,
				() -> new NegotiationPurpose(NegotiationId.random(), Purpose.MODIFIED_AGREEMENT, Optional.of(ParagraphId.random())));
	}
}
