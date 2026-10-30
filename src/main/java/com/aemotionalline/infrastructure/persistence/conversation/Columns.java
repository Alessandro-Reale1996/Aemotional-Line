package com.aemotionalline.infrastructure.persistence.conversation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import com.aemotionalline.infrastructure.persistence.Utc;

/** Reading typed values out of a result set, the same way for every table of the conversation. */
final class Columns
{
	private Columns()
	{
	}

	static UUID uuid(ResultSet rs, String column) throws SQLException
	{
		Object value = rs.getObject(column);

		return switch (value)
		{
			case null -> null;
			case UUID uuid -> uuid;
			default -> UUID.fromString(value.toString());
		};
	}

	static UUID uuid(ResultSet rs, int column) throws SQLException
	{
		Object value = rs.getObject(column);

		return switch (value)
		{
			case null -> null;
			case UUID uuid -> uuid;
			default -> UUID.fromString(value.toString());
		};
	}

	static Instant instant(ResultSet rs, String column) throws SQLException
	{
		return Utc.fromColumn(rs.getObject(column, LocalDateTime.class));
	}

	static Integer integer(ResultSet rs, String column) throws SQLException
	{
		return rs.getObject(column, Integer.class);
	}
}
