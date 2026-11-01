package com.aemotionalline.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.user.UserId;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class CoupleServiceTest
{
	@Autowired
	private CoupleService service;

	@Autowired
	private CoupleRepository couples;

	@Test
	void shouldLetTheTherapistCreateACouple()
	{
		UserId therapist = UserId.random();
		UserId one = UserId.random();
		UserId two = UserId.random();

		CoupleId id = service.createCouple(therapist, one, two);

		Couple couple = couples.findById(id).orElseThrow();
		assertEquals(therapist, couple.getTherapistId());
		assertEquals(one, couple.getPartnerOneId());
		assertEquals(two, couple.getPartnerTwoId());
	}

	@Test
	void shouldRejectACoupleTheDomainRejects()
	{
		UserId therapist = UserId.random();

		assertThrows(DomainException.class, () -> service.createCouple(therapist, therapist, UserId.random()));
	}
}
