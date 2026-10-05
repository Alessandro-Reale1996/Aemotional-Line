package com.aemotionalline.domain.constraint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.Ids;

public class ConstraintChangeRequestTest
{
	@Test
	void shouldThrowExceptionWhenNotTherapistSendRequest()
	{
		UserId partnerOne = Ids.user(10L);
		UserId partnerTwo = Ids.user(20L);
		UserId therapist = Ids.user(30L);
		
		Couple couple = new Couple(Ids.couple(1L), partnerOne, partnerTwo, therapist);
		
		TimeConstraint constraint = new TimeConstraint(LocalTime.of(22, 0));
		ConstraintAssignment constraintAssignment = new ConstraintAssignment(partnerOne, constraint);
		
		List <ConstraintAssignment>assignments = new ArrayList<>();
		assignments.add(constraintAssignment);
		
		assertThrows(DomainException.class, () -> new ConstraintChangeRequest(couple.getPartnerOneId(), assignments, couple));
	}
	
	@Test
	void shouldThrowExceptionWhenNotPartnerApprove()
	{
		UserId partnerOne = Ids.user(10L);
		UserId partnerTwo = Ids.user(20L);
		UserId therapist = Ids.user(30L);
		
		Couple couple = new Couple(Ids.couple(1L), partnerOne, partnerTwo, therapist);
		
		TimeConstraint constraint = new TimeConstraint(LocalTime.of(22, 0));
		ConstraintAssignment constraintAssignment = new ConstraintAssignment(partnerOne, constraint);
		
		List <ConstraintAssignment>assignments = new ArrayList<>();
		assignments.add(constraintAssignment);
		
		var costraintChangeRequest = new ConstraintChangeRequest(couple.getTherapistId(),assignments,couple);
		
		UserId notPartner = Ids.user(40L);
		
		assertThrows(DomainException.class, () -> costraintChangeRequest.approve(therapist));
		assertThrows(DomainException.class, () -> costraintChangeRequest.approve(notPartner));
	}
	
	@Test
	void shouldThrowExceptionWhenApprovingWithNullUser()
	{
		UserId partnerOne = Ids.user(10L);
		UserId partnerTwo = Ids.user(20L);
		UserId therapist = Ids.user(30L);
		
		Couple couple = new Couple(Ids.couple(1L), partnerOne, partnerTwo, therapist);
		
		TimeConstraint constraint = new TimeConstraint(LocalTime.of(22, 0));
		ConstraintAssignment constraintAssignment = new ConstraintAssignment(partnerOne, constraint);
		
		List <ConstraintAssignment>assignments = new ArrayList<>();
		assignments.add(constraintAssignment);
		
		var costraintChangeRequest = new ConstraintChangeRequest(couple.getTherapistId(),assignments,couple);
		
		assertThrows(DomainException.class, () -> costraintChangeRequest.approve(null));	
	}
	
	@Test
	void shouldReturnTrueWhenChangesIsAccepted()
	{
		UserId partnerOne = Ids.user(10L);
		UserId partnerTwo = Ids.user(20L);
		UserId therapist = Ids.user(30L);
		
		Couple couple = new Couple(Ids.couple(1L), partnerOne, partnerTwo, therapist);
		
		TimeConstraint constraint = new TimeConstraint(LocalTime.of(22, 0));
		ConstraintAssignment constraintAssignment = new ConstraintAssignment(partnerOne, constraint);
		
		List <ConstraintAssignment>assignments = new ArrayList<>();
		assignments.add(constraintAssignment);
		
		var constraintChangeRequest = new ConstraintChangeRequest(couple.getTherapistId(),assignments,couple);
		
		constraintChangeRequest.approve(partnerOne);
		constraintChangeRequest.approve(partnerTwo);
		
		assertTrue(constraintChangeRequest.isFullyApproved());
	}

	// STEP 6d: copy of the list, validated assignees, life cycle (PENDING -> REJECTED or APPLIED).
	
	private static final UserId PARTNER_ONE = Ids.user(10L);
	private static final UserId PARTNER_TWO = Ids.user(20L);
	private static final UserId THERAPIST = Ids.user(30L);
	private static final Couple COUPLE = new Couple(Ids.couple(1L), PARTNER_ONE, PARTNER_TWO, THERAPIST);
	
	private static ConstraintAssignment assignmentFor(UserId user)
	{
		return new ConstraintAssignment(user, new TimeConstraint(LocalTime.of(22, 0)));
	}
	
	private static ConstraintChangeRequest requestFor(UserId user)
	{
		return new ConstraintChangeRequest(THERAPIST, List.of(assignmentFor(user)), COUPLE);
	}
	
	private static ConstraintChangeRequest approvedRequest()
	{
		ConstraintChangeRequest request = requestFor(PARTNER_ONE);
		request.approve(PARTNER_ONE);
		request.approve(PARTNER_TWO);
		return request;
	}
	
