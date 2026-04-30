package com.aemotionalline.domain.constraint;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.awt.List;
import java.time.LocalTime;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import com.aemotionalline.domain.user.UserId;



public class ConstraintSetTest
{
	@Test
	void shouldNotThrowWhenNoConstraintsPresent() {
	    ConstraintSet set = new ConstraintSet();

	    UserId user = new UserId(10L);
	    ConstraintContext context = new ConstraintContext(LocalTime.of(10, 0));

	    assertDoesNotThrow(() -> set.ensureSatisfiedBy(user, context));
	}}
