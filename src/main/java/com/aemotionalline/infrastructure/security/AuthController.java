package com.aemotionalline.infrastructure.security;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aemotionalline.application.InvalidRequestException;
import com.aemotionalline.application.LoggedInUser;
import com.aemotionalline.application.LoginService;
import com.aemotionalline.application.UserService;
import com.aemotionalline.application.view.AccountView;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.user.EmailAddress;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Login, "who am I", account activation and password reset (CLAUDE.md, S4 decision 1); logout is handled by the security
 * filters (POST /api/auth/logout). The client first calls GET /api/auth/csrf to get the __Host-XSRF-TOKEN cookie, then
 * sends its value in the X-XSRF-TOKEN header with every POST.
 */
@RestController
@RequestMapping("/api/auth")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class AuthController
{
	private static final Logger log = LoggerFactory.getLogger(AuthController.class);

	private final LoginService logins;
	private final UserService users;
	private final SessionAuthenticationStrategy loginSessionStrategy;
	private final SecurityContextRepository securityContexts;

	AuthController(LoginService logins, UserService users, SessionAuthenticationStrategy loginSessionStrategy, SecurityContextRepository securityContexts)
	{
		this.logins = logins;
		this.users = users;
		this.loginSessionStrategy = loginSessionStrategy;
		this.securityContexts = securityContexts;
	}

	record Credentials(String email, String password)
	{
	}

	record NewPassword(String code, String password)
	{
	}

	record ResetRequest(String email)
	{
	}

	record Account(String id, String email, String displayName, String role)
	{
		static Account of(AccountView view)
		{
			return new Account(view.id().value().toString(), view.email(), view.displayName(), view.role().name());
		}
	}

	record ApiError(String error)
	{
	}

	/** Renders the __Host-XSRF-TOKEN cookie, whose value the client sends back in the X-XSRF-TOKEN header. */
	@GetMapping("/csrf")
	ResponseEntity<Void> csrf(CsrfToken token)
	{
		token.getToken();

		return ResponseEntity.noContent().build();
	}

	@PostMapping("/login")
	ResponseEntity<?> logIn(@RequestBody Credentials credentials, HttpServletRequest request, HttpServletResponse response)
	{
		Optional<LoggedInUser> user = logins.logIn(credentials.email(), credentials.password());

		if (user.isEmpty())
		{
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("Invalid credentials."));
		}

		SessionUser principal = new SessionUser(user.get().id().value(), user.get().role());
		Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null,
				List.of(new SimpleGrantedAuthority("ROLE_" + user.get().role().name())));

		loginSessionStrategy.onAuthentication(authentication, request, response);
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		securityContexts.saveContext(context, request, response);
		// The login replaced the CSRF token: render the new cookie with this response.
		((CsrfToken) request.getAttribute(CsrfToken.class.getName())).getToken();

		return ResponseEntity.ok(Account.of(users.account(user.get().id())));
	}

	@GetMapping("/me")
	Account me(@AuthenticationPrincipal SessionUser user)
	{
		return Account.of(users.account(user.userId()));
	}

	@PostMapping("/activate")
	ResponseEntity<Void> activate(@RequestBody NewPassword body)
	{
		users.activate(body.code(), body.password());

		return ResponseEntity.noContent().build();
	}

	/**
	 * Always the same answer, whether the email exists, is malformed, or the email couldn't be sent: anything else would
	 * tell who has an account. A sending failure is logged without the address.
	 */
	@PostMapping("/password-reset")
	ResponseEntity<Void> requestPasswordReset(@RequestBody ResetRequest body)
	{
		try
		{
			users.requestPasswordReset(new EmailAddress(body.email()));
		}
		catch (DomainException malformed)
		{
			// Same answer as for an unknown email.
		}
		catch (RuntimeException failure)
		{
			log.warn("A password reset email could not be sent ({}).", failure.getClass().getSimpleName());
		}

		return ResponseEntity.accepted().build();
	}

	@PostMapping("/password-reset/confirm")
	ResponseEntity<Void> resetPassword(@RequestBody NewPassword body)
	{
		users.resetPassword(body.code(), body.password());

		return ResponseEntity.noContent().build();
	}

	@ExceptionHandler(InvalidRequestException.class)
	ResponseEntity<ApiError> invalid(InvalidRequestException failure)
	{
		return ResponseEntity.badRequest().body(new ApiError(failure.getMessage()));
	}
}
