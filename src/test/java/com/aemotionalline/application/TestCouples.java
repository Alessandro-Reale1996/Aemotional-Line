package com.aemotionalline.application;

import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.user.EmailAddress;
import com.aemotionalline.domain.user.PasswordHash;
import com.aemotionalline.domain.user.Role;
import com.aemotionalline.domain.user.User;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.user.UserRepository;

/**
 * Test helper for the services that need a couple but aren't about how it is born: it saves three active accounts and the
 * couple directly, skipping the invitation flow (tested in InvitationServiceTest).
 */
final class TestCouples
{
	/** The password of every account saved here. */
	static final String PASSWORD = "not-a-real-password";

	private TestCouples()
	{
	}

	static CoupleId create(UserRepository users, CoupleRepository couples, UserId therapist, UserId partnerOne, UserId partnerTwo)
	{
		activeAccount(users, therapist, Role.THERAPIST);
		activeAccount(users, partnerOne, Role.PATIENT);
		activeAccount(users, partnerTwo, Role.PATIENT);

		Couple couple = new Couple(CoupleId.random(), partnerOne, partnerTwo, therapist);
		couples.save(couple);

		return couple.getId();
	}

	static void activeAccount(UserRepository users, UserId id, Role role)
	{
		User user = User.pending(id, new EmailAddress(id.value() + "@example.org"), role.name().toLowerCase(), role);
		user.activate(new PasswordHash("{noop}" + PASSWORD));
		users.save(user);
	}
}
