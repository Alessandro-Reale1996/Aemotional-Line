package com.aemotionalline.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.application.view.MessageView;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.constraint.ConstraintAssignment;
import com.aemotionalline.domain.constraint.GeoPosition;
import com.aemotionalline.domain.constraint.LocationConstraint;
import com.aemotionalline.domain.conversation.Conversation;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.conversation.ConversationRepository;
import com.aemotionalline.domain.conversation.DiscussionId;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.message.ParagraphId;
import com.aemotionalline.domain.message.ParagraphType;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.user.UserId;

/** The conversation use cases end to end on MariaDB: drafts turned into messages, opening with constraints, previews. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class ConversationServiceTest
{
	private static final LocalTime NOON = LocalTime.NOON;
	private static final GeoPosition HOME = new GeoPosition(45.4642, 9.19);
	private static final GeoPosition FAR = new GeoPosition(45.48, 9.19);

	@Autowired
	private ConversationService service;

	@Autowired
	private NegotiationService negotiations;

	@Autowired
	private CoupleService coupleService;

	@Autowired
	private ConversationRepository conversations;

	@Autowired
	private TransactionTemplate transactions;

	private UserId a;
	private UserId b;
	private UserId therapist;
	private ConversationId conversation;

	@BeforeEach
	void startAConversation()
	{
		a = UserId.random();
		b = UserId.random();
		therapist = UserId.random();
		CoupleId couple = coupleService.createCouple(therapist, a, b);

		NegotiationId start = negotiations.startAgreementNegotiation(a, couple, "OUR AGREEMENT");
		negotiations.acceptRequest(b, start);
		negotiations.sendProposal(a, start, "READY");
		conversation = negotiations.acceptProposal(b, start).conversationId();
	}

	private static ParagraphDraft simple(String body, ParagraphId... answered)
	{
		return new ParagraphDraft(ParagraphType.SIMPLE, "Title", "Subtitle", body, List.of(answered), List.of(), Optional.empty());
	}

	private static ParagraphDraft question(String title)
	{
		return new ParagraphDraft(ParagraphType.QUESTION, title, null, "Please answer.", List.of(), List.of(), Optional.empty());
	}

	private static ParagraphDraft pointed(ParagraphId question, String body)
	{
		return new ParagraphDraft(ParagraphType.POINTED, null, null, body, List.of(), List.of(), Optional.of(question));
	}

	private MessageId send(UserId sender, int discussion, ParagraphDraft... paragraphs)
	{
		return service.sendMessage(sender, conversation, discussion, new MessageDraft(List.of(paragraphs)), NOON, null);
	}

	private List<ParagraphId> paragraphsOf(UserId viewer, MessageId message)
	{
		return service.view(viewer, conversation).discussions().getFirst().messages().stream()
				.filter(view -> view.id().equals(message))
				.findFirst().orElseThrow()
				.paragraphs().stream().map(paragraph -> paragraph.id()).toList();
	}

	@Test
	void shouldSendOpenAndAnswerAMessage()
	{
		MessageId first = send(a, 0, simple("Hello."), question("Why?"));
		List<ParagraphId> sent = paragraphsOf(a, first);

		// B sees a closed envelope until B opens it; opening returns the content.
		assertTrue(service.view(b, conversation).discussions().getFirst().messages().getFirst().closedEnvelope());
		MessageView opened = service.readMessage(b, conversation, 0, first, NOON, null);
		assertFalse(opened.closedEnvelope());
		assertEquals(sent, opened.paragraphs().stream().map(paragraph -> paragraph.id()).toList());
		assertTrue(opened.readAt().isPresent());

		MessageId reply = send(b, 0, pointed(sent.get(1), "Because."), simple("Hi.", sent.get(0)));

		// A, in turn, sees B's reply only after opening it.
		assertTrue(service.view(a, conversation).discussions().getFirst().messages().get(1).closedEnvelope());
		service.readMessage(a, conversation, 0, reply, NOON, null);

		var answer = service.view(a, conversation).discussions().getFirst().messages().get(1).paragraphs();
		assertEquals(ParagraphType.POINTED, answer.get(0).type());
		assertEquals(List.of(sent.get(1)), answer.get(0).references());
		assertEquals(List.of(sent.get(0)), answer.get(1).references());
		assertEquals(reply, service.view(a, conversation).discussions().getFirst().messages().get(1).id());
	}

	@Test
	void shouldTreatAParagraphOfAnUnopenedMessageAsMissing()
	{
		MessageId first = send(a, 0, simple("Hello."));
		ParagraphId hidden = paragraphsOf(a, first).getFirst();

		// B hasn't opened A's message: B can't answer or cite a paragraph B hasn't seen.
		assertThrows(InvalidRequestException.class, () -> send(b, 0, simple("Hi.", hidden)));
		assertThrows(InvalidRequestException.class, () -> send(b, 0, simple("Hi.", ParagraphId.random())));
	}

	@Test
	void shouldRejectAMalformedPointedParagraph()
	{
		MessageId first = send(a, 0, simple("Hello."), question("Why?"));
		List<ParagraphId> sent = paragraphsOf(a, first);
		service.readMessage(b, conversation, 0, first, NOON, null);

		assertThrows(InvalidRequestException.class, () -> send(b, 0, pointed(sent.get(0), "Not a question.")));
		assertThrows(InvalidRequestException.class, () -> send(b, 0, new ParagraphDraft(ParagraphType.POINTED, null, null, "No question.",
				List.of(), List.of(), Optional.empty())));
		assertThrows(InvalidRequestException.class, () -> send(b, 0, new ParagraphDraft(ParagraphType.POINTED, null, null, "Two links.",
				List.of(sent.get(0)), List.of(), Optional.of(sent.get(1)))));
	}

	@Test
	void shouldApplyTheDomainRules()
	{
		MessageId first = send(a, 0, simple("Hello."));

		// B must open A's message before answering it; A can't write twice in a row.
		assertThrows(DomainException.class, () -> send(b, 0, simple("Hi.")));
		assertThrows(DomainException.class, () -> send(a, 0, simple("Again.")));

		service.readMessage(b, conversation, 0, first, NOON, null);
		send(b, 0, simple("Hi."));
		assertEquals(2, service.view(therapist, conversation).discussions().getFirst().messages().size());
	}

	@Test
	void shouldOpenAMessageOnlyWhereTheReadConstraintAllows()
	{
		transactions.executeWithoutResult(status ->
		{
			Conversation locked = conversations.findByIdForUpdate(conversation).orElseThrow();
			var request = locked.proposeConstraints(therapist, List.of(new ConstraintAssignment(b,
					new LocationConstraint(HOME, 200, LocationConstraint.Direction.WITHIN))));
			locked.approveConstraints(request, a);
			locked.approveConstraints(request, b);
			conversations.save(locked);
		});
		MessageId first = send(a, 0, simple("Read this at home."));

		assertThrows(DomainException.class, () -> service.readMessage(b, conversation, 0, first, NOON, FAR));
		assertTrue(service.view(b, conversation).discussions().getFirst().messages().getFirst().closedEnvelope());

		assertFalse(service.readMessage(b, conversation, 0, first, NOON, HOME).closedEnvelope());
		assertFalse(service.view(b, conversation).discussions().getFirst().messages().getFirst().closedEnvelope());
	}

	@Test
	void shouldLetTheTherapistReadWithoutOpeningTheMessageForTheRecipient()
	{
		MessageId first = send(a, 0, simple("Hello."));

		MessageView read = service.readMessage(therapist, conversation, 0, first, NOON, null);

		assertFalse(read.closedEnvelope());
		assertTrue(read.readAt().isEmpty());
		assertTrue(service.view(b, conversation).discussions().getFirst().messages().getFirst().closedEnvelope());
	}

	@Test
	void shouldPreviewTheSkippedQuestionsAndTheBlockingDiscussions()
	{
		MessageId first = send(a, 0, question("Why?"), simple("Holidays."));
		List<ParagraphId> sent = paragraphsOf(a, first);
		service.readMessage(b, conversation, 0, first, NOON, null);

		MessageDraft skipping = new MessageDraft(List.of(simple("Hi.", sent.get(1))));
		assertEquals(List.of(sent.get(0)), service.previewQuestionsLeftUnanswered(b, conversation, 0, skipping));
		assertTrue(service.previewBlockingDiscussions(b, conversation, 0, skipping).isEmpty());

		NegotiationId branch = negotiations.startDiscussionNegotiation(a, conversation, "Holidays", "Where", Optional.of(sent.get(1)));
		negotiations.acceptRequest(b, branch);
		negotiations.sendProposal(a, branch, "LET'S EXPAND");
		negotiations.acceptProposal(b, branch);

		assertEquals(List.of(new DiscussionId(conversation, 1)), service.previewBlockingDiscussions(b, conversation, 0, skipping));
		// Previews change nothing.
		assertEquals(1, service.view(therapist, conversation).discussions().getFirst().messages().size());
	}

	@Test
	void shouldTellAnOutsiderTheConversationDoesNotExist()
	{
		MessageId first = send(a, 0, simple("Hello."));
		UserId stranger = UserId.random();

		assertThrows(NotFoundException.class, () -> service.readMessage(stranger, conversation, 0, first, NOON, null));
		assertThrows(NotFoundException.class, () -> service.sendMessage(stranger, conversation, 0, new MessageDraft(List.of(simple("Hi."))), NOON, null));
		assertThrows(NotFoundException.class, () -> service.previewQuestionsLeftUnanswered(stranger, conversation, 0, new MessageDraft(List.of())));
	}
}
