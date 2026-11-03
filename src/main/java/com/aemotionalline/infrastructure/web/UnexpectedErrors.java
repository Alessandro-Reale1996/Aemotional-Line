package com.aemotionalline.infrastructure.web;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs an unexpected failure (CLAUDE.md, REST point 3): under a new error id, which the client gets too, the exception
 * types and the stack trace, but never the exception messages, which can quote data (a database error repeats the
 * duplicate email it refused). No personal content in the logs (security point 5c).
 */
public final class UnexpectedErrors
{
	private static final Logger log = LoggerFactory.getLogger(UnexpectedErrors.class);
	private static final int MAX_FRAMES = 60;

	private UnexpectedErrors()
	{
	}

	/** Returns the error id to give the client. */
	public static String log(Throwable failure)
	{
		String errorId = UUID.randomUUID().toString();
		StringBuilder trace = new StringBuilder();
		Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());

		for (Throwable current = failure; current != null && seen.add(current); current = current.getCause())
		{
			trace.append(current == failure ? "" : "Caused by: ").append(current.getClass().getName()).append('\n');
			StackTraceElement[] frames = current.getStackTrace();

			for (int index = 0; index < Math.min(frames.length, MAX_FRAMES); index++)
			{
				trace.append("\tat ").append(frames[index]).append('\n');
			}

			if (frames.length > MAX_FRAMES)
			{
				trace.append("\t... ").append(frames.length - MAX_FRAMES).append(" more\n");
			}
		}

		log.error("Unexpected error {} (exception messages are left out of the log):\n{}", errorId, trace);

		return errorId;
	}

	/** An error answered by the container with no exception to show (a 5xx from a filter): only the id. */
	public static String logWithoutException(int status)
	{
		String errorId = UUID.randomUUID().toString();
		log.error("Unexpected error {}: status {} without an exception.", errorId, status);

		return errorId;
	}
}
