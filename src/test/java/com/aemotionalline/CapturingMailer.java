package com.aemotionalline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.aemotionalline.application.Mailer;
import com.aemotionalline.domain.user.EmailAddress;

/** Test double: keeps the emails instead of sending them, so tests can read the codes and "click" the links. */
public class CapturingMailer implements Mailer
{
	public enum Kind
	{
		ACTIVATION,
		PASSWORD_RESET,
		COUPLE_INVITATION
	}

	public record Sent(Kind kind, EmailAddress to, String displayName, String code)
	{
	}

	private final List<Sent> sent = Collections.synchronizedList(new ArrayList<>());

	@Override
	public void sendActivationLink(EmailAddress to, String displayName, String code)
	{
		sent.add(new Sent(Kind.ACTIVATION, to, displayName, code));
	}

	@Override
	public void sendPasswordResetLink(EmailAddress to, String displayName, String code)
	{
		sent.add(new Sent(Kind.PASSWORD_RESET, to, displayName, code));
	}

	@Override
	public void sendCoupleInvitation(EmailAddress to, String displayName, String therapistName)
	{
		sent.add(new Sent(Kind.COUPLE_INVITATION, to, displayName, null));
	}

	public List<Sent> to(EmailAddress address)
	{
		synchronized (sent)
		{
			return sent.stream().filter(mail -> mail.to().equals(address)).toList();
		}
	}

	/** The code of the last email of that kind sent to the address. */
	public Optional<String> lastCode(EmailAddress address, Kind kind)
	{
		return to(address).stream().filter(mail -> mail.kind() == kind).reduce((first, second) -> second).map(Sent::code);
	}
}
