package com.aemotionalline.application;

import java.time.Duration;
import java.time.LocalTime;

import com.aemotionalline.domain.constraint.ConstraintScope;
import com.aemotionalline.domain.constraint.LocationConstraint;
import com.aemotionalline.domain.user.UserId;

/**
 * A constraint the therapist wants to assign to a partner, as the client sends it: type TIME (notBefore), REPLY_DELAY
 * (delay; it always restricts writing) or LOCATION (latitude, longitude, radiusMetres, direction). A null scope means the
 * type's default (TIME writing, LOCATION reading).
 */
public record ConstraintSpec(UserId assignee, String type, ConstraintScope scope, LocalTime notBefore, Duration delay, Double latitude,
		Double longitude, Double radiusMetres, LocationConstraint.Direction direction)
{
}
