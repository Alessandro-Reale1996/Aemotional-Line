package com.aemotionalline.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.user.EmailAddress;
import com.aemotionalline.domain.user.PasswordHash;
import com.aemotionalline.domain.user.User;
import com.aemotionalline.domain.user.UserRepository;

/**
 * Checks an email and a password at login (CLAUDE.md, security points 4 and 6e). Every failure looks the same to the
 * caller: unknown or malformed email, wrong password, an account pending, disabled or locked. Five wrong passwords in a
 * row lock the account for 15 minutes, which is why a failure is returned rather than thrown: the count must be committed.
 * When there is no account to check, a decoy hash is compared anyway, so the time taken doesn't reveal it.
 */
@Service
public class LoginService
{
	private final UserRepository users;
	private final PasswordHasher hasher;
	private final Clock clock;
	private final PasswordHash decoy;

	LoginService(UserRepository users, PasswordHasher hasher, Clock clock)
	{
		this.users = users;
		this.hasher = hasher;
		this.clock = clock;
		this.decoy = hasher.hash(OneTimeCodes.newCode());
	}

	@Transactional
	public Optional<LoggedInUser> logIn(String email, String password)
	{
		Optional<User> account = parse(email).flatMap(users::findByEmail).flatMap(found -> users.findByIdForUpdate(found.getId()));
		Instant now = clock.instant();

		if (account.isEmpty() || !account.get().canLogInAt(now))
		{
			hasher.matches(password, decoy);
			return Optional.empty();
		}

		User user = account.get();

		if (!hasher.matches(password, user.getPasswordHash().orElseThrow()))
		{
			user.recordFailedLogin(now);
			users.save(user);
			return Optional.empty();
		}

		if (user.getFailedLogins() > 0)
		{
			user.recordSuccessfulLogin();
			users.save(user);
		}

		return Optional.of(new LoggedInUser(user.getId(), user.getRole()));
	}

	private static Optional<EmailAddress> parse(String email)
	{
		try
		{
			return Optional.of(new EmailAddress(email));
		}
		catch (DomainException malformed)
		{
			return Optional.empty();
		}
	}
}
