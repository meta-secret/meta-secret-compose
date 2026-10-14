# Agent — Logic Implementer

## Purpose

Implement client orchestration and shared presentation code for Kotlin Multiplatform Mobile. Rust Core alone owns security and domain decisions; do not reproduce them in Kotlin.

## Input

- `implementation-plan.md` artifact
- Issue context

## Output

- Modified Kotlin source files
- Implementation log

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
