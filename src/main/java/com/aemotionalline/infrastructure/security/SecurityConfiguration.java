package com.aemotionalline.infrastructure.security;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

import com.aemotionalline.infrastructure.web.HttpErrorCode;
import com.aemotionalline.infrastructure.web.Problems;

import tools.jackson.databind.json.JsonMapper;


/**
 * The HTTP security of the API (CLAUDE.md, security points 2 and 4, S4): a session cookie after a JSON login (sessions in
 * MariaDB through Spring Session), CSRF protection with the token in a cookie the React app reads and sends back in the
 * X-XSRF-TOKEN header, no login page and no HTTP Basic, errors as Problem Details, hardened cookies and headers (S5). Paths: the login steps are open, /api/admin/** needs
 * the administrator role, the rest of /api/** a login; anything else is closed until the frontend is served. Only in the
 * web application: the first-administrator command runs without it.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration
{
	/** The __Host- prefix: browsers accept the cookie only if Secure, for the whole host (Path=/) and with no Domain. */
	public static final String CSRF_COOKIE = "__Host-XSRF-TOKEN";

	/** Readable by the app (not HttpOnly), and otherwise as strict as the session cookie (S5). */
	@Bean
	CookieCsrfTokenRepository csrfTokens()
	{
		CookieCsrfTokenRepository tokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
		tokens.setCookieName(CSRF_COOKIE);
		tokens.setCookiePath("/");
		tokens.setCookieCustomizer(cookie -> cookie.secure(true).sameSite("Strict"));

		return tokens;
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
	SecurityFilterChain api(HttpSecurity http, CookieCsrfTokenRepository csrfTokens, SecurityContextRepository securityContexts, JsonMapper json,
			@Value("${springdoc.api-docs.enabled:false}") boolean apiDocs) throws Exception
	{
		http.authorizeHttpRequests(paths -> paths
				// The OpenAPI spec and Swagger UI exist only in development (REST point 7).
				.requestMatchers(apiDocs ? new String[] { "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**" } : new String[0]).permitAll()
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
						.authenticationEntryPoint((request, response, failure) -> Problems.write(request, response, json, HttpStatus.UNAUTHORIZED,
								HttpErrorCode.AUTHENTICATION_REQUIRED, "Authentication required."))
						.accessDeniedHandler((request, response, failure) -> Problems.write(request, response, json, HttpStatus.FORBIDDEN,
								HttpErrorCode.ACCESS_DENIED, "Access denied.")))
				.logout(logout -> logout.logoutUrl("/api/auth/logout").logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
				// HSTS goes out on HTTPS requests only (TLS ends at the proxy, see server.forward-headers-strategy). No
				// referrer: the activation and reset pages carry their one-time code in the address. Spring's other default
				// headers stay (nosniff, no framing, no caching).
				.headers(headers -> headers
						.httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(Duration.ofDays(365).toSeconds()).includeSubDomains(true).preload(false))
						.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)));

		return http.build();
	}
}
