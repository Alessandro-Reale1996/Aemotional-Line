package com.aemotionalline.domain.agreement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.user.UserId;

public class AgreementTest
{
	@Test
	void shouldNotCreateIncompleteAgreement()
	{
		assertThrows(DomainException.class, () -> new Agreement(null, "test"));
		assertThrows(DomainException.class, () -> new Agreement(1L, null));
		assertThrows(DomainException.class, () -> new Agreement(1L, ""));
	}
	
	@Test
	void shouldThrowExceptionWhenAprovalIsNotAdded()
	{
		
		Couple couple = new Couple(
																	1L,
																	new UserId(10L),
																	new UserId(20L),
																	new UserId(30L)
																);
		
		Agreement agreement = new Agreement(1L, "Initial Agrement");

		assertThrows(DomainException.class , ()  -> agreement.accept(null, couple));
		
	}
	
	@Test
	void shouldThrowExceptionWhenNotPartnersAcceptAgreement()
	{
		Couple couple = new Couple(
				1L,
				new UserId(10L),
				new UserId(20L),
				new UserId(30L)
			);
		
		UserId notPartner = new UserId(1L);

		Agreement agreement = new Agreement(1L, "Initial Agrement");
		
		assertThrows(DomainException.class , ()  -> agreement.accept(notPartner, couple));
		assertThrows(DomainException.class , ()  -> agreement.accept(couple.getTherapistId(), couple));
		
	}
	
	@Test
	void shouldChangeStatusWhenBothPartnersHaveAccepted()
	{
		Couple couple = new Couple(
				1L,
				new UserId(10L),
				new UserId(20L),
				new UserId(30L)
			);
		

		Agreement agreement = new Agreement(1L, "Initial Agrement");
		
        agreement.accept(couple.getPartnerOneId(), couple);
        agreement.accept(couple.getPartnerTwoId(), couple);
		
		assertEquals(AgreementStatus.ACCEPTED, agreement.getStatus());
	}
	
	@Test
	void shouldReturnTrueWhenAgreementIsAccepted()
	{
		Couple couple = new Couple(
				1L,
				new UserId(10L),
				new UserId(20L),
				new UserId(30L)
			);
		

		Agreement agreement = new Agreement(1L, "Initial Agrement");
		
        agreement.accept(couple.getPartnerOneId(), couple);
        agreement.accept(couple.getPartnerTwoId(), couple);
		
		assertTrue(agreement.isAccepted());
	}
	
	@Test
	void shouldHave_DRAFT_AsInitialStatus()
	{
		Agreement agreement = new Agreement(1L, "Initial Agrement");
		
		assertEquals(AgreementStatus.DRAFT, agreement.getStatus());
	}
}
