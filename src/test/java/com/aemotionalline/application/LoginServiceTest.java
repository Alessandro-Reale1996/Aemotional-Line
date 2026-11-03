package com.aemotionalline.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.domain.user.EmailAddress;
import com.aemotionalline.domain.user.Role;
import com.aemotionalline.domain.user.User;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.user.UserRepository;

/** The login check on MariaDB: every failure looks the same, five wrong passwords lock the account for 15 minutes. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class LoginServiceTest
{
	@Autowired
	private LoginService logins;

	@Autowired
	private UserRepository users;

	@Autowired
	private Clock clock;

	@Test
	void shouldLogInAnActiveAccountWithItsPassword()
	{
		UserId therapist = UserId.random();
		TestCouples.activeAccount(users, therapist, Role.THERAPIST);

		assertEquals(Optional.of(new LoggedInUser(therapist, Role.THERAPIST)), logins.logIn(emailOf(therapist).value(), TestCouples.PASSWORD));
		// The email is compared as the domain stores it: trimmed and lower-cased.
		assertTrue(logins.logIn("  " + emailOf(therapist).value().toUpperCase() + " ", TestCouples.PASSWORD).isPresent());
	}

	@Test
	void shouldRefuseEveryFailureTheSameWay()
	{
		UserId active = UserId.random();
		TestCouples.activeAccount(users, active, Role.PATIENT);
		UserId disabled = UserId.random();
		TestCouples.activeAccount(users, disabled, Role.THERAPIST);
		User disabling = users.findById(disabled).orElseThrow();
		disabling.disable();
		users.save(disabling);
		User pending = User.pending(UserId.random(), new EmailAddress(UserId.random().value() + "@example.org"), "Pending", Role.PATIENT);
		users.save(pending);

		assertEquals(Optional.empty(), logins.logIn(emailOf(active).value(), "not the password"));
		assertEquals(Optional.empty(), logins.logIn(emailOf(active).value(), null));
		assertEquals(Optional.empty(), logins.logIn("nobody-" + UserId.random().value() + "@example.org", TestCouples.PASSWORD));
		assertEquals(Optional.empty(), logins.logIn("not an email", TestCouples.PASSWORD));
		assertEquals(Optional.empty(), logins.logIn(null, TestCouples.PASSWORD));
		assertEquals(Optional.empty(), logins.logIn(emailOf(disabled).value(), TestCouples.PASSWORD));
		assertEquals(Optional.empty(), logins.logIn(pending.getEmail().value(), ""));
	}

	@Test
	void shouldLockTheAccountAfterFiveWrongPasswordsInARow()
	{
		UserId patient = UserId.random();
		TestCouples.activeAccount(users, patient, Role.PATIENT);
		String email = emailOf(patient).value();

		for (int attempt = 1; attempt < User.MAX_FAILED_LOGINS; attempt++)
		{
			logins.logIn(email, "wrong " + attempt);
		}

		assertEquals(User.MAX_FAILED_LOGINS - 1, users.findById(patient).orElseThrow().getFailedLogins());

		logins.logIn(email, "wrong again");

		assertTrue(users.findById(patient).orElseThrow().isLockedAt(clock.instant()));
		assertEquals(Optional.empty(), logins.logIn(email, TestCouples.PASSWORD));
	}

	@Test
	void shouldStartCountingAgainAfterASuccessfulLogin()
	{
		UserId patient = UserId.random();
		TestCouples.activeAccount(users, patient, Role.PATIENT);
		String email = emailOf(patient).value();

		logins.logIn(email, "wrong");
		logins.logIn(email, "wrong");
		logins.logIn(email, TestCouples.PASSWORD);

		assertEquals(0, users.findById(patient).orElseThrow().getFailedLogins());
	}

	private EmailAddress emailOf(UserId user)
	{
		return users.findById(user).orElseThrow().getEmail();
	}
}
