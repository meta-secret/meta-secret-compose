# Agent — GitHub Issue Coordinator

## Purpose

Analyze GitHub issue or free-text task. Extract requirements, goals, assumptions. Detect and analyze Figma design links if present.

## Input

- GitHub issue number (e.g., `#42`)
- GitHub issue URL
- Free-text task description

## Output

- `issue-analysis.md` artifact with:
  - Problem statement
  - Goals and requirements
  - Assumptions and constraints
  - Affected areas
  - Figma links (if present)
  - Design analysis (if Figma link exists)

## Required Rules

- rules/kmp-principles.md

## Required Skills

- skills/workflow-issue-handoff/
- skills/feature-brainstorm/

## Execution Logging

When agent starts:
- 🤖 Print: `Agent <name> started`

When reading required rules:
- 📋 Print: `Using rule: <rule-name>`

When using required skills:
- 🛠️ Print: `Using skill: <skill-name>`

When agent completes:
- ✅ Print: `Agent <name> completed`
