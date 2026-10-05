package com.aemotionalline.domain.negotiation;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

// A sent proposal is part of the record of the agreement: only Negotiation may change its status, text or timestamp.
public class ProposalEncapsulationTest
{
	@Test
	void shouldNotExposeProposalSettersOutsideThePackage()
	{
		assertNotPublic("setProposalStatus");
		assertNotPublic("setSentAt");
		assertNotPublic("setJustification");
		assertNotPublic("setText");
	}

	private static void assertNotPublic(String methodName)
	{
		Method[] methods = Arrays.stream(Proposal.class.getDeclaredMethods())
				.filter(method -> method.getName().equals(methodName))
				.toArray(Method[]::new);

		assertFalse(methods.length == 0, "Proposal." + methodName + " not found");

		for (Method method : methods)
		{
			assertFalse(Modifier.isPublic(method.getModifiers()), "Proposal." + methodName + " must not be public");
		}
	}
}
