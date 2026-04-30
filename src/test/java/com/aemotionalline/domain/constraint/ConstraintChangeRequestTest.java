package com.aemotionalline.domain.constraint;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.user.UserId;

public class ConstraintChangeRequestTest
{
	@Test
	void shouldThrowExceptionWhenNotTherapistSendRequest()
	{
		UserId partnerOne = new UserId(10L);
		UserId partnerTwo = new UserId(20L);
		UserId therapist = new UserId(30L);
		
		Couple couple = new Couple(1L, partnerOne, partnerTwo, therapist);
		
		TimeConstraint constraint = new TimeConstraint(LocalTime.of(22, 0));
		ConstraintAssignment constraintAssignment = new ConstraintAssignment(partnerOne, constraint);
		
		List <ConstraintAssignment>assignments = new ArrayList<>();
		assignments.add(constraintAssignment);
		
		assertThrows(DomainException.class, () -> new ConstraintChangeRequest(couple.getPartnerOneId(), assignments, couple));
	}
	
	@Test
	void shouldThrowExceptionWhenNotPartnerApprove()
	{
		UserId partnerOne = new UserId(10L);
		UserId partnerTwo = new UserId(20L);
		UserId therapist = new UserId(30L);
		
		Couple couple = new Couple(1L, partnerOne, partnerTwo, therapist);
		
		TimeConstraint constraint = new TimeConstraint(LocalTime.of(22, 0));
		ConstraintAssignment constraintAssignment = new ConstraintAssignment(partnerOne, constraint);
		
		List <ConstraintAssignment>assignments = new ArrayList<>();
		assignments.add(constraintAssignment);
		
		var costraintChangeRequest = new ConstraintChangeRequest(couple.getTherapistId(),assignments,couple);
		
		UserId notPartner = new UserId(40L);
		
		assertThrows(DomainException.class, () -> costraintChangeRequest.approve(therapist, couple));
		assertThrows(DomainException.class, () -> costraintChangeRequest.approve(notPartner, couple));
	}
	
	@Test
	void shouldThrowExceptionWhenUserIdIsNotAddedToApproval()
	{
		UserId partnerOne = new UserId(10L);
		UserId partnerTwo = new UserId(20L);
		UserId therapist = new UserId(30L);
		
		Couple couple = new Couple(1L, partnerOne, partnerTwo, therapist);
		
		TimeConstraint constraint = new TimeConstraint(LocalTime.of(22, 0));
		ConstraintAssignment constraintAssignment = new ConstraintAssignment(partnerOne, constraint);
		
		List <ConstraintAssignment>assignments = new ArrayList<>();
		assignments.add(constraintAssignment);
		
		var costraintChangeRequest = new ConstraintChangeRequest(couple.getTherapistId(),assignments,couple);
		
		assertThrows(DomainException.class, () -> costraintChangeRequest.approve(null, couple));	
	}
	
	@Test
	void shouldReturnTrueWhenChangesIsAccepted()
	{
		UserId partnerOne = new UserId(10L);
		UserId partnerTwo = new UserId(20L);
		UserId therapist = new UserId(30L);
		
		Couple couple = new Couple(1L, partnerOne, partnerTwo, therapist);
		
		TimeConstraint constraint = new TimeConstraint(LocalTime.of(22, 0));
		ConstraintAssignment constraintAssignment = new ConstraintAssignment(partnerOne, constraint);
		
		List <ConstraintAssignment>assignments = new ArrayList<>();
		assignments.add(constraintAssignment);
		
		var constraintChangeRequest = new ConstraintChangeRequest(couple.getTherapistId(),assignments,couple);
		
		constraintChangeRequest.approve(partnerOne, couple);
		constraintChangeRequest.approve(partnerTwo, couple);
		
		assertTrue(constraintChangeRequest.isFullyApproved(couple));
	}
}
