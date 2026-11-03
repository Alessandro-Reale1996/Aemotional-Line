package com.aemotionalline.application.view;

import com.aemotionalline.domain.user.Role;
import com.aemotionalline.domain.user.UserId;

/** The logged-in user's own account, as the app shows it. */
public record AccountView(UserId id, String email, String displayName, Role role)
{
}
