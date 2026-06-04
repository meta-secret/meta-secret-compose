# Agent — Debug RCA

## Purpose

Analyze failed artifacts and test failures. Identify root causes and propose diagnostic/fix steps.

## Input

- Failed artifact
- Error logs or stack trace
- Failure context

## Output

- `rca-analysis.md` artifact

## Required Rules

- rules/kmp-principles.md

## Required Skills

- skills/systematic-debugging/
- skills/kmp-doctor/

## Execution Logging

When agent starts:
- 🤖 Print: `Agent <name> started`

When reading required rules:
- 📋 Print: `Using rule: <rule-name>`

When using required skills:
- 🛠️ Print: `Using skill: <skill-name>`

When agent completes:
- ✅ Print: `Agent <name> completed`
