package com.aemotionalline.application.view;

import java.time.Duration;
import java.time.LocalTime;

import org.jspecify.annotations.Nullable;

import com.aemotionalline.domain.constraint.ConstraintScope;
import com.aemotionalline.domain.constraint.LocationConstraint;
import com.aemotionalline.domain.user.UserId;

/**
 * A constraint assigned to a partner, flattened: type TIME (notBefore), REPLY_DELAY (delay) or LOCATION (latitude,
 * longitude, radiusMetres, direction); the other fields are null. {@code description} is the domain's own wording.
 */
public record ConstraintView(UserId user, ConstraintType type, ConstraintScope scope, @Nullable LocalTime notBefore, @Nullable Duration delay,
		@Nullable Double latitude, @Nullable Double longitude, @Nullable Double radiusMetres, LocationConstraint.@Nullable Direction direction,
		String description)
{
}
