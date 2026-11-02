package com.aemotionalline.domain.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;

public class UserTest
{
	private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
	private static final PasswordHash HASH = new PasswordHash("{bcrypt}$2a$10$abc");

	private static User pendingPatient()
	{
		return User.pending(UserId.random(), new EmailAddress("Anna@Example.org"), "Anna", Role.PATIENT);
	}

	private static User activePatient()
	{
		User user = pendingPatient();
		user.activate(HASH);
		return user;
	}

	@Test
	void shouldBeBornPendingAndLogInOnlyOnceActivated()
	{
		User user = pendingPatient();
		assertEquals(AccountStatus.PENDING, user.getStatus());
		assertFalse(user.canLogInAt(NOW));
		assertTrue(user.getPasswordHash().isEmpty());

		user.activate(HASH);

		assertEquals(AccountStatus.ACTIVE, user.getStatus());
		assertTrue(user.canLogInAt(NOW));
		assertEquals(Optional.of(HASH), user.getPasswordHash());
		assertThrows(DomainException.class, () -> user.activate(HASH));
	}

	@Test
	void shouldNormaliseAndCheckTheEmail()
	{
		assertEquals("anna@example.org", pendingPatient().getEmail().value());
		assertThrows(DomainException.class, () -> new EmailAddress("no-at-sign"));
		assertThrows(DomainException.class, () -> new EmailAddress("@example.org"));
		assertThrows(DomainException.class, () -> new EmailAddress("a@b@c"));
		assertThrows(DomainException.class, () -> new EmailAddress("anna@"));
		assertThrows(DomainException.class, () -> new EmailAddress("an na@example.org"));
		assertThrows(DomainException.class, () -> new EmailAddress(" "));
	}

	@Test
	void shouldRequireADisplayNameAndKeepTheHashOutOfToString()
	{
		assertThrows(DomainException.class, () -> User.pending(UserId.random(), new EmailAddress("a@b.c"), " ", Role.PATIENT));
		assertThrows(DomainException.class, () -> User.pending(UserId.random(), new EmailAddress("a@b.c"), "x".repeat(101), Role.PATIENT));
		assertFalse(HASH.toString().contains("bcrypt"));
	}

	@Test
	void shouldLockForFifteenMinutesAfterFiveFailures()
	{
		User user = activePatient();

		for (int failure = 1; failure < User.MAX_FAILED_LOGINS; failure++)
		{
			user.recordFailedLogin(NOW);
			assertTrue(user.canLogInAt(NOW));
		}

		user.recordFailedLogin(NOW);

		assertTrue(user.isLockedAt(NOW));
		assertFalse(user.canLogInAt(NOW.plus(Duration.ofMinutes(14))));
		assertTrue(user.canLogInAt(NOW.plus(Duration.ofMinutes(15))));
		// The count starts again after a lock.
		assertEquals(0, user.getFailedLogins());
	}

	@Test
	void shouldResetTheFailuresOnASuccessfulLogin()
	{
		User user = activePatient();
		user.recordFailedLogin(NOW);
		user.recordFailedLogin(NOW);

		user.recordSuccessfulLogin();

		assertEquals(0, user.getFailedLogins());
		assertTrue(user.getLockedUntil().isEmpty());
	}

	@Test
	void shouldChangeThePasswordOnlyWhenActiveAndLiftALock()
	{
		assertThrows(DomainException.class, () -> pendingPatient().changePassword(HASH));

		User user = activePatient();
		for (int failure = 0; failure < User.MAX_FAILED_LOGINS; failure++)
		{
			user.recordFailedLogin(NOW);
		}

		PasswordHash newHash = new PasswordHash("{bcrypt}$2a$10$new");
		user.changePassword(newHash);

		assertEquals(Optional.of(newHash), user.getPasswordHash());
		assertTrue(user.canLogInAt(NOW));
	}

	@Test
	void shouldStayDisabledForGood()
	{
		User user = activePatient();

		user.disable();

		assertFalse(user.canLogInAt(NOW));
		assertThrows(DomainException.class, user::disable);
		assertThrows(DomainException.class, () -> user.changePassword(HASH));
		assertThrows(DomainException.class, () -> user.activate(HASH));
	}
}
