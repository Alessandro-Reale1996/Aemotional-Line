package com.aemotionalline.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.aemotionalline.CapturingMailer;
import com.aemotionalline.CapturingMailer.Kind;
import com.aemotionalline.RecordingSessions;
import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleInvitation;
import com.aemotionalline.domain.couple.CoupleInvitationId;
import com.aemotionalline.domain.couple.CoupleInvitationRepository;
import com.aemotionalline.domain.couple.CoupleInvitationStatus;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.user.AccountStatus;
import com.aemotionalline.domain.user.EmailAddress;
import com.aemotionalline.domain.user.Role;
import com.aemotionalline.domain.user.User;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.user.UserRepository;

/** Accounts end to end on MariaDB: creation, activation and reset through one-time links, disabling, deletion. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class UserServiceTest
{
	private static final String GOOD_PASSWORD = "un cavallo sul tetto blu";
	private static final String INVALID_LINK = "The link is not valid or has expired.";

	@Autowired
	private UserService userService;

	@Autowired
	private InvitationService invitationService;

	@Autowired
	private UserRepository users;

	@Autowired
	private CoupleRepository couples;

	@Autowired
	private CoupleInvitationRepository invitations;

	@Autowired
	private OneTimeLinkRepository links;

	@Autowired
	private PasswordHasher hasher;

	@Autowired
	private CapturingMailer mailer;

	@Autowired
	private RecordingSessions sessions;

	@Autowired
	private Clock clock;

	private UserId administrator;

	@BeforeEach
	void createAnAdministrator()
	{
		administrator = UserId.random();
		TestCouples.activeAccount(users, administrator, Role.ADMINISTRATOR);
	}

	@Test
	void shouldCreateATherapistWhoActivatesTheAccountThroughTheEmailedLink()
	{
		EmailAddress email = freshEmail();
		UserId therapist = userService.createTherapist(administrator, email, "Dott.ssa Rossi");

		User pending = users.findById(therapist).orElseThrow();
		assertEquals(Role.THERAPIST, pending.getRole());
		assertEquals(AccountStatus.PENDING, pending.getStatus());
		assertTrue(pending.getPasswordHash().isEmpty());

		String code = mailer.lastCode(email, Kind.ACTIVATION).orElseThrow();
		userService.activate(code, GOOD_PASSWORD);

		User active = users.findById(therapist).orElseThrow();
		assertEquals(AccountStatus.ACTIVE, active.getStatus());
		assertTrue(hasher.matches(GOOD_PASSWORD, active.getPasswordHash().orElseThrow()));
		assertFalse(active.getPasswordHash().orElseThrow().value().contains(GOOD_PASSWORD));

		// The link works once.
		InvalidRequestException reused = assertThrows(InvalidRequestException.class, () -> userService.activate(code, GOOD_PASSWORD));
		assertEquals(INVALID_LINK, reused.getMessage());
	}

	@Test
	void shouldLetOnlyAnActiveAdministratorCreateTherapists()
	{
		UserId therapist = UserId.random();
		TestCouples.activeAccount(users, therapist, Role.THERAPIST);

		assertThrows(NotFoundException.class, () -> userService.createTherapist(therapist, freshEmail(), "Someone"));
		assertThrows(NotFoundException.class, () -> userService.createTherapist(UserId.random(), freshEmail(), "Someone"));
	}

	@Test
	void shouldRejectASecondAccountWithTheSameEmail()
	{
		EmailAddress email = freshEmail();
		userService.createTherapist(administrator, email, "First");

		assertThrows(DomainException.class,
				() -> userService.createTherapist(administrator, new EmailAddress(email.value().toUpperCase()), "Second"));
	}

	@Test
	void shouldRejectWeakPasswordsAndKeepTheLinkUsable()
	{
		EmailAddress email = new EmailAddress("weak-" + UUID.randomUUID() + "@example.org");
		UserId therapist = userService.createTherapist(administrator, email, "Dott. Bianchi");
		String code = mailer.lastCode(email, Kind.ACTIVATION).orElseThrow();

		assertPasswordRefused(RequestErrorCode.PASSWORD_TOO_SHORT, () -> userService.activate(code, "k".repeat(PasswordPolicy.MIN_LENGTH - 1)));
		assertPasswordRefused(RequestErrorCode.PASSWORD_TOO_LONG, () -> userService.activate(code, "x".repeat(PasswordPolicy.MAX_LENGTH + 1)));
		// 40 characters, but 80 bytes: past bcrypt's 72.
		assertPasswordRefused(RequestErrorCode.PASSWORD_TOO_LONG, () -> userService.activate(code, "è".repeat(40)));
		assertPasswordRefused(RequestErrorCode.PASSWORD_TOO_EASY, () -> userService.activate(code, "AmoreMio1234"));
		assertPasswordRefused(RequestErrorCode.PASSWORD_TOO_EASY, () -> userService.activate(code, email.value().toUpperCase()));
		assertPasswordRefused(RequestErrorCode.PASSWORD_TOO_SHORT, () -> userService.activate(code, null));

		assertEquals(AccountStatus.PENDING, users.findById(therapist).orElseThrow().getStatus());
		assertDoesNotThrow(() -> userService.activate(code, GOOD_PASSWORD));
	}

	@Test
	void shouldGiveTheSameErrorForEveryBadLink()
	{
		EmailAddress email = freshEmail();
		UserId therapist = userService.createTherapist(administrator, email, "Dott. Verdi");
		String activation = mailer.lastCode(email, Kind.ACTIVATION).orElseThrow();

		String expired = OneTimeCodes.newCode();
		Instant longAgo = clock.instant().minus(Duration.ofDays(8));
		links.save(new OneTimeLink(OneTimeCodes.hash(expired), therapist, OneTimeLink.Purpose.ACTIVATION, longAgo,
				longAgo.plus(UserService.ACTIVATION_VALIDITY), Optional.empty()));

		assertInvalidLink(() -> userService.activate(OneTimeCodes.newCode(), GOOD_PASSWORD));
		assertInvalidLink(() -> userService.activate(null, GOOD_PASSWORD));
		assertInvalidLink(() -> userService.activate(" ", GOOD_PASSWORD));
		assertInvalidLink(() -> userService.activate(expired, GOOD_PASSWORD));
		// An activation link is not a reset link.
		assertInvalidLink(() -> userService.resetPassword(activation, GOOD_PASSWORD));

		userService.activate(activation, GOOD_PASSWORD);
		assertInvalidLink(() -> userService.activate(activation, GOOD_PASSWORD));
	}

	@Test
	void shouldAnswerAResetRequestTheSameWayWhateverTheEmail()
	{
		EmailAddress unknown = freshEmail();
		EmailAddress pending = freshEmail();
		userService.createTherapist(administrator, pending, "Not yet active");

		assertDoesNotThrow(() -> userService.requestPasswordReset(unknown));
		assertDoesNotThrow(() -> userService.requestPasswordReset(pending));

		assertTrue(mailer.to(unknown).isEmpty());
		assertTrue(mailer.lastCode(pending, Kind.PASSWORD_RESET).isEmpty());
	}

	@Test
	void shouldResetThePasswordEndTheSessionsAndInvalidateEarlierResetLinks()
	{
		EmailAddress email = freshEmail();
		UserId therapist = userService.createTherapist(administrator, email, "Dott.ssa Neri");
		userService.activate(mailer.lastCode(email, Kind.ACTIVATION).orElseThrow(), GOOD_PASSWORD);

		userService.requestPasswordReset(email);
		String first = mailer.lastCode(email, Kind.PASSWORD_RESET).orElseThrow();
		userService.requestPasswordReset(email);
		String second = mailer.lastCode(email, Kind.PASSWORD_RESET).orElseThrow();

		assertInvalidLink(() -> userService.resetPassword(first, "una nuova password lunga"));

		userService.resetPassword(second, "una nuova password lunga");

		User user = users.findById(therapist).orElseThrow();
		assertTrue(hasher.matches("una nuova password lunga", user.getPasswordHash().orElseThrow()));
		assertFalse(hasher.matches(GOOD_PASSWORD, user.getPasswordHash().orElseThrow()));
		assertTrue(sessions.wereEnded(therapist));
		assertInvalidLink(() -> userService.resetPassword(second, "ancora un'altra password"));
	}

	@Test
	void shouldDisableATherapistEndingTheirSessionsAndCouplesAndWithdrawingTheirInvitations()
	{
		UserId therapist = UserId.random();
		UserId a = UserId.random();
		UserId b = UserId.random();
		CoupleId couple = TestCouples.create(users, couples, therapist, a, b);
		UserId otherTherapist = UserId.random();
		CoupleId otherCouple = TestCouples.create(users, couples, otherTherapist, UserId.random(), UserId.random());
		CoupleInvitationId pending = invitationService.invite(therapist, freshEmail(), "Carla", freshEmail(), "Dario");
		Instant longAgo = clock.instant().minus(Duration.ofDays(8));
		CoupleInvitation expired = CoupleInvitation.create(CoupleInvitationId.random(), therapist, UserId.random(), UserId.random(), longAgo,
				longAgo.plus(InvitationService.VALIDITY));
		invitations.save(expired);

		userService.disableTherapist(administrator, therapist, TestCouples.PASSWORD);

		assertEquals(AccountStatus.DISABLED, users.findById(therapist).orElseThrow().getStatus());
		assertTrue(sessions.wereEnded(therapist));
		assertFalse(couples.findById(couple).orElseThrow().isActive());
		assertTrue(couples.findById(otherCouple).orElseThrow().isActive());
		assertEquals(CoupleInvitationStatus.WITHDRAWN, invitations.findById(pending).orElseThrow().getStatus());
		// An expired invitation can't be accepted anyway: it is left as it was.
		assertEquals(CoupleInvitationStatus.PENDING, invitations.findById(expired.getId()).orElseThrow().getStatus());
		assertThrows(NotFoundException.class, () -> invitationService.invite(therapist, freshEmail(), "E", freshEmail(), "F"));
		// The patients can be invited again by another therapist.
		assertDoesNotThrow(() -> invitationService.invite(otherTherapist, users.findById(a).orElseThrow().getEmail(), "A",
				users.findById(b).orElseThrow().getEmail(), "B"));
	}

	@Test
	void shouldAskThePasswordBeforeDisablingATherapistOrDeletingOnesAccount()
	{
		UserId therapist = UserId.random();
		TestCouples.activeAccount(users, therapist, Role.THERAPIST);
		UserId patient = UserId.random();
		TestCouples.activeAccount(users, patient, Role.PATIENT);

		assertThrows(IncorrectPasswordException.class, () -> userService.disableTherapist(administrator, therapist, "not the password"));
		assertThrows(IncorrectPasswordException.class, () -> userService.deleteOwnPatientAccount(patient, "not the password"));

		assertEquals(AccountStatus.ACTIVE, users.findById(therapist).orElseThrow().getStatus());
		assertTrue(users.findById(patient).isPresent());
		// Each wrong password counted as a failed login of the account that gave it.
		assertEquals(1, users.findById(administrator).orElseThrow().getFailedLogins());
		assertEquals(1, users.findById(patient).orElseThrow().getFailedLogins());
	}

	@Test
	void shouldDisableOnlyTherapistsAndOnlyByAnAdministrator()
	{
		UserId therapist = UserId.random();
		UserId patient = UserId.random();
		TestCouples.activeAccount(users, therapist, Role.THERAPIST);
		TestCouples.activeAccount(users, patient, Role.PATIENT);

		assertThrows(NotFoundException.class, () -> userService.disableTherapist(administrator, patient, TestCouples.PASSWORD));
		assertThrows(NotFoundException.class, () -> userService.disableTherapist(administrator, UserId.random(), TestCouples.PASSWORD));
		assertThrows(NotFoundException.class, () -> userService.disableTherapist(therapist, therapist, TestCouples.PASSWORD));
		assertEquals(AccountStatus.ACTIVE, users.findById(therapist).orElseThrow().getStatus());
	}

	@Test
	void shouldLetAPatientOutsideAnActiveCoupleDeleteTheirAccount()
	{
		UserId therapist = UserId.random();
		TestCouples.activeAccount(users, therapist, Role.THERAPIST);
		UserId patient = UserId.random();
		TestCouples.activeAccount(users, patient, Role.PATIENT);
		EmailAddress patientEmail = users.findById(patient).orElseThrow().getEmail();

		CoupleInvitationId invitation = invitationService.invite(therapist, patientEmail, "Anna", freshEmail(), "Bruno");
		userService.requestPasswordReset(patientEmail);

		userService.deleteOwnPatientAccount(patient, TestCouples.PASSWORD);

		assertTrue(users.findById(patient).isEmpty());
		assertTrue(sessions.wereEnded(patient));
		assertEquals(CoupleInvitationStatus.DECLINED, invitations.findById(invitation).orElseThrow().getStatus());
		assertEquals(Optional.of(patient), invitations.findById(invitation).orElseThrow().getDeclinedBy());
		// The email is free again.
		assertDoesNotThrow(() -> userService.createTherapist(administrator, patientEmail, "Reused email"));
	}

	@Test
	void shouldNotDeleteTheAccountOfAPatientInAnActiveCouple()
	{
		UserId partner = UserId.random();
		TestCouples.create(users, couples, UserId.random(), partner, UserId.random());

		assertThrows(DomainException.class, () -> userService.deleteOwnPatientAccount(partner, TestCouples.PASSWORD));
		assertTrue(users.findById(partner).isPresent());
	}

	@Test
	void shouldLetOnlyAPatientDeleteTheirOwnAccountThisWay()
	{
		UserId therapist = UserId.random();
		TestCouples.activeAccount(users, therapist, Role.THERAPIST);

		assertThrows(NotFoundException.class, () -> userService.deleteOwnPatientAccount(therapist, TestCouples.PASSWORD));
		assertThrows(NotFoundException.class, () -> userService.deleteOwnPatientAccount(administrator, TestCouples.PASSWORD));
		assertTrue(users.findById(therapist).isPresent());
	}

	private static void assertPasswordRefused(RequestErrorCode code, Runnable action)
	{
		assertEquals(code, assertThrows(InvalidRequestException.class, action::run).getCode());
	}

	private static void assertInvalidLink(Runnable action)
	{
		InvalidRequestException failure = assertThrows(InvalidRequestException.class, action::run);
		assertEquals(INVALID_LINK, failure.getMessage());
		assertEquals(RequestErrorCode.INVALID_LINK, failure.getCode());
	}

	private static EmailAddress freshEmail()
	{
		return new EmailAddress(UUID.randomUUID() + "@example.org");
	}
}
