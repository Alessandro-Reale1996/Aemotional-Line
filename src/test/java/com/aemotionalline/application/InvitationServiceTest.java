package com.aemotionalline.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.aemotionalline.CapturingMailer;
import com.aemotionalline.CapturingMailer.Kind;
import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.application.view.InvitationView;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.common.ErrorCode;
import com.aemotionalline.domain.couple.Couple;
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

/** How a couple is born, end to end on MariaDB: invitation by email, two acceptances, one active couple per patient. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class InvitationServiceTest
{
	private static final String PASSWORD = "un cavallo sul tetto blu";

	@Autowired
	private InvitationService invitationService;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository users;

	@Autowired
	private CoupleRepository couples;

	@Autowired
	private CoupleInvitationRepository invitations;

	@Autowired
	private CapturingMailer mailer;

	@Autowired
	private Clock clock;

	private UserId therapist;

	@BeforeEach
	void createATherapist()
	{
		therapist = UserId.random();
		TestCouples.activeAccount(users, therapist, Role.THERAPIST);
	}

	@Test
	void shouldInviteNewPatientsWhoActivateTheirAccountsAndFormTheCouple()
	{
		EmailAddress emailOne = freshEmail();
		EmailAddress emailTwo = freshEmail();

		CoupleInvitationId id = invitationService.invite(therapist, emailOne, "Anna", emailTwo, "Bruno");

		User one = users.findByEmail(emailOne).orElseThrow();
		User two = users.findByEmail(emailTwo).orElseThrow();
		assertEquals(Role.PATIENT, one.getRole());
		assertEquals(AccountStatus.PENDING, one.getStatus());
		assertTrue(mailer.lastCode(emailOne, Kind.ACTIVATION).isPresent());
		assertTrue(mailer.to(emailTwo).stream().anyMatch(mail -> mail.kind() == Kind.COUPLE_INVITATION));

		userService.activate(mailer.lastCode(emailOne, Kind.ACTIVATION).orElseThrow(), PASSWORD);
		userService.activate(mailer.lastCode(emailTwo, Kind.ACTIVATION).orElseThrow(), PASSWORD);

		invitationService.accept(one.getId(), id);
		InvitationView halfway = invitationService.view(therapist, id);
		assertEquals(CoupleInvitationStatus.PENDING, halfway.status());
		assertEquals(Set.of(one.getId()), halfway.acceptedBy());
		assertTrue(couples.findActiveCoupleOf(one.getId()).isEmpty());

		invitationService.accept(two.getId(), id);

		InvitationView accepted = invitationService.view(two.getId(), id);
		assertEquals(CoupleInvitationStatus.ACCEPTED, accepted.status());
		Couple couple = couples.findActiveCoupleOf(one.getId()).orElseThrow();
		assertEquals(accepted.coupleId().orElseThrow(), couple.getId());
		assertTrue(couple.isPartner(two.getId()));
		assertTrue(couple.isTherapist(therapist));
	}

	@Test
	void shouldInviteExistingPatientsWithoutNewAccounts()
	{
		UserId one = activePatient();
		UserId two = activePatient();

		CoupleInvitationId id = invitationService.invite(therapist, emailOf(one), "Anna", emailOf(two), "Bruno");

		assertTrue(mailer.lastCode(emailOf(one), Kind.ACTIVATION).isEmpty());
		assertEquals(List.of(id), invitationService.pending(one).stream().map(InvitationView::id).toList());
		assertEquals(List.of(id), invitationService.pending(two).stream().map(InvitationView::id).toList());
		assertTrue(invitationService.pending(therapist).stream().anyMatch(view -> view.id().equals(id)));
	}

	@Test
	void shouldLetOnlyAnActiveTherapistInviteTwoDifferentPeople()
	{
		UserId patient = activePatient();
		EmailAddress email = freshEmail();

		assertThrows(NotFoundException.class, () -> invitationService.invite(patient, freshEmail(), "Anna", freshEmail(), "Bruno"));
		assertEquals(ErrorCode.SAME_EMAIL_TWICE,
				assertThrows(DomainException.class, () -> invitationService.invite(therapist, email, "Anna", email, "Anna again")).getCode());
		assertTrue(users.findByEmail(email).isEmpty());
	}

	@Test
	void shouldRefuseToInviteSomeoneWhoCantBeInvitedWithOneGenericMessage()
	{
		UserId otherTherapist = UserId.random();
		TestCouples.activeAccount(users, otherTherapist, Role.THERAPIST);
		UserId inACouple = UserId.random();
		TestCouples.create(users, couples, UserId.random(), inACouple, UserId.random());
		UserId disabledTherapist = UserId.random();
		TestCouples.activeAccount(users, disabledTherapist, Role.THERAPIST);
		UserId administrator = UserId.random();
		TestCouples.activeAccount(users, administrator, Role.ADMINISTRATOR);
		userService.disableTherapist(administrator, disabledTherapist);

		for (UserId person : List.of(otherTherapist, inACouple, disabledTherapist))
		{
			DomainException refused = assertThrows(DomainException.class,
					() -> invitationService.invite(therapist, emailOf(person), "X", freshEmail(), "Y"));
			assertEquals("This person can't be invited now.", refused.getMessage());
			assertEquals(ErrorCode.CANNOT_BE_INVITED, refused.getCode());
		}
	}

	@Test
	void shouldKeepAPatientInOneActiveCoupleOnly()
	{
		UserId shared = activePatient();
		UserId first = activePatient();
		UserId second = activePatient();
		CoupleInvitationId one = invitationService.invite(therapist, emailOf(shared), "S", emailOf(first), "F");
		CoupleInvitationId two = invitationService.invite(therapist, emailOf(shared), "S", emailOf(second), "G");

		invitationService.accept(shared, one);
		invitationService.accept(second, two);
		invitationService.accept(first, one);

		// The couple of the first invitation exists: the second can no longer make the shared patient a partner.
		assertThrows(DomainException.class, () -> invitationService.accept(shared, two));
		assertEquals(CoupleInvitationStatus.PENDING, invitations.findById(two).orElseThrow().getStatus());
		assertTrue(couples.findActiveCoupleOf(second).isEmpty());
	}

	@Test
	void shouldDeclineAndWithdraw()
	{
		UserId one = activePatient();
		UserId two = activePatient();
		CoupleInvitationId declined = invitationService.invite(therapist, emailOf(one), "A", emailOf(two), "B");
		CoupleInvitationId withdrawn = invitationService.invite(therapist, emailOf(one), "A", emailOf(two), "B");

		invitationService.accept(one, declined);
		invitationService.decline(two, declined);
		invitationService.withdraw(therapist, withdrawn);

		assertEquals(CoupleInvitationStatus.DECLINED, invitationService.view(one, declined).status());
		assertEquals(CoupleInvitationStatus.WITHDRAWN, invitationService.view(two, withdrawn).status());
		assertTrue(couples.findActiveCoupleOf(one).isEmpty());
		assertTrue(invitationService.pending(one).isEmpty());
		assertThrows(DomainException.class, () -> invitationService.accept(two, declined));
		// A partner can't withdraw: only the therapist who sent it.
		CoupleInvitationId third = invitationService.invite(therapist, emailOf(one), "A", emailOf(two), "B");
		assertThrows(DomainException.class, () -> invitationService.withdraw(one, third));
	}

	@Test
	void shouldHideAnInvitationFromOutsiders()
	{
		UserId one = activePatient();
		UserId two = activePatient();
		UserId outsider = activePatient();
		UserId otherTherapist = UserId.random();
		TestCouples.activeAccount(users, otherTherapist, Role.THERAPIST);
		CoupleInvitationId id = invitationService.invite(therapist, emailOf(one), "A", emailOf(two), "B");

		assertThrows(NotFoundException.class, () -> invitationService.view(outsider, id));
		assertThrows(NotFoundException.class, () -> invitationService.accept(outsider, id));
		assertThrows(NotFoundException.class, () -> invitationService.decline(outsider, id));
		assertThrows(NotFoundException.class, () -> invitationService.withdraw(otherTherapist, id));
		assertThrows(NotFoundException.class, () -> invitationService.view(one, CoupleInvitationId.random()));
		assertTrue(invitationService.pending(outsider).isEmpty());
	}

	@Test
	void shouldNotListOrAcceptAnExpiredInvitation()
	{
		UserId one = activePatient();
		UserId two = activePatient();
		Instant longAgo = clock.instant().minus(Duration.ofDays(8));
		CoupleInvitation expired = CoupleInvitation.create(CoupleInvitationId.random(), therapist, one, two, longAgo,
				longAgo.plus(InvitationService.VALIDITY));
		invitations.save(expired);

		assertTrue(invitationService.pending(one).isEmpty());
		assertTrue(invitationService.pending(therapist).stream().noneMatch(view -> view.id().equals(expired.getId())));
		assertThrows(DomainException.class, () -> invitationService.accept(one, expired.getId()));
		assertTrue(couples.findActiveCoupleOf(one).isEmpty());
	}

	@Test
	void shouldNotLetAPendingAccountAccept()
	{
		EmailAddress emailOne = freshEmail();
		CoupleInvitationId id = invitationService.invite(therapist, emailOne, "Anna", freshEmail(), "Bruno");
		UserId one = users.findByEmail(emailOne).orElseThrow().getId();

		assertThrows(NotFoundException.class, () -> invitationService.accept(one, id));
	}

	private UserId activePatient()
	{
		UserId patient = UserId.random();
		TestCouples.activeAccount(users, patient, Role.PATIENT);

		return patient;
	}

	private EmailAddress emailOf(UserId user)
	{
		return users.findById(user).orElseThrow().getEmail();
	}

	private static EmailAddress freshEmail()
	{
		return new EmailAddress(UUID.randomUUID() + "@example.org");
	}
}
