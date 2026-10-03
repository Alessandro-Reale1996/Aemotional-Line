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
- **Message / Paragraph** (`domain.message`) — messages are lists of paragraphs; no attachments. `Paragraph` is abstract with `SimpleParagraph`, `QuestionParagraph`, `PointedParagraph`, each with its own title/subtitle/body length limits and rules on which paragraph types it may reference (a POINTED references exactly one question). Two kinds of link: **references** (`addReference`/`getReferences`, rebuild the conversation) and **citations** (`addCitation`/`getCitations`, a link to the sender's own earlier paragraph, ignored by the graph and `Analyzer`). `Message.seal()`, called by `sendMessage`, makes the message and its paragraphs read-only.
- **ConversationGraph** — flat list of every sent paragraph; edges are `Paragraph.references` pointing from a reply to the earlier paragraph. Used to find roots, replies (`findRepliesTo`), and the whole topic branch of a paragraph — this is the basis of the spec's archive "history by topic". `Analyzer` computes unanswered questions.
- **Constraints** (`domain.constraint`) — `Constraint.isSatisfied(ConstraintContext)`; `ConstraintAssignment` binds a constraint to a user. Only the therapist creates a `ConstraintChangeRequest`; it applies to the conversation's `ConstraintSet` only after **both** partners approve. `Conversation.sendMessage(message, discussionId, context)` runs every check before any change (not already sent, readiness, sender permission, discussion, turn, constraints, then `ensureValidParagraphs` for ids, references, answers and citations), then appends to the discussion and graph and seals the message. The context may be null when the sender has no constraints. Only `TimeConstraint` exists; the spec also calls for read→reply delays, location, and device constraints.

Code style: Allman braces, tabs/spaces mixed as in existing files; many methods re-verify collection mutations after performing them (e.g. "add then check contains") — follow surrounding style but don't treat those checks as meaningful logic.

## Current work

**Keep this section up to date.** Whenever an issue is fixed, a decision is made, or a session ends with work done, update the status and "Next" below and commit it with the related change, so a session on any workstation can pick up where the last one stopped.

### 2026-10-01: finish negotiation (A5), fix Couple (A7), couple check (A8), unanswered questions (A1)

- **Done:** `Negotiation` — `editCurrentProposal(user, text)` replaces `setCurrentProposal`; `refuseProposal`/`editCurrentProposal` run every check (turn, phase, pending proposal, null/duplicate id across the archive, blank text) before changing state; `start` takes a `Clock` (used for `sentAt`) and rejects a non-partner initial author; `Proposal` setters are package-private and text can't be blank. `Couple` rejects a therapist who is also a partner, null arguments are `DomainException`, `equals`/`hashCode` by id. `Conversation.ensureNegotiationOfThisCouple` guards `start`/`openDiscussion`/`modifyAgreement`. `QuestionParagraph.isAnswered()` removed; `Analyzer` treats a question as answered when a POINTED paragraph references it. Tests: 86 pass (new cases in `NegotiationTest`, `CoupleTest`, `ConversationTest`, `AnalyzerTest`; `CoupleTest.shouldReconizeTherapist` fixed).
- **Decisions:** refusing a proposal = entering edit mode on a new draft (new id, author = refuser, refused text copied); a couple is equal by id; "answered" is computed from the replies, never from the question's own references.
- **State / next steps:** A9 was explained to the user but not fixed — reference cycles hang `ConversationGraph.findRootOf` (infinite loop) and crash `collectRepliesRecursively` (`StackOverflowError`). Recommended: fix A9 and A10 together (visited set; walk all references; `findRootOf` likely becomes a multi-root lookup; on a root-less cycle throw `DomainException`; tests with `assertTimeoutPreemptively`). The user hasn't chosen yet whether to do it themselves or let Claude. Known gaps in `Negotiation` are listed under "Negotiation — how it works now".

### 2026-10-03: graph traversal (A9, A10) and sendMessage validation (step 5: A11, A12)

- **Done:** `ConversationGraph` — `findRootsOf` (replaces `findRootOf`) and `findConversationBranchContaining` walk every reference iteratively with a visited set (no infinite loop, no `StackOverflowError`, no duplicates); `Paragraph.references` is a `LinkedHashSet`; `containsParagraph` added; null arguments are `DomainException`. `Conversation.sendMessage` now checks everything before any change: already-sent message, turn, constraints, then `ensureValidParagraphs` — unique paragraph ids (5b), references only to the last message of the discussion (5c), one POINTED answer per question and no POINTED without a question (5d), citations only of the sender's own sent paragraphs (5g); it then seals the message (`Message.seal()` / package-private `Paragraph.seal()`, 5e), which with 5c makes cycles impossible at the source. `PointedParagraph` references exactly one question. `Paragraph.addCitation`/`getCitations` are a separate link ignored by the graph and `Analyzer`. `ConstraintSet.ensureSatisfiedBy` accepts a null context when the sender has no constraints (5f). Tests: new `ConversationSendMessageTest` (`assertUnchanged` checks that a rejected message changes nothing), plus additions in `ConversationGraphTest`, `ParagraphTest`, `MessageTest`, `ConstraintSetTest` (stray `java.awt.List` import fixed). 128 tests pass.
- **Decisions (user):** multiple references in both directions; merged branches for joined topics; a question gets one POINTED answer and a POINTED answers one question; references only to the message being answered (same-message references forbidden); citations are a separate kind of link. See "Paragraph link rules".
- **State / next steps:** citation scope (same discussion? first message? same topic?) is still open. A9/A10 code was first written earlier the same day, lost in a `git reset`, and restored from commit `8bd41f1` (branch `backup-2026-10-03`); stash `step 5 del 2026-10-03` is an obsolete step-5 attempt and can be dropped. Run tests without `clean` while Eclipse is open. Next: step 6 (encapsulation).

### How we work
The user fixes the issues themselves and wants to be guided, not handed code. For each issue: explain what is wrong and why (reproduce it if useful), point to the code, suggest the tests and an approach, then review the user's change and run the tests. Do not edit `src/` unless the user explicitly asks for that specific task.

### The plan
Domain diagnostic and fix plan (written 2026-09-29 against `df1e867`): https://claude.ai/code/artifact/f2484746-ec41-47c7-b839-95c15769b5a8 — read it for the full description of each issue. Fix steps, in order:

1. Build — ✅ done (Maven wrapper added).
2. Negotiation state machine (A2–A6) — ✅ done.
3. Couple: reject therapist == partner, `equals`/`hashCode`, check the negotiation's couple in `Conversation` (A7, A8) — ✅ done.
4. Graph and questions: "answered" = has a POINTED reply in the graph; walk references with a visited set; ordered references (A1, A9, A10) — ✅ done.
5. `Conversation.sendMessage` validation: references must be sent and in the same discussion, no duplicate paragraphs, POINTED only in replies, freeze sent messages, null context allowed without constraints (A11, A12) — ✅ done (5a–5g).
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
| A5 proposals not tied to author/turn | ✅ fixed | 2026-10-01 |
| A7 therapist can also be a partner; `Couple` had no `equals`/`hashCode` | ✅ fixed (null arguments now `DomainException` too; equality by id) | 2026-10-01 |
| A8 a negotiation of another couple could start a conversation, open a discussion or change the agreement | ✅ fixed (`Conversation.ensureNegotiationOfThisCouple`, compares by `Couple.equals`) | 2026-10-01 |
| A1 every question reported unanswered (checked the question's own references) | ✅ fixed: `QuestionParagraph.isAnswered()` removed; `Analyzer` looks for a POINTED reply (`ConversationGraph.findRepliesTo` for a conversation, the discussion's own paragraphs for a discussion); `AnalyzerTest` filled | 2026-10-01 |
| A9 reference cycles hung `findRootOf` and overflowed `collectRepliesRecursively` | ✅ fixed: iterative walks with a visited set; a root-less cycle makes `findRootsOf` throw `DomainException` | 2026-10-03 |
| A10 `findRootOf` followed only the first (arbitrary) reference; diamonds counted twice | ✅ fixed: `Paragraph.references` is a `LinkedHashSet`; `findRootOf` replaced by `findRootsOf`; topics joined by a reply are merged into one branch, each paragraph once, in sending order | 2026-10-03 |
| A11 step 5a+5b: checks before changes, unique paragraph ids | ✅ done: `sendMessage` checks the turn before any change; `ensureValidParagraphs` rejects an id already in the graph or repeated in the message; `ConversationGraph.containsParagraph`; new `ConversationSendMessageTest` (`assertUnchanged` helper) | 2026-10-03 |
| A11 step 5c: references only to the message being answered (rule 3) | ✅ done: `ensureValidParagraphs(message, discussion)` accepts a reference only if its id belongs to the last message of the discussion (none in a first message); covers unsent targets, other discussions, older messages, own paragraphs, same-message references (user confirmed the last one is forbidden) | 2026-10-03 |
| A11 step 5d: one answer per question, POINTED must answer (rules 2, 4) | ✅ done: `PointedParagraph.addReference` rejects a second question; `ensureValidParagraphs` rejects a POINTED without a question and a question answered twice in the message (answers can only target the last message, so counting within the message suffices) | 2026-10-03 |
| A11 step 5e: sent messages and paragraphs were still mutable | ✅ done: `Message.seal()` (called at the end of `sendMessage`) blocks `addParagraph` and seals the paragraphs (package-private `Paragraph.seal()`; `validateReference` rejects a sealed paragraph before any subclass rule); `sendMessage` rejects an already-sent message. With 5c, references only target sealed paragraphs, so cycles can no longer be created | 2026-10-03 |
| A12 step 5f: null `ConstraintContext` failed even without constraints | ✅ done in `ConstraintSet.ensureSatisfiedBy` (context required only if the sender has constraints; null user is a `DomainException`); `ConstraintSetTest` had a stray `java.awt.List` import, fixed | 2026-10-03 |
| A11 step 5g: citations (rule 5) | ✅ done: `Paragraph.addCitation`/`getCitations` (any type, no self/duplicate, sealed check); `sendMessage` accepts a citation only of a paragraph already sent by the same sender (any earlier message, exempt from rule 3); graph and `Analyzer` ignore citations | 2026-10-03 |
| B, D | open | |

### Negotiation — how it works now (A5 decisions)
The stipula is a back-and-forth of the same text. `Negotiation.start(id, couple, initialProposal, clock)` requires the initial author to be a partner; the clock sets `sentAt`. Refusing = entering edit mode: `refuseProposal(user, newId)` marks the received proposal REFUSED and creates the refuser's draft (new id, author = refuser, refused text copied); every check (turn, phase, pending proposal, null/duplicate id across the whole archive) runs before any state change. `editCurrentProposal(user, text)` lets only the draft's author, on their turn, change the text (never blank). `sendProposal(user, justification)` sets justification and `sentAt`, archives the draft and clears it. `setCurrentProposal` was removed. `Proposal` setters are package-private, so archived proposals can't be rewritten from outside. Every rule violation is a `DomainException`, including null user/id/text in the action methods; `start` still uses `Objects.requireNonNull` (NPE) for its arguments. Known remaining gaps: `getProposals()` exposes the mutable `ProposalArchive` (outsiders can `add`, which can leave the negotiation stuck — fix in step 6), one `Proposal` object can be passed to two negotiations, and there is no way to end a negotiation without agreement once accepted (spec question). Regression tests for each case are in `NegotiationTest` (73 tests pass).

### Paragraph link rules (decided by the user, 2026-10-03)
1. A paragraph may reply to / refer to **several** earlier paragraphs, and a paragraph may be referred to by **several** paragraphs (revised by the user the same day; it replaces an earlier "at most one" decision).
2. A question can be answered **once**, by **one** POINTED paragraph; a POINTED paragraph answers **exactly one** question (decided 2026-10-03).
3. Several paragraphs of the **same reply message** may refer to the same paragraph; links may only point to the message being answered (not from later messages).
4. A POINTED paragraph must always reply to at least one paragraph (so no POINTED in the first message of a discussion).
5. **Citations are a separate kind of link** from references. A *reference* rebuilds the conversation on a topic (it is what `ConversationGraph` walks, and rules 1–3 apply to it). A *citation* only points to a paragraph **the same user wrote earlier**, shown as a link in the reply text; it does **not** take part in topic reconstruction (graph walks and `Analyzer` must ignore it) and is exempt from rule 3. Implemented minimally (2026-10-03): a citation must point to a paragraph already sent by the same sender, anywhere in the conversation. Still open for the user: whether citations are limited to the same discussion, allowed in a first message, and must belong to the citing paragraph's topic (the spec says they should).

Rule 1 keeps `Paragraph.references` a set, so A10 stays fully relevant. Rules 2–5 need the whole discussion, so they go in `Conversation.sendMessage` (step 5, A11).

### Next
Step 6 of the plan (encapsulation, section B): `sendMessage`'s validation can still be bypassed through public mutators — `Discussion.addMessage`/`setLastSender`, `ConversationGraph.addParagraph`, `ConstraintSet.getAssignments()` (returns the live list), `Negotiation.getProposals()` (exposes `ProposalArchive.add`); `ConstraintChangeRequest` should store its couple, copy its list, validate assignees, and get reject/applied states.
