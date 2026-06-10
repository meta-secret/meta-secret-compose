---
description: Stage 3.5 - Validate plan and code against MetaSecret architecture constraints
---

# Command: only-constraint-validator

## Trigger

User wants to validate implementation plan against MetaSecret constraints before coding.

## Purpose

Execute Constraint Validator agent to ensure plan compliance with CONSTRAINTS.md.

## Flow

1. Read `.ai/INDEX.md`
2. Read `.ai/ORCHESTRATOR.md`
3. Load `.ai/CONSTRAINTS.md` (full reference)
4. Load agent: `.ai/agents/constraint-validator.md`
5. Invoke constraint-validator agent with:
   - implementation-plan.md (from Stage 3)
   - clarification-report.md (from Stage 2)
6. Agent validates against all 35 confirmed constraints
7. Output constraint-validation-report.md
8. If FAIL: block implementation, return to planning
9. If PASS: proceed to Stage 4

## Expected Input

- implementation-plan.md (from Stage 3: Planning)
- clarification-report.md (from Stage 2: Grill Me)

## Expected Output

- constraint-validation-report.md with:
  - Compliance status for each affected constraint
  - Risk analysis
  - Implementation recommendations
  - Sign-off or FAIL with required changes

## Notes

This stage is MANDATORY before implementing any feature.

If constraints are violated:
- Do NOT proceed to implementation
- Return to Stage 3 (Planning) for redesign
- Consult CONSTRAINTS.md section 28 (Confirmed Rules)
