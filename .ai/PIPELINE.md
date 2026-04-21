# Pipeline Specification

Detailed stage contract for `.ai/WORKFLOW.md`.

## Stage 1: Issue Intake + Optional Figma Context

- Agent: `github-issue-coordinator`
- Optional helper: `figma-design-analyst`
- Input: issue id or free-text task
- Output: `MS-<run-id>-001-understanding.md`
- Template: `issue-analysis-template.md`
- Must include:
  - Problem, Goal, Requirements, Assumptions, Affected Areas
  - `Figma Present: YES/NO`
  - `Figma Links:` list (if any)

## Stage 2: Planning

- Agent: `feature-planner`
- Input:
  - Stage 1 artifact
  - Failed artifact on retries
- Output: `MS-<run-id>-002-planning.md`
- Template: `implementation-plan-template.md`
- Must include:
  - File-level execution plan
  - Risks and edge cases
  - Retry fix section when retrying

## Stage 3: Implementation (split)

- Agent A: `logic-implementer`
  - Output: `MS-<run-id>-003-implementation-logic.md`
- Agent B: `ui-implementer`
  - Output: `MS-<run-id>-003-implementation-ui.md`
- Consolidated output: `MS-<run-id>-003-implementation.md`

Execution preference:
- Parallel if file scopes do not overlap
- Sequential otherwise (logic -> UI)

## Stage 4: Build (no tests)

- Command: `./gradlew build -x test --no-daemon --parallel --console=plain`
- Timeout: 600 seconds
- Output: `MS-<run-id>-004-build.md`
- Template: `build-report-template.md`
- Pass condition: `Status: PASSED`

## Stage 5: Code Review

- Agent: `code-reviewer`
- Output: `MS-<run-id>-005-review.md`
- Template: `review-report-template.md`
- Pass condition: `Status: PASSED`

## Stage 6: Design Review (conditional)

- Condition: run only when Stage 1 has Figma link
- Agent: `design-reviewer`
- Output: `MS-<run-id>-006-design-review.md`
- Template: `design-review-report-template.md`
- Pass condition: `Status: PASSED`

## Stage 7: Test Authoring

- Agent: `test-author`
- Output: `MS-<run-id>-007-testing.md`
- Inputs: implementation artifacts + review findings

## Stage 8: Test Run

- Agent: `test-verifier`
- Command: `./gradlew test --no-daemon --parallel --console=plain`
- Output: `MS-<run-id>-008-test-run.md`
- Template: `test-report-template.md`
- Pass condition: `Status: PASSED`

## Stage 9: Branch + Commit + PR

- Agent: `release-manager`
- Output: `MS-<run-id>-009-pr.md`

## Retry Rules

Retry trigger:
- Build failed (Stage 4)
- Code review failed (Stage 5)
- Design review failed (Stage 6)
- Test run failed (Stage 8)

Retry path:
- Return to Stage 2 with failed artifact as input
- Re-run pipeline from Stage 3 onward
- Max retries: 2

## Stage Log Contract

Each stage must print:
- `Start stage <n>: <name>`
- `Stage <n>: <name> completed`

## Failure Markers

- `Status: FAILED`
- `Return to Planning: YES`
- `**FAIL**`
- `FAIL`

Last updated: 2026-04-22
