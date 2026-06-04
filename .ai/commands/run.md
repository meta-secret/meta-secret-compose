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
4. **logic-implementer** + **ui-implementer** — Implement code (parallel if possible)
5. (Build & Test) — Compile and run basic tests
6. **code-reviewer** — Review implementation
7. **design-reviewer** — Review design (if Figma link exists)
8. **test-author** — Write test cases
9. **test-verifier** — Execute and verify tests
10. **release-manager** — Create branch, commit, pull request

See `.ai/WORKFLOW.md` for complete 10-stage specification.

## Expected Input

- GitHub issue number (e.g., `#42`)
- GitHub issue URL
- Free-text task description

## Output

- Pull Request with implementation, tests, and documentation
