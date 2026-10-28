package com.aemotionalline.domain.constraint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.constraint.LocationConstraint.Direction;

public class LocationConstraintTest
{
	private static final GeoPosition HOME = new GeoPosition(45.4642, 9.19);
	private static final GeoPosition NEAR = new GeoPosition(45.4645, 9.1902);   // about 37 m from HOME
	private static final GeoPosition FAR = new GeoPosition(45.48, 9.19);        // about 1.76 km from HOME

	private static ConstraintContext at(GeoPosition position)
	{
		return new ConstraintContext(LocalTime.NOON, Instant.EPOCH, position);
	}

	@Test
	void shouldAllowWithinTheRadiusOnlyInside()
	{
		LocationConstraint constraint = new LocationConstraint(HOME, 100, Direction.WITHIN);

		assertTrue(constraint.isSatisfied(at(NEAR)));
		assertFalse(constraint.isSatisfied(at(FAR)));
	}

	@Test
	void shouldAllowBeyondTheRadiusOnlyOutside()
	{
		LocationConstraint constraint = new LocationConstraint(HOME, 100, Direction.BEYOND);

		assertFalse(constraint.isSatisfied(at(NEAR)));
		assertTrue(constraint.isSatisfied(at(FAR)));
	}

	@Test
	void shouldIncludeTheBoundaryInBothDirections()
	{
		double exactlyOnTheBoundary = HOME.distanceTo(NEAR);

		assertTrue(new LocationConstraint(HOME, exactlyOnTheBoundary, Direction.WITHIN).isSatisfied(at(NEAR)));
		assertTrue(new LocationConstraint(HOME, exactlyOnTheBoundary, Direction.BEYOND).isSatisfied(at(NEAR)));
	}

	@Test
	void shouldFailWithoutAPosition()
	{
		assertFalse(new LocationConstraint(HOME, 100, Direction.WITHIN).isSatisfied(new ConstraintContext(LocalTime.NOON, Instant.EPOCH)));
		assertFalse(new LocationConstraint(HOME, 100, Direction.BEYOND).isSatisfied(new ConstraintContext(LocalTime.NOON)));
	}

	@Test
	void shouldRestrictReadingByDefault()
	{
		assertEquals(ConstraintScope.READ, new LocationConstraint(HOME, 100, Direction.WITHIN).getScope());
		assertEquals(ConstraintScope.WRITE, new LocationConstraint(HOME, 100, Direction.WITHIN, ConstraintScope.WRITE).getScope());
	}

	@Test
	void shouldRejectAnInvalidRadiusOrMissingArguments()
	{
		assertThrows(DomainException.class, () -> new LocationConstraint(HOME, 0, Direction.WITHIN));
		assertThrows(DomainException.class, () -> new LocationConstraint(HOME, -1, Direction.WITHIN));
		assertThrows(DomainException.class, () -> new LocationConstraint(HOME, Double.NaN, Direction.WITHIN));
		assertThrows(DomainException.class, () -> new LocationConstraint(HOME, Double.POSITIVE_INFINITY, Direction.WITHIN));
		assertThrows(DomainException.class, () -> new LocationConstraint(null, 100, Direction.WITHIN));
		assertThrows(DomainException.class, () -> new LocationConstraint(HOME, 100, null));
		assertThrows(DomainException.class, () -> new LocationConstraint(HOME, 100, Direction.WITHIN, null));
	}
}
