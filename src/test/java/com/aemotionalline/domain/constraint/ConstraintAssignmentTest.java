package com.aemotionalline.domain.constraint;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.user.UserId;

public class ConstraintAssignmentTest
{
	@Test
	void shouldNotCreateConstraintAssigmentWhitoutUserIdOrConstraint()
	{
		UserId userId = new UserId(1L);
		Constraint constraint = new Constraint("Test String");
		
		assertThrows(IllegalArgumentException.class, () -> new ConstraintAssignment(null,constraint));
		assertThrows(IllegalArgumentException.class, () -> new ConstraintAssignment(userId,null));
	}
}
