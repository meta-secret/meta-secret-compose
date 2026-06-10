# Command — Run

## Trigger

```
run <payload>
run <payload> --from stage-<n>
```

## Purpose

Execute complete 10-stage automated workflow for meta-secret-compose.

## Flow

1. **github-issue-coordinator** — Analyze issue/task (with optional Figma)
2. **requirements-clarifier** — Deep dive clarification (Grill Me)
3. **feature-planner** — Create implementation plan
3.5. **constraint-validator** — Validate plan against CONSTRAINTS.md (MANDATORY GATE)
4. **TDD Implementation** (Test-Driven Development):
   - 4a. **tdd-test-author** — Write failing tests
   - 4b. **tdd-implementer** — Red-Green-Refactor cycles (minimal code → pass tests)
   - 4c. **tdd-refactorer** — Major refactoring after 3-5 cycles
5. **Build** — Compile code (no tests)
6. **code-reviewer** — Review implementation + 80% coverage check + constraints re-check
7. **design-reviewer** — Review design (if Figma link exists)
8. **Coverage Verification** — Verify 80%+ test coverage
9. **test-verifier** — Execute full test suite
10. **release-manager** — Create branch, commit, pull request

See `.ai/WORKFLOW.md` for complete 10-stage specification.

## Expected Input

- GitHub issue number (e.g., `#42`)
- GitHub issue URL
- Free-text task description

## Output

- Pull Request with implementation, tests, and documentation

---

## Artifacts Generated

Each stage creates an artifact in `.ai/artifacts/run/`:

- **Stage 1:** `MS-<run-id>-001-understanding.md` — Issue analysis
- **Stage 2:** `MS-<run-id>-002-clarification.md` — Clarifications & decisions
- **Stage 3:** `MS-<run-id>-003-planning.md` — Implementation plan
- **Stage 3.5:** `MS-<run-id>-0035-constraints.md` — Constraint validation
- **Stage 4a:** `MS-<run-id>-004a-tests.md` — Failing test cases
- **Stage 4b:** `MS-<run-id>-004b-implementation.md` — Implementation (red-green cycles)
- **Stage 4c:** `MS-<run-id>-004c-refactored.md` — Refactored code
- **Stage 5:** `MS-<run-id>-005-build.md` — Build report
- **Stage 6:** `MS-<run-id>-006-review.md` — Code review findings
- **Stage 7:** `MS-<run-id>-007-design-review.md` — Design review (if Figma)
- **Stage 8:** `MS-<run-id>-008-coverage.md` — Coverage verification
- **Stage 9:** `MS-<run-id>-009-test-run.md` — Test execution results
- **Stage 10:** `MS-<run-id>-010-pr.md` — PR details

Each artifact includes **Status: Success / Failed / Skipped**.

See `.ai/rules/artifact-writing-guide.md` for artifact specification.
