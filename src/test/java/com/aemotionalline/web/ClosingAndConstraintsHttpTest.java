package com.aemotionalline.web;

import static com.aemotionalline.web.HttpCouple.assertMalformed;
import static com.aemotionalline.web.HttpCouple.created;
import static com.aemotionalline.web.HttpCouple.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
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
import com.aemotionalline.domain.conversation.OutcomeDeclaration;
import com.aemotionalline.domain.conversation.TherapistClosingRequestRecord;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.user.User;
import com.aemotionalline.domain.user.UserRepository;

import tools.jackson.databind.JsonNode;

/**
 * Closing a discussion and the constraints over HTTP (CLAUDE.md, R7): both ways of closing with what each user sees, the
 * life of a constraint request, and the checks of this layer. The rules themselves are tested on the domain and the services.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({ TestcontainersConfiguration.class, TestEndpoints.class })
public class ClosingAndConstraintsHttpTest
{
	private static final String RESOLVED = "{\"outcome\":\"RESOLVED\",\"reason\":\"Abbiamo deciso\"}";
	private static final String STALLED = "{\"outcome\":\"STALLED\",\"reason\":\"Non ci siamo capiti\"}";
	private static final String JUSTIFICATION = "Bruno non vuole chiudere";

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
	private String path;
	private String closing;
	private String constraints;
	private String requests;

	@BeforeEach
	void startAConversationWithASpecificDiscussion()
	{
		couple = new HttpCouple(port, users, couples, hasher);
		anna = couple.anna;
		bruno = couple.bruno;
		therapist = couple.therapist;
		path = "/api/conversations/" + couple.startConversation();
		closing = path + "/discussions/1/closing";
		constraints = path + "/constraints";
		requests = constraints + "/requests";

		String negotiation = "/api/negotiations/" + created(anna.post(path + "/discussion-negotiations", "{\"title\":\"Le vacanze\",\"subtitle\":\"Dove andare\"}"));
		assertEquals(204, bruno.post(negotiation + "/accept-request", "").status());
		assertEquals(204, anna.post(negotiation + "/send", "{\"justification\":\"Merita un discorso a parte\"}").status());
		assertEquals(1, bruno.post(negotiation + "/accept-proposal", "").json().path("openedDiscussion").asInt());
	}

	// CLOSING

	@Test
	void shouldCloseADiscussionByAgreement()
	{
		assertEquals(204, anna.post(closing + "/request", "").status());

		// The request, as a view: who, when, how it stands, and nothing of the domain's internals.
		JsonNode request = bruno.get(closing).json().path("requests").get(0);
		assertEquals(id(couple.annaAccount), request.path("requester").asString());
		assertEquals("PENDING", request.path("status").asString());
		assertTrue(request.path("sentAt").isString());
		assertTrue(request.path("endedAt").isNull());
		assertEquals(4, request.size(), request.toString());
		assertFalse(bruno.get(closing).json().path("closed").asBoolean());
		assertEquals("CLOSING_REQUEST_PENDING", bruno.post(closing + "/request", "").code());

		// Bruno accepts: it closes at once, and takes no more messages.
		assertEquals(204, bruno.post(closing + "/accept", "").status());
		JsonNode closed = therapist.get(closing).json();
		assertTrue(closed.path("closed").asBoolean());
		assertEquals("AGREEMENT", closed.path("way").asString());
		assertTrue(closed.path("closedAt").isString());
		assertEquals("ACCEPTED", closed.path("requests").get(0).path("status").asString());
		assertTrue(closed.path("requests").get(0).path("endedAt").isString());
		assertTrue(anna.get(path).json().path("discussions").get(1).path("closed").asBoolean());
		assertEquals("DISCUSSION_CLOSED", anna.post(path + "/discussions/1/messages",
				"{\"paragraphs\":[{\"type\":\"SIMPLE\",\"title\":\"Titolo\",\"body\":\"Testo\"}]}").code());

		// Anna declares: Bruno knows she did, not what she declared, until he has declared too. The therapist sees it at once.
		assertEquals(204, anna.post(closing + "/outcome", RESOLVED).status());
		JsonNode hidden = bruno.get(closing).json().path("declarations").get(0);
		assertEquals(id(couple.annaAccount), hidden.path("partner").asString());
		assertTrue(hidden.path("declaredAt").isString());
		assertTrue(hidden.path("outcome").isNull());
		assertTrue(hidden.path("reason").isNull());
		assertEquals("RESOLVED", anna.get(closing).json().path("declarations").get(0).path("outcome").asString());
		assertEquals("Abbiamo deciso", therapist.get(closing).json().path("declarations").get(0).path("reason").asString());

		assertEquals(204, bruno.post(closing + "/outcome", STALLED).status());
		JsonNode declarations = bruno.get(closing).json().path("declarations");
		assertEquals("RESOLVED", declarations.get(0).path("outcome").asString());
		assertEquals("Abbiamo deciso", declarations.get(0).path("reason").asString());
		assertEquals("STALLED", anna.get(closing).json().path("declarations").get(1).path("outcome").asString());
		assertEquals("OUTCOME_ALREADY_DECLARED", anna.post(closing + "/outcome", STALLED).code());
	}

	@Test
	void shouldCloseADiscussionThroughTheTherapist()
	{
		String request = "{\"justification\":\"" + JUSTIFICATION + "\"}";
		assertEquals("THERAPIST_REQUEST_NOT_ALLOWED_YET", anna.post(closing + "/therapist-request", request).code());

		// Two failed attempts: one refused, one withdrawn.
		assertEquals(204, anna.post(closing + "/request", "").status());
		assertEquals(204, bruno.post(closing + "/refuse", "").status());
		assertEquals(204, anna.post(closing + "/request", "").status());
		assertEquals(204, anna.post(closing + "/withdraw", "").status());
		JsonNode attempts = anna.get(closing).json().path("requests");
		assertEquals("REFUSED", attempts.get(0).path("status").asString());
		assertEquals("WITHDRAWN", attempts.get(1).path("status").asString());
		assertTrue(attempts.get(1).path("endedAt").isString());

		// The justification is for the requester and the therapist, who also sees the attempts with their instants.
		assertEquals(204, anna.post(closing + "/therapist-request", request).status());
		JsonNode seenByTherapist = therapist.get(closing).json().path("therapistRequests").get(0);
		assertEquals(JUSTIFICATION, seenByTherapist.path("justification").asString());
		assertEquals("PENDING", seenByTherapist.path("status").asString());
		assertEquals(2, seenByTherapist.path("previousRequests").size());
		assertTrue(seenByTherapist.path("previousRequests").get(1).path("endedAt").isString());
		assertEquals(JUSTIFICATION, anna.get(closing).json().path("therapistRequests").get(0).path("justification").asString());
		JsonNode seenByBruno = bruno.get(closing).json().path("therapistRequests").get(0);
		assertEquals(id(couple.annaAccount), seenByBruno.path("requester").asString());
		assertTrue(seenByBruno.path("justification").isNull());

		// While it waits, no other closing can start, and only the therapist decides.
		assertEquals("CLOSING_REQUEST_PENDING", bruno.post(closing + "/request", "").code());
		assertEquals("NOT_THE_THERAPIST", anna.post(closing + "/therapist-request/confirm", "").code());

		// A refusal leaves it open, and asking again needs a new attempt.
		assertEquals(204, therapist.post(closing + "/therapist-request/refuse", "").status());
		JsonNode refused = anna.get(closing).json();
		assertFalse(refused.path("closed").asBoolean());
		assertEquals("REFUSED", refused.path("therapistRequests").get(0).path("status").asString());
		assertTrue(refused.path("therapistRequests").get(0).path("decidedAt").isString());
		assertEquals("THERAPIST_REQUEST_NOT_ALLOWED_YET", anna.post(closing + "/therapist-request", request).code());

		assertEquals(204, anna.post(closing + "/request", "").status());
		assertEquals(204, bruno.post(closing + "/refuse", "").status());
		assertEquals(204, anna.post(closing + "/therapist-request", request).status());
		assertEquals(204, therapist.post(closing + "/therapist-request/confirm", "").status());

		JsonNode closed = bruno.get(closing).json();
		assertTrue(closed.path("closed").asBoolean());
		assertEquals("THERAPIST", closed.path("way").asString());
		assertEquals(204, bruno.post(closing + "/outcome", STALLED).status());
	}

	@Test
	void shouldCheckWhatAClosingIsAskedFor()
	{
		// What can't be asked yet, or by this user.
		assertEquals("RULE_VIOLATED", anna.post(path + "/discussions/0/closing/request", "").code());
		assertEquals("NOT_A_PARTNER", therapist.post(closing + "/request", "").code());
		assertEquals("NO_PENDING_CLOSING_REQUEST", bruno.post(closing + "/accept", "").code());
		assertEquals("NO_PENDING_CLOSING_REQUEST", anna.post(closing + "/withdraw", "").code());
		assertEquals("NO_PENDING_CLOSING_REQUEST", therapist.post(closing + "/therapist-request/confirm", "").code());
		assertEquals("DISCUSSION_NOT_CLOSED", anna.post(closing + "/outcome", RESOLVED).code());

		// The justification.
		assertEquals(204, anna.post(closing + "/request", "").status());
		assertEquals(204, bruno.post(closing + "/refuse", "").status());
		assertMalformed(anna.post(closing + "/therapist-request", "{}"), "The field 'justification' is required.");
		assertEquals("TEXT_REQUIRED", anna.post(closing + "/therapist-request", "{\"justification\":\"  \"}").code());
		assertEquals("TEXT_TOO_LONG", anna.post(closing + "/therapist-request",
				"{\"justification\":\"" + "j".repeat(TherapistClosingRequestRecord.MAX_JUSTIFICATION_LENGTH + 1) + "\"}").code());
		assertTrue(anna.get(closing).json().path("therapistRequests").isEmpty());

		// The declaration.
		assertEquals(204, anna.post(closing + "/request", "").status());
		assertEquals(204, bruno.post(closing + "/accept", "").status());
		assertMalformed(anna.post(closing + "/outcome", "{}"), "The field 'outcome' is required.");
		assertMalformed(anna.post(closing + "/outcome", "{\"outcome\":\"RESOLVED\"}"), "The field 'reason' is required.");
		assertMalformed(anna.post(closing + "/outcome", "{\"outcome\":\"HAPPY\",\"reason\":\"Testo\"}"), "Missing or malformed value of field 'outcome'.");
		assertEquals("TEXT_REQUIRED", anna.post(closing + "/outcome", "{\"outcome\":\"RESOLVED\",\"reason\":\" \"}").code());
		assertEquals("TEXT_TOO_LONG", anna.post(closing + "/outcome",
				"{\"outcome\":\"RESOLVED\",\"reason\":\"" + "r".repeat(OutcomeDeclaration.MAX_REASON_LENGTH + 1) + "\"}").code());
		assertTrue(anna.get(closing).json().path("declarations").isEmpty());

		// What doesn't exist, and an outsider.
		assertEquals(404, anna.get(path + "/discussions/99/closing").status());
		assertEquals(404, anna.post(path + "/discussions/-1/closing/request", "").status());
		ApiClient outsider = couple.outsider();
		assertEquals(404, outsider.get(closing).status());
		assertEquals(404, outsider.post(closing + "/request", "").status());
	}

	// CONSTRAINTS

	@Test
	void shouldApplyTheConstraintsOnceBothPartnersApprove()
	{
		String request = created(therapist.post(requests, proposal(time(couple.annaAccount, "18:00"), delay(couple.brunoAccount, "PT1H"), location(couple.brunoAccount))));

		// Every member sees what is proposed, with each type's default scope; nothing is in force yet.
		JsonNode pending = bruno.get(constraints).json();
		assertTrue(pending.path("inForce").isEmpty());
		JsonNode proposed = pending.path("requests").get(0);
		assertEquals(request, proposed.path("id").asString());
		assertEquals(id(couple.therapistAccount), proposed.path("proposedBy").asString());
		assertEquals("PENDING", proposed.path("status").asString());
		assertTrue(proposed.path("approvals").isEmpty());
		assertTrue(proposed.path("proposedAt").isString());
		assertTrue(proposed.path("closedAt").isNull());
		assertTrue(proposed.path("previousAssignments").isNull());

		JsonNode time = proposed.path("assignments").get(0);
		assertEquals(id(couple.annaAccount), time.path("user").asString());
		assertEquals("TIME", time.path("type").asString());
		assertEquals("WRITE", time.path("scope").asString());
		assertEquals(LocalTime.of(18, 0), LocalTime.parse(time.path("notBefore").asString()));
		assertTrue(time.path("delay").isNull());
		JsonNode delay = proposed.path("assignments").get(1);
		assertEquals("REPLY_DELAY", delay.path("type").asString());
		assertEquals("WRITE", delay.path("scope").asString());
		assertEquals("PT1H", delay.path("delay").asString());
		JsonNode location = proposed.path("assignments").get(2);
		assertEquals("LOCATION", location.path("type").asString());
		assertEquals("READ", location.path("scope").asString());
		assertEquals(45.4642, location.path("latitude").asDouble());
		assertEquals(200.0, location.path("radiusMetres").asDouble());
		assertEquals("WITHIN", location.path("direction").asString());

		// The place is Bruno's: Anna sees the radius and the direction, never the centre, not even in the description.
		JsonNode seenByAnna = anna.get(constraints).json().path("requests").get(0).path("assignments").get(2);
		assertTrue(seenByAnna.path("latitude").isNull());
		assertTrue(seenByAnna.path("longitude").isNull());
		assertEquals(200.0, seenByAnna.path("radiusMetres").asDouble());
		assertEquals("Allowed within 200.0 m of the agreed place", seenByAnna.path("description").asString());
		assertEquals(9.19, therapist.get(constraints).json().path("requests").get(0).path("assignments").get(2).path("longitude").asDouble());

		// The second approval applies them at once.
		assertEquals(204, anna.post(requests + "/" + request + "/approve", "").status());
		JsonNode halfApproved = therapist.get(constraints).json();
		assertEquals(id(couple.annaAccount), halfApproved.path("requests").get(0).path("approvals").get(0).asString());
		assertTrue(halfApproved.path("inForce").isEmpty());
		assertEquals(204, bruno.post(requests + "/" + request + "/approve", "").status());

		JsonNode applied = therapist.get(constraints).json();
		assertEquals(3, applied.path("inForce").size());
		assertEquals("APPLIED", applied.path("requests").get(0).path("status").asString());
		assertTrue(applied.path("requests").get(0).path("closedAt").isString());
		assertTrue(applied.path("requests").get(0).path("previousAssignments").isEmpty());

		// They bind: Anna can't write before six in the evening.
		String message = "\"paragraphs\":[{\"type\":\"SIMPLE\",\"title\":\"Titolo\",\"body\":\"Testo\"}]";
		assertEquals("CONSTRAINT_NOT_SATISFIED", anna.post(path + "/discussions/0/messages", "{" + message + ",\"localTime\":\"17:00\"}").code());
		assertEquals(201, anna.post(path + "/discussions/0/messages", "{" + message + ",\"localTime\":\"18:00\"}").status());

		// An empty proposal lifts them all, and the request remembers what it replaced.
		String lifting = created(therapist.post(requests, proposal()));
		assertEquals(204, anna.post(requests + "/" + lifting + "/approve", "").status());
		assertEquals(204, bruno.post(requests + "/" + lifting + "/approve", "").status());
		JsonNode lifted = anna.get(constraints).json();
		assertTrue(lifted.path("inForce").isEmpty());
		assertEquals(3, lifted.path("requests").get(1).path("previousAssignments").size());
	}

	@Test
	void shouldKeepTheRequestsThatDidNotApply()
	{
		String rejected = created(therapist.post(requests, proposal(time(couple.annaAccount, "09:00"))));
		assertEquals("CONSTRAINT_REQUEST_PENDING", therapist.post(requests, proposal(time(couple.brunoAccount, "09:00"))).code());
		assertEquals(204, bruno.post(requests + "/" + rejected + "/reject", "").status());
		assertEquals("CONSTRAINT_REQUEST_CLOSED", anna.post(requests + "/" + rejected + "/approve", "").code());

		String withdrawn = created(therapist.post(requests, proposal(time(couple.brunoAccount, "09:00"))));
		assertEquals(204, therapist.post(requests + "/" + withdrawn + "/withdraw", "").status());

		JsonNode view = anna.get(constraints).json();
		assertTrue(view.path("inForce").isEmpty());
		assertEquals("REJECTED", view.path("requests").get(0).path("status").asString());
		assertEquals(id(couple.brunoAccount), view.path("requests").get(0).path("rejectedBy").asString());
		assertEquals("WITHDRAWN", view.path("requests").get(1).path("status").asString());
		assertTrue(view.path("requests").get(1).path("closedAt").isString());
	}

	@Test
	void shouldCheckTheShapeOfAConstraint()
	{
		String assignee = "\"assignee\":\"" + id(couple.annaAccount) + "\"";

		assertMalformed(therapist.post(requests, "{}"), "The field 'constraints' is required.");
		assertMalformed(therapist.post(requests, "{\"constraints\":[null]}"), "Every constraint needs a type and a partner it is assigned to.");
		assertMalformed(therapist.post(requests, "{\"constraints\":[{\"type\":\"TIME\",\"notBefore\":\"09:00\"}]}"), "Every constraint needs a type and a partner it is assigned to.");
		assertMalformed(therapist.post(requests, "{\"constraints\":[{" + assignee + "}]}"), "Every constraint needs a type and a partner it is assigned to.");
		assertMalformed(therapist.post(requests, "{\"constraints\":[{" + assignee + ",\"type\":\"MOOD\"}]}"), "Missing or malformed value of field 'constraints[0].type'.");
		assertMalformed(therapist.post(requests, "{\"constraints\":[{" + assignee + ",\"type\":\"TIME\"}]}"), "The constraint's notBefore is required.");
		assertMalformed(therapist.post(requests, "{\"constraints\":[{" + assignee + ",\"type\":\"REPLY_DELAY\"}]}"), "The constraint's delay is required.");
		assertMalformed(therapist.post(requests, "{\"constraints\":[{" + assignee + ",\"type\":\"LOCATION\",\"latitude\":45.0}]}"), "The constraint's longitude is required.");
		assertMalformed(therapist.post(requests, "{\"constraints\":[{" + assignee + ",\"type\":\"REPLY_DELAY\",\"scope\":\"READ\",\"delay\":\"PT1H\"}]}"), "A reply delay always restricts writing.");

		// A parameter the type doesn't have, which would be lost.
		String foreign = "A constraint takes only the parameters of its type.";
		assertMalformed(therapist.post(requests, "{\"constraints\":[{" + assignee + ",\"type\":\"TIME\",\"notBefore\":\"09:00\",\"delay\":\"PT1H\"}]}"), foreign);
		assertMalformed(therapist.post(requests, "{\"constraints\":[{" + assignee + ",\"type\":\"REPLY_DELAY\",\"delay\":\"PT1H\",\"radiusMetres\":200}]}"), foreign);
		assertMalformed(therapist.post(requests, "{\"constraints\":[{" + assignee + ",\"type\":\"LOCATION\",\"notBefore\":\"09:00\",\"latitude\":45.0,\"longitude\":9.0,\"radiusMetres\":200,\"direction\":\"WITHIN\"}]}"), foreign);

		// Values of the wrong format, named by the field.
		assertMalformed(therapist.post(requests, "{\"constraints\":[{" + assignee + ",\"type\":\"REPLY_DELAY\",\"delay\":\"soon\"}]}"), "Missing or malformed value of field 'constraints[0].delay'.");
		assertMalformed(therapist.post(requests, "{\"constraints\":[{" + assignee + ",\"type\":\"TIME\",\"notBefore\":\"late\"}]}"), "Missing or malformed value of field 'constraints[0].notBefore'.");
		assertMalformed(therapist.post(requests, "{\"constraints\":[{" + assignee + ",\"type\":\"TIME\",\"scope\":\"NEVER\",\"notBefore\":\"09:00\"}]}"), "Missing or malformed value of field 'constraints[0].scope'.");
		assertMalformed(therapist.post(requests, "{\"constraints\":[{\"assignee\":\"someone\",\"type\":\"TIME\",\"notBefore\":\"09:00\"}]}"), "Missing or malformed value of field 'constraints[0].assignee'.");

		// Values the domain refuses. An enormous delay is one of them: it must not reach the database.
		assertEquals("INVALID_CONSTRAINT", therapist.post(requests, proposal(delay(couple.annaAccount, "-PT1H"))).code());
		assertEquals("INVALID_CONSTRAINT", therapist.post(requests, proposal(delay(couple.annaAccount, "P30DT1S"))).code());
		assertEquals("INVALID_CONSTRAINT", therapist.post(requests, proposal(delay(couple.annaAccount, "PT2562047788015215H"))).code());
		assertEquals("INVALID_CONSTRAINT", therapist.post(requests, "{\"constraints\":[{" + assignee + ",\"type\":\"LOCATION\",\"latitude\":45.0,\"longitude\":9.0,\"radiusMetres\":0,\"direction\":\"WITHIN\"}]}").code());
		assertEquals("RULE_VIOLATED", therapist.post(requests, proposal(time(couple.therapistAccount, "09:00"))).code());
		assertTrue(therapist.get(constraints).json().path("requests").isEmpty());

		// The longest delay is accepted, and survives the database.
		created(therapist.post(requests, proposal(delay(couple.annaAccount, "P30D"))));
		assertEquals("PT720H", anna.get(constraints).json().path("requests").get(0).path("assignments").get(0).path("delay").asString());
	}

	@Test
	void shouldLetEachUserDoOnlyTheirPartOfARequest()
	{
		String valid = proposal(time(couple.annaAccount, "09:00"));
		assertEquals("NOT_THE_THERAPIST", anna.post(requests, valid).code());

		String request = created(therapist.post(requests, valid));
		assertEquals("NOT_A_PARTNER", therapist.post(requests + "/" + request + "/approve", "").code());
		assertEquals("NOT_A_PARTNER", therapist.post(requests + "/" + request + "/reject", "").code());
		assertEquals("NOT_THE_THERAPIST", anna.post(requests + "/" + request + "/withdraw", "").code());
		assertEquals(404, anna.post(requests + "/" + UUID.randomUUID() + "/approve", "").status());
		assertEquals("MALFORMED_REQUEST", anna.post(requests + "/not-a-uuid/approve", "").code());

		// An outsider gets "not found", whatever they send.
		ApiClient outsider = couple.outsider();
		assertEquals(404, outsider.get(constraints).status());
		assertEquals(404, outsider.post(requests, valid).status());
		assertEquals(404, outsider.post(requests, "{\"constraints\":[{\"assignee\":\"" + id(couple.annaAccount) + "\",\"type\":\"TIME\"}]}").status());
		assertEquals(404, outsider.post(requests + "/" + request + "/approve", "").status());
	}

	private static String proposal(String... constraints)
	{
		return "{\"constraints\":[" + String.join(",", constraints) + "]}";
	}

	private static String time(User user, String notBefore)
	{
		return "{\"assignee\":\"" + id(user) + "\",\"type\":\"TIME\",\"notBefore\":\"" + notBefore + "\"}";
	}

	private static String delay(User user, String delay)
	{
		return "{\"assignee\":\"" + id(user) + "\",\"type\":\"REPLY_DELAY\",\"delay\":\"" + delay + "\"}";
	}

	private static String location(User user)
	{
		return "{\"assignee\":\"" + id(user) + "\",\"type\":\"LOCATION\",\"latitude\":45.4642,\"longitude\":9.19,\"radiusMetres\":200.0,\"direction\":\"WITHIN\"}";
	}
}
