package com.aemotionalline.domain.constraint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.couple.Couple;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.Ids;

public class ConstraintChangeRequestTest
{
	private static final ConstraintChangeRequestId ID = new ConstraintChangeRequestId(new UUID(0L, 1L));
	private static final Instant AT = Instant.parse("2026-10-28T10:00:00Z");
	private static final Instant LATER = AT.plusSeconds(3600);

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
		
		assertThrows(DomainException.class, () -> new ConstraintChangeRequest(ID, couple.getPartnerOneId(), assignments, couple, AT));
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
		
		var costraintChangeRequest = new ConstraintChangeRequest(ID, couple.getTherapistId(),assignments,couple, AT);
		
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
		
		var costraintChangeRequest = new ConstraintChangeRequest(ID, couple.getTherapistId(),assignments,couple, AT);
		
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
		
		var constraintChangeRequest = new ConstraintChangeRequest(ID, couple.getTherapistId(),assignments,couple, AT);
		
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
		return new ConstraintChangeRequest(ID, THERAPIST, List.of(assignmentFor(user)), COUPLE, AT);
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
		ConstraintChangeRequest request = new ConstraintChangeRequest(ID, THERAPIST, source, COUPLE, AT);
		
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
		
		assertThrows(DomainException.class, () -> new ConstraintChangeRequest(ID, THERAPIST, null, COUPLE, AT));
		assertThrows(DomainException.class, () -> new ConstraintChangeRequest(ID, THERAPIST, withNull, COUPLE, AT));
		assertThrows(DomainException.class, () -> new ConstraintChangeRequest(ID, THERAPIST, List.of(assignmentFor(THERAPIST)), COUPLE, AT));
		assertThrows(DomainException.class, () -> new ConstraintChangeRequest(ID, THERAPIST, List.of(assignmentFor(Ids.user(40L))), COUPLE, AT));
		assertThrows(DomainException.class, () -> new ConstraintChangeRequest(ID, THERAPIST, List.of(), null, AT));
	}
	
	@Test
	void shouldAllowAnEmptyRequestToLiftEveryConstraint()
	{
		ConstraintChangeRequest request = new ConstraintChangeRequest(ID, THERAPIST, List.of(), COUPLE, AT);
		request.approve(PARTNER_ONE);
		request.approve(PARTNER_TWO);
		
		ConstraintSet set = new ConstraintSet();
		set.replaceWith(List.of(assignmentFor(PARTNER_ONE)));
		
		request.applyTo(set, AT);
		
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
		
		request.reject(PARTNER_TWO, AT);
		
		assertEquals(ConstraintChangeRequestStatus.REJECTED, request.status());
		assertThrows(DomainException.class, () -> request.approve(PARTNER_TWO));
		assertThrows(DomainException.class, () -> request.reject(PARTNER_ONE, AT));
		assertThrows(DomainException.class, () -> request.applyTo(new ConstraintSet(), AT));
	}
	
	@Test
	void shouldNotLetTheTherapistOrAnOutsiderReject()
	{
		ConstraintChangeRequest request = requestFor(PARTNER_ONE);
		
		assertThrows(DomainException.class, () -> request.reject(THERAPIST, AT));
		assertThrows(DomainException.class, () -> request.reject(Ids.user(40L), AT));
		assertThrows(DomainException.class, () -> request.reject(null, AT));
		assertEquals(ConstraintChangeRequestStatus.PENDING, request.status());
	}
	
	@Test
	void shouldLetOnlyTheProposingTherapistWithdraw()
	{
		ConstraintChangeRequest request = requestFor(PARTNER_ONE);
		
		assertThrows(DomainException.class, () -> request.withdraw(PARTNER_ONE, AT));
		assertThrows(DomainException.class, () -> request.withdraw(null, AT));
		assertEquals(ConstraintChangeRequestStatus.PENDING, request.status());
		
		request.withdraw(THERAPIST, AT);
		
		assertEquals(ConstraintChangeRequestStatus.WITHDRAWN, request.status());
		assertThrows(DomainException.class, () -> request.approve(PARTNER_ONE));
	}
	
	@Test
	void shouldApplyOnlyAFullyApprovedRequestAndOnlyOnce()
	{
		ConstraintSet set = new ConstraintSet();
		
		ConstraintChangeRequest halfApproved = requestFor(PARTNER_ONE);
		halfApproved.approve(PARTNER_ONE);
		assertThrows(DomainException.class, () -> halfApproved.applyTo(set, AT));
		assertTrue(set.isEmpty());
		assertEquals(ConstraintChangeRequestStatus.PENDING, halfApproved.status());
		
		ConstraintChangeRequest request = approvedRequest();
		request.applyTo(set, AT);
		
		assertEquals(1, set.getAssignments().size());
		assertEquals(ConstraintChangeRequestStatus.APPLIED, request.status());
		assertThrows(DomainException.class, () -> request.applyTo(set, AT));
		assertThrows(DomainException.class, () -> request.approve(PARTNER_ONE));
		assertThrows(DomainException.class, () -> request.reject(PARTNER_ONE, AT));
		assertThrows(DomainException.class, () -> request.withdraw(THERAPIST, AT));
	}
	
	@Test
	void shouldRejectANullConstraintSet()
	{
		assertThrows(DomainException.class, () -> approvedRequest().applyTo(null, AT));
	}

	// HISTORY (6a.3): what the archive shows of a request.
	
	@Test
	void shouldRecordWhoRejectedAndWhen()
	{
		ConstraintChangeRequest request = requestFor(PARTNER_ONE);
		
		request.reject(PARTNER_TWO, LATER);
		
		ConstraintRequestRecord record = request.toRecord();
		assertEquals(ConstraintChangeRequestStatus.REJECTED, record.status());
		assertEquals(Optional.of(PARTNER_TWO), record.rejectedBy());
		assertEquals(Optional.of(LATER), record.closedAt());
		assertEquals(AT, record.proposedAt());
		assertEquals(Optional.empty(), record.previousAssignments());
	}
	
	@Test
	void shouldTellAWithdrawalFromARejection()
	{
		ConstraintChangeRequest request = requestFor(PARTNER_ONE);
		
		request.withdraw(THERAPIST, LATER);
		
		assertEquals(ConstraintChangeRequestStatus.WITHDRAWN, request.toRecord().status());
		assertEquals(Optional.empty(), request.toRecord().rejectedBy());
		assertEquals(Optional.of(LATER), request.toRecord().closedAt());
	}
	
	@Test
	void shouldKeepTheConstraintsAnAppliedRequestReplaced()
	{
		ConstraintSet set = new ConstraintSet();
		ConstraintAssignment before = assignmentFor(PARTNER_TWO);
		set.replaceWith(List.of(before));
		ConstraintChangeRequest request = approvedRequest();
		
		request.applyTo(set, LATER);
		
		ConstraintRequestRecord record = request.toRecord();
		assertEquals(Optional.of(List.of(before)), record.previousAssignments());
		assertEquals(request.assignments(), set.getAssignments());
		assertEquals(Set.of(PARTNER_ONE, PARTNER_TWO), record.approvals());
		assertEquals(Optional.of(LATER), record.closedAt());
	}
	
	@Test
	void shouldGiveARecordThatCantChangeTheRequest()
	{
		ConstraintChangeRequest request = requestFor(PARTNER_ONE);
		request.approve(PARTNER_ONE);
		ConstraintRequestRecord record = request.toRecord();
		
		assertThrows(UnsupportedOperationException.class, () -> record.approvals().add(PARTNER_TWO));
		assertThrows(UnsupportedOperationException.class, () -> record.assignments().clear());
		assertEquals(false, request.isFullyApproved());
	}
	
	@Test
	void shouldRequireTheInstants()
	{
		assertThrows(DomainException.class, () -> new ConstraintChangeRequest(ID, THERAPIST, List.of(), COUPLE, null));
		assertThrows(DomainException.class, () -> new ConstraintChangeRequest(null, THERAPIST, List.of(), COUPLE, AT));
		
		ConstraintChangeRequest request = approvedRequest();
		ConstraintSet set = new ConstraintSet();
		
		assertThrows(DomainException.class, () -> request.reject(PARTNER_ONE, null));
		assertThrows(DomainException.class, () -> request.withdraw(THERAPIST, null));
		assertThrows(DomainException.class, () -> request.applyTo(set, null));
		assertEquals(ConstraintChangeRequestStatus.PENDING, request.status());
		assertTrue(set.isEmpty());
	}
}
