package com.aemotionalline.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.aemotionalline.TestcontainersConfiguration;

/**
 * The pool's transactions run at READ COMMITTED, and a read-only transaction that asks for REPEATABLE READ gets it, after
 * which the connection goes back to READ COMMITTED (CLAUDE.md, 2026-11-03).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class TransactionIsolationTest
{
	@Autowired
	private PlatformTransactionManager manager;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void shouldRunWritesAtReadCommittedAndConsistentReadsAtRepeatableRead()
	{
		TransactionTemplate write = new TransactionTemplate(manager);
		TransactionTemplate read = new TransactionTemplate(manager);
		read.setReadOnly(true);
		read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

		assertEquals("READ-COMMITTED", write.execute(status -> isolation()));
		assertEquals("REPEATABLE-READ", read.execute(status -> isolation()));
		assertEquals("READ-COMMITTED", write.execute(status -> isolation()));
	}

	private String isolation()
	{
		return jdbc.sql("SELECT @@transaction_isolation").query(String.class).single();
	}
}
