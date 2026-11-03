package com.aemotionalline.infrastructure.web;

/** The answer to a creation (201): the id of the new thing, which the server chose (CLAUDE.md, application point 3a). */
public record Created<T>(T id)
{
}
