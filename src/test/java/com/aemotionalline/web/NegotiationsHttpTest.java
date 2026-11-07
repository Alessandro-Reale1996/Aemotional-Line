package com.aemotionalline.web;

import static com.aemotionalline.web.HttpCouple.assertMalformed;
import static com.aemotionalline.web.HttpCouple.created;
import static com.aemotionalline.web.HttpCouple.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.aemotionalline.domain.negotiation.AgreementText;
import com.aemotionalline.domain.negotiation.Proposal;
import com.aemotionalline.domain.user.UserRepository;

import tools.jackson.databind.JsonNode;

/**
 * The negotiations over HTTP (CLAUDE.md, R5): the whole exchange, what each end leaves behind, and the checks of this
 * layer. The rules themselves are tested on the domain and the services.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({ TestcontainersConfiguration.class, TestEndpoints.class })
public class NegotiationsHttpTest
{
	@LocalServerPort
	private int port;

	@Autowired
	private UserRepository users;

	@Autowired
	private CoupleRepository couples;

	@Autowired
	private PasswordHasher hasher;

	private HttpCouple couple;
	private ApiClient therapist;
	private ApiClient anna;
	private ApiClient bruno;

	@BeforeEach
	void createTheCouple()
	{
		couple = new HttpCouple(port, users, couples, hasher);
		therapist = couple.therapist;
		anna = couple.anna;
		bruno = couple.bruno;
	}

	@Test
	void shouldCarryANegotiationFromTheRequestToTheConversation()
	{
		// Anna asks to talk: the proposal is still her draft, and only she sees it.
		String negotiation = created(anna.post(agreementNegotiations(), "{\"text\":\"Ci scriviamo con calma\"}"));
		String path = "/api/negotiations/" + negotiation;

		JsonNode asSeenByBruno = bruno.get(path).json();
		assertEquals("STARTED_CONVERSATION", asSeenByBruno.path("purpose").asString());
		assertEquals("DRAFT", asSeenByBruno.path("status").asString());
		assertEquals(id(couple.brunoAccount), asSeenByBruno.path("currentResponder").asString());
		assertEquals("AGREEMENT", asSeenByBruno.path("request").path("kind").asString());
		assertEquals("Ci scriviamo con calma", asSeenByBruno.path("request").path("text").asString());
		assertTrue(asSeenByBruno.path("sentProposals").isEmpty());
		assertTrue(asSeenByBruno.path("draft").isNull());
		assertEquals("Ci scriviamo con calma", anna.get(path).json().path("draft").path("text").asString());

		// Bruno accepts to talk; Anna rewrites her proposal and sends it.
		assertEquals(204, bruno.post(path + "/accept-request", "").status());
		assertEquals(204, anna.put(path + "/draft", "{\"text\":\"Ci scriviamo una volta al giorno\"}").status());
		assertEquals(204, anna.post(path + "/send", "{\"justification\":\"Così non ci rincorriamo\"}").status());

		// Bruno refuses it: the answer is his counter-proposal, a draft starting from the refused text.
		ApiClient.Response refused = bruno.post(path + "/refuse-proposal", "");
		assertEquals(201, refused.status());
		JsonNode afterRefusal = bruno.get(path).json();
		assertEquals(refused.json().path("id").asString(), afterRefusal.path("draft").path("id").asString());
		assertEquals("Ci scriviamo una volta al giorno", afterRefusal.path("draft").path("text").asString());
		assertEquals("REFUSED", afterRefusal.path("sentProposals").get(0).path("status").asString());
		assertTrue(anna.get(path).json().path("draft").isNull());

		assertEquals(204, bruno.put(path + "/draft", "{\"text\":\"Ci scriviamo la sera\"}").status());
		assertEquals(204, bruno.post(path + "/send", "{\"justification\":\"Di giorno lavoro\"}").status());

		// The therapist reads the exchange, not the drafts.
		JsonNode asSeenByTherapist = therapist.get(path).json();
		assertEquals(2, asSeenByTherapist.path("sentProposals").size());
		assertEquals("Di giorno lavoro", asSeenByTherapist.path("sentProposals").get(1).path("justification").asString());
		assertTrue(asSeenByTherapist.path("draft").isNull());
		assertEquals(1, therapist.get(inProgress()).json().size());

		// Anna accepts: the conversation starts at once and the negotiation is gone.
		ApiClient.Response accepted = anna.post(path + "/accept-proposal", "");
		assertEquals(200, accepted.status());
		assertTrue(accepted.json().path("openedDiscussion").isNull());
		assertEquals(404, anna.get(path).status());
		assertTrue(anna.get(inProgress()).json().isEmpty());
		assertEquals(accepted.json().path("conversationId").asString(), anna.get("/api/me/couples").json().get(0).path("conversation").asString());
	}

	@Test
	void shouldOpenADiscussionExpandingAParagraph()
	{
		String conversation = couple.startConversation();
		String conversationPath = "/api/conversations/" + conversation;
		created(anna.post(conversationPath + "/discussions/0/messages",
				"{\"paragraphs\":[{\"type\":\"SIMPLE\",\"title\":\"Le vacanze\",\"subtitle\":\"Dove andare\",\"body\":\"Io direi al mare\"}]}"));
		String paragraph = anna.get(conversationPath).json().path("discussions").get(0).path("messages").get(0).path("paragraphs").get(0).path("id").asString();
		String requests = conversationPath + "/discussion-negotiations";
		String request = "{\"title\":\"Le vacanze\",\"subtitle\":\"Dove andare\",\"originParagraphId\":\"" + paragraph + "\"}";

		// Bruno hasn't opened the message yet: for him the paragraph doesn't exist.
		assertEquals("MALFORMED_REQUEST", bruno.post(requests, request).code());

		String negotiation = created(anna.post(requests, request));
		String path = "/api/negotiations/" + negotiation;
		JsonNode view = bruno.get(path).json();
		assertEquals("OPENED_DISCUSSION", view.path("purpose").asString());
		assertEquals(paragraph, view.path("originParagraphId").asString());
		assertEquals("DISCUSSION_TITLE", view.path("request").path("kind").asString());
		assertEquals("Dove andare", view.path("request").path("subtitle").asString());
		assertEquals("EXPANSION_ALREADY_REQUESTED", anna.post(requests, request).code());

		assertEquals(204, bruno.post(path + "/accept-request", "").status());
		assertEquals(204, anna.put(path + "/draft", "{\"title\":\"Le vacanze estive\",\"subtitle\":\"Dove andare ad agosto\"}").status());
		assertEquals(204, anna.post(path + "/send", "{\"justification\":\"È più preciso\"}").status());

		ApiClient.Response accepted = bruno.post(path + "/accept-proposal", "");
		assertEquals(200, accepted.status());
		assertEquals(conversation, accepted.json().path("conversationId").asString());
		assertEquals(1, accepted.json().path("openedDiscussion").asInt());
		assertEquals("PARAGRAPH_ALREADY_EXPANDED", anna.post(requests, request).code());

		// The origin is optional.
		String withoutOrigin = created(anna.post(requests, "{\"title\":\"I soldi\",\"subtitle\":\"Come dividerli\"}"));
		assertTrue(anna.get("/api/negotiations/" + withoutOrigin).json().path("originParagraphId").isNull());
	}

	@Test
	void shouldKeepWhatAFailedNegotiationLeavesBehind()
	{
		// A failed attempt to start the conversation stays readable at couple level...
		String refused = created(anna.post(agreementNegotiations(), "{\"text\":\"Ci scriviamo ogni ora\"}"));
		assertEquals(204, bruno.post("/api/negotiations/" + refused + "/refuse-request", "").status());
		JsonNode failedStart = therapist.get(failedStarts()).json().get(0);
		assertEquals(refused, failedStart.path("id").asString());
		assertEquals("REFUSED", failedStart.path("status").asString());
		assertEquals(id(couple.brunoAccount), failedStart.path("closedBy").asString());
		assertTrue(failedStart.path("closedAt").isString());
		assertEquals(200, anna.get("/api/negotiations/" + refused).status());
		assertTrue(anna.get(inProgress()).json().isEmpty());

		// ...until the conversation starts and takes it.
		couple.startConversation();
		assertTrue(anna.get(failedStarts()).json().isEmpty());
		assertEquals(404, anna.get("/api/negotiations/" + refused).status());

		// Abandoning a change of the agreement, whoever's turn it is, leaves nothing in progress.
		String change = created(anna.post(agreementNegotiations(), "{\"text\":\"Ci scriviamo la mattina\"}"));
		assertEquals("MODIFIED_AGREEMENT", anna.get("/api/negotiations/" + change).json().path("purpose").asString());
		assertEquals(204, anna.post("/api/negotiations/" + change + "/abandon", "").status());
		assertEquals(404, anna.get("/api/negotiations/" + change).status());
		assertTrue(anna.get(inProgress()).json().isEmpty());
	}

	@Test
	void shouldRefuseWhatEachUserMayNotDo()
	{
		// A therapist, a partner out of turn, a second agreement negotiation: each rule says which.
		assertEquals("NOT_A_PARTNER", therapist.post(agreementNegotiations(), "{\"text\":\"Testo\"}").code());

		String negotiation = created(anna.post(agreementNegotiations(), "{\"text\":\"Ci scriviamo con calma\"}"));
		String path = "/api/negotiations/" + negotiation;

		assertEquals("NOT_YOUR_TURN", anna.post(path + "/accept-request", "").code());
		assertEquals(409, anna.post(path + "/accept-request", "").status());
		assertEquals("AGREEMENT_NEGOTIATION_IN_PROGRESS", bruno.post(agreementNegotiations(), "{\"text\":\"Testo\"}").code());

		// An outsider sees nothing: not the couple, not its negotiations.
		ApiClient outsider = couple.outsider();
		assertEquals(404, outsider.post(agreementNegotiations(), "{\"text\":\"Testo\"}").status());
		assertEquals(404, outsider.get(inProgress()).status());
		assertEquals(404, outsider.get(failedStarts()).status());
		assertEquals(404, outsider.get(path).status());
		assertEquals(404, outsider.post(path + "/abandon", "").status());
	}

	@Test
	void shouldCheckTheShapeAndTheLengthOfTheTexts()
	{
		// The text of the request.
		assertMalformed(anna.post(agreementNegotiations(), "{}"), "The field 'text' is required.");
		assertEquals("TEXT_REQUIRED", anna.post(agreementNegotiations(), "{\"text\":\"   \"}").code());
		assertEquals("TEXT_TOO_LONG", anna.post(agreementNegotiations(), "{\"text\":\"" + "t".repeat(AgreementText.MAX_LENGTH + 1) + "\"}").code());

		// The longest text fits in the database, in characters of three bytes too.
		String longestText = "€".repeat(AgreementText.MAX_LENGTH);
		String negotiation = created(anna.post(agreementNegotiations(), "{\"text\":\"" + longestText + "\"}"));
		String path = "/api/negotiations/" + negotiation;
		assertEquals(longestText, anna.get(path).json().path("request").path("text").asString());
		assertEquals(204, bruno.post(path + "/accept-request", "").status());

		// The content of a draft: of its own kind, one kind only.
		assertMalformed(anna.put(path + "/draft", "{}"), "Send either 'text', or 'title' and 'subtitle'.");
		assertMalformed(anna.put(path + "/draft", "{\"text\":\"Testo\",\"title\":\"Titolo\"}"), "Send either 'text', or 'title' and 'subtitle'.");
		assertMalformed(anna.put(path + "/draft", "{\"title\":\"Titolo\"}"), "The field 'subtitle' is required.");
		assertEquals("RULE_VIOLATED", anna.put(path + "/draft", "{\"title\":\"Titolo\",\"subtitle\":\"Sottotitolo\"}").code());
		assertEquals("TEXT_REQUIRED", anna.put(path + "/draft", "{\"text\":\" \"}").code());

		// The justification; a refused send changes nothing.
		assertMalformed(anna.post(path + "/send", "{}"), "The field 'justification' is required.");
		assertEquals("TEXT_REQUIRED", anna.post(path + "/send", "{\"justification\":\"  \"}").code());
		assertEquals("TEXT_TOO_LONG", anna.post(path + "/send", "{\"justification\":\"" + "j".repeat(Proposal.MAX_JUSTIFICATION_LENGTH + 1) + "\"}").code());
		assertTrue(anna.get(path).json().path("sentProposals").isEmpty());

		String longestJustification = "€".repeat(Proposal.MAX_JUSTIFICATION_LENGTH);
		assertEquals(204, anna.post(path + "/send", "{\"justification\":\"" + longestJustification + "\"}").status());
		assertEquals(longestJustification, bruno.get(path).json().path("sentProposals").get(0).path("justification").asString());

		// The fields of a discussion request.
		String requests = "/api/conversations/" + UUID.randomUUID() + "/discussion-negotiations";
		assertMalformed(anna.post(requests, "{\"title\":\"Titolo\"}"), "The field 'subtitle' is required.");
		assertMalformed(anna.post(requests, "{\"title\":\"Titolo\",\"subtitle\":\"Sottotitolo\",\"originParagraphId\":\"not a uuid\"}"),
				"Missing or malformed value of field 'originParagraphId'.");
	}

	private String agreementNegotiations()
	{
		return "/api/couples/" + couple.id + "/agreement-negotiations";
	}

	private String inProgress()
	{
		return "/api/couples/" + couple.id + "/negotiations";
	}

	private String failedStarts()
	{
		return "/api/couples/" + couple.id + "/failed-starts";
	}
}
