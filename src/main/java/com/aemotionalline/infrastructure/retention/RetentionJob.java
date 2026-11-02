package com.aemotionalline.infrastructure.retention;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.aemotionalline.application.RetentionService;
import com.aemotionalline.domain.couple.CoupleId;

/**
 * Applies the retention period every night: deletes the data of the couples that ended longer ago than
 * aemotional.retention.after-end, an ISO-8601 duration such as P365D. Empty, the default, means disabled (CLAUDE.md, S3
 * part 2: the rule is to be decided with the privacy advisor). Each couple is deleted in its own transaction, so one
 * failure doesn't stop the others; the log carries only counts and ids, never content.
 */
@Component
public class RetentionJob
{
	private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

	private final RetentionService retention;
	private final Optional<Duration> period;
	private final Clock clock;

	RetentionJob(RetentionService retention, @Value("${aemotional.retention.after-end:}") String period, Clock clock)
	{
		this.retention = retention;
		this.period = parse(period);
		this.clock = clock;
	}

	/** Returns how many couples' data were deleted (none when the period is disabled). */
	@Scheduled(cron = "${aemotional.retention.cron:0 30 3 * * *}", zone = "UTC")
	public int run()
	{
		if (period.isEmpty())
		{
			return 0;
		}

		Instant cutoff = clock.instant().minus(period.get());
		int deleted = 0;

		for (CoupleId couple : retention.couplesEndedBefore(cutoff))
		{
			try
			{
				if (retention.deleteIfEndedBefore(couple, cutoff))
				{
					deleted++;
				}
			}
			catch (RuntimeException failure)
			{
				log.warn("Retention: the data of couple {} could not be deleted ({}).", couple.value(), failure.getClass().getSimpleName());
			}
		}

		if (deleted > 0)
		{
			log.info("Retention: deleted the data of {} ended couples.", deleted);
		}

		return deleted;
	}

	// A wrong value stops the application at startup rather than silently disabling (or misapplying) the retention.
	static Optional<Duration> parse(String value)
	{
		if (value == null || value.isBlank())
		{
			return Optional.empty();
		}

		Duration duration;

		try
		{
			duration = Duration.parse(value.strip());
		}
		catch (DateTimeParseException failure)
		{
			throw new IllegalStateException("aemotional.retention.after-end must be an ISO-8601 duration such as P365D.", failure);
		}

		if (duration.isNegative() || duration.isZero())
		{
			throw new IllegalStateException("aemotional.retention.after-end must be positive.");
		}

		return Optional.of(duration);
	}
}
