-- Security layer (CLAUDE.md, 2026-11-02): accounts, couple invitations, one-time links, ended couples.
-- The couples' user ids are not foreign keys to user_account yet: couples created before the accounts existed (and the
-- tests that build couples from bare ids) keep working; the services create couples only from invitations of accounts.

ALTER TABLE couple ADD COLUMN ended_at DATETIME(6) NULL;
CREATE INDEX ix_couple_partner_one ON couple (partner_one_id, ended_at);
CREATE INDEX ix_couple_partner_two ON couple (partner_two_id, ended_at);

-- user is a reserved word. The email is the login name: stored lower-cased by the domain, compared exactly (binary collation).
CREATE TABLE user_account (
    id                  UUID         NOT NULL PRIMARY KEY,
    email               VARCHAR(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    display_name        VARCHAR(100) NOT NULL,
    role                VARCHAR(20)  NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    password_hash       VARCHAR(255) NULL,
    failed_logins       INT          NOT NULL DEFAULT 0,
    locked_until        DATETIME(6)  NULL,
    UNIQUE KEY uq_user_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_uca1400_ai_ci;

CREATE TABLE couple_invitation (
    id                  UUID         NOT NULL PRIMARY KEY,
    therapist_id        UUID         NOT NULL,
    partner_one_id      UUID         NOT NULL,
    partner_two_id      UUID         NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    expires_at          DATETIME(6)  NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    closed_at           DATETIME(6)  NULL,
    declined_by         UUID         NULL,
    couple_id           UUID         NULL,
    INDEX ix_invitation_therapist (therapist_id, status),
    INDEX ix_invitation_partner_one (partner_one_id, status),
    INDEX ix_invitation_partner_two (partner_two_id, status)
) ENGINE = InnoDB;

CREATE TABLE couple_invitation_acceptance (
    invitation_id       UUID         NOT NULL,
    partner_id          UUID         NOT NULL,
    PRIMARY KEY (invitation_id, partner_id),
    CONSTRAINT fk_acceptance_invitation FOREIGN KEY (invitation_id) REFERENCES couple_invitation (id)
) ENGINE = InnoDB;

-- Only the SHA-256 hash of a link's code is stored (64 hex characters): the database holds no usable link.
CREATE TABLE one_time_link (
    code_hash           CHAR(64)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    user_id             UUID         NOT NULL,
    purpose             VARCHAR(20)  NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    expires_at          DATETIME(6)  NOT NULL,
    used_at             DATETIME(6)  NULL,
    CONSTRAINT fk_link_user FOREIGN KEY (user_id) REFERENCES user_account (id),
    INDEX ix_link_user (user_id, purpose)
) ENGINE = InnoDB;
