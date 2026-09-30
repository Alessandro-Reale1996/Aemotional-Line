# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Aemotional-Line: a deliberately slow, structured messaging system for couples, supervised by a couples therapist (paid version) — a free public version without therapist/archive is planned as a separate app. The full spec is an Italian design document ("Aemotional-Line - documento di progetto"); code and identifiers are in English, commit messages are often in Italian/English mix.

Planned stack (per spec): Spring Boot REST backend + Spring Data JPA on MySQL/MariaDB, React + Vite + React Router + Bootstrap frontend. **Current state: only the pure domain model exists** (`com.aemotionalline.domain`). No persistence, REST controllers, security config, or frontend yet; `pom.xml` has no JPA/DB dependencies. `spring-boot-starter-security` is on the classpath with no config, so any endpoint will be locked behind the generated default password.

## Build & test

Spring Boot 4.0.x, Java 25, Maven, JUnit 5.

```bash
./mvnw test                                        # all tests
./mvnw test -Dtest=NegotiationTest                 # one class
./mvnw test -Dtest=NegotiationTest#shouldThrowExceptionIfNegotiationStatusIsRefused   # one method
./mvnw spring-boot:run
```

The Maven wrapper needs only Java; plain `mvn` works too where Maven is installed. The project is also developed in Eclipse (`.project`/`.classpath`/`.settings` are committed).

## Domain architecture

The domain layer is plain Java (should not depend on Spring) and throws `DomainException` for every rule violation (one type, so the future REST layer can map all of them to one client error). IDs are small records (`UserId`, `ParagraphId`, `NegotiationId`, `DiscussionId(conversationId, discussionNumber)`, …). Tests live in mirrored packages under `src/test/java/com/aemotionalline/domain/` and construct aggregates directly (no Spring context), passing a `Clock` where time matters.

Key concepts and how they connect:

