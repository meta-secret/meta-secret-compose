# Command — Run

## Trigger

```
run <payload>
run <payload> --from stage-<n>
```

## Purpose

Execute complete 9-stage automated workflow for meta-secret-compose.

## Flow

1. **github-issue-coordinator** — Analyze issue/task (with optional Figma)
2. **feature-planner** — Create implementation plan
3. **logic-implementer** + **ui-implementer** — Implement code (parallel if possible)
4. (Build & Test) — Compile and run basic tests
5. **code-reviewer** — Review implementation
6. **design-reviewer** — Review design (if Figma link exists)
7. **test-author** — Write test cases
8. **test-verifier** — Execute and verify tests
9. **release-manager** — Create branch, commit, pull request

See `.ai/WORKFLOW.md` for complete 9-stage specification.

## Expected Input

- GitHub issue number (e.g., `#42`)
- GitHub issue URL
- Free-text task description

## Output

- Pull Request with implementation, tests, and documentation
