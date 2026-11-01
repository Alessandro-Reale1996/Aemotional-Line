package com.aemotionalline.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.application.view.NegotiationEffect;
import com.aemotionalline.application.view.NegotiationView;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.conversation.Conversation;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.conversation.ConversationRepository;
import com.aemotionalline.domain.conversation.DiscussionId;
import com.aemotionalline.domain.conversation.FailedNegotiationRecord;
import com.aemotionalline.domain.conversation.NegotiationRecord.Purpose;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.message.SimpleParagraph;
import com.aemotionalline.domain.negotiation.AgreementText;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.negotiation.NegotiationRepository;
import com.aemotionalline.domain.negotiation.NegotiationStatus;
import com.aemotionalline.domain.negotiation.ProposalId;
import com.aemotionalline.domain.user.UserId;

/** The negotiation use cases end to end on MariaDB: purposes, limits, the automatic effects of success and failure, visibility. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class NegotiationServiceTest
{
	@Autowired
	private NegotiationService service;

	@Autowired
	private CoupleService coupleService;

	@Autowired
	private ConversationRepository conversations;

	@Autowired
	private NegotiationRepository negotiations;

	@Autowired
	private NegotiationPurposeRepository purposes;

	@Autowired
	private TransactionTemplate transactions;

	private UserId a;
	private UserId b;
	private UserId therapist;
	private CoupleId couple;

	@BeforeEach
	void createACouple()
	{
		a = UserId.random();
		b = UserId.random();
		therapist = UserId.random();
		couple = coupleService.createCouple(therapist, a, b);
	}

	// A asks, B accepts to talk, A sends the request as the first proposal, B accepts it.
	private NegotiationEffect agreeOn(NegotiationId negotiation)
	{
		service.acceptRequest(b, negotiation);
		service.sendProposal(a, negotiation, "LET'S DO IT");

		return service.acceptProposal(b, negotiation);
	}

	private ConversationId startConversation()
	{
		return agreeOn(service.startAgreementNegotiation(a, couple, "FIRST AGREEMENT")).conversationId();
	}

	// A sends a message in the main discussion (the conversation use cases come in A3).
	private ParagraphId aSendsAParagraph(ConversationId conversation)
	{
		SimpleParagraph paragraph = new SimpleParagraph(ParagraphId.random(), "Holidays", "Where", "Let's talk about the holidays.");

		transactions.executeWithoutResult(status ->
		{
			Conversation locked = conversations.findByIdForUpdate(conversation).orElseThrow();
			Message message = new Message(MessageId.random(), a);
			message.addParagraph(paragraph);
			locked.sendMessage(message, new DiscussionId(conversation, 0), null);
			conversations.save(locked);
		});

		return paragraph.getId();
	}

	@Test
	void shouldStartTheConversationWhenTheFirstAgreementIsAccepted()
	{
		NegotiationId negotiation = service.startAgreementNegotiation(a, couple, "FIRST AGREEMENT");
		assertEquals(Purpose.STARTED_CONVERSATION, service.view(a, negotiation).purpose());

		NegotiationEffect effect = agreeOn(negotiation);

		Conversation conversation = conversations.findByCouple(couple).orElseThrow();
		assertEquals(conversation.getId(), effect.conversationId());
		assertEquals(new AgreementText("FIRST AGREEMENT"), conversation.getAgreement().getContent());
		assertTrue(negotiations.findById(negotiation).isEmpty());
		assertTrue(purposes.findByNegotiation(negotiation).isEmpty());
	}

	@Test
	void shouldMoveTheFailedStartsIntoTheConversationWhenItStarts()
	{
		NegotiationId refused = service.startAgreementNegotiation(a, couple, "TOO EARLY");
		service.refuseRequest(b, refused);
		assertEquals(List.of(refused), service.failedStarts(therapist, couple).stream().map(NegotiationView::id).toList());

		startConversation();

		List<FailedNegotiationRecord> failed = conversations.findByCouple(couple).orElseThrow().getFailedNegotiations();
		assertEquals(List.of(refused), failed.stream().map(FailedNegotiationRecord::negotiationId).toList());
		assertEquals(Purpose.STARTED_CONVERSATION, failed.getFirst().attemptedPurpose());
		assertTrue(service.failedStarts(therapist, couple).isEmpty());
	}

	@Test
	void shouldAllowOnlyOneAgreementNegotiationAtATime()
	{
		NegotiationId first = service.startAgreementNegotiation(a, couple, "MINE");

		assertThrows(DomainException.class, () -> service.startAgreementNegotiation(b, couple, "MINE INSTEAD"));

		service.abandon(a, first);
		NegotiationId second = service.startAgreementNegotiation(b, couple, "MINE INSTEAD");
		assertEquals(NegotiationStatus.DRAFT, service.view(b, second).status());
	}

	@Test
	void shouldChangeTheAgreementAndKeepTheWholeExchange()
	{
		ConversationId conversation = startConversation();
		NegotiationId change = service.startAgreementNegotiation(a, couple, "SECOND");
		assertEquals(Purpose.MODIFIED_AGREEMENT, service.view(a, change).purpose());

		service.acceptRequest(b, change);
		service.sendProposal(a, change, "BECAUSE");
		service.refuseProposal(b, change);
		service.editProposal(b, change, new AgreementText("B'S SECOND"));
		service.sendProposal(b, change, "BETTER");
		NegotiationEffect effect = service.acceptProposal(a, change);

		assertEquals(conversation, effect.conversationId());
		Conversation changed = conversations.findById(conversation).orElseThrow();
		assertEquals(new AgreementText("B'S SECOND"), changed.getAgreement().getContent());
		assertEquals(2, changed.getNegotiationHistory().getLast().proposals().size());
		assertTrue(negotiations.findById(change).isEmpty());
	}

	@Test
	void shouldRecordAFailedChangeInTheConversation()
	{
		ConversationId conversation = startConversation();
		NegotiationId change = service.startAgreementNegotiation(a, couple, "SECOND");

		service.refuseRequest(b, change);

		FailedNegotiationRecord failed = conversations.findById(conversation).orElseThrow().getFailedNegotiations().getLast();
		assertEquals(change, failed.negotiationId());
		assertEquals(Purpose.MODIFIED_AGREEMENT, failed.attemptedPurpose());
		assertEquals(FailedNegotiationRecord.Outcome.REFUSED, failed.outcome());
		assertTrue(negotiations.findById(change).isEmpty());
	}

	@Test
	void shouldOpenADiscussionFromAnOriginTheRequesterCanSee()
	{
		ConversationId conversation = startConversation();
		ParagraphId origin = aSendsAParagraph(conversation);

		// B hasn't opened A's message: for B that paragraph doesn't exist.
		assertThrows(InvalidRequestException.class, () -> service.startDiscussionNegotiation(b, conversation, "Holidays", "Where", Optional.of(origin)));

		NegotiationId request = service.startDiscussionNegotiation(a, conversation, "Holidays", "Where", Optional.of(origin));
		assertThrows(DomainException.class, () -> service.startDiscussionNegotiation(a, conversation, "Again", "Same", Optional.of(origin)));

		NegotiationEffect effect = agreeOn(request);

		DiscussionId opened = effect.openedDiscussion().orElseThrow();
		assertEquals(new DiscussionId(conversation, 1), opened);
		var discussion = conversations.findById(conversation).orElseThrow().findDiscussion(opened);
		assertEquals(Optional.of(origin), discussion.getOriginParagraphId());
		assertEquals("Holidays", discussion.getTitle().orElseThrow().title());
		assertThrows(DomainException.class, () -> service.startDiscussionNegotiation(a, conversation, "Again", "Same", Optional.of(origin)));
	}

	@Test
	void shouldOpenADiscussionWithoutOriginAndRecordAnAbandonedOne()
	{
		ConversationId conversation = startConversation();

		NegotiationEffect effect = agreeOn(service.startDiscussionNegotiation(a, conversation, "Money", "Monthly budget", Optional.empty()));
		assertEquals(Optional.of(new DiscussionId(conversation, 1)), effect.openedDiscussion());

		NegotiationId abandoned = service.startDiscussionNegotiation(b, conversation, "Never", "Mind", Optional.empty());
		service.abandon(b, abandoned);
		FailedNegotiationRecord failed = conversations.findById(conversation).orElseThrow().getFailedNegotiations().getLast();
		assertEquals(Purpose.OPENED_DISCUSSION, failed.attemptedPurpose());
		assertEquals(FailedNegotiationRecord.Outcome.ABANDONED, failed.outcome());
	}

	@Test
	void shouldShowTheDraftOnlyToItsAuthor()
	{
		NegotiationId negotiation = service.startAgreementNegotiation(a, couple, "MINE");
		service.acceptRequest(b, negotiation);
		service.sendProposal(a, negotiation, "BECAUSE");
		ProposalId draft = service.refuseProposal(b, negotiation);
		service.editProposal(b, negotiation, new AgreementText("B'S WORK IN PROGRESS"));

		assertEquals(draft, service.view(b, negotiation).draft().orElseThrow().id());
		assertEquals("B'S WORK IN PROGRESS", service.view(b, negotiation).draft().orElseThrow().text());
		assertTrue(service.view(a, negotiation).draft().isEmpty());
		assertTrue(service.view(therapist, negotiation).draft().isEmpty());
		assertEquals("MINE", service.view(therapist, negotiation).request().text());
		assertEquals(1, service.view(therapist, negotiation).sentProposals().size());
	}

	@Test
	void shouldListTheNegotiationsInProgress()
	{
		ConversationId conversation = startConversation();
		NegotiationId change = service.startAgreementNegotiation(a, couple, "SECOND");
		NegotiationId branch = service.startDiscussionNegotiation(b, conversation, "Money", "Budget", Optional.empty());

		assertEquals(List.of(change, branch), service.inProgress(therapist, couple).stream().map(NegotiationView::id).toList());
	}

	@Test
	void shouldTellOutsidersAndUnknownIdsThatNothingExists()
	{
		NegotiationId negotiation = service.startAgreementNegotiation(a, couple, "MINE");
		UserId stranger = UserId.random();

		assertThrows(NotFoundException.class, () -> service.view(stranger, negotiation));
		assertThrows(NotFoundException.class, () -> service.acceptRequest(stranger, negotiation));
		assertThrows(NotFoundException.class, () -> service.abandon(stranger, negotiation));
		assertThrows(NotFoundException.class, () -> service.startAgreementNegotiation(stranger, couple, "HERS"));
		assertThrows(NotFoundException.class, () -> service.view(a, NegotiationId.random()));
		assertThrows(NotFoundException.class, () -> service.inProgress(stranger, couple));
		assertEquals(NegotiationStatus.DRAFT, service.view(a, negotiation).status());
	}

	@Test
	void shouldLetOnlyOneOfTwoSimultaneousAgreementNegotiationsStart() throws Exception
	{
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

		try
		{
			List<Future<?>> starts = new ArrayList<>();

			for (UserId partner : List.of(a, b))
			{
				starts.add(executor.submit(() ->
				{
					try
					{
						go.await(10, TimeUnit.SECONDS);
						service.startAgreementNegotiation(partner, couple, "AT THE SAME TIME");
					}
					catch (Throwable failure)
					{
						failures.add(failure);
					}
				}));
			}

			go.countDown();

			for (Future<?> start : starts)
			{
				start.get(10, TimeUnit.SECONDS);
			}
		}
		finally
		{
			executor.shutdownNow();
		}

		assertEquals(1, service.inProgress(therapist, couple).size());
		assertEquals(1, failures.size());
		assertTrue(failures.getFirst() instanceof DomainException);
	}
}
