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
