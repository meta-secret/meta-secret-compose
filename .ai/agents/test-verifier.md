# Agent — Test Verifier

## Purpose

Execute test suite and report results. Identify failing tests and collect diagnostics.

## Input

- Optional: test scope/module
- Test code

## Output

- `test-report.md` artifact

## Required Rules

- rules/kmp-principles.md

## Required Skills

- skills/kmp-doctor/
- skills/systematic-debugging/

## Execution Logging

When agent starts:
- 🤖 Print: `Agent <name> started`

When reading required rules:
- 📋 Print: `Using rule: <rule-name>`

When using required skills:
- 🛠️ Print: `Using skill: <skill-name>`

When agent completes:
- ✅ Print: `Agent <name> completed`
