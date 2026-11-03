package com.aemotionalline.infrastructure.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aemotionalline.application.RequestErrorCode;
import com.aemotionalline.domain.common.ErrorCode;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * The errors answered outside Spring MVC (a request refused by a filter or by the container) in the same Problem Details
 * format, instead of Spring Boot's default body: replaces Boot's BasicErrorController.
 */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ProblemErrorController implements ErrorController
{
	@RequestMapping("/error")
	ResponseEntity<ProblemDetail> error(HttpServletRequest request)
	{
		HttpStatus status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code && HttpStatus.resolve(code) != null
				? HttpStatus.resolve(code)
				: HttpStatus.INTERNAL_SERVER_ERROR;
		ProblemDetail problem;

		if (status.is5xxServerError())
		{
			problem = Problems.of(status, HttpErrorCode.INTERNAL_ERROR, "An unexpected error occurred.");
			problem.setProperty(Problems.ERROR_ID, request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable failure
					? UnexpectedErrors.log(failure)
					: UnexpectedErrors.logWithoutException(status.value()));
		}
		else
		{
			problem = switch (status)
			{
				case UNAUTHORIZED -> Problems.of(status, HttpErrorCode.AUTHENTICATION_REQUIRED, "Authentication required.");
				case FORBIDDEN -> Problems.of(status, HttpErrorCode.ACCESS_DENIED, "Access denied.");
				case NOT_FOUND -> Problems.of(status, ErrorCode.NOT_FOUND, "Not found.");
				default -> Problems.of(status, RequestErrorCode.MALFORMED_REQUEST, "Malformed request.");
			};
		}

		return ResponseEntity.status(status).body(problem);
	}
}
