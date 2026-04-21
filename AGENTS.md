# AGENTS.md — meta-secret-compose

Unified automation entry for Claude Code, Cursor, and Codex CLI.

## Run Command

- `run issue 123`
- `run issue "my custom task"`
- `run issue 123 --from stage-4`

## Single Source of Truth

- Brain: `.ai/WORKFLOW.md`
- Detailed contracts: `.ai/PIPELINE.md`
- Agent definitions: `.ai/agents/`

IDE entrypoints must delegate to `.ai/WORKFLOW.md` and must not duplicate stage logic.

## 9-Stage Automatic Flow

1. Issue Intake + optional Figma analysis
2. Planning
3. Implementation split (Logic + UI)
4. Build (no tests, max 10 minutes)
5. Code Review
6. Design Review (only when Figma link exists; can run in parallel with stage 5)
7. Test Authoring
8. Test Run
9. Branch + Commit + PR

## Required Stage Logs

Each stage must print exact lines:

- `Start stage <n>: <name>`
- `Stage <n>: <name> completed`

## Artifacts

Directory:
- `.ai/artifacts/run/`

Naming:
- `MS-<run-id>-<stage-number>-<stage-name>.md`
- Retry suffix: `-retry-1`, `-retry-2`

Examples:
- `MS-123-001-understanding.md`
- `MS-123-004-build.md`
- `MS-123-005-review-retry-1.md`

## Required Output Templates

- Stage 1: `.ai/artifacts/issue-analysis-template.md`
- Stage 2: `.ai/artifacts/implementation-plan-template.md`
- Stage 4: `.ai/artifacts/build-report-template.md`
- Stage 5: `.ai/artifacts/review-report-template.md`
- Stage 6: `.ai/artifacts/design-review-report-template.md`
- Stage 8: `.ai/artifacts/test-report-template.md`

## Automatic Recovery Rules

If stage fails at Build, Code Review, Design Review, or Test Run:

1. Take failed stage artifact as mandatory input
2. Return to Stage 2 (Planning)
3. Re-run stages from Stage 3 onward
4. Maximum retries: 2
5. If still failing: stop and ask user

## Figma Rules

- If issue contains Figma link, run Figma analysis through MCP in Stage 1.
- Planning, UI implementation, and design review must use that context.
- If Figma MCP fails, continue with warning in artifact.

## Branch Rules

For issue-based runs:
- `{Prefix}/kuklin/MS-{issueNumber}`
- Prefix from issue title tags: `Task`, `Feature`, `Bug` (default `Task`)

For custom text runs:
- `Task/kuklin/MS-<run-id>`

## Status

- Last updated: 2026-04-22
- Workflow status: Active
