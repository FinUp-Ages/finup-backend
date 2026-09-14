# Railforge Principles

## P1 - Requirements First

Understand the relevant goal, criteria, constraints, and non-goals before implementing.

## P2 - Recorded State

Development work should have a clear, recorded workflow state.

## P3 - Deliberate Transitions

Move between workflow stages intentionally and update the recorded state when the task changes stage or is completed.

## P4 - Scoped Work

Define the task scope and the files expected to change before implementation when appropriate to the size and risk of the work.

## P5 - Approved Scope Before Build

Implement only work that is understood, in scope, and aligned with the active task.

## P6 - No Silent Expansion

When new work is identified, make the scope change explicit and revisit requirements or planning as needed.

## P7 - Relevant Validation

Run the available and relevant checks before considering a change complete.

## P8 - Review Before Completion

Review the final diff and confirm the relevant criteria before marking the task as completed.

## P9 - User-Controlled Commits

Commits are separate from implementation and require explicit user authorization.

## P10 - Artifacts Over Memory

Versioned repository artifacts are the persistent source of truth. Conversation and agent memory are supporting context only.

## P11 - Proportional Process

Apply the workflow in proportion to the size, risk, and ambiguity of the change. Small, clear changes may use brief requirements, planning, and review records.

## P12 - Backend Context

For backend changes, preserve established Java, Spring Boot, package, API, persistence, and configuration patterns. Consider relevant API contracts, input validation, authorization, data integrity, migration impact, and environment-specific behavior.

## P13 - Responsible AI Context

For AI-related changes, make the intended behavior, input and output boundaries, sensitive-data constraints, failure handling, and evaluation approach explicit. Do not record secrets, credentials, or provider keys in versioned artifacts.

## Limits

Railforge is a lightweight, documentation-based workflow. It does not provide a CLI, hooks, runtime enforcement, task-graph execution, or sandboxing.

It helps prevent accidental workflow drift, silent scope expansion, incomplete validation, and premature commits through clear records and review.
