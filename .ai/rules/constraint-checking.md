# Rule: Constraint-Driven Development

## Overview

All features must comply with MetaSecret architecture constraints documented in CONSTRAINTS.md.

Constraint checking is NOT optional—it is a MANDATORY gate in the workflow.

## Workflow Integration

### Stage 3.5: Constraint Validation (MANDATORY GATE)

After planning, BEFORE implementation:

1. **Invoke Constraint Validator Agent**
   - Input: implementation-plan.md
   - Reference: CONSTRAINTS.md (all 35 rules)
   - Output: constraint-validation-report.md

2. **Validation Rules**
   - Check each constraint affected by this feature
   - For each constraint: PASS or FAIL
   - If ANY constraint FAILS: **BLOCK Stage 4**
   - Return to Stage 3 (Planning) for redesign

3. **Sign-off**
   - If all constraints PASS: sign-off to proceed to implementation
   - Agent provides PASS/FAIL status for each constraint

### Stage 6: Code Review (MANDATORY RE-CHECK)

After implementation, during code review:

1. **Re-validate Constraints**
   - Ensure implementation matches validated plan
   - Check for deviations from constraint requirements
   - Verify no undocumented architecture changes

2. **Code Review Agent**
   - Uses constraint-validation-report.md from Stage 3.5
   - Confirms implementation stays within bounds
   - Reports any violations

## Constraint Categories (CONSTRAINTS.md Section 28)

When validating a feature, consider which constraints are affected:

- **Device Storage** (Constraints 12-20): Device Master Key, Database, Backup
- **Vault Model** (Constraints 1-11): Single device, 1-of-2, 2-of-3, k=n-1 rule
- **Shamir Sharing** (Constraints 4-11): Individual share distribution, resharing
- **Approval Model** (Constraints 22-29): Who approves what
- **Biometry** (Constraints 30-34): When biometry is used, fallback to PIN
- **FFI/Core** (Constraints 13-17, 26, 35): How mobile talks to Core

## Common Pitfalls

❌ **Treating constraints as suggestions**
- They are hard requirements
- Violations must be caught in Stage 3.5, not Stage 6

❌ **Deferring constraint questions to "later"**
- Answer them during Stage 2 (Grill Me)
- Use Stage 3.5 to validate answers

❌ **Implementing beyond constraint bounds**
- Only implement what passes constraint validation
- If constraints conflict with requirements: go back to Stage 2

❌ **Assuming Server can store data**
- Server is ONLY signaling layer
- No secrets, shares, or keys on server
- Everything stays on devices

## Reference

**CONSTRAINTS.md:**
- Section 28: 35 Confirmed Rules (numbered 1-35)
- Section 29: Open Questions (not constraints, only questions)

**Key Constraint Numbers:**
- #1-3: Vault and device basics
- #4-11: Shamir sharing rules
- #12-20: Device Master Key and storage
- #21-29: Vault operations (join, remove, compromised)
- #30-35: Biometry and communication

---

Last updated: 2026-06-10
