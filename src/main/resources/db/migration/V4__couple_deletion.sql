-- Ending couples and deleting their data (CLAUDE.md, S3 part 2, 2026-11-02).

-- Disabling a therapist locks their active couples (SELECT ... FOR UPDATE): the index keeps the lock to their rows.
CREATE INDEX ix_couple_therapist ON couple (therapist_id, ended_at);

-- The minimal trace of a couple whose data were deleted: no partner ids, no content. deleted_by is empty when the
-- retention period deleted it.
CREATE TABLE deleted_couple (
    couple_id           UUID         NOT NULL PRIMARY KEY,
    therapist_id        UUID         NOT NULL,
    ended_at            DATETIME(6)  NOT NULL,
    deleted_at          DATETIME(6)  NOT NULL,
    cause               VARCHAR(20)  NOT NULL,
    deleted_by          UUID         NULL
) ENGINE = InnoDB;