	@Test
	void shouldNotChangeWhenTheListPassedToTheConstructorIsModifiedLater()
	{
		List<ConstraintAssignment> source = new ArrayList<>(List.of(assignmentFor(PARTNER_ONE)));
		ConstraintChangeRequest request = new ConstraintChangeRequest(THERAPIST, source, COUPLE);
		
		// The partners approve what they saw: a later change to the caller's list must not reach the request.
		source.add(assignmentFor(PARTNER_TWO));
		
		assertEquals(1, request.assignments().size());
		assertThrows(UnsupportedOperationException.class, () -> request.assignments().clear());
	}
	
	@Test
	void shouldRejectInvalidAssignments()
	{
		List<ConstraintAssignment> withNull = new ArrayList<>();
		withNull.add(null);
		
		assertThrows(DomainException.class, () -> new ConstraintChangeRequest(THERAPIST, null, COUPLE));
		assertThrows(DomainException.class, () -> new ConstraintChangeRequest(THERAPIST, withNull, COUPLE));
		assertThrows(DomainException.class, () -> new ConstraintChangeRequest(THERAPIST, List.of(assignmentFor(THERAPIST)), COUPLE));
		assertThrows(DomainException.class, () -> new ConstraintChangeRequest(THERAPIST, List.of(assignmentFor(Ids.user(40L))), COUPLE));
		assertThrows(DomainException.class, () -> new ConstraintChangeRequest(THERAPIST, List.of(), null));
	}
	
	@Test
	void shouldAllowAnEmptyRequestToLiftEveryConstraint()
	{
		ConstraintChangeRequest request = new ConstraintChangeRequest(THERAPIST, List.of(), COUPLE);
		request.approve(PARTNER_ONE);
		request.approve(PARTNER_TWO);
		
		ConstraintSet set = new ConstraintSet();
		set.replaceWith(List.of(assignmentFor(PARTNER_ONE)));
		
		request.applyTo(set);
		
		assertTrue(set.isEmpty());
	}
	
	@Test
	void shouldStartPendingAndNotBeApprovedUntilBothPartnersApprove()
	{
		ConstraintChangeRequest request = requestFor(PARTNER_ONE);
		
		assertEquals(ConstraintChangeRequestStatus.PENDING, request.status());
		assertFalse(request.isFullyApproved());
		
		request.approve(PARTNER_ONE);
		assertFalse(request.isFullyApproved());
		
		request.approve(PARTNER_TWO);
		assertTrue(request.isFullyApproved());
	}
	
	@Test
	void shouldLetAPartnerRejectTheRequestAndThenNothingCanHappenToIt()
	{
		ConstraintChangeRequest request = requestFor(PARTNER_ONE);
		request.approve(PARTNER_ONE);
		
		request.reject(PARTNER_TWO);
		
		assertEquals(ConstraintChangeRequestStatus.REJECTED, request.status());
		assertThrows(DomainException.class, () -> request.approve(PARTNER_TWO));
		assertThrows(DomainException.class, () -> request.reject(PARTNER_ONE));
		assertThrows(DomainException.class, () -> request.applyTo(new ConstraintSet()));
	}
	
	@Test
	void shouldNotLetTheTherapistOrAnOutsiderReject()
	{
		ConstraintChangeRequest request = requestFor(PARTNER_ONE);
		
		assertThrows(DomainException.class, () -> request.reject(THERAPIST));
		assertThrows(DomainException.class, () -> request.reject(Ids.user(40L)));
		assertThrows(DomainException.class, () -> request.reject(null));
		assertEquals(ConstraintChangeRequestStatus.PENDING, request.status());
	}
	
	@Test
	void shouldLetOnlyTheProposingTherapistWithdraw()
	{
		ConstraintChangeRequest request = requestFor(PARTNER_ONE);
		
		assertThrows(DomainException.class, () -> request.withdraw(PARTNER_ONE));
		assertThrows(DomainException.class, () -> request.withdraw(null));
		assertEquals(ConstraintChangeRequestStatus.PENDING, request.status());
		
		request.withdraw(THERAPIST);
		
		assertEquals(ConstraintChangeRequestStatus.REJECTED, request.status());
		assertThrows(DomainException.class, () -> request.approve(PARTNER_ONE));
	}
	
	@Test
	void shouldApplyOnlyAFullyApprovedRequestAndOnlyOnce()
	{
		ConstraintSet set = new ConstraintSet();
		
		ConstraintChangeRequest halfApproved = requestFor(PARTNER_ONE);
		halfApproved.approve(PARTNER_ONE);
		assertThrows(DomainException.class, () -> halfApproved.applyTo(set));
		assertTrue(set.isEmpty());
		assertEquals(ConstraintChangeRequestStatus.PENDING, halfApproved.status());
		
		ConstraintChangeRequest request = approvedRequest();
		request.applyTo(set);
		
		assertEquals(1, set.getAssignments().size());
		assertEquals(ConstraintChangeRequestStatus.APPLIED, request.status());
		assertThrows(DomainException.class, () -> request.applyTo(set));
		assertThrows(DomainException.class, () -> request.approve(PARTNER_ONE));
		assertThrows(DomainException.class, () -> request.reject(PARTNER_ONE));
		assertThrows(DomainException.class, () -> request.withdraw(THERAPIST));
	}
	
	@Test
	void shouldRejectANullConstraintSet()
	{
		assertThrows(DomainException.class, () -> approvedRequest().applyTo(null));
	}
}
