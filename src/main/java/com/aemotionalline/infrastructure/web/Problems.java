package com.aemotionalline.infrastructure.web;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * The one error format of the API (CLAUDE.md, REST point 3): RFC 9457 Problem Details with a stable "code" member, by
 * which the frontend picks its Italian message; "detail" stays in English, for developers.
 */
public final class Problems
{
	public static final String CODE = "code";
	public static final String ERROR_ID = "errorId";

	private Problems()
	{
	}

	public static ProblemDetail of(HttpStatusCode status, Enum<?> code, String detail)
	{
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setProperty(CODE, code.name());

		return problem;
	}

	/** For the filters that answer before Spring MVC (security): the same members Spring MVC renders (no "type": about:blank). */
	public static void write(HttpServletRequest request, HttpServletResponse response, JsonMapper json, HttpStatus status, Enum<?> code, String detail)
			throws IOException
	{
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("title", status.getReasonPhrase());
		body.put("status", status.value());
		body.put("detail", detail);
		body.put("instance", request.getRequestURI());
		body.put(CODE, code.name());

		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		json.writeValue(response.getOutputStream(), body);
	}
}
