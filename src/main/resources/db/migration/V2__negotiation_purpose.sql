-- What each negotiation in progress is for (application layer, CLAUDE.md 2026-11-01, point 4b): starting the conversation,
-- changing the agreement or opening a discussion, and for a discussion the paragraph it will expand. The domain's
-- Negotiation deliberately doesn't know it. The row goes away with its negotiation.

CREATE TABLE negotiation_purpose (
    negotiation_id      UUID         NOT NULL PRIMARY KEY,
    purpose             VARCHAR(30)  NOT NULL,
    origin_paragraph_id UUID         NULL,
    CONSTRAINT fk_purpose_negotiation FOREIGN KEY (negotiation_id) REFERENCES negotiation (id) ON DELETE CASCADE
) ENGINE = InnoDB;
