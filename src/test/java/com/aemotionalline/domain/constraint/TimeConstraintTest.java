package com.aemotionalline.domain.constraint;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

public class TimeConstraintTest
{
	@Test
	void shouldAllowWhenTimeIsAfterLimit() 
	{
	    TimeConstraint constraint = new TimeConstraint(LocalTime.of(22, 0));

	    assertTrue(constraint.isSatisfied(new ConstraintContext(LocalTime.of(23, 0))));
	}
	
	@Test
	void shouldBlockWhenTimeIsBeforeLimit()
	{
	    TimeConstraint constraint = new TimeConstraint(LocalTime.of(22, 0));

	    assertFalse(constraint.isSatisfied(new ConstraintContext(LocalTime.of(10, 0))));
	}
	
	@Test
	void shouldAllowExactlyAtBoundary()
	{
	    TimeConstraint constraint = new TimeConstraint(LocalTime.of(22, 0));

	    assertTrue(constraint.isSatisfied(new ConstraintContext(LocalTime.of(22, 0))));
	}
}
