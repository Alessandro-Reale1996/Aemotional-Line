package com.aemotionalline.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Checks every answer of the HTTP tests against frontend/openapi.json, the spec the frontend generates its types from
 * (CLAUDE.md, frontend point 2 and F1): the route and the status are declared, every required field is there, null only
 * where it is declared, types, formats and enum values match, and no field is left undeclared (a helper method of a
 * record would otherwise reach the JSON unnoticed). Errors are checked against the Problem schema, whatever the route.
 * A small validator of the few rules the spec uses, rather than a library: the Java ones follow OpenAPI 3.1 unevenly.
 */
final class ApiContract
{
	/** Routes outside the spec: the test endpoints, and the logout, which a security filter answers. */
	private static final List<String> OUTSIDE_THE_SPEC = List.of("/api/test/", "/api/admin/probe", "/api/auth/logout");
	private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static ApiContract loaded;

	private record Route(String template, Pattern pattern, int literals)
	{
	}

	private final JsonNode spec;
	private final List<Route> routes = new ArrayList<>();

	private ApiContract(JsonNode spec)
	{
		this.spec = spec;

		for (String template : spec.path("paths").propertyNames())
		{
			String regex = Pattern.quote(template).replaceAll("\\{[^}/]+\\}", "\\\\E[^/]+\\\\Q");
			int literals = template.replaceAll("\\{[^}/]+\\}", "").length();
			routes.add(new Route(template, Pattern.compile(regex), literals));
		}

		routes.sort(Comparator.comparingInt(Route::literals).reversed());
	}

	static synchronized ApiContract get()
	{
		if (loaded == null)
		{
			if (!Files.exists(ApiSpecHttpTest.COMMITTED))
			{
				throw new AssertionError(ApiSpecHttpTest.COMMITTED + " is missing: run ApiSpecHttpTest and copy the spec it leaves in "
						+ ApiSpecHttpTest.FRESH);
			}

			try
			{
				loaded = new ApiContract(JSON.readTree(Files.readString(ApiSpecHttpTest.COMMITTED)));
			}
			catch (IOException failure)
			{
				throw new UncheckedIOException(failure);
			}
		}

		return loaded;
	}

	/** Fails the test if this answer breaks the contract. */
	void check(String method, String path, int status, Optional<String> contentType, String body)
	{
		if (!path.startsWith("/api/"))
		{
			return;
		}

		String where = method + " " + path + " -> " + status;
		boolean json = contentType.filter(type -> type.contains("json")).isPresent();

		if (status >= 400)
		{
			if (json)
			{
				ensureValid(read(body, where), schema("Problem"), where);
			}

			return;
		}

		Optional<Route> route = routes.stream().filter(candidate -> candidate.pattern().matcher(path).matches()).findFirst();

		if (route.isEmpty())
		{
			if (OUTSIDE_THE_SPEC.stream().noneMatch(path::startsWith))
			{
				throw new AssertionError(where + ": the route is not in " + ApiSpecHttpTest.COMMITTED);
			}

			return;
		}

		JsonNode response = spec.path("paths").path(route.get().template()).path(method.toLowerCase()).path("responses").path(String.valueOf(status));

		if (response.isMissingNode())
		{
			throw new AssertionError(where + ": the spec doesn't declare this status for " + route.get().template());
		}

		if (response.path("content").isEmpty())
		{
			if (!body.isEmpty())
			{
				throw new AssertionError(where + ": the spec declares no body, the answer has one");
			}

			return;
		}

		if (!json)
		{
			throw new AssertionError(where + ": the spec declares a JSON body, the answer is " + contentType.orElse("empty"));
		}

		ensureValid(read(body, where), response.path("content").values().iterator().next().path("schema"), where);
	}

	private void ensureValid(JsonNode value, JsonNode schema, String where)
	{
		List<String> problems = new ArrayList<>();
		validate(value, schema, "$", problems);

		if (!problems.isEmpty())
		{
			throw new AssertionError(where + " breaks the contract of " + ApiSpecHttpTest.COMMITTED + ":\n  "
					+ String.join("\n  ", problems.subList(0, Math.min(problems.size(), 10))));
		}
	}

