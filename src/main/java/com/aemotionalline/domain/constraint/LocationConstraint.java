package com.aemotionalline.domain.constraint;

import com.aemotionalline.domain.common.DomainException;

/**
 * Allows acting only within a radius of a place, or only beyond it (spec 1.2: "a una certa distanza da un determinato
 * luogo o in un luogo specifico"; a small radius means "in that place"). It restricts reading by default: the message
 * opens only where the therapist set, and since a reply needs a read, the reply depends on it too.
 * Without a position in the context the constraint can't be shown satisfied, so it fails.
 */
public class LocationConstraint implements Constraint
{
	/** Whether the position must be inside the radius or outside it. Both boundaries are inclusive. */
	public enum Direction
	{
		WITHIN,
		BEYOND
	}

	private final GeoPosition center;
	private final double radiusMetres;
	private final Direction direction;
	private final ConstraintScope scope;

	public LocationConstraint(GeoPosition center, double radiusMetres, Direction direction)
	{
		this(center, radiusMetres, direction, ConstraintScope.READ);
	}

	public LocationConstraint(GeoPosition center, double radiusMetres, Direction direction, ConstraintScope scope)
	{
		if (center == null || direction == null || scope == null)
		{
			throw new DomainException("Center, direction and scope are required.");
		}

		if (!Double.isFinite(radiusMetres) || radiusMetres <= 0)
		{
			throw new DomainException("The radius must be a positive number of metres.");
		}

		this.center = center;
		this.radiusMetres = radiusMetres;
		this.direction = direction;
		this.scope = scope;
	}

	public GeoPosition getCenter()
	{
		return center;
	}

	public double getRadiusMetres()
	{
		return radiusMetres;
	}

	public Direction getDirection()
	{
		return direction;
	}

	@Override
	public boolean isSatisfied(ConstraintContext context)
	{
		if (context.position() == null)
		{
			return false;
		}

		double distance = center.distanceTo(context.position());

		return direction == Direction.WITHIN ? distance <= radiusMetres : distance >= radiusMetres;
	}

	@Override
	public ConstraintScope getScope()
	{
		return scope;
	}

	@Override
	public String getDescription()
	{
		return (direction == Direction.WITHIN ? "Allowed within " : "Allowed beyond ") + radiusMetres + " m of " + center;
	}
}
