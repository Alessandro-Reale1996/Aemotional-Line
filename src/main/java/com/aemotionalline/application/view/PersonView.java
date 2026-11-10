package com.aemotionalline.application.view;

import org.jspecify.annotations.Nullable;

import com.aemotionalline.domain.user.UserId;

/** A member of a couple as the others see them; displayName is null when the account was deleted (after the end). */
public record PersonView(UserId id, @Nullable String displayName)
{
}
