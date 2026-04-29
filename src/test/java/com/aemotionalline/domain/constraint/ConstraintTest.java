package com.aemotionalline.domain.constraint;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class ConstraintTest
{
	@Test
	void shouldNotCreateCostrainWithoutDescription()
	{
		assertThrows(IllegalArgumentException.class, () -> new Constraint(null));
		assertThrows(IllegalArgumentException.class, () -> new Constraint(""));
	}
}
