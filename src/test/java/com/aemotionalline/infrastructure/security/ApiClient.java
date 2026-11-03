package com.aemotionalline.infrastructure.security;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A minimal browser for the HTTP security tests: it keeps the cookies the server sets (and drops the ones it expires),
 * sends them back, and, like the React app, copies the XSRF-TOKEN cookie into the X-XSRF-TOKEN header of every POST.
 */
final class ApiClient
{
	record Response(int status, String body, List<String> setCookies)
	{
	}

	private final HttpClient http = HttpClient.newHttpClient();
	private final String base;
	private final Map<String, String> cookies = new LinkedHashMap<>();

	ApiClient(int port)
	{
		this.base = "http://localhost:" + port;
	}

	Response get(String path)
	{
		return send(HttpRequest.newBuilder(URI.create(base + path)).GET(), true);
	}

	Response post(String path, String json)
	{
		return send(HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json)), true);
	}

	/** A POST without the CSRF header, as a forged request from another site would be. */
	Response postWithoutCsrfHeader(String path, String json)
	{
		return send(HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json)), false);
	}

	/** Gets the CSRF cookie, then logs in. */
	Response logIn(String email, String password)
	{
		get("/api/auth/csrf");

		return post("/api/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
	}

	String cookie(String name)
	{
		return cookies.get(name);
	}

	void setCookie(String name, String value)
	{
		cookies.put(name, value);
	}

	private Response send(HttpRequest.Builder request, boolean withCsrfHeader)
	{
		if (!cookies.isEmpty())
		{
			request.header("Cookie", cookies.entrySet().stream().map(cookie -> cookie.getKey() + "=" + cookie.getValue()).collect(Collectors.joining("; ")));
		}

		if (withCsrfHeader && cookies.containsKey("XSRF-TOKEN"))
		{
			request.header("X-XSRF-TOKEN", cookies.get("XSRF-TOKEN"));
		}

		try
		{
			HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
			List<String> setCookies = response.headers().allValues("Set-Cookie");
			setCookies.forEach(this::keep);

			return new Response(response.statusCode(), response.body(), setCookies);
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
