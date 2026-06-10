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

## 10-Stage Pipeline (with TDD)

1. Stage 1: Issue Intake + Optional Figma Context
2. Stage 2: Grill Me (Clarification & Deep Dive)
3. Stage 3: Planning
4. Stage 4: TDD Implementation
   - 4a: Test Author (write failing tests)
   - 4b: Red-Green-Refactor (implement minimal code)
   - 4c: Major Refactor (clean up after 3-5 cycles)
5. Stage 5: Build (no tests, max 10 minutes)
6. Stage 6: Code Review (with 80% coverage check)
7. Stage 7: Design Review (only if Figma link exists; may run in parallel with Stage 6)
8. Stage 8: Test Coverage Verification
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

### Stage 3.5: Constraint Validation

Agent: `constraint-validator`
Template: `.ai/artifacts/constraint-validation-template.md`
Input:
- Stage 3 artifact (implementation plan)
- Stage 2 artifact (clarifications)
- `.ai/CONSTRAINTS.md` (full reference)
Output: `.ai/artifacts/run/MS-<run-id>-0035-constraints.md`

Required behavior:
- Validate plan against all 35 confirmed constraints (Section 28 of CONSTRAINTS.md)
- Check: Device Master Key handling, Vault model, Shamir distribution, biometry, approval model
- Identify affected constraints
- Check: PASS or FAIL for each constraint
- If FAIL: block Stage 4, return to Stage 3 with required changes
- If PASS: provide sign-off to proceed to implementation

**MANDATORY GATE:** Do not proceed to implementation if constraints violated.

### Stage 4: TDD Implementation

**TDD (Test-Driven Development)** is mandatory. Write test first, then implement minimal code, then refactor.

#### Stage 4a: Test Author

Agent: `tdd-test-author`
Template: `.ai/artifacts/test-template.md`
Input:
- Stage 3 artifact (implementation plan with breakdown)
- Stage 2 artifact (clarifications)
Output: `.ai/artifacts/run/MS-<run-id>-004a-tests.md`

Required behavior:
- Write failing test cases for each requirement in plan
- Tests must fail (feature doesn't exist)
- Use `kotlin("test")` for Kotlin, `XCTest` for Swift
- Test naming: `test<Function><Scenario>`

#### Stage 4b: Red-Green-Refactor Cycle

Agent: `tdd-implementer`
Input:
- Test files (failing)
- Stage 3 artifact
Output: `.ai/artifacts/run/MS-<run-id>-004b-implementation.md`

Required behavior:
- For each test:
  - **RED:** Confirm test fails
  - **GREEN:** Write minimal code to pass (and only that)
  - **REFACTOR:** Skip mini refactor, continue to next test
- Repeat for 3 consecutive tests
- All tests passing at end of batch

#### Stage 4c: Major Refactor (every 3-5 cycles)

Agent: `tdd-refactorer`
Input:
- Implementation code (from red-green cycles)
- All passing tests
- Stage 3 artifact
Output: `.ai/artifacts/run/MS-<run-id>-004c-refactored.md`

Required behavior:
- Clean up code from 3-5 red-green cycles
- Extract duplication, improve naming
- Add documentation and comments
- Run full test suite: all tests must pass
- Ensure code quality and maintainability

**Flow:** Test Author → (3x Red-Green-Refactor) → Major Refactor → Repeat until all features done

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

### Stage 8: Test Coverage Verification

Agent: `code-reviewer`
Command: `./gradlew koverReport`
Template: `.ai/artifacts/coverage-report-template.md`
Output: `.ai/artifacts/run/MS-<run-id>-008-coverage.md`

Required behavior:
- Verify test coverage is 80% minimum
- Business logic: 90%+ preferred
- Report uncovered lines
- Fail if coverage < 80%
- Pass condition: `Coverage: >=80%`

### Stage 9: Test Run (Final Validation)

Agent: `test-verifier`
Command: `./gradlew test --no-daemon --parallel --console=plain`
Template: `.ai/artifacts/test-report-template.md`
Output: `.ai/artifacts/run/MS-<run-id>-009-test-run.md`

Required behavior:
- Execute all tests
- Report pass/fail
- Capture failing test details
- Pass condition: `Status: PASSED` + `Coverage: >=80%`

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
