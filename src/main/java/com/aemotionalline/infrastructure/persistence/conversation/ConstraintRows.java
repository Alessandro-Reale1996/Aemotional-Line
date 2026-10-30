package com.aemotionalline.infrastructure.persistence.conversation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalTime;

import com.aemotionalline.domain.constraint.Constraint;
import com.aemotionalline.domain.constraint.ConstraintAssignment;
import com.aemotionalline.domain.constraint.ConstraintScope;
import com.aemotionalline.domain.constraint.GeoPosition;
import com.aemotionalline.domain.constraint.LocationConstraint;
import com.aemotionalline.domain.constraint.ReplyDelayConstraint;
import com.aemotionalline.domain.constraint.TimeConstraint;
import com.aemotionalline.domain.user.UserId;

/**
 * Constraints in the constraint_assignment table: one row per assignment, a type and the columns of that type
 * (TIME: not_before; REPLY_DELAY: delay_millis; LOCATION: latitude, longitude, radius_metres, direction).
 */
final class ConstraintRows
{
	static final String TIME = "TIME";
	static final String REPLY_DELAY = "REPLY_DELAY";
	static final String LOCATION = "LOCATION";

	/** The values of a constraint_assignment row, in the order of the INSERT in ConversationWriter. */
	record Values(String type, String scope, LocalTime notBefore, Long delayMillis, Double latitude, Double longitude, Double radiusMetres, String direction)
	{
	}

	private ConstraintRows()
	{
	}

	static Values values(Constraint constraint)
	{
		String scope = constraint.getScope().name();

		return switch (constraint)
		{
			case TimeConstraint time -> new Values(TIME, scope, time.getNotBefore(), null, null, null, null, null);
			case ReplyDelayConstraint delay -> new Values(REPLY_DELAY, scope, null, delay.getDelay().toMillis(), null, null, null, null);
			case LocationConstraint location -> new Values(LOCATION, scope, null, null, location.getCenter().latitude(),
					location.getCenter().longitude(), location.getRadiusMetres(), location.getDirection().name());
			default -> throw new IllegalStateException("No storage for constraint type " + constraint.getClass().getName());
		};
	}

	static ConstraintAssignment assignment(ResultSet rs) throws SQLException
	{
		UserId user = new UserId(Columns.uuid(rs, "user_id"));
		ConstraintScope scope = ConstraintScope.valueOf(rs.getString("scope"));
		String type = rs.getString("type");

		Constraint constraint = switch (type)
		{
			case TIME -> new TimeConstraint(rs.getObject("not_before", LocalTime.class), scope);
			case REPLY_DELAY -> new ReplyDelayConstraint(Duration.ofMillis(rs.getLong("delay_millis")));
			case LOCATION -> new LocationConstraint(new GeoPosition(rs.getDouble("latitude"), rs.getDouble("longitude")),
					rs.getDouble("radius_metres"), LocationConstraint.Direction.valueOf(rs.getString("direction")), scope);
			default -> throw new IllegalStateException("Unknown constraint type in the database: " + type);
		};

		return new ConstraintAssignment(user, constraint);
	}
}