	private void validate(JsonNode value, JsonNode schema, String at, List<String> problems)
	{
		if (schema.has("$ref"))
		{
			String ref = schema.get("$ref").asString();
			validate(value, schema(ref.substring(ref.lastIndexOf('/') + 1)), at, problems);

			return;
		}

		if (schema.has("oneOf"))
		{
			List<List<String>> failures = new ArrayList<>();

			for (JsonNode alternative : schema.get("oneOf"))
			{
				List<String> errors = new ArrayList<>();
				validate(value, alternative, at, errors);
				failures.add(errors);
			}

			long matching = failures.stream().filter(List::isEmpty).count();

			if (matching != 1)
			{
				problems.add(at + ": matches " + matching + " of the alternatives" + (matching == 0 ? " (" + failures.getFirst() + ")" : ""));
			}

			return;
		}

		Set<String> types = types(schema);

		if (value.isNull())
		{
			if (!types.contains("null"))
			{
				problems.add(at + ": null, where the spec doesn't allow it");
			}

			return;
		}

		if (schema.has("enum"))
		{
			boolean listed = false;

			for (JsonNode allowed : schema.get("enum"))
			{
				listed |= allowed.equals(value);
			}

			if (!listed)
			{
				problems.add(at + ": " + value + " is not one of the values the spec lists");
			}
		}

		String type = typeOf(value);

		if (!types.isEmpty() && !types.contains(type) && !(type.equals("integer") && types.contains("number")))
		{
			problems.add(at + ": a " + type + ", where the spec says " + types);

			return;
		}

		switch (type)
		{
			case "object" -> validateObject(value, schema, at, problems);
			case "array" -> value.forEach(element -> validate(element, schema.path("items"), at + "[]", problems));
			case "string" -> validateFormat(value.asString(), schema.path("format").asString(), at, problems);
			default ->
			{
			}
		}
	}

	private void validateObject(JsonNode value, JsonNode schema, String at, List<String> problems)
	{
		JsonNode properties = schema.path("properties");
		JsonNode additional = schema.path("additionalProperties");

		for (JsonNode required : schema.path("required"))
		{
			if (!value.has(required.asString()))
			{
				problems.add(at + "." + required.asString() + ": missing, the spec requires it");
			}
		}

		for (String name : value.propertyNames())
		{
			if (properties.has(name))
			{
				validate(value.get(name), properties.get(name), at + "." + name, problems);
			}
			else if (additional.isObject())
			{
				validate(value.get(name), additional, at + "." + name, problems);
			}
			else
			{
				problems.add(at + "." + name + ": not declared in the spec");
			}
		}
	}

	private static void validateFormat(String text, String format, String at, List<String> problems)
	{
		try
		{
			switch (format)
			{
				case "uuid" ->
				{
					if (!UUID.matcher(text).matches())
					{
						problems.add(at + ": not a UUID");
					}
				}
				case "date-time" -> Instant.parse(text);
				case "time" -> LocalTime.parse(text);
				case "duration" -> Duration.parse(text);
				default ->
				{
				}
			}
		}
		catch (DateTimeParseException wrong)
		{
			problems.add(at + ": not a valid " + format);
		}
	}

	private JsonNode schema(String name)
	{
		JsonNode schema = spec.path("components").path("schemas").path(name);

		if (schema.isMissingNode())
		{
			throw new AssertionError("No schema " + name + " in " + ApiSpecHttpTest.COMMITTED);
		}

		return schema;
	}

	// The types a schema allows: its "type", a string or a list; none means any, unless it has properties (an object).
	private static Set<String> types(JsonNode schema)
	{
		Set<String> types = new LinkedHashSet<>();
		JsonNode type = schema.path("type");

		if (type.isArray())
		{
			type.forEach(item -> types.add(item.asString()));
		}
		else if (type.isString())
		{
			types.add(type.asString());
		}
		else if (schema.has("properties"))
		{
			types.add("object");
		}

		return types;
	}

	private static String typeOf(JsonNode value)
	{
		if (value.isObject())
		{
			return "object";
		}

		if (value.isArray())
		{
			return "array";
		}

		if (value.isString())
		{
			return "string";
		}

		if (value.isBoolean())
		{
			return "boolean";
		}

		return value.isIntegralNumber() ? "integer" : "number";
	}

	private static JsonNode read(String body, String where)
	{
		try
		{
			return JSON.readTree(body);
		}
		catch (JacksonException malformed)
		{
			throw new AssertionError(where + ": the answer is not JSON");
		}
	}
}
