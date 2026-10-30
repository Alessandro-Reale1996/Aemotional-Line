package com.aemotionalline.infrastructure.persistence;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Instants are stored in DATETIME(6) columns, which have no time zone: they always hold UTC. Converting here, explicitly,
 * keeps the JVM's or the server's time zone from ever shifting a stored instant.
 */
public final class Utc
{
	private Utc()
	{
	}

	public static LocalDateTime toColumn(Instant instant)
	{
		return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	public static Instant fromColumn(LocalDateTime value)
	{
		return value == null ? null : value.toInstant(ZoneOffset.UTC);
	}
}
