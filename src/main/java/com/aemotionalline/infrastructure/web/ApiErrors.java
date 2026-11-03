package com.aemotionalline.infrastructure.web;

import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.aemotionalline.application.InvalidRequestException;
import com.aemotionalline.application.NotFoundException;
import com.aemotionalline.application.RequestErrorCode;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.common.ErrorCode;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Every failure of a controller becomes one Problem Details answer with a stable code (CLAUDE.md, REST points 3 and 4):
 * a broken domain rule 409 (its "not found" 404), "not found" 404, a malformed request 400, anything unexpected 500 with
 * an error id. The details never repeat what the client sent: only field names.
 */
@RestControllerAdvice
public class ApiErrors extends ResponseEntityExceptionHandler
{
	private static final String UNEXPECTED = "An unexpected error occurred.";

	@ExceptionHandler(DomainException.class)
	ProblemDetail rule(DomainException failure)
	{
		HttpStatus status = failure.getCode() == ErrorCode.NOT_FOUND ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;

		return Problems.of(status, failure.getCode(), failure.getMessage());
	}

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail notFound(NotFoundException failure)
	{
		return Problems.of(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, failure.getMessage());
	}

	@ExceptionHandler(InvalidRequestException.class)
	ProblemDetail invalid(InvalidRequestException failure)
	{
		return Problems.of(HttpStatus.BAD_REQUEST, failure.getCode(), failure.getMessage());
	}

	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail denied(AccessDeniedException failure)
	{
		return Problems.of(HttpStatus.FORBIDDEN, HttpErrorCode.ACCESS_DENIED, "Access denied.");
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail unexpected(Exception failure)
	{
		ProblemDetail problem = Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, HttpErrorCode.INTERNAL_ERROR, UNEXPECTED);
		problem.setProperty(Problems.ERROR_ID, UnexpectedErrors.log(failure));

		return problem;
	}

	/**
	 * A body that can't be read: a rule broken by a request record's constructor is answered as that rule; otherwise
	 * malformed JSON, an unknown field or a value of the wrong type, named by its path.
	 */
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException failure, HttpHeaders headers, HttpStatusCode status,
			WebRequest request)
	{
		for (Throwable cause = failure.getCause(); cause != null; cause = cause.getCause())
		{
			if (cause instanceof DomainException rule)
			{
				return answer(rule(rule));
			}

			if (cause instanceof InvalidRequestException invalid)
			{
				return answer(invalid(invalid));
			}
		}

		JacksonException json = jacksonCause(failure);
		String field = json == null ? "" : pathOf(json);
		String detail;

		if (json instanceof UnrecognizedPropertyException)
		{
			detail = "Unknown field '" + field + "'.";
		}
		else if (!field.isEmpty())
		{
			detail = "Missing or malformed value of field '" + field + "'.";
		}
		else
		{
			detail = "The request body is missing or is not valid JSON.";
		}

		return answer(Problems.of(HttpStatus.BAD_REQUEST, RequestErrorCode.MALFORMED_REQUEST, detail));
	}

	/** The other failures Spring MVC itself detects (wrong method, path variable, parameter, content type...). */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception failure, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request)
	{
		ProblemDetail problem;

		if (status.is5xxServerError())
		{
			problem = Problems.of(status, HttpErrorCode.INTERNAL_ERROR, UNEXPECTED);
			problem.setProperty(Problems.ERROR_ID, UnexpectedErrors.log(failure));
		}
		else if (status.value() == HttpStatus.NOT_FOUND.value())
		{
			problem = Problems.of(status, ErrorCode.NOT_FOUND, "Not found.");
		}
		else
		{
			problem = Problems.of(status, RequestErrorCode.MALFORMED_REQUEST, malformed(failure));
		}

		return ResponseEntity.status(status).headers(headers).body(problem);
	}

	private static String malformed(Exception failure)
	{
		return switch (failure)
		{
			case TypeMismatchException mismatch -> "Malformed value of '" + mismatch.getPropertyName() + "'.";
			case MissingServletRequestParameterException missing -> "Missing parameter '" + missing.getParameterName() + "'.";
			case HttpRequestMethodNotSupportedException method -> "Method " + method.getMethod() + " is not allowed here.";
			case HttpMediaTypeNotSupportedException type -> "Unsupported content type.";
			default -> "Malformed request.";
		};
	}

	private static ResponseEntity<Object> answer(ProblemDetail problem)
	{
		return ResponseEntity.status(problem.getStatus()).body(problem);
	}

	private static JacksonException jacksonCause(Throwable failure)
	{
		for (Throwable cause = failure; cause != null; cause = cause.getCause())
		{
			if (cause instanceof JacksonException json)
			{
				return json;
			}
		}

		return null;
	}

	// "paragraphs[0].type": the field names and indexes only, never the values.
	private static String pathOf(JacksonException failure)
	{
		StringBuilder path = new StringBuilder();

		for (JacksonException.Reference reference : failure.getPath())
		{
			if (reference.getPropertyName() != null)
			{
				path.append(path.isEmpty() ? "" : ".").append(reference.getPropertyName());
			}
			else if (reference.getIndex() >= 0)
			{
				path.append('[').append(reference.getIndex()).append(']');
			}
		}

		return path.toString();
	}
}
