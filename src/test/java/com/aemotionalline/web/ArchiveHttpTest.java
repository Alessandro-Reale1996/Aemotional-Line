package com.aemotionalline.web;

import static com.aemotionalline.web.HttpCouple.assertMalformed;
import static com.aemotionalline.web.HttpCouple.created;
import static com.aemotionalline.web.HttpCouple.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.application.PasswordHasher;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.user.UserRepository;

import tools.jackson.databind.JsonNode;

/**
 * The archive over HTTP (CLAUDE.md, R8): what each member finds in it, closed envelopes in their place, the negotiations
 * that are over, and the checks of this layer. The rules themselves are tested on the domain and the services.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({ TestcontainersConfiguration.class, TestEndpoints.class })
public class ArchiveHttpTest
{
	private static final String EVER = "2000-01-01T00:00:00Z";
	private static final String NEVER = "2100-01-01T00:00:00Z";

	@LocalServerPort
	private int port;

	@Autowired
	private UserRepository users;

	@Autowired
	private CoupleRepository couples;

	@Autowired
	private PasswordHasher hasher;

	private HttpCouple couple;
	private ApiClient anna;
	private ApiClient bruno;
	private ApiClient therapist;
	private String conversation;
	private String archive;

	private String first;
	private String statement;
	private String asked;
	private String reply;
	private String answer;

	@BeforeEach
	void createTheCouple()
	{
		couple = new HttpCouple(port, users, couples, hasher);
		anna = couple.anna;
		bruno = couple.bruno;
		therapist = couple.therapist;
	}

	private void startTheConversation()
	{
		conversation = "/api/conversations/" + couple.startConversation();
		archive = conversation + "/archive";
	}

	// Anna writes a statement and a question; Bruno opens them and answers. Anna hasn't opened the answer.
	private void anExchange()
	{
		startTheConversation();
		first = created(anna.post(messages(), draft(simple("Le vacanze", "Io direi al mare"), question("Dove vuoi andare?", "Dimmelo tu"))));
		JsonNode sent = lastMessage(anna).path("paragraphs");
		statement = sent.get(0).path("id").asString();
		asked = sent.get(1).path("id").asString();
		assertEquals(200, bruno.post(open(first), "{}").status());
		reply = created(bruno.post(messages(), draft(pointed(asked, "In montagna"), simple("Allora", "Prenoto io", statement), simple("E poi", "Compro i biglietti", statement))));
		answer = lastMessage(therapist).path("paragraphs").get(0).path("id").asString();
	}

	@Test
	void shouldFindAWordWithoutRevealingAClosedEnvelope()
	{
		anExchange();

		// Anna hasn't opened Bruno's answer: for her the word isn't there, not even as an envelope.
		assertTrue(search(anna, "montagna").path("paragraphs").isEmpty());

		JsonNode result = search(therapist, "montagna");
		assertEquals(2, result.size(), result.toString());
		JsonNode found = result.path("paragraphs").get(0);
		assertEquals(answer, found.path("paragraphId").asString());
		assertEquals("POINTED", found.path("type").asString());
		assertEquals("In risposta a «Dove vuoi andare?»", found.path("title").asString());
		assertEquals("In montagna", found.path("body").asString());
		assertEquals(0, found.path("discussionId").asInt());
		assertEquals(reply, found.path("messageId").asString());
		assertEquals(id(couple.brunoAccount), found.path("sender").asString());
		assertTrue(found.path("sentAt").isString());
		assertFalse(found.path("closedEnvelope").asBoolean());
		assertEquals(1, search(bruno, "montagna").path("paragraphs").size());

		assertEquals(200, anna.post(open(reply), "{}").status());
		assertEquals(answer, search(anna, "MONTÀGNA").path("paragraphs").get(0).path("paragraphId").asString());

		// Proposals are found too, as they are shown in the negotiations.
		JsonNode proposal = search(bruno, "calma").path("proposals").get(0);
		assertEquals("STARTED_CONVERSATION", proposal.path("purpose").asString());
		assertEquals("TOOK_EFFECT", proposal.path("section").asString());
		assertEquals("AGREEMENT", proposal.path("proposal").path("kind").asString());
		assertEquals(HttpCouple.AGREEMENT, proposal.path("proposal").path("text").asString());
		assertEquals("Così stiamo sereni", proposal.path("proposal").path("justification").asString());
		assertEquals("ACCEPTED", proposal.path("proposal").path("status").asString());

		JsonNode nothing = search(anna, "%");
		assertTrue(nothing.path("paragraphs").isEmpty() && nothing.path("proposals").isEmpty());

		// The word travels in the body: a GET doesn't exist.
		assertEquals(405, anna.get(archive + "/search?text=montagna").status());
		assertMalformed(anna.post(archive + "/search", "{}"), "The field 'text' is required.");
		assertEquals("MALFORMED_REQUEST", anna.post(archive + "/search", "{\"text\":\"   \"}").code());
	}

	@Test
	void shouldBrowseByTimeWithClosedEnvelopesInPlace()
	{
		anExchange();

		// For Anna, her two paragraphs and one envelope for Bruno's whole answer: how many paragraphs it has stays hidden.
		JsonNode forAnna = anna.get(messagesBetween(EVER, NEVER)).json();
		assertEquals(3, forAnna.size(), forAnna.toString());
		assertEquals(statement, forAnna.get(0).path("paragraphId").asString());
		assertEquals(asked, forAnna.get(1).path("paragraphId").asString());
		JsonNode envelope = forAnna.get(2);
		assertTrue(envelope.path("closedEnvelope").asBoolean());
		assertEquals(reply, envelope.path("messageId").asString());
		assertEquals(id(couple.brunoAccount), envelope.path("sender").asString());
		assertTrue(envelope.path("paragraphId").isNull());
		assertTrue(envelope.path("type").isNull());
		assertTrue(envelope.path("title").isNull());
		assertTrue(envelope.path("body").isNull());

		// The therapist sees it all, the pointed answer with its computed title.
		JsonNode forTherapist = therapist.get(messagesBetween(EVER, NEVER)).json();
		assertEquals(5, forTherapist.size());
		assertEquals("In risposta a «Dove vuoi andare?»", forTherapist.get(2).path("title").asString());

		// The start is included, the end excluded.
		String sentAt = forTherapist.get(2).path("sentAt").asString();
		assertEquals(2, therapist.get(messagesBetween(EVER, sentAt)).json().size());
		assertEquals(3, therapist.get(messagesBetween(sentAt, NEVER)).json().size());
		assertTrue(therapist.get(messagesBetween(NEVER, NEVER)).json().isEmpty());

		// What isn't an interval.
		assertMalformed(anna.get(archive + "/messages?from=" + EVER), "Missing parameter 'to'.");
		assertMalformed(anna.get(archive + "/messages?to=" + NEVER), "Missing parameter 'from'.");
		assertMalformed(anna.get(messagesBetween("yesterday", NEVER)), "Malformed value of 'from'.");
		assertEquals("MALFORMED_REQUEST", anna.get(messagesBetween(NEVER, EVER)).code());
		assertEquals("MALFORMED_REQUEST", anna.get(messagesBetween(EVER, "%2B20000-01-01T00:00:00Z")).code());
		assertEquals("MALFORMED_REQUEST", anna.get(messagesBetween("-999999999-01-01T00:00:00Z", NEVER)).code());
	}

	@Test
	void shouldShowTheHistoryOfATopicWithClosedEnvelopesInPlace()
	{
		anExchange();

		// The statement's topic holds two paragraphs of Bruno's answer: for Anna, one envelope.
		JsonNode forAnna = anna.get(topic(statement)).json();
		assertEquals(statement, forAnna.path("paragraphId").asString());
		assertEquals("MAIN_CONVERSATION", forAnna.path("rule").asString());
		assertEquals(2, forAnna.path("entries").size(), forAnna.toString());
		assertEquals(statement, forAnna.path("entries").get(0).path("paragraphId").asString());
		assertTrue(forAnna.path("entries").get(1).path("closedEnvelope").asBoolean());

		JsonNode forBruno = bruno.get(topic(statement)).json();
		assertEquals(3, forBruno.path("entries").size());
		assertEquals("Prenoto io", forBruno.path("entries").get(1).path("body").asString());

		// A paragraph she hasn't opened and one that doesn't exist get the same answer.
		ApiClient.Response unopened = anna.get(topic(answer));
		assertEquals(404, unopened.status());
		assertEquals("NOT_FOUND", unopened.code());
		assertEquals(unopened.json().path("detail").asString(), anna.get(topic(UUID.randomUUID().toString())).json().path("detail").asString());
		assertEquals("MALFORMED_REQUEST", anna.get(topic("not-a-uuid")).code());
	}

	@Test
	void shouldListTheUnansweredQuestionsEachUserCanSee()
	{
		startTheConversation();
		String message = created(anna.post(messages(), draft(question("Dove vuoi andare?", "Dimmelo tu"))));
		String question = lastMessage(anna).path("paragraphs").get(0).path("id").asString();

		// Still waiting for an answer; Bruno, who hasn't opened it, doesn't know it is there.
		JsonNode waiting = anna.get(archive + "/unanswered-questions").json();
		assertEquals(question, waiting.path("stillOpen").get(0).path("paragraphId").asString());
		assertTrue(waiting.path("leftUnanswered").isEmpty());
		assertTrue(bruno.get(archive + "/unanswered-questions").json().path("stillOpen").isEmpty());

		// Bruno opens it and writes without answering it: it is skipped for good.
		assertEquals(200, bruno.post(open(message), "{}").status());
		created(bruno.post(messages(), draft(simple("Ne parliamo", "Più tardi"))));

		JsonNode skipped = therapist.get(archive + "/unanswered-questions").json();
		assertEquals(question, skipped.path("leftUnanswered").get(0).path("paragraphId").asString());
		assertEquals("Dove vuoi andare?", skipped.path("leftUnanswered").get(0).path("title").asString());
	}

	@Test
	void shouldListTheNegotiationsThatAreOver()
	{
		// A failed attempt to start the conversation, before it exists.
		String refused = created(anna.post(agreementNegotiations(), "{\"text\":\"Ci scriviamo ogni ora\"}"));
		assertEquals(204, bruno.post("/api/negotiations/" + refused + "/refuse-request", "").status());

		startTheConversation();

		// A change of the agreement, with a counter-proposal.
		String change = "/api/negotiations/" + created(anna.post(agreementNegotiations(), "{\"text\":\"Ci scriviamo la sera\"}"));
		assertEquals(204, bruno.post(change + "/accept-request", "").status());
		assertEquals(204, anna.post(change + "/send", "{\"justification\":\"Di giorno lavoro\"}").status());
		assertEquals(201, bruno.post(change + "/refuse-proposal", "").status());
		assertEquals(204, bruno.put(change + "/draft", "{\"text\":\"Ci scriviamo la mattina\"}").status());
		assertEquals(204, bruno.post(change + "/send", "{\"justification\":\"La sera sono stanco\"}").status());
		assertEquals(200, anna.post(change + "/accept-proposal", "").status());

		// A discussion that never opened.
		String abandoned = created(anna.post(conversation + "/discussion-negotiations", "{\"title\":\"I soldi\",\"subtitle\":\"Come dividerli\"}"));
		assertEquals(204, anna.post("/api/negotiations/" + abandoned + "/abandon", "").status());

		ApiClient.Response response = anna.get(archive + "/negotiations");
		assertEquals(200, response.status());
		JsonNode history = response.json();

		JsonNode tookEffect = history.path("tookEffect");
		assertEquals(2, tookEffect.size(), tookEffect.toString());
		assertEquals("STARTED_CONVERSATION", tookEffect.get(0).path("purpose").asString());
		assertEquals(0, tookEffect.get(0).path("discussionId").asInt());
		assertTrue(tookEffect.get(0).path("archivedAt").isString());
		assertEquals("MODIFIED_AGREEMENT", tookEffect.get(1).path("purpose").asString());
		assertTrue(tookEffect.get(1).path("discussionId").isNull());
		JsonNode exchange = tookEffect.get(1).path("proposals");
		assertEquals(2, exchange.size());
		assertEquals("REFUSED", exchange.get(0).path("status").asString());
		assertEquals("Di giorno lavoro", exchange.get(0).path("justification").asString());
		assertEquals("ACCEPTED", exchange.get(1).path("status").asString());
		assertEquals("Ci scriviamo la mattina", exchange.get(1).path("text").asString());

		JsonNode failed = history.path("failed");
		assertEquals(2, failed.size(), failed.toString());
		assertEquals(refused, failed.get(0).path("negotiationId").asString());
		assertEquals("STARTED_CONVERSATION", failed.get(0).path("attemptedPurpose").asString());
		assertEquals("REFUSED", failed.get(0).path("outcome").asString());
		assertEquals(id(couple.brunoAccount), failed.get(0).path("closedBy").asString());
		assertTrue(failed.get(0).path("startedAt").isString());
		assertTrue(failed.get(0).path("closedAt").isString());
		assertEquals("Ci scriviamo ogni ora", failed.get(0).path("proposals").get(0).path("text").asString());
		assertEquals("OPENED_DISCUSSION", failed.get(1).path("attemptedPurpose").asString());
		assertEquals("ABANDONED", failed.get(1).path("outcome").asString());
		assertEquals("DISCUSSION_TITLE", failed.get(1).path("proposals").get(0).path("kind").asString());
		assertEquals("I soldi", failed.get(1).path("proposals").get(0).path("title").asString());

		// The same for the couple's three members.
		assertEquals(history, bruno.get(archive + "/negotiations").json());
		assertEquals(history, therapist.get(archive + "/negotiations").json());
	}

	@Test
	void shouldKeepTheArchiveForTheMembersOnly()
	{
		anExchange();

		// After the couple has ended everything stays readable.
		assertEquals(204, therapist.post("/api/couples/" + couple.id + "/end", "{\"password\":\"" + HttpCouple.PASSWORD + "\"}").status());
		assertEquals(1, search(anna, "mare").path("paragraphs").size());
		assertEquals(200, anna.get(messagesBetween(EVER, NEVER)).status());
		assertEquals(200, anna.get(topic(statement)).status());
		assertEquals(200, anna.get(archive + "/unanswered-questions").status());
		assertEquals(200, anna.get(archive + "/negotiations").status());

		// An outsider finds nothing, not even that the conversation exists.
		ApiClient outsider = couple.outsider();
		assertEquals(404, outsider.post(archive + "/search", "{\"text\":\"mare\"}").status());
		assertEquals(404, outsider.get(messagesBetween(EVER, NEVER)).status());
		assertEquals(404, outsider.get(topic(statement)).status());
		assertEquals(404, outsider.get(archive + "/unanswered-questions").status());
		assertEquals(404, outsider.get(archive + "/negotiations").status());
		assertEquals(404, anna.get("/api/conversations/" + UUID.randomUUID() + "/archive/negotiations").status());
	}

	private JsonNode search(ApiClient viewer, String text)
	{
		ApiClient.Response response = viewer.post(archive + "/search", "{\"text\":\"" + text + "\"}");
		assertEquals(200, response.status(), response.body());

		return response.json();
	}

	private JsonNode lastMessage(ApiClient viewer)
	{
		JsonNode messages = viewer.get(conversation).json().path("discussions").get(0).path("messages");

		return messages.get(messages.size() - 1);
	}

	private String agreementNegotiations()
	{
		return "/api/couples/" + couple.id + "/agreement-negotiations";
	}

	private String messages()
	{
		return conversation + "/discussions/0/messages";
	}

	private String open(String message)
	{
		return messages() + "/" + message + "/open";
	}

	private String messagesBetween(String from, String to)
	{
		return archive + "/messages?from=" + from + "&to=" + to;
	}

	private String topic(String paragraph)
	{
		return archive + "/topics/" + paragraph;
	}

	private static String draft(String... paragraphs)
	{
		return "{\"paragraphs\":[" + String.join(",", paragraphs) + "]}";
	}

	private static String simple(String title, String body, String... references)
	{
		StringBuilder ids = new StringBuilder();

		for (String reference : references)
		{
			ids.append(ids.isEmpty() ? "" : ",").append('"').append(reference).append('"');
		}

		return "{\"type\":\"SIMPLE\",\"title\":\"" + title + "\",\"body\":\"" + body + "\",\"references\":[" + ids + "]}";
	}

	private static String question(String title, String body)
	{
		return "{\"type\":\"QUESTION\",\"title\":\"" + title + "\",\"body\":\"" + body + "\"}";
	}

	private static String pointed(String question, String body)
	{
		return "{\"type\":\"POINTED\",\"question\":\"" + question + "\",\"body\":\"" + body + "\"}";
	}
}
