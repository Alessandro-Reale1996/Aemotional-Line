package com.aemotionalline.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.aemotionalline.TestcontainersConfiguration;

/**
 * Flyway builds the whole schema on an empty MariaDB, and the conventions agreed for it hold: native UUID ids,
 * an accent- and case-insensitive collation for text, one conversation per couple.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class SchemaMigrationTest
{
	private static final List<String> TABLES = List.of(
			"couple", "negotiation", "proposal", "conversation", "discussion", "message", "message_skipped_question", "message_read",
			"paragraph", "paragraph_reference", "paragraph_self_citation", "negotiation_record", "failed_negotiation_record",
			"constraint_request", "constraint_request_approval", "constraint_assignment", "closing_request", "therapist_closing_request",
			"outcome_declaration", "negotiation_purpose");

	@Autowired
	private JdbcClient jdbc;

	@Test
	void shouldCreateEveryTable()
	{
		List<String> tables = jdbc.sql("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()")
				.query(String.class)
				.list();

		assertTrue(tables.containsAll(TABLES), "missing: " + TABLES.stream().filter(table -> !tables.contains(table)).toList());
	}

	@Test
	void shouldStoreIdsAsNativeUuids()
	{
		String type = jdbc.sql("SELECT data_type FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'paragraph' AND column_name = 'id'")
				.query(String.class)
				.single();

		assertEquals("uuid", type.toLowerCase());
	}

	@Test
	void shouldCompareTextIgnoringCaseAndAccents()
	{
		Boolean matches = jdbc.sql("SELECT CAST('Perché' AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_uca1400_ai_ci LIKE '%PERCHE%'")
				.query(Boolean.class)
				.single();

		assertTrue(matches);
	}

	@Test
	void shouldKeepOneConversationPerCouple()
	{
		UUID couple = UUID.randomUUID();
		jdbc.sql("INSERT INTO couple (id, partner_one_id, partner_two_id, therapist_id) VALUES (?, ?, ?, ?)")
				.params(couple, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
				.update();
		jdbc.sql("INSERT INTO conversation (id, couple_id) VALUES (?, ?)").params(UUID.randomUUID(), couple).update();

		assertTrue(failsToInsertASecondConversationFor(couple));
	}

	private boolean failsToInsertASecondConversationFor(UUID couple)
	{
		try
		{
			jdbc.sql("INSERT INTO conversation (id, couple_id) VALUES (?, ?)").params(UUID.randomUUID(), couple).update();
			return false;
		}
		catch (org.springframework.dao.DataIntegrityViolationException expected)
		{
			return true;
		}
	}
}
