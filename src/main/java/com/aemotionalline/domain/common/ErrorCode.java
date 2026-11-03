package com.aemotionalline.domain.common;

/**
 * The stable code of a violated rule (CLAUDE.md, REST point 3 and R1): the frontend picks its Italian message by code,
 * while the exception's message stays in English for developers. A specific code exists only for the rules a user can
 * meet through the interface; the programming guards (null arguments, repeated ids, the shape of restored data) keep
 * RULE_VIOLATED. A code, once published, keeps its name: new ones may be added.
 */
public enum ErrorCode
{
	/** A programming guard: the request breaks an invariant a correct client never breaks. */
	RULE_VIOLATED,
	/** Something the client named doesn't exist (answered 404, like the application's "not found"). */
	NOT_FOUND,

	// Roles and the couple
	NOT_A_PARTNER,
	NOT_THE_THERAPIST,
	COUPLE_ENDED,
	COUPLE_NOT_ENDED,

	// Messages and the links between paragraphs
	NOT_YOUR_TURN,
	MESSAGE_NOT_OPENED,
	MESSAGE_EMPTY,
	REFERENCE_NOT_ALLOWED,
	ORIGIN_REFERENCE_REQUIRED,
	PARAGRAPH_EXPANDED_ELSEWHERE,
	QUESTION_NEEDS_POINTED_ANSWER,
	QUESTION_ALREADY_ANSWERED,
	SELF_CITATION_NOT_ALLOWED,
	/** A text is blank: the message names the field. */
	TEXT_REQUIRED,
	/** A text is longer than its limit: the message names the field and the limit. */
	TEXT_TOO_LONG,

	// Specific discussions and their closing
	PARAGRAPH_ALREADY_EXPANDED,
	EXPANSION_ALREADY_REQUESTED,
	DISCUSSION_CLOSED,
	DISCUSSION_NOT_CLOSED,
	CLOSING_REQUEST_PENDING,
	NO_PENDING_CLOSING_REQUEST,
	THERAPIST_REQUEST_NOT_ALLOWED_YET,
	OUTCOME_ALREADY_DECLARED,

	// Negotiations and constraints
	AGREEMENT_NEGOTIATION_IN_PROGRESS,
	NEGOTIATION_WRONG_PHASE,
	NEGOTIATION_ENDED,
	CONSTRAINT_NOT_SATISFIED,
	CONSTRAINT_REQUEST_PENDING,
	CONSTRAINT_REQUEST_CLOSED,
	INVALID_CONSTRAINT,

	// Invitations and accounts
	SAME_EMAIL_TWICE,
	CANNOT_BE_INVITED,
	PARTNER_IN_ACTIVE_COUPLE,
	INVITATION_ALREADY_ACCEPTED,
	INVITATION_CLOSED,
	INVITATION_EXPIRED,
	INVALID_EMAIL,
	EMAIL_ALREADY_USED,
	ACCOUNT_IN_ACTIVE_COUPLE,
	ADMINISTRATOR_EXISTS,
	ADMINISTRATOR_PENDING
}
