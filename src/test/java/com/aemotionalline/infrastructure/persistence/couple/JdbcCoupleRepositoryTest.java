package com.aemotionalline.infrastructure.persistence.couple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.aemotionalline.TestcontainersConfiguration;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.couple.CoupleId;
import com.aemotionalline.domain.couple.CoupleRepository;
import com.aemotionalline.domain.user.UserId;

// The database is shared by the Spring tests: every test uses fresh ids.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class JdbcCoupleRepositoryTest
{
	@Autowired
	private CoupleRepository couples;

	@Test
	void shouldSaveAndFindACouple()
	{
		Couple couple = new Couple(CoupleId.random(), UserId.random(), UserId.random(), UserId.random());

		couples.save(couple);
		Couple found = couples.findById(couple.getId()).orElseThrow();

		assertEquals(couple, found);
		assertEquals(couple.getPartnerOneId(), found.getPartnerOneId());
		assertEquals(couple.getPartnerTwoId(), found.getPartnerTwoId());
		assertEquals(couple.getTherapistId(), found.getTherapistId());
	}

	@Test
	void shouldSaveTheSameCoupleTwiceWithoutDuplicatingIt()
	{
		Couple couple = new Couple(CoupleId.random(), UserId.random(), UserId.random(), UserId.random());

		couples.save(couple);
		couples.save(couple);

		assertEquals(couple, couples.findById(couple.getId()).orElseThrow());
	}

	@Test
	void shouldFindNothingForAnUnknownId()
	{
		assertTrue(couples.findById(CoupleId.random()).isEmpty());
	}
}
