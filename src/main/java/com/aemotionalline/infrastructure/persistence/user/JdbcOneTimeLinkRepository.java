package com.aemotionalline.infrastructure.persistence.user;

import static com.aemotionalline.infrastructure.persistence.Rows.instant;
import static com.aemotionalline.infrastructure.persistence.Rows.uuid;

import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.aemotionalline.application.OneTimeLink;
import com.aemotionalline.application.OneTimeLinkRepository;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.infrastructure.persistence.Utc;

/** The application's OneTimeLinkRepository on MariaDB: only the hash of a link's code is stored. */
@Repository
public class JdbcOneTimeLinkRepository implements OneTimeLinkRepository
{
	private final JdbcClient jdbc;

	JdbcOneTimeLinkRepository(JdbcClient jdbc)
	{
		this.jdbc = jdbc;
	}

	@Override
	public void save(OneTimeLink link)
	{
		jdbc.sql("""
				INSERT INTO one_time_link (code_hash, user_id, purpose, created_at, expires_at, used_at) VALUES (?, ?, ?, ?, ?, ?)
				ON DUPLICATE KEY UPDATE used_at = VALUES(used_at)""")
				.params(link.codeHash(), link.user().value(), link.purpose().name(), Utc.toColumn(link.createdAt()), Utc.toColumn(link.expiresAt()),
						Utc.toColumn(link.usedAt().orElse(null)))
				.update();
	}

	@Override
	public Optional<OneTimeLink> findByCodeHash(String codeHash)
	{
		return jdbc.sql("SELECT * FROM one_time_link WHERE code_hash = ?").param(codeHash)
				.query((rs, row) -> new OneTimeLink(rs.getString("code_hash"), new UserId(uuid(rs, "user_id")),
						OneTimeLink.Purpose.valueOf(rs.getString("purpose")), instant(rs, "created_at"), instant(rs, "expires_at"),
						Optional.ofNullable(instant(rs, "used_at"))))
				.optional();
	}

	@Override
	public void invalidateUnused(UserId user, OneTimeLink.Purpose purpose, Instant at)
	{
		jdbc.sql("UPDATE one_time_link SET used_at = ? WHERE user_id = ? AND purpose = ? AND used_at IS NULL")
				.params(Utc.toColumn(at), user.value(), purpose.name())
				.update();
	}
}
