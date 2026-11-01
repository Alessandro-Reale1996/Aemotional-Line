package com.aemotionalline.application;

import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.user.UserId;

/**
 * The check every service makes first, for reads and writes alike: only the couple's two partners and its therapist may
 * see or touch anything of the couple. Anyone else is told the thing doesn't exist.
 */
final class Access
{
	private Access()
	{
	}

	static boolean isMember(Couple couple, UserId user)
	{
		return user != null && (couple.isPartner(user) || couple.isTherapist(user));
	}

	static void ensureMember(Couple couple, UserId user, String what)
	{
		if (!isMember(couple, user))
		{
			throw new NotFoundException(what + " not found.");
		}
	}
}
