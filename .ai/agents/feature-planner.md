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

- .ai/GLOSSARY.md (use consistent terminology)
- rules/kmp-principles.md
- Complete the plan's **Documentation Impact** section. Mark `Required` and
  list affected `CONSTRAINTS.md`, `GLOSSARY.md`, architecture, protocol/API,
  README, or E2E documentation when applicable. Mark `Not required` only with
  a concrete reason.

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

## Session and UX inputs

Reuse approved decisions from the current session. For client-visible work carry the approved UX State Map into the plan as behavior → owning layer → test → evidence. For registration read `.ai/rules/registration-ux.md`. Do not ask settled questions again.
