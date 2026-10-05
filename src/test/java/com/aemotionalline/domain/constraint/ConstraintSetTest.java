package com.aemotionalline.domain.constraint;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.common.DomainException;
import com.aemotionalline.domain.user.UserId;
import com.aemotionalline.domain.Ids;



public class ConstraintSetTest
{
	@Test
	void shouldNotThrowWhenNoConstraintsPresent() {
	    ConstraintSet set = new ConstraintSet();

	    UserId user = Ids.user(10L);
	    ConstraintContext context = new ConstraintContext(LocalTime.of(10, 0));

	    assertDoesNotThrow(() -> set.ensureSatisfiedBy(user, context));
	}
	
	@Test
	void shouldAcceptNullContextWhenTheUserHasNoConstraints()
	{
		ConstraintSet set = new ConstraintSet();
		set.replaceWith(List.of(new ConstraintAssignment(Ids.user(20L), new TimeConstraint(LocalTime.of(22, 0)))));
		
		// Only user 20 is constrained: user 10 has nothing to check, so no context is needed.
		assertDoesNotThrow(() -> set.ensureSatisfiedBy(Ids.user(10L), null));
	}
	
	@Test
	void shouldRequireContextWhenTheUserHasConstraints()
	{
		ConstraintSet set = new ConstraintSet();
		set.replaceWith(List.of(new ConstraintAssignment(Ids.user(10L), new TimeConstraint(LocalTime.of(22, 0)))));
		
		assertThrows(DomainException.class, () -> set.ensureSatisfiedBy(Ids.user(10L), null));
	}
	
	@Test
	void shouldNotAllowChangingTheSetThroughTheReturnedList()
	{
		ConstraintSet set = new ConstraintSet();
		ConstraintAssignment assignment = new ConstraintAssignment(Ids.user(10L), new TimeConstraint(LocalTime.of(22, 0)));
		set.replaceWith(List.of(assignment));

		assertThrows(UnsupportedOperationException.class, () -> set.getAssignments().add(assignment));
		assertThrows(UnsupportedOperationException.class, () -> set.getAssignments().clear());
		assertEquals(1, set.getAssignments().size());
	}

	@Test
	void shouldNotChangeWhenTheListPassedToReplaceWithIsModifiedLater()
	{
		ConstraintSet set = new ConstraintSet();
		ConstraintAssignment assignment = new ConstraintAssignment(Ids.user(10L), new TimeConstraint(LocalTime.of(22, 0)));
		List<ConstraintAssignment> source = new ArrayList<>(List.of(assignment));

		set.replaceWith(source);
		source.clear();

		assertEquals(1, set.getAssignments().size());
	}

	@Test
	void shouldKeepThePreviousConstraintsWhenReplaceWithFails()
	{
		ConstraintSet set = new ConstraintSet();
		ConstraintAssignment assignment = new ConstraintAssignment(Ids.user(10L), new TimeConstraint(LocalTime.of(22, 0)));
		set.replaceWith(List.of(assignment));

		List<ConstraintAssignment> withNull = new ArrayList<>();
		withNull.add(null);

		assertThrows(DomainException.class, () -> set.replaceWith(withNull));
		assertEquals(1, set.getAssignments().size());
	}

	@Test
	void shouldRejectANullUser()
	{
		ConstraintSet set = new ConstraintSet();
		
		assertThrows(DomainException.class, () -> set.ensureSatisfiedBy(null, new ConstraintContext(LocalTime.NOON)));
	}
}
