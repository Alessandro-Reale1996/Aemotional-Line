package com.aemotionalline.application;

import java.time.Duration;
import java.time.LocalTime;

import org.jspecify.annotations.Nullable;

import com.aemotionalline.application.view.ConstraintType;
import com.aemotionalline.domain.constraint.ConstraintScope;
import com.aemotionalline.domain.constraint.LocationConstraint;
import com.aemotionalline.domain.user.UserId;

/**
 * A constraint the therapist wants to assign to a partner, as the client sends it: type TIME (notBefore), REPLY_DELAY
 * (delay; it always restricts writing) or LOCATION (latitude, longitude, radiusMetres, direction). A null scope means the
 * type's default (TIME writing, LOCATION reading).
 */
public record ConstraintSpec(UserId assignee, ConstraintType type, @Nullable ConstraintScope scope, @Nullable LocalTime notBefore,
		@Nullable Duration delay, @Nullable Double latitude, @Nullable Double longitude, @Nullable Double radiusMetres,
		LocationConstraint.@Nullable Direction direction)
{
}
