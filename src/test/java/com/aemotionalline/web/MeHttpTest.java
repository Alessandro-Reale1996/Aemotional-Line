package com.aemotionalline.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.application.PasswordHasher;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.message.SimpleParagraph;
import com.aemotionalline.domain.user.EmailAddress;
import com.aemotionalline.domain.user.Role;
import com.aemotionalline.domain.user.User;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.user.UserRepository;

import tools.jackson.databind.JsonNode;

/** /api/me and /api/limits over HTTP (CLAUDE.md, R3): the shape the frontend starts from. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({ TestcontainersConfiguration.class, TestEndpoints.class })
public class MeHttpTest
{
	private static final String PASSWORD = "un cavallo sul tetto blu";

	@LocalServerPort
	private int port;

	@Autowired
	private UserRepository users;

	@Autowired
	private CoupleRepository couples;

	@Autowired
	private PasswordHasher hasher;

	@Test
	void shouldGiveThePatientTheirCoupleAndWhatWaitsForThem()
	{
		User therapist = account(Role.THERAPIST, "Dott.ssa Rossi");
		User anna = account(Role.PATIENT, "Anna");
		User bruno = account(Role.PATIENT, "Bruno");
		Couple couple = new Couple(CoupleId.random(), anna.getId(), bruno.getId(), therapist.getId());
		couples.save(couple);
		ApiClient client = new ApiClient(port);
		client.logIn(anna.getEmail().value(), PASSWORD);

		JsonNode mine = client.get("/api/me/couples").json();
		assertEquals(1, mine.size());
		assertEquals(couple.getId().value().toString(), mine.get(0).path("id").asString());
		assertEquals("Bruno", mine.get(0).path("partnerTwo").path("displayName").asString());
		assertEquals("Dott.ssa Rossi", mine.get(0).path("therapist").path("displayName").asString());
		assertTrue(mine.get(0).path("active").asBoolean());
		assertTrue(mine.get(0).path("conversation").isNull());

		JsonNode summary = client.get("/api/me/summary").json();
		assertTrue(summary.path("pendingInvitations").isArray());
		assertEquals(couple.getId().value().toString(), summary.path("couples").get(0).path("couple").asString());
		assertTrue(summary.path("couples").get(0).path("unopenedMessages").isEmpty());
	}

	@Test
	void shouldGiveTheLimitsToAnyone()
	{
		ApiClient.Response limits = new ApiClient(port).get("/api/limits");

		assertEquals(200, limits.status());
		assertEquals(SimpleParagraph.MAX_BODY_LENGTH, limits.json().path("simpleParagraphBody").asInt());
		assertEquals(12, limits.json().path("passwordMinLength").asInt());
		assertEquals("UTF_16_CODE_UNITS", limits.json().path("textUnit").asString());
	}

	private User account(Role role, String name)
	{
		User user = User.pending(UserId.random(), new EmailAddress(UUID.randomUUID() + "@example.org"), name, role);
		user.activate(hasher.hash(PASSWORD));
		users.save(user);

		return user;
	}
}
