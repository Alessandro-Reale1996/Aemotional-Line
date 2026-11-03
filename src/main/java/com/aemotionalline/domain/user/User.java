package com.aemotionalline.domain.user;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.common.ErrorCode;

/**
 * An account. It is born PENDING (a patient invited by a therapist, a therapist created by the administrator) and is
 * activated by its owner choosing a password through a one-time link sent to its email, which also verifies the address.
 * Only an ACTIVE account can log in; after 5 consecutive failed logins it is locked for 15 minutes. DISABLED is final.
 * The role never changes. The couple and conversation rules know users only by their UserId.
 */
public class User
{
	public static final int MAX_FAILED_LOGINS = 5;
	public static final Duration LOCK_DURATION = Duration.ofMinutes(15);
	private static final int MAX_DISPLAY_NAME_LENGTH = 100;

	private final UserId id;
	private final EmailAddress email;
	private final String displayName;
	private final Role role;

	private AccountStatus status;
	private PasswordHash passwordHash;
	private int failedLogins;
	private Instant lockedUntil;

	private User(UserId id, EmailAddress email, String displayName, Role role, AccountStatus status, PasswordHash passwordHash, int failedLogins,
			Instant lockedUntil)
	{
		if (id == null || email == null || role == null || status == null)
		{
			throw new DomainException("A user needs an id, an email, a role and a status.");
		}

		if (displayName == null || displayName.isBlank())
		{
			throw new DomainException(ErrorCode.TEXT_REQUIRED, "A display name is required.");
		}

		if (displayName.strip().length() > MAX_DISPLAY_NAME_LENGTH)
		{
			throw new DomainException(ErrorCode.TEXT_TOO_LONG, "The display name can't be longer than " + MAX_DISPLAY_NAME_LENGTH + " characters.");
		}

		this.id = id;
		this.email = email;
		this.displayName = displayName.strip();
		this.role = role;
		this.status = status;
		this.passwordHash = passwordHash;
		this.failedLogins = failedLogins;
		this.lockedUntil = lockedUntil;
	}

	/** A new account, waiting for its owner to activate it. */
	public static User pending(UserId id, EmailAddress email, String displayName, Role role)
	{
		return new User(id, email, displayName, role, AccountStatus.PENDING, null, 0, null);
	}

	/** Reserved to the persistence layer: rebuilds a user from its stored facts. */
	public static User restore(UserId id, EmailAddress email, String displayName, Role role, AccountStatus status, PasswordHash passwordHash,
			int failedLogins, Instant lockedUntil)
	{
		return new User(id, email, displayName, role, status, passwordHash, failedLogins, lockedUntil);
	}

	/** The owner chose a password through the activation link. */
	public void activate(PasswordHash password)
	{
		if (status != AccountStatus.PENDING)
		{
			throw new DomainException("Only a pending account can be activated.");
		}

		this.passwordHash = Objects.requireNonNull(password);
		this.status = AccountStatus.ACTIVE;
	}

	/** A new password chosen through a reset link; it also lifts a lock. */
	public void changePassword(PasswordHash password)
	{
		if (status != AccountStatus.ACTIVE)
		{
			throw new DomainException("Only an active account can change its password.");
		}

		this.passwordHash = Objects.requireNonNull(password);
		this.failedLogins = 0;
		this.lockedUntil = null;
	}

	public void disable()
	{
		if (status == AccountStatus.DISABLED)
		{
			throw new DomainException("The account is already disabled.");
		}

		this.status = AccountStatus.DISABLED;
	}

	/** After the 5th consecutive failure the account is locked for 15 minutes, and the count starts again. */
	public void recordFailedLogin(Instant at)
	{
		failedLogins++;

		if (failedLogins >= MAX_FAILED_LOGINS)
		{
			lockedUntil = at.plus(LOCK_DURATION);
			failedLogins = 0;
		}
	}

	public void recordSuccessfulLogin()
	{
		failedLogins = 0;
		lockedUntil = null;
	}

	public boolean isLockedAt(Instant at)
	{
		return lockedUntil != null && at.isBefore(lockedUntil);
	}

	/** Whether the account may log in now (the password itself is checked by the security infrastructure). */
	public boolean canLogInAt(Instant at)
	{
		return status == AccountStatus.ACTIVE && !isLockedAt(at);
	}

	public UserId getId()
	{
		return id;
	}

	public EmailAddress getEmail()
	{
		return email;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public Role getRole()
	{
		return role;
	}

	public AccountStatus getStatus()
	{
		return status;
	}

	public Optional<PasswordHash> getPasswordHash()
	{
		return Optional.ofNullable(passwordHash);
	}

	public int getFailedLogins()
	{
		return failedLogins;
	}

	public Optional<Instant> getLockedUntil()
	{
		return Optional.ofNullable(lockedUntil);
	}

	@Override
	public boolean equals(Object other)
	{
		return this == other || other instanceof User user && id.equals(user.id);
	}

	@Override
	public int hashCode()
	{
		return id.hashCode();
	}
}
