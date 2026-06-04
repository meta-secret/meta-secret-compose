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

## 10-Stage Pipeline

1. Stage 1: Issue Intake + Optional Figma Context
2. Stage 2: Grill Me (Clarification & Deep Dive)
3. Stage 3: Planning
4. Stage 4: Implementation (Logic + UI)
5. Stage 5: Build (no tests, max 10 minutes)
6. Stage 6: Code Review
7. Stage 7: Design Review (only if Figma link exists; may run in parallel with Stage 6)
8. Stage 8: Test Authoring
9. Stage 9: Test Run
10. Stage 10: Branch + Commit + PR

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

### Stage 2: Grill Me (Clarification & Deep Dive)

Primary agent: `requirements-clarifier`
Template: `.ai/artifacts/clarification-template.md`
Input:
- Stage 1 artifact
Output: `.ai/artifacts/run/MS-<run-id>-002-clarification.md`

Duration (adaptive - stop when shared understanding reached):
- Simple tasks (button color, text fix): 5-10 minutes
- Medium tasks (feature addition): 15-25 minutes
- Complex tasks (architecture, encryption): 30-45 minutes

Required behavior:
- Use "Grill Me" methodology: walk decision tree, resolve dependencies
- Ask clarifying questions about unclear/risky areas ONLY (not all possible questions)
- Provide recommendations for each question
- Explore codebase if questions can be answered by code
- Identify and map decision dependencies
- Get explicit user approval before proceeding
- Document all clarifications in artifact

### Stage 3: Planning

Agent: `feature-planner`
Template: `.ai/artifacts/implementation-plan-template.md`
Input:
- Stage 1 artifact
- Stage 2 artifact (clarifications)
- If retry: failed artifact from Stage 5/6/7/9
Output: `.ai/artifacts/run/MS-<run-id>-003-planning.md`

Required behavior:
- Create implementation plan aligned with architecture/security/style
- Incorporate all clarifications from Stage 2
- If Figma present, include design constraints and acceptance checks
- If retry, add explicit fix plan derived from failure artifact

### Stage 4: Implementation (Logic + UI)

Agents:
- `logic-implementer`
- `ui-implementer`

Input:
- Stage 3 artifact
- Stage 1 artifact (for Figma-derived constraints)
- Stage 2 artifact (for clarifications)
Output:
- `.ai/artifacts/run/MS-<run-id>-004-implementation-logic.md`
- `.ai/artifacts/run/MS-<run-id>-004-implementation-ui.md`
- `.ai/artifacts/run/MS-<run-id>-004-implementation.md` (merged summary)

Execution model:
- Prefer parallel execution when file ownership is disjoint
- If overlap exists, run logic first then UI

### Stage 5: Build (no tests)

Command:
- `./gradlew build -x test --no-daemon --parallel --console=plain`

Timeout:
- hard limit 10 minutes (600 seconds)

Template: `.ai/artifacts/build-report-template.md`
Output: `.ai/artifacts/run/MS-<run-id>-005-build.md`

Required behavior:
- Capture command, duration, and status
- If build fails, produce root-cause analysis
- Retry once on failure, then escalate to debug-rca

### Stage 6: Code Review

Agent: `code-reviewer`
Template: `.ai/artifacts/review-report-template.md`
Output: `.ai/artifacts/run/MS-<run-id>-006-review.md`

Required behavior:
- Review against architecture and style rules
- Check for security issues
- Verify Stage 2 clarifications are addressed
- Pass condition: `Status: PASSED`

### Stage 7: Design Review (conditional)

Condition: run only when Stage 1 has Figma link
Agent: `design-reviewer`
Template: `.ai/artifacts/design-review-report-template.md`
Output: `.ai/artifacts/run/MS-<run-id>-007-design-review.md`

Required behavior:
- Review UI against Figma specifications
- Verify design constraints from Stage 2 are met
- Pass condition: `Status: PASSED`

### Stage 8: Test Authoring

Agent: `test-author`
Template: `.ai/artifacts/test-authoring-template.md`
Output: `.ai/artifacts/run/MS-<run-id>-008-testing.md`

Required behavior:
- Write unit and integration tests
- Cover boundary cases from Stage 2
- Cover error handling scenarios from Stage 2

### Stage 9: Test Run

Agent: `test-verifier`
Command: `./gradlew test --no-daemon --parallel --console=plain`
Template: `.ai/artifacts/test-report-template.md`
Output: `.ai/artifacts/run/MS-<run-id>-009-test-run.md`

Required behavior:
- Execute all tests
- Report pass/fail
- Capture failing test details
- Pass condition: `Status: PASSED`

### Stage 10: Branch + Commit + PR

Agent: `release-manager`
Output: `.ai/artifacts/run/MS-<run-id>-010-pr.md`

Required behavior:
- Create feature branch
- Stage and commit changes
- Create pull request with description

## Retry Rules

Retry trigger:
- Build failed (Stage 5)
- Code review failed (Stage 6)
- Design review failed (Stage 7)
- Test run failed (Stage 9)

Retry path:
- Return to Stage 3 (Planning) with failed artifact as input
- Re-run stages from Stage 4 onward
- Max retries: 2

## Failure Markers

- `Status: FAILED`
- `Return to Planning: YES`
- `**FAIL**`
- `FAIL`
