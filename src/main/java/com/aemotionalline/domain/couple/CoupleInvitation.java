package com.aemotionalline.domain.couple;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.user.UserId;

/**
 * The therapist's request to two patients to form a couple (spec 1.2: "inviando a entrambi la richiesta di costituire una
 * coppia"). The couple exists only once both have accepted: the second acceptance creates it. Either partner may decline,
 * the therapist may withdraw it while pending, and it expires. Whether the people have the right roles and whether a
 * patient is already in another active couple is checked by the application layer, which knows the accounts.
 */
public class CoupleInvitation
{
	private final CoupleInvitationId id;
	private final UserId therapistId;
	private final UserId partnerOneId;
	private final UserId partnerTwoId;
	private final Instant createdAt;
	private final Instant expiresAt;
	private final Set<UserId> acceptedBy = new LinkedHashSet<>();

	private CoupleInvitationStatus status;
	private Instant closedAt;
	private UserId declinedBy;
	private CoupleId coupleId;

	private CoupleInvitation(CoupleInvitationId id, UserId therapistId, UserId partnerOneId, UserId partnerTwoId, Instant createdAt, Instant expiresAt)
	{
		if (id == null || therapistId == null || partnerOneId == null || partnerTwoId == null || createdAt == null || expiresAt == null)
		{
			throw new DomainException("An invitation needs an id, a therapist, two partners and its instants.");
		}

		if (partnerOneId.equals(partnerTwoId) || therapistId.equals(partnerOneId) || therapistId.equals(partnerTwoId))
		{
			throw new DomainException("The therapist and the two partners must be three different people.");
		}

		if (!expiresAt.isAfter(createdAt))
		{
			throw new DomainException("An invitation must expire after it is created.");
		}

		this.id = id;
		this.therapistId = therapistId;
		this.partnerOneId = partnerOneId;
		this.partnerTwoId = partnerTwoId;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
		this.status = CoupleInvitationStatus.PENDING;
	}

	public static CoupleInvitation create(CoupleInvitationId id, UserId therapist, UserId partnerOne, UserId partnerTwo, Instant now, Instant expiresAt)
	{
		return new CoupleInvitation(id, therapist, partnerOne, partnerTwo, now, expiresAt);
	}

	/** Reserved to the persistence layer: rebuilds an invitation from its stored facts. */
	public static CoupleInvitation restore(CoupleInvitationId id, UserId therapist, UserId partnerOne, UserId partnerTwo, Instant createdAt,
			Instant expiresAt, Set<UserId> acceptedBy, CoupleInvitationStatus status, Instant closedAt, UserId declinedBy, CoupleId coupleId)
	{
		CoupleInvitation invitation = new CoupleInvitation(id, therapist, partnerOne, partnerTwo, createdAt, expiresAt);
		invitation.acceptedBy.addAll(acceptedBy);
		invitation.status = status;
		invitation.closedAt = closedAt;
		invitation.declinedBy = declinedBy;
		invitation.coupleId = coupleId;

		return invitation;
	}

	/**
	 * A partner accepts. The second acceptance creates the couple, which is returned (with a new id); the first returns
	 * nothing. Accepting twice, or after the invitation ended or expired, is rejected.
	 */
	public Optional<Couple> accept(UserId partner, Instant now)
	{
		ensureInvited(partner);
		ensurePendingAt(now);

		if (!acceptedBy.add(partner))
		{
			throw new DomainException("This partner has already accepted.");
		}

		if (acceptedBy.size() < 2)
		{
			return Optional.empty();
		}

		Couple couple = new Couple(CoupleId.random(), partnerOneId, partnerTwoId, therapistId);
		coupleId = couple.getId();
		close(CoupleInvitationStatus.ACCEPTED, now);

		return Optional.of(couple);
	}

	public void decline(UserId partner, Instant now)
	{
		ensureInvited(partner);
		ensurePendingAt(now);

		declinedBy = partner;
		close(CoupleInvitationStatus.DECLINED, now);
	}

	public void withdraw(UserId therapist, Instant now)
	{
		if (!therapistId.equals(therapist))
		{
			throw new DomainException("Only the therapist who sent the invitation can withdraw it.");
		}

		ensurePendingAt(now);
		close(CoupleInvitationStatus.WITHDRAWN, now);
	}

	/** Records that the time ran out; only a pending invitation past its expiry can be expired. */
	public void expire(Instant now)
	{
		if (status != CoupleInvitationStatus.PENDING || now.isBefore(expiresAt))
		{
			throw new DomainException("Only a pending invitation past its expiry can expire.");
		}

		close(CoupleInvitationStatus.EXPIRED, expiresAt);
	}

	public boolean isInvited(UserId user)
	{
		return partnerOneId.equals(user) || partnerTwoId.equals(user);
	}

	private void ensureInvited(UserId partner)
	{
		if (!isInvited(partner))
		{
			throw new DomainException("Only the two invited partners can answer the invitation.");
		}
	}

	private void ensurePendingAt(Instant now)
	{
		if (status != CoupleInvitationStatus.PENDING)
		{
			throw new DomainException("The invitation is already " + status + ".");
		}

		if (!now.isBefore(expiresAt))
		{
			throw new DomainException("The invitation has expired.");
		}
	}

	private void close(CoupleInvitationStatus finalStatus, Instant at)
	{
		status = finalStatus;
		closedAt = at;
	}

	public CoupleInvitationId getId()
	{
		return id;
	}

	public UserId getTherapistId()
	{
		return therapistId;
	}

	public UserId getPartnerOneId()
	{
		return partnerOneId;
	}

	public UserId getPartnerTwoId()
	{
		return partnerTwoId;
	}

	public Instant getCreatedAt()
	{
		return createdAt;
	}

	public Instant getExpiresAt()
	{
		return expiresAt;
	}

	public Set<UserId> getAcceptedBy()
	{
		return Set.copyOf(acceptedBy);
	}

	public CoupleInvitationStatus getStatus()
	{
		return status;
	}

	public Optional<Instant> getClosedAt()
	{
		return Optional.ofNullable(closedAt);
	}

	public Optional<UserId> getDeclinedBy()
	{
		return Optional.ofNullable(declinedBy);
	}

	/** The couple created by the second acceptance. */
	public Optional<CoupleId> getCoupleId()
	{
		return Optional.ofNullable(coupleId);
	}
}
