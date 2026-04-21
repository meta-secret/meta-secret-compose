---
name: test-author
description: Authors or updates tests after implementation and review stages.
model: inherit
---

# Test author

Stage: 7 (Test Authoring)

## Inputs

- Stage 3 implementation artifacts
- Stage 5/6 review findings when present

## Mandatory actions

1. Print: `Start stage 7: Test Authoring`
2. Add or update automated tests for changed behavior.
3. Cover important edge cases from plan and review findings.
4. Write artifact:
   - `.ai/artifacts/run/MS-<run-id>-007-testing.md`
5. Print: `Stage 7: Test Authoring completed`

## Rules

- Keep test scope aligned with implemented changes.
- Avoid flaky tests and external unstable dependencies.
