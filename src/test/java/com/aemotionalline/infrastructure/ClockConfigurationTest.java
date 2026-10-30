package com.aemotionalline.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

public class ClockConfigurationTest
{
	@Test
	void shouldGiveUtcInstantsInWholeMicrosecondsLikeTheDatabase()
	{
		Clock clock = new ClockConfiguration().clock();

		assertEquals(ZoneOffset.UTC, clock.getZone());

		for (int i = 0; i < 1_000; i++)
		{
			assertEquals(0, clock.instant().getNano() % 1_000);
		}
	}
}
