package com.aemotionalline.infrastructure.mail;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.aemotionalline.application.Mailer;
import com.aemotionalline.domain.user.EmailAddress;

/**
 * Sends the application's emails through SMTP (spring.mail.*). Without a configured mail server the application still
 * starts, but sending fails loudly instead of losing the email. Nothing here is logged: the links are secrets.
 */
@Component
public class SmtpMailer implements Mailer
{
	private final ObjectProvider<JavaMailSender> sender;
	private final String baseUrl;
	private final String from;

	SmtpMailer(ObjectProvider<JavaMailSender> sender, @Value("${aemotional.base-url}") String baseUrl, @Value("${aemotional.mail.from}") String from)
	{
		this.sender = sender;
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.from = from;
	}

	@Override
	public void sendActivationLink(EmailAddress to, String displayName, String code)
	{
		send(to, "Attiva il tuo account Aemotional-Line", "Ciao " + displayName + ",\n\n"
				+ "per attivare il tuo account e scegliere la password apri questo link (vale una sola volta e scade tra 7 giorni):\n"
				+ baseUrl + "/attiva?codice=" + code + "\n\nSe non ti aspettavi questa email, ignorala.");
	}

	@Override
	public void sendPasswordResetLink(EmailAddress to, String displayName, String code)
	{
		send(to, "Reimposta la password di Aemotional-Line", "Ciao " + displayName + ",\n\n"
				+ "per scegliere una nuova password apri questo link (vale una sola volta e scade tra un'ora):\n"
				+ baseUrl + "/reimposta-password?codice=" + code + "\n\nSe non l'hai chiesto tu, ignora questa email: la password non cambia.");
	}

	@Override
	public void sendCoupleInvitation(EmailAddress to, String displayName, String therapistName)
	{
		send(to, "Invito a formare una coppia su Aemotional-Line", "Ciao " + displayName + ",\n\n"
				+ therapistName + " ti ha invitato a formare una coppia su Aemotional-Line.\n"
				+ "Accedi per accettare o rifiutare l'invito: " + baseUrl + "\n");
	}

	private void send(EmailAddress to, String subject, String text)
	{
		JavaMailSender mail = sender.getIfAvailable();

		if (mail == null)
		{
			throw new IllegalStateException("No mail server is configured (spring.mail.host): the email can't be sent.");
		}

		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(to.value());
		message.setSubject(subject);
		message.setText(text);

		mail.send(message);
	}
}
