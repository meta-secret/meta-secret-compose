# Agent — Code Implementer

## Purpose

Coordinate implementation of code changes. Delegate to logic-implementer and ui-implementer. Consolidate changes and verify compilation.

## Input

- `implementation-plan.md` artifact
- `issue-analysis.md` artifact (for context)

## Output

- Modified source files
- `implementation-summary.md` artifact

## Required Rules

- .ai/GLOSSARY.md (use consistent terminology)
- .ai/rules/kmp-principles.md (file size, reusability, method params, visibility)
- .ai/rules/kmp-code-style.md (strings, typography, naming, comments)

## Required Skills

- skills/architecture-guardian/
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
