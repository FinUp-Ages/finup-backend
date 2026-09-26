# Workflow

## Source of truth

Conversation, agent memory, and agent reasoning do not replace the state recorded in the repository.

The canonical workflow state is `.railforge/state.json`. It is versioned, small, and updated manually when a task changes stage or is completed. It is not an execution log.

Other versioned project artifacts may record requirements, decisions, plans, and validation results when useful for the task.

## Backend context

This variant applies to a Java and Spring Boot backend with AI capabilities.

When a task affects an API, identify affected consumers, contract compatibility, input validation, and authorization concerns. When it affects persistence, identify relevant data-integrity and migration impact. When it affects AI behavior, record the intended outcome, input and output boundaries, sensitive-data constraints, failure handling, and validation approach. Secrets, credentials, and provider keys must not be recorded in versioned artifacts.

## Flow

Use the stages below in proportion to the size and risk of the change:

1. `DISCOVERY` — clarify questions and record relevant decisions.
2. `REQUIREMENTS` — define the goal, criteria, constraints, and non-goals.
3. `PLANNING` — define the plan, files in scope, risks, and validation approach.
4. `IMPLEMENTING` — change only the approved scope.
5. `VALIDATING` — run the available and relevant checks, then review the diff.
6. `REVIEWING` — confirm the criteria and record any remaining items.
7. `COMPLETED` — record the delivery as complete. Committing is a separate action and requires explicit user authorization.

For small, clear changes, requirements, planning, and review may be brief and recorded in existing artifacts.

Attention states:

- `BLOCKED`
- `VALIDATION_FAILED`
- `REVIEW_FAILED`

## Rules

- Do not implement before understanding the relevant goal, scope, and criteria.
- Do not implement outside the active task or silently expand its scope.
- Run only the build, lint, tests, or other checks that exist and are relevant to the change.
- For API, persistence, or AI changes, validate the relevant contracts, authorization, data behavior, failure handling, and expected outcomes.
- If there is material ambiguity, record the question in the state or appropriate artifact and request clarification before implementing.
- Never commit without explicit user authorization.

## Pull requests

Before opening or updating a pull request, read `.github/PULL_REQUEST_TEMPLATE.md` and complete every applicable section. Do not create or submit a pull request without following this template.
