package com.aemotionalline.infrastructure.persistence.user;

import static com.aemotionalline.infrastructure.persistence.Rows.instant;
import static com.aemotionalline.infrastructure.persistence.Rows.uuid;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.aemotionalline.domain.user.AccountStatus;
import com.aemotionalline.domain.user.EmailAddress;
import com.aemotionalline.domain.user.PasswordHash;
import com.aemotionalline.domain.user.Role;
import com.aemotionalline.domain.user.User;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.user.UserRepository;
import com.aemotionalline.infrastructure.persistence.Utc;

/** The domain's UserRepository on MariaDB (table user_account). */
@Repository
public class JdbcUserRepository implements UserRepository
{
	private final JdbcClient jdbc;

	JdbcUserRepository(JdbcClient jdbc)
	{
		this.jdbc = jdbc;
	}

	@Override
	public Optional<User> findById(UserId id)
	{
		return jdbc.sql("SELECT * FROM user_account WHERE id = ?").param(id.value()).query((rs, row) -> user(rs)).optional();
	}

	/** The lock lasts until the end of the caller's transaction, so there must be one. */
	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<User> findByIdForUpdate(UserId id)
	{
		return jdbc.sql("SELECT * FROM user_account WHERE id = ? FOR UPDATE").param(id.value()).query((rs, row) -> user(rs)).optional();
	}

	@Override
	public Optional<User> findByEmail(EmailAddress email)
	{
		return jdbc.sql("SELECT * FROM user_account WHERE email = ?").param(email.value()).query((rs, row) -> user(rs)).optional();
	}

	/**
	 * Inserts a new account or updates the mutable facts of an existing one, telling them apart by id. Not an
	 * INSERT ... ON DUPLICATE KEY UPDATE: that fires on the unique email too, and a new account with an existing email would
	 * overwrite the other account's status and password instead of being refused.
	 */
	@Override
	@Transactional
	public void save(User user)
	{
		boolean exists = jdbc.sql("SELECT COUNT(*) FROM user_account WHERE id = ?").param(user.getId().value()).query(Long.class).single() > 0;

		if (exists)
		{
			jdbc.sql("UPDATE user_account SET status = ?, password_hash = ?, failed_logins = ?, locked_until = ? WHERE id = ?")
					.params(user.getStatus().name(), user.getPasswordHash().map(PasswordHash::value).orElse(null), user.getFailedLogins(),
							Utc.toColumn(user.getLockedUntil().orElse(null)), user.getId().value())
					.update();
		}
		else
		{
			jdbc.sql("INSERT INTO user_account (id, email, display_name, role, status, password_hash, failed_logins, locked_until) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
					.params(user.getId().value(), user.getEmail().value(), user.getDisplayName(), user.getRole().name(), user.getStatus().name(),
							user.getPasswordHash().map(PasswordHash::value).orElse(null), user.getFailedLogins(),
							Utc.toColumn(user.getLockedUntil().orElse(null)))
					.update();
		}
	}

	private static User user(ResultSet rs) throws SQLException
	{
		String hash = rs.getString("password_hash");

		return User.restore(new UserId(uuid(rs, "id")), new EmailAddress(rs.getString("email")), rs.getString("display_name"),
				Role.valueOf(rs.getString("role")), AccountStatus.valueOf(rs.getString("status")), hash == null ? null : new PasswordHash(hash),
				rs.getInt("failed_logins"), instant(rs, "locked_until"));
	}
}
