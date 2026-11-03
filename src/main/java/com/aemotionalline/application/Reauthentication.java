package com.aemotionalline.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.aemotionalline.domain.user.AccountStatus;
import com.aemotionalline.domain.user.User;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.user.UserRepository;

/**
 * Confirms an irreversible action with the account's own password (CLAUDE.md, REST point 5): deleting an ended couple's
 * data, deleting one's own account, ending a couple, disabling a therapist. It locks the account, refuses a locked one and
 * counts a wrong password as a failed login (5 lock the account for 15 minutes), so a stolen session can't be used to
 * guess it. Called first, before any change, by methods whose transaction doesn't roll back on IncorrectPasswordException:
 * the failure is committed, nothing else changes.
 */
@Component
class Reauthentication
{
	private final UserRepository users;
	private final PasswordHasher hasher;
	private final Clock clock;

	Reauthentication(UserRepository users, PasswordHasher hasher, Clock clock)
	{
		this.users = users;
		this.hasher = hasher;
		this.clock = clock;
	}

	/** The confirmed, locked account; an unknown or inactive one is "not found". */
	User confirm(UserId actor, String password)
	{
		User user = users.findByIdForUpdate(actor).filter(found -> found.getStatus() == AccountStatus.ACTIVE)
				.orElseThrow(() -> new NotFoundException("Not found."));
		Instant now = clock.instant();

		if (!user.canLogInAt(now))
		{
			throw new IncorrectPasswordException();
		}

		if (!hasher.matches(password, user.getPasswordHash().orElseThrow()))
		{
			user.recordFailedLogin(now);
			users.save(user);
			throw new IncorrectPasswordException();
		}

		if (user.getFailedLogins() > 0)
		{
			user.recordSuccessfulLogin();
			users.save(user);
		}

		return user;
	}
}
