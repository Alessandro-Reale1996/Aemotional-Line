package com.aemotionalline.infrastructure;

import java.time.Clock;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The server's clock, in UTC: the domain takes every instant it records from it, so a client can't fake time. It ticks in
 * microseconds, the precision of the DATETIME(6) columns, so an instant reads back from the database exactly as recorded.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfiguration
{
	@Bean
	Clock clock()
	{
		return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
	}
}
