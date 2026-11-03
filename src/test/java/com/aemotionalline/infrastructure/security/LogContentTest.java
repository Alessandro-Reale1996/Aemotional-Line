package com.aemotionalline.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.aemotionalline.CapturingMailer;
import com.aemotionalline.CapturingMailer.Kind;
import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.application.ClosingService;
import com.aemotionalline.application.ConversationService;
import com.aemotionalline.application.InvitationService;
import com.aemotionalline.application.MessageDraft;
import com.aemotionalline.application.NegotiationService;
import com.aemotionalline.application.ParagraphDraft;
import com.aemotionalline.application.PasswordHasher;
import com.aemotionalline.application.UserService;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.conversation.OutcomeDeclaration.Outcome;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleInvitationId;
import com.aemotionalline.domain.message.MessageId;
import com.aemotionalline.domain.message.ParagraphType;
import com.aemotionalline.domain.negotiation.NegotiationId;
import com.aemotionalline.domain.user.EmailAddress;
import com.aemotionalline.domain.user.Role;
import com.aemotionalline.domain.user.User;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.user.UserRepository;

/**
 * No personal content and no secret reaches the logs (CLAUDE.md, security point 5c and S5): a whole scenario runs, over
 * HTTP and through the services, with a random marker in every name, email, password, text and justification, plus
 * failed logins and malformed requests; neither the marker nor any one-time code may appear in the output.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({ TestcontainersConfiguration.class, SecurityHttpTest.AdminProbe.class })
@ExtendWith(OutputCaptureExtension.class)
public class LogContentTest
{
	private static final LocalTime NOON = LocalTime.NOON;

	@LocalServerPort
	private int port;

	@Autowired
	private UserRepository users;

	@Autowired
	private PasswordHasher hasher;

	@Autowired
	private UserService userService;

	@Autowired
	private InvitationService invitationService;

	@Autowired
	private NegotiationService negotiationService;

	@Autowired
	private ConversationService conversationService;

	@Autowired
	private ClosingService closingService;

	@Autowired
	private CapturingMailer mailer;

	@Test
	void shouldKeepPersonalContentAndSecretsOutOfTheLogs(CapturedOutput output)
	{
		String marker = "zqx" + new Random().ints(8, 'a', 'z' + 1).mapToObj(letter -> String.valueOf((char) letter)).collect(Collectors.joining());
		String password = "password " + marker + " one";
		List<String> secrets = new ArrayList<>();

		// Accounts: the administrator creates a therapist, who invites a couple; everyone activates and logs in over HTTP.
		User administrator = User.pending(UserId.random(), new EmailAddress(marker + "-admin@example.org"), "Admin " + marker, Role.ADMINISTRATOR);
		administrator.activate(hasher.hash(password));
		users.save(administrator);
		EmailAddress therapistEmail = new EmailAddress(marker + "-therapist@example.org");
		UserId therapist = userService.createTherapist(administrator.getId(), therapistEmail, "Dott " + marker);
		activate(therapistEmail, password, secrets);
		EmailAddress emailA = new EmailAddress(marker + "-anna@example.org");
		EmailAddress emailB = new EmailAddress(marker + "-bruno@example.org");
		CoupleInvitationId invitation = invitationService.invite(therapist, emailA, "Anna " + marker, emailB, "Bruno " + marker);
		activate(emailA, password, secrets);
		activate(emailB, password, secrets);
		UserId a = users.findByEmail(emailA).orElseThrow().getId();
		UserId b = users.findByEmail(emailB).orElseThrow().getId();
		invitationService.accept(a, invitation);
		invitationService.accept(b, invitation);
		ApiClient client = new ApiClient(port);
		assertEquals(200, client.logIn(therapistEmail.value(), password).status());
		assertEquals(200, client.logIn(emailA.value(), password).status());
		secrets.add(client.cookie(ApiClient.SESSION));
		secrets.add(client.cookie(ApiClient.CSRF));

		// Failed logins and malformed requests, each carrying the marker.
		ApiClient stranger = new ApiClient(port);
		stranger.logIn(emailA.value(), "wrong " + marker);
		stranger.logIn(marker + "-nobody@example.org", password);
		stranger.post("/api/auth/login", "{\"email\":\"" + emailA.value() + "\",\"password\":" + marker + "}");
		stranger.post("/api/auth/login", "{\"email\":\"" + emailA.value() + "\",\"password\":\"" + marker + "\"");
		stranger.post("/api/auth/login", marker);
		stranger.post("/api/auth/activate", "{\"code\":\"" + marker + "\",\"password\":\"" + password + "\"}");
		stranger.post("/api/auth/password-reset", "{\"email\":\"" + marker + "-nobody@example.org\"}");
		stranger.post("/api/auth/password-reset", "{\"email\":" + marker + "}");
		stranger.post("/api/auth/password-reset/confirm", "{\"code\":\"" + marker + "\",\"password\":\"" + password + "\"}");
		stranger.postWithoutCsrfHeader("/api/auth/login", "{\"email\":\"" + emailA.value() + "\",\"password\":\"" + password + "\"}");

		// A password reset, done over HTTP.
		stranger.post("/api/auth/password-reset", "{\"email\":\"" + emailB.value() + "\"}");
		String resetCode = mailer.lastCode(emailB, Kind.PASSWORD_RESET).orElseThrow();
		secrets.add(resetCode);
		stranger.post("/api/auth/password-reset/confirm", "{\"code\":\"" + resetCode + "\",\"password\":\"new " + password + "\"}");

		// The therapy itself: the agreement, messages, a specific discussion closed through the therapist.
		CoupleId couple = invitationService.view(a, invitation).coupleId().orElseThrow();
		NegotiationId start = negotiationService.startAgreementNegotiation(a, couple, "Agreement " + marker);
		negotiationService.acceptRequest(b, start);
		negotiationService.sendProposal(a, start, "Because " + marker);
		ConversationId conversation = negotiationService.acceptProposal(b, start).conversationId();
		MessageId first = send(a, conversation, 0, marker);
		conversationService.readMessage(b, conversation, 0, first, NOON, null);
		send(b, conversation, 0, marker);
		NegotiationId branch = negotiationService.startDiscussionNegotiation(a, conversation, "Holidays " + marker, "Where " + marker, Optional.empty());
		negotiationService.acceptRequest(b, branch);
		negotiationService.sendProposal(a, branch, "Ready " + marker);
		negotiationService.acceptProposal(b, branch);
		closingService.requestClosing(a, conversation, 1);
		closingService.refuseClosing(b, conversation, 1);
		closingService.requestTherapistClosing(a, conversation, 1, "B never agrees " + marker);
		closingService.confirmTherapistClosing(therapist, conversation, 1);
		closingService.declareOutcome(a, conversation, 1, Outcome.RESOLVED, "Done " + marker);
		closingService.declareOutcome(b, conversation, 1, Outcome.STALLED, "Not done " + marker);
		assertEquals(204, client.post("/api/auth/logout", "").status());

		String logs = output.getAll();
		assertFalse(logs.contains(marker), () -> "personal content in the logs:\n" + linesWith(logs, marker));

		for (String secret : secrets)
		{
			assertFalse(logs.contains(secret), () -> "a secret in the logs:\n" + linesWith(logs, secret));
		}
	}

	// Activates the account through its emailed link, over HTTP, and keeps the code among the secrets.
	private void activate(EmailAddress email, String password, List<String> secrets)
	{
		String code = mailer.lastCode(email, Kind.ACTIVATION).orElseThrow();
		secrets.add(code);
		ApiClient client = new ApiClient(port);
		client.get("/api/auth/csrf");

		assertEquals(204, client.post("/api/auth/activate", "{\"code\":\"" + code + "\",\"password\":\"" + password + "\"}").status());
	}

	private MessageId send(UserId sender, ConversationId conversation, int discussion, String marker)
	{
		return conversationService.sendMessage(sender, conversation, discussion, new MessageDraft(List.of(
				new ParagraphDraft(ParagraphType.SIMPLE, "Title " + marker, "Subtitle " + marker, "Body " + marker, List.of(), List.of(), Optional.empty()))),
				NOON, null);
	}

	private static String linesWith(String logs, String text)
	{
		return logs.lines().filter(line -> line.contains(text)).collect(Collectors.joining("\n"));
	}
}
