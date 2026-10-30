-- Aemotional-Line, initial schema (persistence design: CLAUDE.md, 2026-10-30).
-- Principle: store the facts, derive the rest. Not stored, but derived when a conversation is loaded: the paragraph
-- graph (paragraphs + references, in message sequence order), a discussion's last sender, the agreement in force,
-- the constraints in force and an applied request's previous constraints, a therapist request's snapshot (the first
-- previous_count closing requests of its discussion).
-- Conventions: MariaDB native UUID ids, enums as text, instants in UTC as DATETIME(6), utf8mb4 text with an accent- and
-- case-insensitive collation (keyword searches), positions keep the order of lists.

SET NAMES utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;

-- COUPLE --------------------------------------------------------------------------------------------------------

CREATE TABLE couple (
    id                  UUID         NOT NULL PRIMARY KEY,
    partner_one_id      UUID         NOT NULL,
    partner_two_id      UUID         NOT NULL,
    therapist_id        UUID         NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_uca1400_ai_ci;

-- NEGOTIATIONS IN PROGRESS --------------------------------------------------------------------------------------
-- A negotiation is kept here while in progress (a stipula lasts days). Once its history is copied into a conversation
-- (a negotiation_record or failed_negotiation_record), this header is deleted and its proposals stay, owned by the
-- record. A failed attempt to start a couple's conversation stays here until the conversation starts.

CREATE TABLE negotiation (
    id                  UUID         NOT NULL PRIMARY KEY,
    couple_id           UUID         NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    current_responder   UUID         NOT NULL,
    -- The request as the other partner received it (a frozen copy of the initial proposal).
    request_id          UUID         NOT NULL,
    request_author      UUID         NOT NULL,
    request_kind        VARCHAR(20)  NOT NULL,
    request_text        TEXT         NULL,
    request_title       VARCHAR(100) NULL,
    request_subtitle    VARCHAR(200) NULL,
    started_at          DATETIME(6)  NOT NULL,
    closed_by           UUID         NULL,
    closed_at           DATETIME(6)  NULL,
    CONSTRAINT fk_negotiation_couple FOREIGN KEY (couple_id) REFERENCES couple (id),
    INDEX ix_negotiation_couple (couple_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_uca1400_ai_ci;

-- Proposals of a negotiation, in progress or recorded in a conversation. The sent ones have a position; the draft in
-- progress (status DRAFT) has none and never reaches a record. When a failed negotiation sent nothing, its record keeps
-- the request as its only proposal.
CREATE TABLE proposal (
    id                  UUID         NOT NULL PRIMARY KEY,
    negotiation_id      UUID         NOT NULL,
    position            INT          NULL,
    author_id           UUID         NOT NULL,
    kind                VARCHAR(20)  NOT NULL,
    text                TEXT         NULL,
    title               VARCHAR(100) NULL,
    subtitle            VARCHAR(200) NULL,
    justification       TEXT         NULL,
    sent_at             DATETIME(6)  NULL,
    status              VARCHAR(30)  NOT NULL,
    UNIQUE KEY uq_proposal_position (negotiation_id, position)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_uca1400_ai_ci;

-- CONVERSATION --------------------------------------------------------------------------------------------------

CREATE TABLE conversation (
    id                  UUID         NOT NULL PRIMARY KEY,
    couple_id           UUID         NOT NULL,
    CONSTRAINT fk_conversation_couple FOREIGN KEY (couple_id) REFERENCES couple (id),
    -- One conversation per couple.
    UNIQUE KEY uq_conversation_couple (couple_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_uca1400_ai_ci;

CREATE TABLE discussion (
    conversation_id     UUID         NOT NULL,
    number              INT          NOT NULL,
    title               VARCHAR(100) NULL,
    subtitle            VARCHAR(200) NULL,
    origin_paragraph_id UUID         NULL,
    closed_way          VARCHAR(20)  NULL,
    closed_at           DATETIME(6)  NULL,
    PRIMARY KEY (conversation_id, number),
    CONSTRAINT fk_discussion_conversation FOREIGN KEY (conversation_id) REFERENCES conversation (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_uca1400_ai_ci;

-- sequence is the sending order across the whole conversation (it rebuilds the graph in order).
CREATE TABLE message (
    id                  UUID         NOT NULL PRIMARY KEY,
    conversation_id     UUID         NOT NULL,
    discussion_number   INT          NOT NULL,
    sequence            INT          NOT NULL,
    sender_id           UUID         NOT NULL,
    sent_at             DATETIME(6)  NOT NULL,
    CONSTRAINT fk_message_discussion FOREIGN KEY (conversation_id, discussion_number) REFERENCES discussion (conversation_id, number),
    UNIQUE KEY uq_message_sequence (conversation_id, sequence),
    INDEX ix_message_sent_at (conversation_id, sent_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_uca1400_ai_ci;

CREATE TABLE message_skipped_question (
    message_id          UUID         NOT NULL,
    position            INT          NOT NULL,
    question_id         UUID         NOT NULL,
    PRIMARY KEY (message_id, position),
    CONSTRAINT fk_skipped_message FOREIGN KEY (message_id) REFERENCES message (id)
) ENGINE = InnoDB;

-- The first read of a message by its recipient (the other partner).
CREATE TABLE message_read (
    message_id          UUID         NOT NULL PRIMARY KEY,
    read_at             DATETIME(6)  NOT NULL,
    CONSTRAINT fk_read_message FOREIGN KEY (message_id) REFERENCES message (id)
) ENGINE = InnoDB;

-- type: SIMPLE, QUESTION or POINTED. A pointed paragraph's title is computed from its question, so it isn't stored;
-- its question is its one reference.
CREATE TABLE paragraph (
    id                  UUID         NOT NULL PRIMARY KEY,
    message_id          UUID         NOT NULL,
    position            INT          NOT NULL,
    type                VARCHAR(10)  NOT NULL,
    title               VARCHAR(250) NULL,
    subtitle            VARCHAR(200) NULL,
    body                VARCHAR(800) NULL,
    CONSTRAINT fk_paragraph_message FOREIGN KEY (message_id) REFERENCES message (id),
    UNIQUE KEY uq_paragraph_position (message_id, position)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_uca1400_ai_ci;

CREATE TABLE paragraph_reference (
    paragraph_id        UUID         NOT NULL,
    position            INT          NOT NULL,
    referenced_id       UUID         NOT NULL,
    PRIMARY KEY (paragraph_id, position),
    CONSTRAINT fk_reference_paragraph FOREIGN KEY (paragraph_id) REFERENCES paragraph (id),
    CONSTRAINT fk_reference_target FOREIGN KEY (referenced_id) REFERENCES paragraph (id),
    INDEX ix_reference_target (referenced_id)
) ENGINE = InnoDB;

CREATE TABLE paragraph_self_citation (
    paragraph_id        UUID         NOT NULL,
    position            INT          NOT NULL,
    cited_id            UUID         NOT NULL,
    PRIMARY KEY (paragraph_id, position),
    CONSTRAINT fk_citation_paragraph FOREIGN KEY (paragraph_id) REFERENCES paragraph (id),
    CONSTRAINT fk_citation_target FOREIGN KEY (cited_id) REFERENCES paragraph (id)
) ENGINE = InnoDB;

-- NEGOTIATION HISTORY -------------------------------------------------------------------------------------------
-- The proposals of a recorded negotiation stay in the proposal table, by negotiation_id.

CREATE TABLE negotiation_record (
    negotiation_id      UUID         NOT NULL PRIMARY KEY,
    conversation_id     UUID         NOT NULL,
    position            INT          NOT NULL,
    purpose             VARCHAR(30)  NOT NULL,
    discussion_number   INT          NULL,
    archived_at         DATETIME(6)  NOT NULL,
    CONSTRAINT fk_record_conversation FOREIGN KEY (conversation_id) REFERENCES conversation (id),
    UNIQUE KEY uq_record_position (conversation_id, position)
) ENGINE = InnoDB;

CREATE TABLE failed_negotiation_record (
    negotiation_id      UUID         NOT NULL PRIMARY KEY,
    conversation_id     UUID         NOT NULL,
    position            INT          NOT NULL,
    attempted_purpose   VARCHAR(30)  NOT NULL,
    outcome             VARCHAR(20)  NOT NULL,
    closed_by           UUID         NOT NULL,
    started_at          DATETIME(6)  NOT NULL,
    closed_at           DATETIME(6)  NOT NULL,
    CONSTRAINT fk_failed_conversation FOREIGN KEY (conversation_id) REFERENCES conversation (id),
    UNIQUE KEY uq_failed_position (conversation_id, position)
) ENGINE = InnoDB;

-- CONSTRAINTS ---------------------------------------------------------------------------------------------------
-- The constraints in force are the assignments of the last APPLIED request; an applied request's previous constraints
-- are those of the APPLIED request before it.

CREATE TABLE constraint_request (
    id                  UUID         NOT NULL PRIMARY KEY,
    conversation_id     UUID         NOT NULL,
    position            INT          NOT NULL,
    proposed_by         UUID         NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    proposed_at         DATETIME(6)  NOT NULL,
    closed_at           DATETIME(6)  NULL,
    rejected_by         UUID         NULL,
    CONSTRAINT fk_constraint_request_conversation FOREIGN KEY (conversation_id) REFERENCES conversation (id),
    UNIQUE KEY uq_constraint_request_position (conversation_id, position)
) ENGINE = InnoDB;

CREATE TABLE constraint_request_approval (
    request_id          UUID         NOT NULL,
    partner_id          UUID         NOT NULL,
    PRIMARY KEY (request_id, partner_id),
    CONSTRAINT fk_approval_request FOREIGN KEY (request_id) REFERENCES constraint_request (id)
) ENGINE = InnoDB;

-- type: TIME (not_before), REPLY_DELAY (delay_millis), LOCATION (latitude, longitude, radius_metres, direction).
CREATE TABLE constraint_assignment (
    request_id          UUID         NOT NULL,
    position            INT          NOT NULL,
    user_id             UUID         NOT NULL,
    type                VARCHAR(20)  NOT NULL,
    scope               VARCHAR(10)  NOT NULL,
    not_before          TIME         NULL,
    delay_millis        BIGINT       NULL,
    latitude            DOUBLE       NULL,
    longitude           DOUBLE       NULL,
    radius_metres       DOUBLE       NULL,
    direction           VARCHAR(10)  NULL,
    PRIMARY KEY (request_id, position),
    CONSTRAINT fk_assignment_request FOREIGN KEY (request_id) REFERENCES constraint_request (id)
) ENGINE = InnoDB;

-- CLOSING A SPECIFIC DISCUSSION ---------------------------------------------------------------------------------

CREATE TABLE closing_request (
    conversation_id     UUID         NOT NULL,
    discussion_number   INT          NOT NULL,
    position            INT          NOT NULL,
    requester           UUID         NOT NULL,
    sent_at             DATETIME(6)  NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    ended_at            DATETIME(6)  NULL,
    PRIMARY KEY (conversation_id, discussion_number, position),
    CONSTRAINT fk_closing_discussion FOREIGN KEY (conversation_id, discussion_number) REFERENCES discussion (conversation_id, number)
) ENGINE = InnoDB;

-- previous_count: the snapshot shown to the therapist is the first previous_count closing requests of the discussion.
CREATE TABLE therapist_closing_request (
    conversation_id     UUID         NOT NULL,
    discussion_number   INT          NOT NULL,
    position            INT          NOT NULL,
    requester           UUID         NOT NULL,
    justification       VARCHAR(800) NOT NULL,
    sent_at             DATETIME(6)  NOT NULL,
    previous_count      INT          NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    decided_at          DATETIME(6)  NULL,
    PRIMARY KEY (conversation_id, discussion_number, position),
    CONSTRAINT fk_therapist_closing_discussion FOREIGN KEY (conversation_id, discussion_number) REFERENCES discussion (conversation_id, number)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_uca1400_ai_ci;

CREATE TABLE outcome_declaration (
    conversation_id     UUID         NOT NULL,
    discussion_number   INT          NOT NULL,
    partner_id          UUID         NOT NULL,
    position            INT          NOT NULL,
    outcome             VARCHAR(10)  NOT NULL,
    reason              VARCHAR(200) NOT NULL,
    declared_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (conversation_id, discussion_number, partner_id),
    CONSTRAINT fk_declaration_discussion FOREIGN KEY (conversation_id, discussion_number) REFERENCES discussion (conversation_id, number)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_uca1400_ai_ci;
