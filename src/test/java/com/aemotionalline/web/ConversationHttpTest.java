package com.aemotionalline.web;

import static com.aemotionalline.web.HttpCouple.assertMalformed;
import static com.aemotionalline.web.HttpCouple.created;
import static com.aemotionalline.web.HttpCouple.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.application.ConstraintService;
import com.aemotionalline.application.ConstraintSpec;
import com.aemotionalline.application.PasswordHasher;
import com.aemotionalline.domain.constraint.ConstraintChangeRequestId;
import com.aemotionalline.domain.constraint.LocationConstraint;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.message.Message;
import com.aemotionalline.domain.user.UserRepository;

import tools.jackson.databind.JsonNode;

/**
 * The conversation over HTTP (CLAUDE.md, R6): a message from the sender to the answer, closed envelopes and read
 * receipts as each user sees them, the circumstances the client reports for the constraints, and the checks of this layer.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({ TestcontainersConfiguration.class, TestEndpoints.class })
public class ConversationHttpTest
{
	@LocalServerPort
	private int port;

	@Autowired
	private UserRepository users;

	@Autowired
	private CoupleRepository couples;

	@Autowired
	private PasswordHasher hasher;

	@Autowired
	private ConstraintService constraints;

	private HttpCouple couple;
	private ApiClient anna;
	private ApiClient bruno;
	private ApiClient therapist;
	private String conversation;
	private String path;

	@BeforeEach
	void startAConversation()
	{
		couple = new HttpCouple(port, users, couples, hasher);
		anna = couple.anna;
		bruno = couple.bruno;
		therapist = couple.therapist;
		conversation = couple.startConversation();
		path = "/api/conversations/" + conversation;
	}

	@Test
	void shouldCarryAMessageFromTheSenderToTheAnswer()
	{
		JsonNode empty = anna.get(path).json();
		assertEquals(HttpCouple.AGREEMENT, empty.path("agreement").asString());
		assertEquals(couple.id, empty.path("coupleId").asString());
		assertEquals(0, main(empty).path("id").asInt());
		assertTrue(main(empty).path("title").isNull());
		assertTrue(main(empty).path("messages").isEmpty());

		// Anna writes and sees what she wrote; the server chose the ids.
		String first = created(anna.post(messages(0), draft(simple("Le vacanze", "Io direi al mare"), question("Dove vuoi andare?", "Dimmelo tu"))));
		JsonNode sent = lastMessage(anna);
		assertEquals(first, sent.path("id").asString());
		assertFalse(sent.path("closedEnvelope").asBoolean());
		String statement = sent.path("paragraphs").get(0).path("id").asString();
		String asked = sent.path("paragraphs").get(1).path("id").asString();

		// Bruno sees a closed envelope: who and when, nothing else. The therapist reads it without opening it for him.
		JsonNode envelope = lastMessage(bruno);
		assertTrue(envelope.path("closedEnvelope").asBoolean());
		assertEquals(id(couple.annaAccount), envelope.path("sender").asString());
		assertTrue(envelope.path("sentAt").isString());
		assertTrue(envelope.path("paragraphs").isEmpty());
		assertEquals("Le vacanze", lastMessage(therapist).path("paragraphs").get(0).path("title").asString());
		assertTrue(lastMessage(bruno).path("closedEnvelope").asBoolean());

		// Until he opens it he can't answer, and the preview doesn't say what the envelope holds.
		assertEquals("MESSAGE_NOT_OPENED", bruno.post(messages(0), draft(simple("Va bene", "D'accordo"))).code());
		assertTrue(bruno.post(previews(0), draft(simple("Va bene", "D'accordo"))).json().path("questionsLeftUnanswered").isEmpty());

		ApiClient.Response opened = bruno.post(open(0, first), "{}");
		assertEquals(200, opened.status());
		assertFalse(opened.json().path("closedEnvelope").asBoolean());
		assertEquals(2, opened.json().path("paragraphs").size());

		// No read receipts: the opening is known to who opened it and to the therapist, not to the sender.
		assertTrue(lastMessage(bruno).path("readAt").isString());
		assertTrue(lastMessage(therapist).path("readAt").isString());
		assertTrue(lastMessage(anna).path("readAt").isNull());

		// Now the preview warns about the question, and sending anyway is allowed.
		JsonNode preview = bruno.post(previews(0), draft(simple("Va bene", "D'accordo", statement))).json();
		assertEquals(asked, preview.path("questionsLeftUnanswered").get(0).asString());
		assertTrue(preview.path("blockingDiscussions").isEmpty());
		assertEquals(1, main(anna.get(path).json()).path("messages").size(), "a preview changes nothing");

		String reply = created(bruno.post(messages(0), draft(pointed(asked, "Al mare anch'io"), simple("Allora", "Prenoto io", statement))));

		// Anna, in turn, gets an envelope; opening it she finds the answer, with its computed title.
		assertTrue(lastMessage(anna).path("closedEnvelope").asBoolean());
		JsonNode answer = anna.post(open(0, reply), "{}").json();
		JsonNode pointed = answer.path("paragraphs").get(0);
		assertEquals("POINTED", pointed.path("type").asString());
		assertEquals("In risposta a «Dove vuoi andare?»", pointed.path("title").asString());
		assertEquals(asked, pointed.path("references").get(0).asString());
		assertEquals(statement, answer.path("paragraphs").get(1).path("references").get(0).asString());
		assertTrue(answer.path("questionsLeftUnanswered").isEmpty());
	}

	@Test
	void shouldApplyTheConstraintsToWhatTheClientReports()
	{
		ConversationId id = new ConversationId(UUID.fromString(conversation));
		ConstraintChangeRequestId request = constraints.proposeConstraints(couple.therapistAccount.getId(), id, List.of(
				new ConstraintSpec(couple.brunoAccount.getId(), "LOCATION", null, null, null, 45.4642, 9.19, 200.0, LocationConstraint.Direction.WITHIN),
				new ConstraintSpec(couple.brunoAccount.getId(), "TIME", null, LocalTime.of(18, 0), null, null, null, null, null)));
		constraints.approveConstraints(couple.annaAccount.getId(), id, request);
		constraints.approveConstraints(couple.brunoAccount.getId(), id, request);
		String first = created(anna.post(messages(0), draft(simple("Le vacanze", "Io direi al mare"))));

		// Bruno opens messages only at home: a missing position doesn't count as being there.
		assertEquals("CONSTRAINT_NOT_SATISFIED", bruno.post(open(0, first), "{}").code());
		assertEquals("CONSTRAINT_NOT_SATISFIED", bruno.post(open(0, first), "{\"position\":{\"latitude\":45.6,\"longitude\":9.19}}").code());
		assertTrue(lastMessage(bruno).path("closedEnvelope").asBoolean());
		assertEquals(200, bruno.post(open(0, first), "{\"position\":{\"latitude\":45.4642,\"longitude\":9.19}}").status());

		// And writes only from six in the evening: nor does a missing time count as being late enough.
		String reply = "\"paragraphs\":[" + simple("Va bene", "D'accordo") + "]";
		assertEquals("CONSTRAINT_NOT_SATISFIED", bruno.post(messages(0), "{" + reply + "}").code());
		assertEquals("CONSTRAINT_NOT_SATISFIED", bruno.post(messages(0), "{" + reply + ",\"localTime\":\"17:59\"}").code());
		assertEquals(201, bruno.post(messages(0), "{" + reply + ",\"localTime\":\"18:00\"}").status());
	}

	@Test
	void shouldSendTheAnswerToAParagraphInTheDiscussionThatExpandsIt()
	{
		String first = created(anna.post(messages(0), draft(simple("Le vacanze", "Io direi al mare"))));
		String paragraph = lastMessage(anna).path("paragraphs").get(0).path("id").asString();
		String negotiation = "/api/negotiations/" + created(anna.post(path + "/discussion-negotiations",
				"{\"title\":\"Le vacanze\",\"subtitle\":\"Dove andare\",\"originParagraphId\":\"" + paragraph + "\"}"));
		assertEquals(204, bruno.post(negotiation + "/accept-request", "").status());
		assertEquals(204, anna.post(negotiation + "/send", "{\"justification\":\"Merita un discorso a parte\"}").status());
		assertEquals(1, bruno.post(negotiation + "/accept-proposal", "").json().path("openedDiscussion").asInt());
		assertEquals(200, bruno.post(open(0, first), "{}").status());

		// On the main conversation the paragraph is blocked: the preview says where to write instead.
		String answer = draft(simple("Va bene", "D'accordo", paragraph));
		assertEquals(1, bruno.post(previews(0), answer).json().path("blockingDiscussions").get(0).asInt());
		assertEquals("PARAGRAPH_EXPANDED_ELSEWHERE", bruno.post(messages(0), answer).code());

		assertTrue(bruno.post(previews(1), answer).json().path("blockingDiscussions").isEmpty());
		assertEquals(201, bruno.post(messages(1), answer).status());

		JsonNode expanded = bruno.get(path).json().path("discussions").get(1);
		assertEquals("Le vacanze", expanded.path("title").asString());
		assertEquals(paragraph, expanded.path("originParagraphId").asString());
		assertEquals(paragraph, expanded.path("messages").get(0).path("paragraphs").get(0).path("references").get(0).asString());
	}

	@Test
	void shouldCheckTheShapeOfADraft()
	{
		assertMalformed(anna.post(messages(0), "{}"), "The field 'paragraphs' is required.");
		assertMalformed(anna.post(previews(0), "{}"), "The field 'paragraphs' is required.");
		assertMalformed(anna.post(messages(0), "{\"paragraphs\":[null]}"), "A paragraph of the draft is missing.");
		assertMalformed(anna.post(messages(0), "{\"paragraphs\":[{\"body\":\"Testo\"}]}"), "Every paragraph of the draft needs a type.");
		assertMalformed(anna.post(messages(0), "{\"paragraphs\":[{\"type\":\"POEM\"}]}"), "Missing or malformed value of field 'paragraphs[0].type'.");
		assertMalformed(anna.post(messages(0), "{\"paragraphs\":[{\"type\":\"SIMPLE\",\"references\":[\"not a uuid\"]}]}"),
				"Missing or malformed value of field 'paragraphs[0].references[0]'.");
		assertMalformed(anna.post(messages(0), "{\"paragraphs\":[{\"type\":\"SIMPLE\",\"references\":[null]}]}"),
				"A paragraph's list of ids has a missing element.");
		assertMalformed(anna.post(messages(0), "{\"paragraphs\":[{\"type\":\"SIMPLE\",\"selfCitations\":[null]}]}"),
				"A paragraph's list of ids has a missing element.");
		assertMalformed(anna.post(messages(0), draft(simple("Titolo", "Testo")).replace("}]}", "}],\"localTime\":\"late\"}")),
				"Missing or malformed value of field 'localTime'.");
		assertMalformed(anna.post(messages(0), "{\"paragraphs\":[],\"position\":{\"latitude\":45.46}}"), "The field 'position.longitude' is required.");

		// A field the type doesn't have, which would be lost.
		String foreign = "A question doesn't take a subtitle or a question.";
		assertMalformed(anna.post(messages(0), "{\"paragraphs\":[{\"type\":\"QUESTION\",\"title\":\"Perché?\",\"subtitle\":\"Sottotitolo\",\"body\":\"Testo\"}]}"), foreign);
		assertMalformed(anna.post(messages(0), "{\"paragraphs\":[{\"type\":\"SIMPLE\",\"question\":\"" + UUID.randomUUID() + "\",\"body\":\"Testo\"}]}"),
				"A simple paragraph doesn't take a question.");
		assertMalformed(anna.post(messages(0), "{\"paragraphs\":[{\"type\":\"POINTED\",\"title\":\"Titolo\",\"question\":\"" + UUID.randomUUID() + "\",\"body\":\"Testo\"}]}"),
				"A pointed paragraph takes only its question and body: its title is computed.");

		// An id that isn't in the conversation.
		assertEquals("MALFORMED_REQUEST", anna.post(messages(0), draft(simple("Titolo", "Testo", UUID.randomUUID().toString()))).code());

		// None of these sent anything.
		assertTrue(main(anna.get(path).json()).path("messages").isEmpty());
	}

	@Test
	void shouldLimitTheParagraphsOfAMessage()
	{
		assertEquals("MESSAGE_EMPTY", anna.post(messages(0), draft()).code());

		String[] paragraphs = new String[Message.MAX_PARAGRAPHS + 1];
		Arrays.fill(paragraphs, simple("Titolo", "Testo"));
		ApiClient.Response tooMany = anna.post(messages(0), draft(paragraphs));
		assertEquals(409, tooMany.status());
		assertEquals("MESSAGE_TOO_LONG", tooMany.code());
		assertEquals("MESSAGE_TOO_LONG", anna.post(previews(0), draft(paragraphs)).code());

		assertEquals(201, anna.post(messages(0), draft(Arrays.copyOf(paragraphs, Message.MAX_PARAGRAPHS))).status());
		assertEquals(Message.MAX_PARAGRAPHS, lastMessage(anna).path("paragraphs").size());
	}

	@Test
	void shouldAnswerForWhatDoesNotExistOrIsNotAllowed()
	{
		String first = created(anna.post(messages(0), draft(simple("Le vacanze", "Io direi al mare"))));
		String body = draft(simple("Titolo", "Testo"));

		// Discussions and messages that don't exist.
		for (int number : new int[] { 99, -1 })
		{
			assertEquals(404, anna.post(messages(number), body).status());
			assertEquals(404, anna.post(previews(number), body).status());
			assertEquals(404, bruno.post(open(number, first), "{}").status());
		}

		assertEquals("MALFORMED_REQUEST", anna.post(path + "/discussions/first/messages", body).code());
		assertEquals(404, bruno.post(open(0, UUID.randomUUID().toString()), "{}").status());
		assertEquals("MALFORMED_REQUEST", bruno.post(open(0, "not-a-uuid"), "{}").code());
		assertEquals("MALFORMED_REQUEST", bruno.post(open(0, first), "").code());

		// A position out of range; a sender out of turn; a therapist doesn't write.
		assertEquals("RULE_VIOLATED", bruno.post(open(0, first), "{\"position\":{\"latitude\":95,\"longitude\":0}}").code());
		assertEquals("NOT_YOUR_TURN", anna.post(messages(0), body).code());
		assertEquals("NOT_A_PARTNER", therapist.post(messages(0), body).code());

		// An outsider sees nothing.
		ApiClient outsider = couple.outsider();
		assertEquals(404, outsider.get(path).status());
		assertEquals(404, outsider.post(messages(0), body).status());
		assertEquals(404, outsider.post(previews(0), body).status());
		assertEquals(404, outsider.post(open(0, first), "{}").status());
		assertTrue(lastMessage(bruno).path("closedEnvelope").asBoolean());
	}

	private static JsonNode main(JsonNode conversation)
	{
		return conversation.path("discussions").get(0);
	}

	private JsonNode lastMessage(ApiClient viewer)
	{
		JsonNode messages = main(viewer.get(path).json()).path("messages");

		return messages.get(messages.size() - 1);
	}

	private String messages(int discussion)
	{
		return path + "/discussions/" + discussion + "/messages";
	}

	private String previews(int discussion)
	{
		return path + "/discussions/" + discussion + "/previews";
	}

	private String open(int discussion, String message)
	{
		return messages(discussion) + "/" + message + "/open";
	}

	private static String draft(String... paragraphs)
	{
		return "{\"paragraphs\":[" + String.join(",", paragraphs) + "]}";
	}

	private static String simple(String title, String body, String... references)
	{
		return "{\"type\":\"SIMPLE\",\"title\":\"" + title + "\",\"body\":\"" + body + "\",\"references\":" + ids(references) + "}";
	}

	private static String question(String title, String body)
	{
		return "{\"type\":\"QUESTION\",\"title\":\"" + title + "\",\"body\":\"" + body + "\"}";
	}

	private static String pointed(String question, String body)
	{
		return "{\"type\":\"POINTED\",\"question\":\"" + question + "\",\"body\":\"" + body + "\"}";
	}

	private static String ids(String... ids)
	{
		return Arrays.stream(ids).map(id -> "\"" + id + "\"").toList().toString();
	}
}