- **Couple** — two partners + one therapist. Most rules gate on `isPartner` / `isTherapist`.
- **Negotiation / Proposal** (`domain.negotiation`) — the "stipula" of the agreement (accordo): partners alternate turns (`currentResponder`) sending edited `Proposal` text + justification. `NegotiationStatus` (the request to talk is accepted/refused) is separate from `ProposalStatus` (the agreement text is accepted). Every sent proposal is kept in `ProposalArchive`; a conversation requires an accepted negotiation whose last proposal is accepted.
- **Conversation** (`domain.conversation`) — aggregate root. Created via `Conversation.start(id, couple, negotiation)`, which opens the first `Discussion` and sets the agreement. Holds the `ConversationGraph`, the `ConstraintSet`, a `NegotiationArchive`, and the set of `Discussion`s. `openDiscussion(negotiation)` creates a new branch (the spec's "discorso specifico"); `modifyAgreement(negotiation)` replaces the agreement. Discussion numbers derive from the negotiation archive size.
- **Discussion** — an ordered list of `Message`s; enforces turn-taking (the same user cannot send twice without a reply, via `setLastSender`).
- **Message / Paragraph** (`domain.message`) — messages are lists of paragraphs; no attachments. `Paragraph` is abstract with `SimpleParagraph`, `QuestionParagraph`, `PointedParagraph`, each with its own title/subtitle/body length limits and rules on which paragraph types it may reference.
- **ConversationGraph** — flat list of every sent paragraph; edges are `Paragraph.references` pointing from a reply to the earlier paragraph. Used to find roots, replies (`findRepliesTo`), and the whole topic branch of a paragraph — this is the basis of the spec's archive "history by topic". `Analyzer` computes unanswered questions.
- **Constraints** (`domain.constraint`) — `Constraint.isSatisfied(ConstraintContext)`; `ConstraintAssignment` binds a constraint to a user. Only the therapist creates a `ConstraintChangeRequest`; it applies to the conversation's `ConstraintSet` only after **both** partners approve. `Conversation.sendMessage(message, discussionId, context)` checks readiness, sender permission, and constraints before appending to the discussion and graph. Only `TimeConstraint` exists; the spec also calls for read→reply delays, location, and device constraints.

Code style: Allman braces, tabs/spaces mixed as in existing files; many methods re-verify collection mutations after performing them (e.g. "add then check contains") — follow surrounding style but don't treat those checks as meaningful logic.

## Current work

**Keep this section up to date.** Whenever an issue is fixed, a decision is made, or a session ends with work done, update the status and "Next" below and commit it with the related change, so a session on any workstation can pick up where the last one stopped.

### How we work
The user fixes the issues themselves and wants to be guided, not handed code. For each issue: explain what is wrong and why (reproduce it if useful), point to the code, suggest the tests and an approach, then review the user's change and run the tests. Do not edit `src/` unless the user explicitly asks for that specific task.

### The plan
Domain diagnostic and fix plan (written 2026-09-29 against `df1e867`): https://claude.ai/code/artifact/f2484746-ec41-47c7-b839-95c15769b5a8 — read it for the full description of each issue. Fix steps, in order:

1. Build — ✅ done (Maven wrapper added).
2. Negotiation state machine (A2–A6) — in progress (A5 left).
3. Couple: reject therapist == partner, `equals`/`hashCode`, check the negotiation's couple in `Conversation` (A7, A8).
4. Graph and questions: "answered" = has a POINTED reply in the graph; walk references with a visited set; ordered references (A1, A9, A10).
5. `Conversation.sendMessage` validation: references must be sent and in the same discussion, no duplicate paragraphs, POINTED only in replies, freeze sent messages, null context allowed without constraints (A11, A12).
6. Encapsulation: return copies, package-private mutators on `Discussion`/`ConversationGraph`/`Proposal`, `ConstraintChangeRequest` stores its couple, copies its list, validates assignees, adds reject/applied states (section B).
7. Tests: fill `AnalyzerTest`, fix assertion-less/misleading tests, one regression test per issue.

Spec gaps (section C: pointed-paragraph semantics, new constraint types, "discorso specifico" flow) are features, scheduled separately. Open question for the user: should POINTED paragraphs reply to any point (spec) or only answer questions (current code)?

### Status
| Issue | Status | Commit |
|---|---|---|
| A2 answering a proposal before one is sent | ✅ fixed | `59c2d6d` |
| A3 negotiation stuck in DRAFT with an accepted proposal | ✅ fixed | `59c2d6d` |
| A4 `acceptNegotiation` repeatable to skip a turn | ✅ fixed | `59c2d6d` |
| A6 refused proposal can still be accepted | ✅ fixed | `59c2d6d` |
| Exceptions: every rule violation in `Negotiation` is a `DomainException` | ✅ decided | `59c2d6d` |
| A5 proposals not tied to author/turn | 🔧 in progress — see below | (this commit) |
| A1, A7–A12, B, D | open | |

### A5 — decisions and state
Decided (2026-09-30): the stipula is a back-and-forth of the same text. Refusing a proposal = entering edit mode: `refuseProposal(user, newId)` marks the received proposal REFUSED and creates the refuser's draft (new id, author = refuser, text copied from the refused proposal). The refuser is then forced to send a counter-proposal; re-sending the same text is allowed, but always as a new object with a new id. Justification and `sentAt` are set in `sendProposal(user, justification)`, and the draft becomes `null` after sending. Refusing and creating the draft stay a single call, so nobody can stop halfway.

Done: `refuseProposal` creates the draft; `sendProposal` takes the justification, sets `sentAt`, clears the draft, and rejects sending when there is no draft. `Proposal.create(..., Clock)` was replaced by a public constructor `new Proposal(id, author, text)`; all tests were updated to the new API.

Still open in `Negotiation`/`Proposal` (reproduced with a probe):
1. No way to edit the draft's text (`text` is final, no edit method), so a counter-proposal can only repeat the received text.
2. `setCurrentProposal` is unchanged and bypasses every rule: out-of-turn replacement, re-sending an already-sent proposal (same object archived twice, justification overwritten), countering without refusing. Planned: remove it once editing exists.
3. `refuseProposal` accepts a duplicate id (archive ends with ids `[1, 1]`).
4. `refuseProposal(user, null)` throws `NullPointerException` *after* marking the proposal REFUSED: the negotiation is then stuck forever (no draft, can't refuse again, can't send). Validate parameters before changing state.
5. `setJustification`, `setSentAt`, `setProposalStatus` are public: anyone can rewrite archived proposals. Make them package-private.
6. `sendProposal` uses `Instant.now()` instead of an injected `Clock` (untestable; `Proposal` Javadoc still mentions the clock).
7. Pre-existing: `start` accepts an initial proposal authored by a non-partner.
8. Typo in the new message: "The is not a proposal to send."

### Next
Continue A5 in the order above: draft editing (1) → remove `setCurrentProposal` (2) → parameter/duplicate-id checks before any state change (3, 4) → package-private setters and `Clock` (5, 6), then add regression tests for each.
