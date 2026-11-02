package com.aemotionalline.domain.couple;

import java.util.List;
import java.util.Optional;

import com.aemotionalline.domain.user.UserId;

/** Where couple invitations are kept. Plain Java: the persistence layer implements it. */
public interface CoupleInvitationRepository
{
	Optional<CoupleInvitation> findById(CoupleInvitationId id);

	/** Like {@link #findById}, but locks the invitation until the end of the transaction. */
	Optional<CoupleInvitation> findByIdForUpdate(CoupleInvitationId id);

	/** The pending invitations a user is part of, as one of the partners or as the therapist, oldest first. */
	List<CoupleInvitation> findPendingInvolving(UserId user);

	void save(CoupleInvitation invitation);
}
