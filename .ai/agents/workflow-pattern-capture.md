# Agent — Workflow Pattern Capture

## Purpose

Identify and document repeated workflow patterns. Propose new skills, commands, or rules to improve future efficiency.

## Input

- Workflow context (repeated issues, patterns, or observations)
- Previous artifacts (optional)

## Output

- `pattern-analysis.md` artifact

## Required Rules

- .ai/GLOSSARY.md (use consistent terminology)
- rules/kmp-principles.md

## Required Skills

- skills/workflow-pattern-capture/

## Execution Logging

When agent starts:
- 🤖 Print: `Agent <name> started`

When reading required rules:
- 📋 Print: `Using rule: <rule-name>`

When using required skills:
- 🛠️ Print: `Using skill: <skill-name>`

When agent completes:
- ✅ Print: `Agent <name> completed`
