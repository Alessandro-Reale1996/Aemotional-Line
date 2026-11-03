package com.aemotionalline.infrastructure.security;

import java.io.IOException;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;

import jakarta.servlet.http.HttpServletResponse;

/**
 * The HTTP security of the API (CLAUDE.md, security points 2 and 4, S4): a session cookie after a JSON login (sessions in
 * MariaDB through Spring Session), CSRF protection with the token in a cookie the React app reads and sends back in the
 * X-XSRF-TOKEN header, no login page and no HTTP Basic, JSON errors. Paths: the login steps are open, /api/admin/** needs
 * the administrator role, the rest of /api/** a login; anything else is closed until the frontend is served. Only in the
 * web application: the first-administrator command runs without it.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration
{
	@Bean
	CookieCsrfTokenRepository csrfTokens()
	{
		return CookieCsrfTokenRepository.withHttpOnlyFalse();
	}

	@Bean
	SecurityContextRepository securityContexts()
	{
		return new HttpSessionSecurityContextRepository();
	}

	/** Applied by the login: a new session id (against session fixation) and a new CSRF token. */
	@Bean
	SessionAuthenticationStrategy loginSessionStrategy(CookieCsrfTokenRepository csrfTokens)
	{
		return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrfTokens)));
	}

	@Bean
	SecurityFilterChain api(HttpSecurity http, CookieCsrfTokenRepository csrfTokens, SecurityContextRepository securityContexts) throws Exception
	{
		http.authorizeHttpRequests(paths -> paths
				.requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/logout", "/api/auth/activate", "/api/auth/password-reset",
						"/api/auth/password-reset/confirm").permitAll()
				.requestMatchers("/api/admin/**").hasRole("ADMINISTRATOR")
				.requestMatchers("/api/**").authenticated()
				.requestMatchers("/error").permitAll()
				.anyRequest().denyAll())
				.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokens))
				.securityContext(context -> context.securityContextRepository(securityContexts))
				// A refused request is never stored to be replayed after a login: no session for anonymous requests.
				.requestCache(cache -> cache.disable())
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable())
				.exceptionHandling(errors -> errors
						.authenticationEntryPoint((request, response, failure) -> error(response, HttpStatus.UNAUTHORIZED, "Authentication required."))
						.accessDeniedHandler((request, response, failure) -> error(response, HttpStatus.FORBIDDEN, "Access denied.")))
				.logout(logout -> logout.logoutUrl("/api/auth/logout").logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));

		return http.build();
	}

	// The same shape as the controllers' errors: {"error": "..."}; the messages are fixed texts, nothing to escape.
	static void error(HttpServletResponse response, HttpStatus status, String message) throws IOException
	{
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("{\"error\":\"" + message + "\"}");
	}
}
