package com.aemotionalline.domain.common;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Time-ordered UUIDs (version 7, RFC 9562): the first 48 bits are the Unix time in milliseconds, the rest is random.
 * Ids created later sort after earlier ones, so the database inserts them in order into its primary-key index
 * instead of scattering them, and the order of ids follows the order of creation (to the millisecond).
 */
public final class UuidV7
{
	private static final SecureRandom RANDOM = new SecureRandom();

	private UuidV7()
	{
	}

	public static UUID generate()
	{
		return generate(System.currentTimeMillis());
	}

	// Separate so that tests can fix the time.
	static UUID generate(long unixMillis)
	{
		long timestamp = unixMillis & 0xFFFF_FFFF_FFFFL;
		long randomA = RANDOM.nextInt(1 << 12);
		long randomB = RANDOM.nextLong();

		// 48 bits of time, the version (7), then 12 random bits.
		long mostSignificant = (timestamp << 16) | 0x7000L | randomA;
		// The variant (binary 10), then 62 random bits.
		long leastSignificant = (randomB & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;

		return new UUID(mostSignificant, leastSignificant);
	}
}
