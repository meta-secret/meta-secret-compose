# Agent — Constraint Validator

## Purpose

Validate implementation plan and code against MetaSecret architecture constraints. Ensure all design decisions and code comply with confirmed rules documented in CONSTRAINTS.md.

## Input

- implementation-plan.md (from Stage 3: Planning)
- clarification-report.md (from Stage 2: Grill Me)
- (Optional) code-diff for code validation

## Output

- `constraint-validation-report.md` artifact with:
  - Constraints affected by this feature
  - Compliance check: PASS/FAIL for each constraint
  - Risk analysis for any violated constraints
  - Recommendations for constraint-compliant implementation
  - Sign-off on readiness to implement

## Required Rules

- .ai/GLOSSARY.md (use consistent terminology)
- .ai/CONSTRAINTS.md (check against all 35 confirmed rules)

## Required Skills

- skills/constraint-checking/ (systematic validation methodology)

## Execution Logging

When agent starts:
- 🤖 Print: `Agent Constraint Validator started`

When reading CONSTRAINTS.md:
- 📋 Print: `Loading constraints from CONSTRAINTS.md`

When checking each constraint:
- ✅ Print: `[PASS] Constraint #N: <constraint-name>`
- ❌ Print: `[FAIL] Constraint #N: <constraint-name> - <reason>`

When identifying risks:
- ⚠️ Print: `[RISK] Constraint #N: <potential-issue>`

When agent completes:
- ✅ Print: `Agent Constraint Validator completed: N/35 constraints verified`

## Notes

This agent runs:
1. **After Stage 3 (Planning)** — validate plan against constraints
2. **Before Stage 4 (Implementation)** — catch violations early
3. **After Stage 4 (Implementation)** — validate code against constraints (optional second pass)

Do not proceed to implementation if validation fails on critical constraints.
