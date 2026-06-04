# Agent — Feature Planner

## Purpose

Create detailed implementation plan from issue analysis. Align with architecture and design constraints. Plan file-level changes and verification steps.

## Input

- `issue-analysis.md` artifact
- `design-analysis.md` artifact (if Figma present)
- Failed artifact (if retry)

## Output

- `implementation-plan.md` artifact with:
  - File-level execution steps
  - Architecture decisions
  - Design constraints and acceptance checks
  - Risks and edge cases
  - Verification criteria
  - (If retry) Fix plan derived from failures

## Required Rules

- rules/kmp-principles.md

## Required Skills

- skills/write-implementation-plan/
- skills/architecture-guardian/

## Execution Logging

When agent starts:
- 🤖 Print: `Agent <name> started`

When reading required rules:
- 📋 Print: `Using rule: <rule-name>`

When using required skills:
- 🛠️ Print: `Using skill: <skill-name>`

When agent completes:
- ✅ Print: `Agent <name> completed`
