package com.aemotionalline.application.view;

import com.aemotionalline.domain.user.AccountStatus;
import com.aemotionalline.domain.user.UserId;

/** A therapist's account, as the administrator who manages them sees it. */
public record TherapistView(UserId id, String email, String displayName, AccountStatus status)
{
}
