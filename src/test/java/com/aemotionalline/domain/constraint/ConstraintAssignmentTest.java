package com.aemotionalline.domain.constraint;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.user.UserId;

public class ConstraintAssignmentTest
{
	@Test
	void shouldNotCreateConstraintAssigmentWhitoutUserIdOrConstraint()
	{
		UserId userId = new UserId(1L);
		TimeConstraint constraint = new TimeConstraint(LocalTime.of(22,0));
		
		assertThrows(IllegalArgumentException.class, () -> new ConstraintAssignment(null,constraint));
		assertThrows(IllegalArgumentException.class, () -> new ConstraintAssignment(userId,null));
	}
}
