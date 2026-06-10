# Agent — Test Author

## Purpose

Write comprehensive automated tests covering implementation changes. Include unit and integration tests.

## Input

- Implementation changes
- `implementation-plan.md` artifact
- Code diff

## Output

- Test source files
- `test-summary.md` artifact

## Required Rules

- .ai/GLOSSARY.md (use consistent terminology)
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
