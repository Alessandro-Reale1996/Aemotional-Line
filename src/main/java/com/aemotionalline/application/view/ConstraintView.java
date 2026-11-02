package com.aemotionalline.application.view;

import java.time.Duration;
import java.time.LocalTime;

import com.aemotionalline.domain.constraint.ConstraintScope;
import com.aemotionalline.domain.constraint.LocationConstraint;
import com.aemotionalline.domain.user.UserId;

/**
 * A constraint assigned to a partner, flattened: type TIME (notBefore), REPLY_DELAY (delay) or LOCATION (latitude,
 * longitude, radiusMetres, direction); the other fields are null. {@code description} is the domain's own wording.
 */
public record ConstraintView(UserId user, String type, ConstraintScope scope, LocalTime notBefore, Duration delay, Double latitude,
		Double longitude, Double radiusMetres, LocationConstraint.Direction direction, String description)
{
}
