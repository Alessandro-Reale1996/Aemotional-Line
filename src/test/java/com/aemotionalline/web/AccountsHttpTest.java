package com.aemotionalline.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.aemotionalline.CapturingMailer;
import com.aemotionalline.CapturingMailer.Kind;
import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.application.PasswordHasher;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.user.EmailAddress;
import com.aemotionalline.domain.user.Role;
import com.aemotionalline.domain.user.User;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.user.UserRepository;

import tools.jackson.databind.JsonNode;

/**
 * Accounts, administration, invitations and couples over HTTP (CLAUDE.md, R4): the whole life of a couple, from the
 * administrator creating the therapist to the deletion of the couple's data, and the main refusals.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({ TestcontainersConfiguration.class, TestEndpoints.class })
public class AccountsHttpTest
{
	private static final String PASSWORD = "un cavallo sul tetto blu";

	@LocalServerPort
	private int port;

	@Autowired
	private UserRepository users;

	@Autowired
	private PasswordHasher hasher;

	@Autowired
	private CapturingMailer mailer;

	@Autowired
	private CoupleRepository couples;

	@Test
	void shouldCarryACoupleFromTheInvitationToTheDeletionOfItsData()
	{
		ApiClient administrator = loggedIn(Role.ADMINISTRATOR);

		// The administrator creates the therapist, who activates the account.
		EmailAddress therapistEmail = freshEmail();
		ApiClient.Response created = administrator.post("/api/admin/therapists",
				"{\"email\":\"" + therapistEmail.value() + "\",\"displayName\":\"Dott.ssa Rossi\"}");
		assertEquals(201, created.status());
		String therapistId = created.json().path("id").asString();
		assertTrue(statusOf(administrator, therapistId).equals("PENDING"));
		ApiClient therapist = activateAndLogIn(therapistEmail);

		// The therapist invites two new patients, who activate their accounts and accept.
		EmailAddress emailA = freshEmail();
		EmailAddress emailB = freshEmail();
		ApiClient.Response invited = therapist.post("/api/invitations",
				"{\"emailOne\":\"" + emailA.value() + "\",\"nameOne\":\"Anna\",\"emailTwo\":\"" + emailB.value() + "\",\"nameTwo\":\"Bruno\"}");
		assertEquals(201, invited.status());
		String invitation = invited.json().path("id").asString();
		ApiClient a = activateAndLogIn(emailA);
		ApiClient b = activateAndLogIn(emailB);
		assertEquals(invitation, a.get("/api/invitations").json().get(0).path("id").asString());
		assertEquals(204, a.post("/api/invitations/" + invitation + "/accept", "").status());
		assertEquals(204, b.post("/api/invitations/" + invitation + "/accept", "").status());
		assertEquals("ACCEPTED", therapist.get("/api/invitations/" + invitation).json().path("status").asString());

		JsonNode couple = a.get("/api/me/couples").json().get(0);
		String coupleId = couple.path("id").asString();
		assertTrue(couple.path("active").asBoolean());
		assertEquals("Dott.ssa Rossi", couple.path("therapist").path("displayName").asString());

		// The therapist ends the couple: the password confirms it.
		ApiClient.Response wrong = therapist.post("/api/couples/" + coupleId + "/end", "{\"password\":\"not the password\"}");
		assertEquals(400, wrong.status());
		assertEquals("INCORRECT_PASSWORD", wrong.code());
		assertEquals(204, therapist.post("/api/couples/" + coupleId + "/end", "{\"password\":\"" + PASSWORD + "\"}").status());
		assertFalse(a.get("/api/me/couples").json().get(0).path("active").asBoolean());

		// Then deletes its data.
		assertEquals(204, therapist.post("/api/couples/" + coupleId + "/data-deletion", "{\"password\":\"" + PASSWORD + "\"}").status());
		assertTrue(a.get("/api/me/couples").json().isEmpty());

		// B deletes their own account: this session ends with the answer, and the account can't log in any more.
		ApiClient.Response deleted = b.post("/api/me/account-deletion", "{\"password\":\"" + PASSWORD + "\"}");
		assertEquals(204, deleted.status());
		assertNull(b.cookie(ApiClient.SESSION));
		assertEquals(401, new ApiClient(port).logIn(emailB.value(), PASSWORD).status());

		// The administrator disables the therapist, whose session ends at once.
		assertEquals(204, administrator.post("/api/admin/therapists/" + therapistId + "/disable", "{\"password\":\"" + PASSWORD + "\"}").status());
		assertEquals("DISABLED", statusOf(administrator, therapistId));
		assertEquals(401, therapist.get("/api/auth/me").status());
	}

	@Test
	void shouldRefuseWhatEachRoleMayNotDo()
	{
		ApiClient administrator = loggedIn(Role.ADMINISTRATOR);
		ApiClient patient = loggedIn(Role.PATIENT);
		ApiClient therapist = loggedIn(Role.THERAPIST);

		ApiClient.Response noName = administrator.post("/api/admin/therapists", "{\"email\":\"" + freshEmail().value() + "\"}");
		assertEquals(400, noName.status());
		assertEquals("MALFORMED_REQUEST", noName.code());
		assertEquals("The field 'displayName' is required.", noName.json().path("detail").asString());
		assertEquals("INVALID_EMAIL", administrator.post("/api/admin/therapists", "{\"email\":\"not an email\",\"displayName\":\"X\"}").code());
		assertEquals("MALFORMED_REQUEST", administrator.post("/api/admin/therapists/" + UUID.randomUUID() + "/disable", "{}").code());

		// Only a therapist invites; only an administrator manages therapists.
		String invitation = "{\"emailOne\":\"" + freshEmail().value() + "\",\"nameOne\":\"A\",\"emailTwo\":\"" + freshEmail().value()
				+ "\",\"nameTwo\":\"B\"}";
		assertEquals(404, patient.post("/api/invitations", invitation).status());
		assertEquals(403, therapist.get("/api/admin/therapists").status());

		// A therapist withdraws an invitation; an outsider doesn't see it.
		String id = therapist.post("/api/invitations", invitation).json().path("id").asString();
		assertEquals(404, patient.get("/api/invitations/" + id).status());
		assertEquals(204, therapist.post("/api/invitations/" + id + "/withdraw", "").status());
		assertEquals("WITHDRAWN", therapist.get("/api/invitations/" + id).json().path("status").asString());
	}

	@Test
	void shouldLetTheAdministratorFindAndDeleteADisabledTherapistsEndedCouples()
	{
		User administratorAccount = account(Role.ADMINISTRATOR);
		ApiClient administrator = logIn(administratorAccount);
		User therapist = account(Role.THERAPIST);
		Couple couple = new Couple(CoupleId.random(), account(Role.PATIENT).getId(), account(Role.PATIENT).getId(), therapist.getId());
		couples.save(couple);
		String endedCouples = "/api/admin/therapists/" + therapist.getId().value() + "/ended-couples";

		assertEquals(404, administrator.get(endedCouples).status());
		assertEquals(204, administrator.post("/api/admin/therapists/" + therapist.getId().value() + "/disable", "{\"password\":\"" + PASSWORD + "\"}")
				.status());

		JsonNode ended = administrator.get(endedCouples).json();
		assertEquals(1, ended.size());
		assertEquals(couple.getId().value().toString(), ended.get(0).path("id").asString());
		assertTrue(ended.get(0).path("endedAt").isString());
		assertEquals(2, ended.get(0).size(), "nothing but the id and the end date");

		String deletion = "/api/couples/" + couple.getId().value() + "/data-deletion";
		assertEquals(204, administrator.post(deletion, "{\"password\":\"" + PASSWORD + "\"}").status());
		assertTrue(administrator.get(endedCouples).json().isEmpty());
	}

	private String statusOf(ApiClient administrator, String therapist)
	{
		for (JsonNode account : administrator.get("/api/admin/therapists").json())
		{
			if (account.path("id").asString().equals(therapist))
			{
				return account.path("status").asString();
			}
		}

		throw new AssertionError("therapist not listed");
	}

	private ApiClient activateAndLogIn(EmailAddress email)
	{
		ApiClient client = new ApiClient(port);
		client.get("/api/auth/csrf");
		String code = mailer.lastCode(email, Kind.ACTIVATION).orElseThrow();
		assertEquals(204, client.post("/api/auth/activate", "{\"code\":\"" + code + "\",\"password\":\"" + PASSWORD + "\"}").status());
		assertEquals(200, client.logIn(email.value(), PASSWORD).status());

		return client;
	}

	private ApiClient loggedIn(Role role)
	{
		return logIn(account(role));
	}

	private ApiClient logIn(User user)
	{
		ApiClient client = new ApiClient(port);
		assertEquals(200, client.logIn(user.getEmail().value(), PASSWORD).status());

		return client;
	}

	private User account(Role role)
	{
		User user = User.pending(UserId.random(), freshEmail(), role.name().toLowerCase(), role);
		user.activate(hasher.hash(PASSWORD));
		users.save(user);

		return user;
	}

	private static EmailAddress freshEmail()
	{
		return new EmailAddress(UUID.randomUUID() + "@example.org");
	}
}
