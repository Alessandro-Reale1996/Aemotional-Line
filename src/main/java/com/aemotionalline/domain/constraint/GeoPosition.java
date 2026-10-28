package com.aemotionalline.domain.constraint;

import com.aemotionalline.domain.common.DomainException;

/**
 * A point on the Earth in decimal degrees, as a phone or browser reports it. The domain can't verify it:
 * the caller is trusted to supply the real position.
 */
public record GeoPosition(double latitude, double longitude)
{
	// Mean Earth radius: the sphere model of the Haversine formula errs by less than 0.5%, far below GPS accuracy.
	private static final double EARTH_RADIUS_METRES = 6_371_008.8;

	public GeoPosition
	{
		if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90)
		{
			throw new DomainException("Latitude must be between -90 and 90 degrees.");
		}

		if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180)
		{
			throw new DomainException("Longitude must be between -180 and 180 degrees.");
		}
	}

	/** Distance along the Earth's surface, in metres (Haversine formula). */
	public double distanceTo(GeoPosition other)
	{
		if (other == null)
		{
			throw new DomainException("The other position can't be null.");
		}

		double lat1 = Math.toRadians(latitude);
		double lat2 = Math.toRadians(other.latitude);
		double deltaLat = lat2 - lat1;
		double deltaLon = Math.toRadians(other.longitude - longitude);

		double halfChord = Math.pow(Math.sin(deltaLat / 2), 2)
				+ Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(deltaLon / 2), 2);

		// min guards against a rounding error pushing the value just above 1, where asin is undefined.
		return 2 * EARTH_RADIUS_METRES * Math.asin(Math.min(1, Math.sqrt(halfChord)));
	}
}
