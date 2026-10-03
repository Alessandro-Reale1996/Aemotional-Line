package com.aemotionalline.domain.constraint;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.user.UserId;



public class ConstraintSetTest
{
	@Test
	void shouldNotThrowWhenNoConstraintsPresent() {
	    ConstraintSet set = new ConstraintSet();

	    UserId user = new UserId(10L);
	    ConstraintContext context = new ConstraintContext(LocalTime.of(10, 0));

	    assertDoesNotThrow(() -> set.ensureSatisfiedBy(user, context));
	}
	
	@Test
	void shouldAcceptNullContextWhenTheUserHasNoConstraints()
	{
		ConstraintSet set = new ConstraintSet();
		set.replaceWith(List.of(new ConstraintAssignment(new UserId(20L), new TimeConstraint(LocalTime.of(22, 0)))));
		
		// Only user 20 is constrained: user 10 has nothing to check, so no context is needed.
		assertDoesNotThrow(() -> set.ensureSatisfiedBy(new UserId(10L), null));
	}
	
	@Test
	void shouldRequireContextWhenTheUserHasConstraints()
	{
		ConstraintSet set = new ConstraintSet();
		set.replaceWith(List.of(new ConstraintAssignment(new UserId(10L), new TimeConstraint(LocalTime.of(22, 0)))));
		
		assertThrows(DomainException.class, () -> set.ensureSatisfiedBy(new UserId(10L), null));
	}
	
	@Test
	void shouldRejectANullUser()
	{
		ConstraintSet set = new ConstraintSet();
		
		assertThrows(DomainException.class, () -> set.ensureSatisfiedBy(null, new ConstraintContext(LocalTime.NOON)));
	}
}
