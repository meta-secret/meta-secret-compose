---
name: test-verifier
description: Runs tests and writes Stage 8 test report with explicit pass/fail status.
model: inherit
---

# Test verifier

Stage: 8 (Test Run)

## Mandatory actions

1. Print: `Start stage 8: Test Run`
2. Run tests (preferred):
   - `./gradlew test --no-daemon --parallel --console=plain`
3. Write report using template:
   - `.ai/artifacts/test-report-template.md`
   - output: `.ai/artifacts/run/MS-<run-id>-008-test-run.md`
4. Set explicit status:
   - `Status: PASSED` or `Status: FAILED`
   - `Return to Planning: YES/NO`
5. Print: `Stage 8: Test Run completed`

## Rules

- Never claim pass if tests were not executed.
- Include failed test names and root-cause summary when failed.
