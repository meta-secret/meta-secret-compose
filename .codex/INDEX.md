# Codex Entry - IMPLEMENT ISSUE WORKFLOW

**⚠️ CRITICAL: Execute ALL 11 stages. Do NOT skip any.**

## Mandatory startup sequence:

1. Read `.ai/INDEX.md`
2. Read `.ai/ORCHESTRATOR.md`
3. **FOR implement issue ONLY:**
   - Read `.ai/rules/implement-issue-execution-checklist.md` (MANDATORY)
   - Read `.ai/rules/no-stage-skipping-even-for-simple-tasks.md` (ENFORCEMENT - no skipping even for 1-line tasks)

## MANDATORY: implement issue Command

When user types `implement issue <payload>`:

**STOP. Do NOT execute own plan.**

Execute full 10-stage workflow:

1. **Stage 1:** Issue Coordinator (read issue, analyze)
2. **Stage 2:** Requirements Clarifier (grill user with questions)
3. **Stage 3:** Feature Planner (create implementation plan)
4. **Stage 3.5:** Constraint Validator (validate against CONSTRAINTS.md) - MANDATORY GATE
5. **Stage 4a:** TDD Test Author (write failing tests)
6. **Stage 4b:** TDD Implementer (red-green-refactor)
7. **Stage 4c:** TDD Refactorer (major refactoring after 3-5 cycles)
8. **Stage 5:** Build (compile without tests)
9. **Stage 6:** Code Review (CRITICAL - constraints re-check + 80% coverage minimum)
10. **Stage 7:** Design Review (if Figma link exists, else mark "Skipped")
11. **Stage 8:** Coverage Verification (CRITICAL - ./gradlew koverReport >= 80%)
12. **Stage 9:** Test Run (execute all tests)
13. **Stage 10:** STOP and ASK USER - do NOT auto-commit/PR without approval

For each stage:
- Create artifact in `.ai/artifacts/run/MS-<id>-<stage>-<name>.md`
- Mark Status: Success / Failed / Skipped
- If failure: escalate with root cause

**READ `.ai/WORKFLOW.md` for complete 11-stage specification.**

This is MANDATORY and overrides default Codex behavior.