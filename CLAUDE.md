# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Aemotional-Line: a deliberately slow, structured messaging system for couples, supervised by a couples therapist (paid version) — a free public version without therapist/archive is planned as a separate app. The full spec is an Italian design document ("Aemotional-Line - documento di progetto"); code and identifiers are in English, commit messages are often in Italian/English mix.

Planned stack (per spec): Spring Boot REST backend + Spring Data JPA on MySQL/MariaDB, React + Vite + React Router + Bootstrap frontend. **Current state: only the pure domain model exists** (`com.aemotionalline.domain`). No persistence, REST controllers, security config, or frontend yet; `pom.xml` has no JPA/DB dependencies. `spring-boot-starter-security` is on the classpath with no config, so any endpoint will be locked behind the generated default password.

## Build & test

Spring Boot 4.0.x, Java 25, Maven, JUnit 5.

```bash
mvn test                                        # all tests
mvn test -Dtest=NegotiationTest                 # one class
mvn test -Dtest=NegotiationTest#shouldThrowExceptionIfNegotiationStatusIsRefused   # one method
mvn spring-boot:run
```

`./mvnw` is currently broken: `.mvn/wrapper/` is missing from the repo (regenerate with `mvn wrapper:wrapper`). The project is also developed in Eclipse (`.project`/`.classpath`/`.settings` are committed).

## Domain architecture

The domain layer is plain Java (should not depend on Spring) and throws `DomainException` for rule violations (`IllegalStateException` is used in a few `Negotiation` guards). IDs are small records (`UserId`, `ParagraphId`, `NegotiationId`, `DiscussionId(conversationId, discussionNumber)`, …). Tests live in mirrored packages under `src/test/java/com/aemotionalline/domain/` and construct aggregates directly (no Spring context), passing a `Clock` where time matters.

Key concepts and how they connect:

- **Couple** — two partners + one therapist. Most rules gate on `isPartner` / `isTherapist`.
- **Negotiation / Proposal** (`domain.negotiation`) — the "stipula" of the agreement (accordo): partners alternate turns (`currentResponder`) sending edited `Proposal` text + justification. `NegotiationStatus` (the request to talk is accepted/refused) is separate from `ProposalStatus` (the agreement text is accepted). Every sent proposal is kept in `ProposalArchive`; a conversation requires an accepted negotiation whose last proposal is accepted.
- **Conversation** (`domain.conversation`) — aggregate root. Created via `Conversation.start(id, couple, negotiation)`, which opens the first `Discussion` and sets the agreement. Holds the `ConversationGraph`, the `ConstraintSet`, a `NegotiationArchive`, and the set of `Discussion`s. `openDiscussion(negotiation)` creates a new branch (the spec's "discorso specifico"); `modifyAgreement(negotiation)` replaces the agreement. Discussion numbers derive from the negotiation archive size.
- **Discussion** — an ordered list of `Message`s; enforces turn-taking (the same user cannot send twice without a reply, via `setLastSender`).
- **Message / Paragraph** (`domain.message`) — messages are lists of paragraphs; no attachments. `Paragraph` is abstract with `SimpleParagraph`, `QuestionParagraph`, `PointedParagraph`, each with its own title/subtitle/body length limits and rules on which paragraph types it may reference.
- **ConversationGraph** — flat list of every sent paragraph; edges are `Paragraph.references` pointing from a reply to the earlier paragraph. Used to find roots, replies (`findRepliesTo`), and the whole topic branch of a paragraph — this is the basis of the spec's archive "history by topic". `Analyzer` computes unanswered questions.
- **Constraints** (`domain.constraint`) — `Constraint.isSatisfied(ConstraintContext)`; `ConstraintAssignment` binds a constraint to a user. Only the therapist creates a `ConstraintChangeRequest`; it applies to the conversation's `ConstraintSet` only after **both** partners approve. `Conversation.sendMessage(message, discussionId, context)` checks readiness, sender permission, and constraints before appending to the discussion and graph. Only `TimeConstraint` exists; the spec also calls for read→reply delays, location, and device constraints.

Code style: Allman braces, tabs/spaces mixed as in existing files; many methods re-verify collection mutations after performing them (e.g. "add then check contains") — follow surrounding style but don't treat those checks as meaningful logic.
