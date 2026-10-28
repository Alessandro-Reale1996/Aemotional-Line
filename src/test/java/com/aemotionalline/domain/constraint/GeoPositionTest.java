package com.aemotionalline.domain.constraint;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;

public class GeoPositionTest
{
	@Test
	void shouldAcceptTheLimitsOfTheRanges()
	{
		assertDoesNotThrow(() -> new GeoPosition(90, 180));
		assertDoesNotThrow(() -> new GeoPosition(-90, -180));
	}

	@Test
	void shouldRejectPositionsOutOfRangeOrNotFinite()
	{
		assertThrows(DomainException.class, () -> new GeoPosition(90.0001, 0));
		assertThrows(DomainException.class, () -> new GeoPosition(-90.0001, 0));
		assertThrows(DomainException.class, () -> new GeoPosition(0, 180.0001));
		assertThrows(DomainException.class, () -> new GeoPosition(0, -180.0001));
		assertThrows(DomainException.class, () -> new GeoPosition(Double.NaN, 0));
		assertThrows(DomainException.class, () -> new GeoPosition(0, Double.POSITIVE_INFINITY));
	}

	@Test
	void shouldMeasureZeroFromAPointToItself()
	{
		GeoPosition milan = new GeoPosition(45.4642, 9.19);

		assertEquals(0, milan.distanceTo(milan), 1e-9);
	}

	@Test
	void shouldMeasureOneDegreeOfLatitudeAsAbout111Kilometres()
	{
		// 2 * pi * 6,371,008.8 m / 360 = 111,195.08 m
		assertEquals(111_195.08, new GeoPosition(0, 0).distanceTo(new GeoPosition(1, 0)), 0.1);
	}

	@Test
	void shouldMeasureTheSameDistanceInBothDirections()
	{
		GeoPosition milan = new GeoPosition(45.4642, 9.19);
		GeoPosition rome = new GeoPosition(41.9028, 12.4964);

		assertEquals(milan.distanceTo(rome), rome.distanceTo(milan), 1e-6);
		// Milan-Rome as the crow flies is about 477 km.
		assertEquals(477_000, milan.distanceTo(rome), 2_000);
	}

	@Test
	void shouldRejectANullOtherPosition()
	{
		assertThrows(DomainException.class, () -> new GeoPosition(0, 0).distanceTo(null));
	}
}
