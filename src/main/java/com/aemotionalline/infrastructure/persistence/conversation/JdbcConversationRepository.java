package com.aemotionalline.infrastructure.persistence.conversation;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.aemotionalline.domain.conversation.Conversation;
import com.aemotionalline.domain.conversation.ConversationId;
import com.aemotionalline.domain.conversation.ConversationRepository;
import com.aemotionalline.domain.couple.CoupleId;

/**
 * The domain's ConversationRepository on MariaDB, with explicit SQL (JdbcClient): the conversation's tables have composite
 * keys and are mostly append-only, which Spring Data JDBC's repositories handle poorly. Loading and saving are in
 * ConversationReader and ConversationWriter.
 */
@Repository
public class JdbcConversationRepository implements ConversationRepository
{
	private final JdbcClient jdbc;
	private final ConversationReader reader;
	private final ConversationWriter writer;

	JdbcConversationRepository(JdbcClient jdbc, ConversationReader reader, ConversationWriter writer)
	{
		this.jdbc = jdbc;
		this.reader = reader;
		this.writer = writer;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Conversation> findById(ConversationId id)
	{
		return reader.load(id.value());
	}

	/** The lock lasts until the end of the caller's transaction, so there must be one. */
	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<Conversation> findByIdForUpdate(ConversationId id)
	{
		Optional<UUID> locked = jdbc.sql("SELECT id FROM conversation WHERE id = ? FOR UPDATE").param(id.value())
				.query((rs, row) -> Columns.uuid(rs, "id")).optional();

		return locked.flatMap(reader::load);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Conversation> findByCouple(CoupleId coupleId)
	{
		return jdbc.sql("SELECT id FROM conversation WHERE couple_id = ?").param(coupleId.value())
				.query((rs, row) -> Columns.uuid(rs, "id")).optional()
				.flatMap(reader::load);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ConversationId> findIdByCouple(CoupleId coupleId)
	{
		return jdbc.sql("SELECT id FROM conversation WHERE couple_id = ?").param(coupleId.value())
				.query((rs, row) -> new ConversationId(Columns.uuid(rs, "id"))).optional();
	}

	@Override
	@Transactional
	public void save(Conversation conversation)
	{
		writer.save(conversation);
	}
}
