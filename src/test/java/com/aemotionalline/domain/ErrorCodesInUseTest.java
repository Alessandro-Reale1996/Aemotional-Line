package com.aemotionalline.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.aemotionalline.application.RequestErrorCode;
import com.aemotionalline.domain.common.ErrorCode;

/**
 * Every published code is thrown somewhere in the code (CLAUDE.md, R1): a code nobody throws would be a promise to the
 * frontend that nothing keeps. RULE_VIOLATED and MALFORMED_REQUEST are the defaults of their exceptions.
 */
public class ErrorCodesInUseTest
{
	@Test
	void shouldThrowEveryCodeSomewhere() throws IOException
	{
		String sources;

		try (Stream<Path> files = Files.walk(Path.of("src/main/java")))
		{
			sources = files.filter(file -> file.toString().endsWith(".java")).map(ErrorCodesInUseTest::read).collect(Collectors.joining("\n"));
		}

		List<String> unused = Stream.concat(
				Arrays.stream(ErrorCode.values()).filter(code -> code != ErrorCode.RULE_VIOLATED).map(code -> "ErrorCode." + code.name()),
				Arrays.stream(RequestErrorCode.values()).filter(code -> code != RequestErrorCode.MALFORMED_REQUEST).map(code -> "RequestErrorCode." + code.name()))
				.filter(reference -> !sources.contains(reference))
				.toList();

		assertEquals(List.of(), unused);
	}

	private static String read(Path file)
	{
		try
		{
			return Files.readString(file);
		}
		catch (IOException failure)
		{
			throw new UncheckedIOException(failure);
		}
	}
}
