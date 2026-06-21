# Execution Checklist: Implement Issue Workflow

**MANDATORY** reference for executing `implement issue <payload>` command.

---

## ⚠️ CRITICAL STAGES — Do NOT skip these under any circumstances:

### Stage 6: Code Review (MANDATORY)
- **Agent:** `code-reviewer`
- **Action:** Review implementation against constraints and architecture
- **Output:** `.ai/artifacts/run/MS-<run-id>-006-review.md`
- **Status:** Success / Failed / Skipped
- **Coverage Check:** Verify 80% minimum test coverage (if coverage < 80%, FAIL this stage)
- **Constraints Check:** Re-validate against `.ai/CONSTRAINTS.md`
- **Non-negotiable:** This stage MUST be executed. If skipped, workflow is incomplete.

**If Stage 6 fails:**
- Return to Stage 3 (Planning) with failed artifact as input
- Re-run stages 4a, 4b, 4c (TDD cycle)
- Re-run Stage 5 (Build)
- Re-run Stage 6 (Code Review)

---

### Stage 7: Design Review (CONDITIONAL)
- **Agent:** `design-reviewer`
- **Condition:** Only run if Stage 1 output contains Figma link
- **Action:** Review UI against Figma specifications
- **Output:** `.ai/artifacts/run/MS-<run-id>-007-design-review.md`
- **Status:** Success / Failed / Skipped
- **If Figma missing:** Mark status as "Skipped" (explicitly document why)
- **If Figma present:** Must execute and must pass

**If Stage 7 fails:**
- Return to Stage 3 (Planning) with failed artifact as input
- Re-run stages 4a, 4b, 4c (TDD cycle)
- Re-run Stage 5 (Build)
- Re-run Stage 6 (Code Review)
- Re-run Stage 7 (Design Review)

---

### Stage 8: Coverage Verification (MANDATORY)
- **Agent:** `code-reviewer` (or dedicated coverage agent)
- **Action:** Execute `./gradlew koverReport` and verify >= 80% coverage
- **Output:** `.ai/artifacts/run/MS-<run-id>-008-coverage.md`
- **Status:** Pass / Fail / Skipped
- **Required metrics:**
  - Overall coverage >= 80%
  - Business logic >= 90% (preferred)
  - Report uncovered lines
- **Non-negotiable:** This stage MUST be executed. Coverage < 80% = FAIL
- **Even without Figma:** This stage still runs (Figma only affects Stage 7)

**If Stage 8 fails:**
- Return to Stage 3 (Planning) with failed artifact as input
- Write additional tests to increase coverage
- Re-run stages 4a, 4b, 4c (TDD cycle) with focus on uncovered lines
- Re-run Stage 5 (Build)
- Re-run Stage 6 (Code Review)
- Re-run Stage 8 (Coverage Verification)

---

### Stage 10: PR Creation (USER APPROVAL REQUIRED)
- **Agent:** `release-manager`
- **Action:** STOP and ASK USER for approval before committing
- **Question:** "Should we proceed to Stage 10 (Branch + Commit + PR)?"
- **Wait for:** Explicit YES/NO response from user
- **If YES:** Continue with commit and PR creation
- **If NO:** Stop workflow and wait for further instructions
- **Output:** `.ai/artifacts/run/MS-<run-id>-010-pr.md`
- **Status:** Success / Failed
- **Non-negotiable:** Do NOT auto-commit or auto-create PR without user approval

---

## Execution Flow — Summary

1. Stage 1 → Issue Intake
2. Stage 2 → Clarification (Grill Me)
3. Stage 3 → Planning
4. Stage 3.5 → Constraint Validation (GATE)
5. **BRANCH:** TDD Implementation (4a → 4b → 4c)
6. Stage 5 → Build (no tests)
7. **→ VALIDATION BRANCH:**
   - Stage 6: Code Review (**MANDATORY**)
   - Stage 7: Design Review (if Figma, else Skipped)
   - Stage 8: Coverage Verification (**MANDATORY**)
8. Stage 9 → Test Run (full test suite)
9. **→ USER APPROVAL GATE:**
   - Stage 10: **ASK USER** before PR
   - If YES: Create PR
   - If NO: Stop

---

## Failure Recovery

If any stage fails:
1. Identify root cause
2. Document in artifact with **Status: Failed**
3. Return to Stage 3 (Planning)
4. Create fix plan based on failure
5. Re-execute stages 4a → 4b → 4c → 5 → 6 → 7 → 8 → 9 → 10
6. Max retries: 2 full loops

---

## Glossary

- **MANDATORY:** Stage must always execute; skipping = workflow invalid
- **CONDITIONAL:** Stage runs based on input condition (e.g., Figma present)
- **GATE:** Decision point; must pass before proceeding
- **BRANCH:** Multiple sub-stages to execute in order
- **Non-negotiable:** Cannot be overridden without explicit user instruction

---

## Validation Checklist Before Stage 10

Before proceeding to Stage 10 (PR creation), verify:

- ✅ Stage 1: Issue analyzed (artifact exists)
- ✅ Stage 2: Clarifications completed (artifact exists)
- ✅ Stage 3: Plan created (artifact exists)
- ✅ Stage 3.5: Constraints validated (artifact exists, Status: Pass)
- ✅ Stage 4a: Failing tests written (artifact exists)
- ✅ Stage 4b: Implementation done (artifact exists, all tests pass)
- ✅ Stage 4c: Refactoring completed (artifact exists)
- ✅ Stage 5: Build successful (artifact exists, Status: Success)
- ✅ Stage 6: Code review passed (artifact exists, Status: Success, Coverage >= 80%)
- ✅ Stage 7: Design review (artifact exists, Status: Success or Skipped)
- ✅ Stage 8: Coverage verified (artifact exists, Status: Pass, Coverage >= 80%)
- ✅ Stage 9: Tests passed (artifact exists, Status: Success)
- **THEN:** Proceed to Stage 10 **with user approval**

If any artifact is missing or status is Failed → return to Stage 3 (Planning).

---

## For Claude Code / Codex Implementers

When executing `implement issue` command:

1. Read this file FIRST
2. Read `.ai/commands/implement-issue.md` SECOND
3. Execute stages in strict order: 1 → 2 → 3 → 3.5 → 4a → 4b → 4c → 5 → 6 → 7 → 8 → 9 → 10
4. Do NOT skip stages 6, 7, 8
5. For Stage 10, use AskUserQuestion tool to get approval before committing
6. Create artifacts for each stage in `.ai/artifacts/run/`
7. If any stage fails, run debug-rca agent and return to Stage 3
