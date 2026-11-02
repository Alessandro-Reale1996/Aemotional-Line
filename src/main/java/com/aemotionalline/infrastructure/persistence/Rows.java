package com.aemotionalline.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/** Reading typed values out of a result set (UUIDs as the driver returns them, instants from UTC DATETIME columns). */
public final class Rows
{
	private Rows()
	{
	}

	public static UUID uuid(ResultSet rs, String column) throws SQLException
	{
		Object value = rs.getObject(column);

		return value == null ? null : value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
	}

	public static Instant instant(ResultSet rs, String column) throws SQLException
	{
		return Utc.fromColumn(rs.getObject(column, LocalDateTime.class));
	}
}
