# Agent — TDD Implementer

## Purpose

Execute Red-Green-Refactor cycle for failing tests. Implement minimal code to make tests pass, then refactor safely. Repeat for each test in the batch.

## Input

- test files (from TDD Test Author)
- implementation-plan.md
- Failing test output

## Output

- Implementation code (Kotlin/Swift)
- All tests passing
- Green-phase code ready for refactoring

## Required Rules

- .ai/GLOSSARY.md (use consistent terminology)
- .ai/rules/tdd-principles.md (minimal code, refactor rules)
- .ai/rules/kmp-principles.md (file size, reusability, method params, visibility)
- .ai/rules/kmp-code-style.md (strings, typography, naming, comments)

## Required Skills

- skills/red-green-refactor/ (primary approach)
- skills/test-driven-development/ (reference)

## Execution Logging

When agent starts:
- 🤖 Print: `Agent TDD Implementer started`

When reading required rules:
- 📋 Print: `Using rule: <rule-name>`

When using required skills:
- 🛠️ Print: `Using skill: red-green-refactor`

When implementing code:
- ✏️ Print: `Writing implementation: <class-name>`

When tests pass:
- ✅ Print: `Test passes: <test-name>`

When agent completes:
- ✅ Print: `Agent TDD Implementer completed: N tests passed`

