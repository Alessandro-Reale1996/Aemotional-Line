package com.aemotionalline.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

/**
 * Every public method of a service runs in a transaction of the right kind (CLAUDE.md, 2026-11-03): a read-only one asks
 * for REPEATABLE READ (one consistent state across its queries), a write keeps the pool's READ COMMITTED, so that what it
 * reads after taking its locks is the latest committed data.
 */
public class ServiceTransactionsTest
{
	@Test
	void shouldGiveEveryServiceMethodTheRightKindOfTransaction() throws Exception
	{
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(Service.class));
		AnnotationTransactionAttributeSource source = new AnnotationTransactionAttributeSource();
		List<String> checked = new ArrayList<>();

		for (BeanDefinition definition : scanner.findCandidateComponents("com.aemotionalline.application"))
		{
			Class<?> service = Class.forName(definition.getBeanClassName());

			for (Method method : service.getDeclaredMethods())
			{
				if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic())
				{
					continue;
				}

				String name = service.getSimpleName() + "." + method.getName();
				TransactionAttribute attribute = source.getTransactionAttribute(method, service);
				assertNotNull(attribute, name + " runs without a transaction");

				int expected = attribute.isReadOnly() ? TransactionDefinition.ISOLATION_REPEATABLE_READ : TransactionDefinition.ISOLATION_DEFAULT;
				assertEquals(expected, attribute.getIsolationLevel(), name);
				checked.add(name);
			}
		}

		// The scan found the services (about fifty public methods today).
		assertTrue(checked.size() > 40, checked.toString());
	}
}
