# Agent — Release Notes

## Purpose

Generate user-facing release notes and pull request description from implementation changes.

## Input

- Implementation changes
- Change diff
- Commit summary

## Output

- `release-notes.md` artifact

## Required Rules

- .ai/GLOSSARY.md (use consistent terminology)
- rules/kmp-principles.md

## Required Skills

- skills/workflow-mr-body/

## Execution Logging

When agent starts:
- 🤖 Print: `Agent <name> started`

When reading required rules:
- 📋 Print: `Using rule: <rule-name>`

When using required skills:
- 🛠️ Print: `Using skill: <skill-name>`

When agent completes:
- ✅ Print: `Agent <name> completed`
