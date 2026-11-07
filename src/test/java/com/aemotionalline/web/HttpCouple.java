package com.aemotionalline.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import com.aemotionalline.application.PasswordHasher;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.user.EmailAddress;
import com.aemotionalline.domain.user.Role;
import com.aemotionalline.domain.user.User;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.user.UserRepository;

/**
 * For the HTTP tests of what happens inside a couple: its three accounts, saved directly and logged in over HTTP (how a
 * couple is born is tested in AccountsHttpTest).
 */
final class HttpCouple
{
	static final String AGREEMENT = "Ci scriviamo con calma";

	private static final String PASSWORD = "un cavallo sul tetto blu";

	final User annaAccount;
	final User brunoAccount;
	final User therapistAccount;
	final ApiClient anna;
	final ApiClient bruno;
	final ApiClient therapist;
	final String id;

	private final int port;
	private final UserRepository users;
	private final PasswordHasher hasher;

	HttpCouple(int port, UserRepository users, CoupleRepository couples, PasswordHasher hasher)
	{
		this.port = port;
		this.users = users;
		this.hasher = hasher;
		therapistAccount = account(Role.THERAPIST);
		annaAccount = account(Role.PATIENT);
		brunoAccount = account(Role.PATIENT);
		Couple couple = new Couple(CoupleId.random(), annaAccount.getId(), brunoAccount.getId(), therapistAccount.getId());
		couples.save(couple);
		id = couple.getId().value().toString();
		therapist = logIn(therapistAccount);
		anna = logIn(annaAccount);
		bruno = logIn(brunoAccount);
	}

	/** A patient outside this couple, logged in. */
	ApiClient outsider()
	{
		return logIn(account(Role.PATIENT));
	}

	/** Anna asks, Bruno accepts: the id of the conversation they start. */
	String startConversation()
	{
		String path = "/api/negotiations/" + created(anna.post("/api/couples/" + id + "/agreement-negotiations", "{\"text\":\"" + AGREEMENT + "\"}"));
		assertEquals(204, bruno.post(path + "/accept-request", "").status());
		assertEquals(204, anna.post(path + "/send", "{\"justification\":\"Così stiamo sereni\"}").status());

		return bruno.post(path + "/accept-proposal", "").json().path("conversationId").asString();
	}

	static String created(ApiClient.Response response)
	{
		assertEquals(201, response.status(), response.body());

		return response.json().path("id").asString();
	}

	static void assertMalformed(ApiClient.Response response, String detail)
	{
		assertEquals(400, response.status(), response.body());
		assertEquals("MALFORMED_REQUEST", response.code());
		assertEquals(detail, response.json().path("detail").asString());
	}

	static String id(User user)
	{
		return user.getId().value().toString();
	}

	private ApiClient logIn(User user)
	{
		ApiClient client = new ApiClient(port);
		assertEquals(200, client.logIn(user.getEmail().value(), PASSWORD).status());

		return client;
	}

	private User account(Role role)
	{
		User user = User.pending(UserId.random(), new EmailAddress(UUID.randomUUID() + "@example.org"), role.name().toLowerCase(), role);
		user.activate(hasher.hash(PASSWORD));
		users.save(user);

		return user;
	}
}
