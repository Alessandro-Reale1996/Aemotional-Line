package com.aemotionalline.infrastructure.persistence.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.domain.user.AccountStatus;
import com.aemotionalline.domain.user.EmailAddress;
import com.aemotionalline.domain.user.PasswordHash;
import com.aemotionalline.domain.user.Role;
import com.aemotionalline.domain.user.User;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.user.UserRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class JdbcUserRepositoryTest
{
	private static final Instant NOW = Instant.parse("2026-11-02T10:00:00.123456Z");

	@Autowired
	private UserRepository users;

	private static EmailAddress freshEmail()
	{
		return new EmailAddress(UserId.random().value() + "@example.org");
	}

	@Test
	void shouldSaveAndFindAPendingAccountByIdAndEmail()
	{
		User user = User.pending(UserId.random(), freshEmail(), "Anna Rossi", Role.PATIENT);

		users.save(user);

		User byId = users.findById(user.getId()).orElseThrow();
		assertEquals(user.getEmail(), byId.getEmail());
		assertEquals("Anna Rossi", byId.getDisplayName());
		assertEquals(Role.PATIENT, byId.getRole());
		assertEquals(AccountStatus.PENDING, byId.getStatus());
		assertTrue(byId.getPasswordHash().isEmpty());
		assertEquals(user.getId(), users.findByEmail(new EmailAddress(user.getEmail().value().toUpperCase())).orElseThrow().getId());
	}

	@Test
	void shouldSaveTheStateChangesOfAnAccount()
	{
		User user = User.pending(UserId.random(), freshEmail(), "Marco", Role.THERAPIST);
		users.save(user);

		User loaded = users.findById(user.getId()).orElseThrow();
		loaded.activate(new PasswordHash("{bcrypt}$2a$10$hash"));
		for (int failure = 0; failure < User.MAX_FAILED_LOGINS; failure++)
		{
			loaded.recordFailedLogin(NOW);
		}
		users.save(loaded);

		User reloaded = users.findById(user.getId()).orElseThrow();
		assertEquals(AccountStatus.ACTIVE, reloaded.getStatus());
		assertEquals(Optional.of(new PasswordHash("{bcrypt}$2a$10$hash")), reloaded.getPasswordHash());
		assertEquals(Optional.of(NOW.plus(User.LOCK_DURATION)), reloaded.getLockedUntil());
		assertTrue(reloaded.isLockedAt(NOW));
	}

	@Test
	void shouldRefuseASecondAccountWithTheSameEmail()
	{
		EmailAddress email = freshEmail();
		User first = User.pending(UserId.random(), email, "First", Role.PATIENT);
		users.save(first);

		User second = User.pending(UserId.random(), email, "Second", Role.THERAPIST);
		second.activate(new PasswordHash("{bcrypt}$2a$10$intruder"));

		assertThrows(DataIntegrityViolationException.class, () -> users.save(second));

		// The first account is untouched: an upsert on the unique email would have overwritten its status and password.
		User kept = users.findByEmail(email).orElseThrow();
		assertEquals(first.getId(), kept.getId());
		assertEquals(AccountStatus.PENDING, kept.getStatus());
		assertTrue(kept.getPasswordHash().isEmpty());
	}

	@Test
	void shouldFindNothingForUnknownIdsOrEmails()
	{
		assertTrue(users.findById(UserId.random()).isEmpty());
		assertTrue(users.findByEmail(freshEmail()).isEmpty());
	}
}
