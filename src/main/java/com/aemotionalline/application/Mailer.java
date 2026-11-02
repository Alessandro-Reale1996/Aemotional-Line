package com.aemotionalline.application;

import com.aemotionalline.domain.user.EmailAddress;

/**
 * The emails the application sends. The application hands over the one-time codes; how a link is built from a code and
 * how the email looks is the implementation's business (SMTP in production, a capturing fake in tests).
 */
public interface Mailer
{
	void sendActivationLink(EmailAddress to, String displayName, String code);

	void sendPasswordResetLink(EmailAddress to, String displayName, String code);

	/** A therapist invited the person to form a couple; they answer after logging in. */
	void sendCoupleInvitation(EmailAddress to, String displayName, String therapistName);
}
