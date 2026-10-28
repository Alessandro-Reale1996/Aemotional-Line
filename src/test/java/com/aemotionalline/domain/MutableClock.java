package com.aemotionalline.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Test clock whose time the test moves by hand, to check what depends on time passing (sends, reads, delays). */
public final class MutableClock extends Clock
{
	private Instant instant;
	private final ZoneId zone;

	public MutableClock(Instant start)
	{
		this(start, ZoneOffset.UTC);
	}

	private MutableClock(Instant start, ZoneId zone)
	{
		this.instant = start;
		this.zone = zone;
	}

	public void set(Instant instant)
	{
		this.instant = instant;
	}

	public void advance(Duration duration)
	{
		this.instant = instant.plus(duration);
	}

	@Override
	public Instant instant()
	{
		return instant;
	}

	@Override
	public ZoneId getZone()
	{
		return zone;
	}

	@Override
	public Clock withZone(ZoneId zone)
	{
		return new MutableClock(instant, zone);
	}
}
