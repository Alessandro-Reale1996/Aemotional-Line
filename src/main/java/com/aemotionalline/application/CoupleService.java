package com.aemotionalline.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.user.UserId;

/**
 * The couple: the therapist links two partners. The spec's invitation flow (both partners accept the therapist's request)
 * is deferred to the users and security phase; for now the therapist creates the couple directly.
 */
@Service
@Transactional
public class CoupleService
{
	private final CoupleRepository couples;

	CoupleService(CoupleRepository couples)
	{
		this.couples = couples;
	}

	public CoupleId createCouple(UserId therapist, UserId partnerOne, UserId partnerTwo)
	{
		Couple couple = new Couple(CoupleId.random(), partnerOne, partnerTwo, therapist);

		couples.save(couple);

		return couple.getId();
	}
}
