# Automated Workflow Orchestration

Single source of truth for `run issue <id>` / `run issue "<text>"` across Claude Code, Cursor, and Codex CLI.

## Command Contract

- Trigger: `run issue <id-or-text>`
- Optional resume: `run issue <id-or-text> --from stage-<n>`
- Artifacts directory: `.ai/artifacts/run/`
- Artifact naming: `MS-<run-id>-<stage-number>-<stage-name>[ -retry-N ].md`
- Retry budget: `2` full fix loops

`<run-id>` rules:
- Numeric issue input: use issue number (`123`)
- Free-text input: use UTC timestamp (`YYYYMMDDHHmmss`)

## Required Stage Logs

Every stage must print these exact lines:

- Start: `Start stage <n>: <name>`
- End: `Stage <n>: <name> completed`

Example:
- `Start stage 4: Build`
- `Stage 4: Build completed`

## 9-Stage Pipeline

1. Stage 1: Issue Intake + Optional Figma Context
2. Stage 2: Planning
3. Stage 3: Implementation (Logic + UI)
4. Stage 4: Build (no tests, max 10 minutes)
5. Stage 5: Code Review
6. Stage 6: Design Review (only if Figma link exists; may run in parallel with Stage 5)
7. Stage 7: Test Authoring
8. Stage 8: Test Run
9. Stage 9: Branch + Commit + PR

## Stage Specs

### Stage 1: Issue Intake + Optional Figma Context

Primary agent: `github-issue-coordinator`
Optional helper: `figma-design-analyst` (only if issue contains Figma URL)
Template: `.ai/artifacts/issue-analysis-template.md`
Output: `.ai/artifacts/run/MS-<run-id>-001-understanding.md`

Required behavior:
- Read issue (or free-text task)
- Detect Figma links
- If Figma links exist, query Figma via MCP and extract actionable UI structure/constraints
- Produce output following issue-analysis template
- Include: `Figma Present: YES/NO`

### Stage 2: Planning

Agent: `feature-planner`
Template: `.ai/artifacts/implementation-plan-template.md`
Input:
- Stage 1 artifact
- If retry: failed artifact from Stage 4/5/6/8
Output: `.ai/artifacts/run/MS-<run-id>-002-planning.md`

Required behavior:
- Create implementation plan aligned with architecture/security/style
- If Figma present, include design constraints and acceptance checks
- If retry, add explicit fix plan derived from failure artifact

### Stage 3: Implementation (Logic + UI)

Agents:
- `logic-implementer`
- `ui-implementer`

Input:
- Stage 2 artifact
- Stage 1 artifact (for Figma-derived constraints)
Output:
- `.ai/artifacts/run/MS-<run-id>-003-implementation-logic.md`
- `.ai/artifacts/run/MS-<run-id>-003-implementation-ui.md`
- `.ai/artifacts/run/MS-<run-id>-003-implementation.md` (merged summary)

Execution model:
- Prefer parallel execution when file ownership is disjoint
- If overlap exists, run logic first then UI

### Stage 4: Build (no tests)

Command:
- `./gradlew build -x test --no-daemon --parallel --console=plain`

Timeout:
- hard limit 10 minutes (600 seconds)

Template: `.ai/artifacts/build-report-template.md`
Output: `.ai/artifacts/run/MS-<run-id>-004-build.md`

Required behavior:
- Capture command, duration, and status
- Mark `Status: PASSED` or `Status: FAILED`

### Stage 5: Code Review

Agent: `code-reviewer`
Template: `.ai/artifacts/review-report-template.md`
Input: code diff + architecture/style/security rules
Output: `.ai/artifacts/run/MS-<run-id>-005-review.md`

Required behavior:
- Output `Status: PASSED` or `Status: FAILED`
- When failed: include concrete blocking issues

### Stage 6: Design Review (conditional)

Condition:
- Run only if Stage 1 indicates `Figma Present: YES`

Agent: `design-reviewer`
Template: `.ai/artifacts/design-review-report-template.md`
Input: Stage 1 Figma analysis + current UI implementation
Output: `.ai/artifacts/run/MS-<run-id>-006-design-review.md`

Execution model:
- May run in parallel with Stage 5

Required behavior:
- Output `Status: PASSED` or `Status: FAILED`
- On failure, include exact mismatches and fixes

### Stage 7: Test Authoring

Agent: `test-author`
Input:
- Stage 3 implementation artifacts
- Stage 5/6 findings (if any)
Output: `.ai/artifacts/run/MS-<run-id>-007-testing.md`

Required behavior:
- Add/update automated tests for changed behavior
- Cover edge cases from plan and review feedback

### Stage 8: Test Run

Agent: `test-verifier`
Template: `.ai/artifacts/test-report-template.md`
Command suggestion:
- `./gradlew test --no-daemon --parallel --console=plain`
Output: `.ai/artifacts/run/MS-<run-id>-008-test-run.md`

Required behavior:
- Output `Status: PASSED` or `Status: FAILED`
- Include failed test list and root-cause summary

### Stage 9: Branch + Commit + PR

Agent: `release-manager`
Output: `.ai/artifacts/run/MS-<run-id>-009-pr.md`

Required behavior:
- Create branch: `{Prefix}/kuklin/MS-{issueNumber}` for numeric issues
- Commit and push
- Open PR to `main`

## Automatic Recovery Loops

If any of these stages fails:
- Stage 4 (Build)
- Stage 5 (Code Review)
- Stage 6 (Design Review)
- Stage 8 (Test Run)

Then run recovery loop:

1. Feed failed artifact into Stage 2 planning as mandatory context
2. Re-run Stage 3 -> Stage 4 -> Stage 5 (+Stage 6 if applicable) -> Stage 7 -> Stage 8
3. Stop when all pass, then continue to Stage 9
4. Max retries: 2

On retry artifacts, append `-retry-1` / `-retry-2`.

## Failure Markers

Pipeline must stop if artifact contains any marker:

- `Status: FAILED`
- `Return to Planning: YES`
- `**FAIL**`
- `FAIL`
- `❌`

## IDE Entry Points

- Claude Code: `.claude/ORCHESTRATE.md`
- Cursor: `.cursor/WORKFLOW.md`
- Codex CLI: `.codex/ORCHESTRATE.md`

All entry points must delegate orchestration logic to this file to avoid duplication.

Last updated: 2026-04-22
