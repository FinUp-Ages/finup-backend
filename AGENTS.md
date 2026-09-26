This repository uses a lightweight, educational Railforge workflow based on documentation and review. It has no Railforge engine, CLI, hooks, generated runtime, or versioned execution environment.

This backend variant is for a Java and Spring Boot application with AI capabilities. Preserve the established package, API, persistence, and configuration patterns. Do not expose secrets, credentials, or provider keys in versioned artifacts.

Before opening or updating a pull request, read `.github/PULL_REQUEST_TEMPLATE.md` and complete every applicable section. Do not create or submit a pull request without following this template.

Before making a development change:

1. Read `.railforge/PRINCIPLES.md` and `.railforge/WORKFLOW.md` when they are relevant to the task.
2. Read `.railforge/state.json` and confirm the current stage before acting.
3. Define or refine the active requirement and a proportional plan before coding.
4. Identify the files in scope and inspect the final diff before finishing. For API, persistence, or AI changes, identify the relevant consumers, data impact, and failure behavior.
5. Run the Java build, lint, tests, or other validation checks that are available and relevant to the change. Validate affected API contracts, authorization, persistence behavior, or AI behavior when relevant.
6. Update `.railforge/state.json` manually when the task changes stage or is completed, then re-read it before finishing.
7. Do not implement outside the active task or expand scope silently.
8. If requirements are ambiguous, record the uncertainty and request clarification before proceeding with implementation.

`.railforge/state.json` is the single versioned workflow state. Keep it small and manual: record the stage, task, scope, validation results, review, and date. Do not add event logs, generated snapshots, temporary execution data, or runtime metadata.

Do not create Railforge files, automation, or tooling merely to satisfy this workflow. Use only the documented files and practices that exist in this repository.

## Git conventions

When creating branches or commits after explicit user authorization, use the conventions below.

### Branches

Use a short kebab-case description with one of these prefixes:

- `feat/<feature-name>`
- `fix/<problem-name>`
- `docs/<document-name>`
- `refactor/<change-name>`
- `test/<test-name>`

`main` is the protected integration branch and must not be used for feature work.

### Commits

Use the format `<type>: <short description>`.

Allowed types: `feat`, `fix`, `docs`, `refactor`, `test`, and `chore`.
