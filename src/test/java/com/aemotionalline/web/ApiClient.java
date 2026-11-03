package com.aemotionalline.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.aemotionalline.infrastructure.security.SecurityConfiguration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A minimal browser for the HTTP tests: it keeps the cookies the server sets (and drops the ones it expires), sends them
 * back, and, like the React app, copies the CSRF cookie into the X-XSRF-TOKEN header of every POST, PUT and DELETE. It
 * ignores the cookies' attributes (Secure, SameSite...): the tests check those on the Set-Cookie headers. The answers'
 * JSON can be read, to take ids from one answer and chain the next request.
 */
public final class ApiClient
{
	public static final String SESSION = "__Host-SESSION";
	public static final String CSRF = SecurityConfiguration.CSRF_COOKIE;

	private static final JsonMapper JSON = JsonMapper.builder().build();

	public record Response(int status, String body, List<String> setCookies, HttpHeaders headers)
	{
		/** The Set-Cookie header of that cookie, if this response set it. */
		public Optional<String> setCookie(String name)
		{
			return setCookies.stream().filter(cookie -> cookie.startsWith(name + "=")).findFirst();
		}

		public JsonNode json()
		{
			return JSON.readTree(body);
		}

		/** The stable code of an error answer (Problem Details). */
		public String code()
		{
			return json().path("code").asString();
		}
	}

	private final HttpClient http = HttpClient.newHttpClient();
	private final String base;
	private final Map<String, String> cookies = new LinkedHashMap<>();

	public ApiClient(int port)
	{
		this.base = "http://localhost:" + port;
	}

	public Response get(String path)
	{
		return send(HttpRequest.newBuilder(URI.create(base + path)).GET(), true);
	}

	/** A GET with extra headers, given as name, value, name, value... */
	public Response get(String path, String... headers)
	{
		return send(HttpRequest.newBuilder(URI.create(base + path)).headers(headers).GET(), true);
	}

	public Response post(String path, String json)
	{
		return send(HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json)), true);
	}

	public Response put(String path, String json)
	{
		return send(HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json")
				.PUT(HttpRequest.BodyPublishers.ofString(json)), true);
	}

	public Response delete(String path)
	{
		return send(HttpRequest.newBuilder(URI.create(base + path)).DELETE(), true);
	}

	/** A request with any method, and a JSON body unless it is null (e.g. a method the endpoint doesn't take). */
	public Response send(String method, String path, String json)
	{
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path));

		return send(json == null ? request.method(method, HttpRequest.BodyPublishers.noBody())
				: request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json)), true);
	}

	/** A POST without the CSRF header, as a forged request from another site would be. */
	public Response postWithoutCsrfHeader(String path, String json)
	{
		return send(HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json)), false);
	}

	/** Gets the CSRF cookie, then logs in. */
	public Response logIn(String email, String password)
	{
		get("/api/auth/csrf");

		return post("/api/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
	}

	public String cookie(String name)
	{
		return cookies.get(name);
	}

	public void setCookie(String name, String value)
	{
		cookies.put(name, value);
	}

	private Response send(HttpRequest.Builder request, boolean withCsrfHeader)
	{
		if (!cookies.isEmpty())
		{
			request.header("Cookie", cookies.entrySet().stream().map(cookie -> cookie.getKey() + "=" + cookie.getValue()).collect(Collectors.joining("; ")));
		}

		if (withCsrfHeader && cookies.containsKey(CSRF))
		{
			request.header("X-XSRF-TOKEN", cookies.get(CSRF));
		}

		try
		{
			HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
			List<String> setCookies = response.headers().allValues("Set-Cookie");
			setCookies.forEach(this::keep);

			return new Response(response.statusCode(), response.body(), setCookies, response.headers());
		}
		catch (IOException failure)
		{
			throw new UncheckedIOException(failure);
		}
		catch (InterruptedException interrupted)
		{
			Thread.currentThread().interrupt();
			throw new IllegalStateException(interrupted);
		}
	}

	// "NAME=value; Path=/; Max-Age=0; ..." : an empty value or a Max-Age of 0 expires the cookie.
	private void keep(String setCookie)
	{
		String[] parts = setCookie.split(";");
		String[] pair = parts[0].split("=", 2);
		String name = pair[0].trim();
		String value = pair.length > 1 ? pair[1].trim() : "";
		boolean expired = value.isEmpty();

		for (int index = 1; index < parts.length; index++)
		{
			expired |= parts[index].trim().equalsIgnoreCase("Max-Age=0");
		}

		if (expired)
		{
			cookies.remove(name);
		}
		else
		{
			cookies.put(name, value);
		}
	}
}
