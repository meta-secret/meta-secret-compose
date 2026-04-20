# ⚠️ EXECUTE THIS PIPELINE - DO NOT SKIP STAGES

**This is NOT documentation. This is EXECUTABLE INSTRUCTIONS.**

You MUST spawn Tasks for all 7 stages in exact sequence.
After each Task completes → spawn next Task.
No branching. No choices. Just execute.

**Note:** Build (compilation) and Test execution removed - user runs these manually.

---

## REQUIRED SEQUENCE

```
Task 1 (Stage 1: Understanding)
  ↓ (after artifact written)
Task 2 (Stage 2: Planning)
  ↓ (after artifact written)
Task 3 (Stage 3: Implementation)
  ↓
Task 4 (Stage 4: Testing)
  ↓
Task 5 (Stage 5: Build)
  ├─ If FAIL → Task 5a (Debug/RCA) → back to Stage 2
  ↓ (if SUCCESS)
Task 6 (Stage 6: Test Run)
  ├─ If FAIL → back to Stage 2 (max 2 times)
  ↓
Task 7 (Stage 7: Code Review)
  ├─ If FAIL → back to Stage 2 (max 2 times)
  ↓
Task 8 (Stage 8: Commit)
  ↓
Task 9 (Stage 9: PR)
  ↓
DONE
```

---

# 🎯 START EXECUTION

## TASK 1: STAGE 1 - Understanding

**Spawn this Task immediately. Do not read ahead.**

```
Prompt to pass to Task:

"🟢 Starting Stage 1: Understanding
═════════════════════════════════════

Execute STAGE 1: Understanding

Steps:
1. Print: 🟡 Reading GitHub issue...
2. Read .ai/agents/github-issue-coordinator.md (full file)
3. Fetch issue 49: gh issue view 49 --json title,body,number
4. Extract: problem, goal, requirements, assumptions, affected areas
5. Write EXACT location: .ai/artifacts/run/MS-49-001-understanding.md
6. Include sections: Problem, Goal, Requirements, Assumptions, Affected Areas
7. Print: ✅ Stage 1: Understanding completed successfully
8. Return

If you encounter 'FAILED', '**FAIL**', or '❌' in your work → STOP and report immediately."
```

### After Task 1 completes:
1. Read artifact: `.ai/artifacts/run/MS-49-001-understanding.md`
2. Check for FAILURE markers (FAILED, **FAIL**, ❌)
3. If FAILURE → STOP. Report error.
4. If SUCCESS → **Spawn Task 2 immediately**

---

## TASK 2: STAGE 2 - Planning

**Spawn this Task after Task 1 succeeds.**

```
Prompt:

"🟢 Starting Stage 2: Planning
═════════════════════════════════════
🟡 Reading requirements and creating plan...

Execute STAGE 2: Planning

Prerequisites:
- Artifact from Stage 1 exists: .ai/artifacts/run/MS-49-001-understanding.md
- You will read it

Steps:
1. Read .ai/agents/feature-planner.md (full file)
2. Read .ai/artifacts/run/MS-49-001-understanding.md (from Stage 1)
3. Read .ai/rules/kmp-principles.md (architecture rules)
4. Create detailed plan:
   - Scope: numbered list of implementation steps
   - Each step: file path + description
   - Tech stack alignment: KMM, MVVM, FFI boundary
   - Deferred: what's out of scope
   - Risks: what could go wrong
5. Write EXACT location: .ai/artifacts/run/MS-49-002-planning.md
6. Print: ✅ Stage 2: Planning completed successfully
7. Return

Check for failure markers. Stop if found."
```

### After Task 2 completes:
1. Read artifact: `.ai/artifacts/run/MS-49-002-planning.md`
2. Check for FAILURE markers
3. If FAILURE → STOP. Report.
4. If SUCCESS → **Spawn Task 3 immediately**

---

## TASK 3: STAGE 3 - Implementation

**Spawn after Task 2 succeeds.**

```
Prompt:

"🟢 Starting Stage 3: Implementation
═════════════════════════════════════
🟡 Writing code changes...

Execute STAGE 3: Implementation

Steps:
1. Read .ai/agents/code-implementer.md (full file)
2. Read .ai/artifacts/run/MS-49-002-planning.md (from Stage 2)
3. Implement code changes:
   - Follow plan exactly
   - Modify only files listed in plan
   - Keep changes minimal
   - No refactoring beyond plan scope
4. Write EXACT location: .ai/artifacts/run/MS-49-003-implementation.md
5. Include:
   - Summary of changes
   - List of modified files with paths
   - Any deviations from plan + reason
6. Print: ✅ Stage 3: Implementation completed successfully
7. Return

Check for failure markers."
```

### After Task 3 completes:
1. Read artifact: `.ai/artifacts/run/MS-49-003-implementation.md`
2. Check for FAILURE markers
3. If FAILURE → STOP. Report.
4. If SUCCESS → **Spawn Task 4 immediately**

---

## TASK 4: STAGE 4 - Test Writing

**Spawn after Task 3 succeeds.**

```
Prompt:

"🟢 Starting Stage 4: Test Writing
═════════════════════════════════════
🟡 Writing test cases...

Execute STAGE 4: Test Writing

Steps:
1. Read .ai/agents/test-author.md (full file)
2. Read .ai/artifacts/run/MS-49-003-implementation.md (from Stage 3)
3. Write tests:
   - Unit tests for new functions
   - Integration tests if needed
   - Cover edge cases
   - Update existing tests if affected
4. Write EXACT location: .ai/artifacts/run/MS-49-004-testing.md
5. Include:
   - Test files created/modified
   - Coverage summary
   - Edge cases tested
6. Print: ✅ Stage 4: Test Writing completed successfully
7. Return

Check for failure markers."
```

### After Task 4 completes:
1. Read artifact: `.ai/artifacts/run/MS-49-004-testing.md`
2. Check for FAILURE markers
3. If FAILURE → STOP. Report.
4. If SUCCESS → **Spawn Task 5 immediately**

---

## TASK 5: STAGE 5 - Build

**Spawn after Task 4 succeeds.**

```
Prompt:

"🟢 Starting Stage 5: Build
═════════════════════════════════════
🟡 Compiling project (this may take 60-90 seconds)...

Execute STAGE 5: Build

Command:
./gradlew build --no-daemon --parallel --console=plain

Steps:
1. Run build command above (full build with test compilation/linking, no -x test)
2. Capture FULL output
3. Write EXACT location: .ai/artifacts/run/MS-49-005-build.md
4. Include:
   - Command used
   - Result: SUCCESS or FAILED
   - Full build output
   - Duration in seconds
5. Print: ✅ Stage 5: Build completed successfully (or ❌ if failed)
6. Return

Artifact should clearly indicate SUCCESS or FAILED."
```

### After Task 5 completes:
1. Read artifact: `.ai/artifacts/run/MS-49-005-build.md`
2. Check result:
   ```
   If contains "SUCCESS":
   → Spawn Task 6 immediately
   
   If contains "FAILED":
   → Spawn Task 5a (Debug/RCA)
   → Then go back to Stage 2
   ```

---

## TASK 5a: STAGE 5a - Debug/RCA (ONLY if Build Failed)

**Only spawn if Stage 5 FAILED.**

```
Prompt:

"🔴 Build failed! Running Root Cause Analysis...
═════════════════════════════════════
🟡 Analyzing build error...

Execute STAGE 5a: Debug/RCA

Input:
- Build error from: .ai/artifacts/run/MS-49-005-build.md

Steps:
1. Read .ai/agents/debug-rca.md (full file)
2. Analyze build error:
   - What failed?
   - Why did it fail?
   - Which file/line?
3. Suggest specific fixes
4. Write EXACT location: .ai/artifacts/run/MS-49-005-build-rca-retry-1.md
5. Include:
   - Error summary
   - Root cause analysis
   - Suggested fixes (specific code changes)
6. Print: ✅ RCA completed. Replanning and retrying build...
7. Return"
```

### After Task 5a completes:
**Go back to Stage 2** (Replan with RCA insights)
- Create: `MS-49-002-planning-retry-1.md`
- Continue: Stages 3-5 again
- Max 2 total retries

---

## TASK 6: STAGE 6 - Test Run

**Spawn after Stage 5 BUILD SUCCESS.**

```
Prompt:

"🟢 Starting Stage 6: Test Run
═════════════════════════════════════
🟡 Executing unit tests...

Execute STAGE 6: Test Run

Command:
./gradlew testDebugUnitTest --no-daemon --parallel --console=plain

Steps:
1. Run test command above
2. Capture FULL output
3. Write EXACT location: .ai/artifacts/run/MS-49-006-test-run.md
4. Include:
   - Command used
   - Result: PASSED or FAILED
   - Test summary (X passed, Y failed)
   - List failed tests if any
   - Duration
5. Print: ✅ Stage 6: Test Run completed successfully (or ❌ if failed)
6. Return

Artifact should clearly indicate PASSED or FAILED."
```

### After Task 6 completes:
1. Read artifact: `.ai/artifacts/run/MS-49-006-test-run.md`
2. Check result:
   ```
   If contains "PASSED":
   → Spawn Task 7 immediately
   
   If contains "FAILED":
   → Go back to Stage 2 (max 2 retries total)
   ```

---

## TASK 7: STAGE 7 - Code Review

**Spawn after Stage 6 SUCCESS.**

```
Prompt:

"🟢 Starting Stage 7: Code Review
═════════════════════════════════════
🟡 Reviewing code against rules...

Execute STAGE 7: Code Review

Steps:
1. Read .ai/agents/code-reviewer.md (full file)
2. Review all code changes against:
   - .ai/rules/kmp-principles.md (architecture)
   - ARCHITECTURE.md
   - CODE_STYLE.md
   - SECURITY.md
3. Check:
   - Architecture (MVVM, FFI boundary)
   - Style (Kotlin/Swift conventions)
   - Security (no secrets, error handling)
   - Dead code
4. Write EXACT location: .ai/artifacts/run/MS-49-007-review.md
5. Include sections:
   - Summary
   - Must-Fix (violations)
   - Should-Fix (style issues)
   - Nice-to-Have (improvements)
   - Status: PASSED or FAILED
6. Print: ✅ Stage 7: Code Review completed successfully (or ❌ if failed)
7. Return"
```

### After Task 7 completes:
1. Read artifact: `.ai/artifacts/run/MS-49-007-review.md`
2. Check for PASSED or FAILED:
   ```
   If PASSED:
   → Spawn Task 8 immediately
   
   If FAILED:
   → Go back to Stage 2 (max 2 retries total)
   ```

---

## TASK 8: STAGE 8 - Commit

**Spawn after Stage 7 SUCCESS.**

```
Prompt:

"🟢 Starting Stage 8: Commit
═════════════════════════════════════
🟡 Creating git commit and pushing...

Execute STAGE 8: Commit

Steps:
1. Read .ai/agents/release-manager.md (full file)
2. From Stage 1 artifact, extract issue title prefix: [Task], [Feature], or [Bug]
3. Git operations:
   - git fetch origin
   - git checkout main
   - git checkout -b {Prefix}/kuklin/MS-49
   - git add [all modified files]
   - git commit -m '[Issue #49] Summary of changes'
   - git push -u origin {Prefix}/kuklin/MS-49
4. Write EXACT location: .ai/artifacts/run/MS-49-008-commit.md
5. Include:
   - Branch name
   - Commit SHA
   - Commit message
   - Push status (SUCCESS)
6. Print: ✅ Stage 8: Commit completed successfully
7. Return"
```

### After Task 8 completes:
1. Read artifact: `.ai/artifacts/run/MS-49-008-commit.md`
2. Check for SUCCESS
3. If SUCCESS → **Spawn Task 9 immediately (FINAL STAGE)**

---

## TASK 9: STAGE 9 - Create PR

**Spawn after Stage 8 SUCCESS. FINAL TASK.**

```
Prompt:

"🟢 Starting Stage 9: Create PR (FINAL STAGE)
═════════════════════════════════════════════
🟡 Creating pull request...

Execute STAGE 9: Create PR (FINAL STAGE)

Steps:
1. Read .ai/agents/release-manager.md (full file)
2. Create PR:
   gh pr create \\
     --title '[Issue #49] Summary of changes' \\
     --body 'Implementation of issue #49 requirements' \\
     --base main
3. Capture PR info:
   - PR number
   - PR URL
   - Status
4. Write EXACT location: .ai/artifacts/run/MS-49-009-pr.md
5. Include:
   - PR number
   - PR URL
   - Base branch
   - Status: CREATED
6. Print message:
   🎉 PIPELINE FINISHED SUCCESSFULLY! 🎉
   ✨ All 9 stages completed!
   📝 PR created and ready for review
7. Return

PIPELINE COMPLETE WHEN THIS TASK FINISHES."
```

### After Task 9 completes:
✅ **ENTIRE PIPELINE FINISHED**

All 9 stages complete. Check artifacts:
- `.ai/artifacts/run/MS-49-001-understanding.md` ✓
- `.ai/artifacts/run/MS-49-002-planning.md` ✓
- `.ai/artifacts/run/MS-49-003-implementation.md` ✓
- `.ai/artifacts/run/MS-49-004-testing.md` ✓
- `.ai/artifacts/run/MS-49-005-build.md` ✓
- `.ai/artifacts/run/MS-49-006-test-run.md` ✓
- `.ai/artifacts/run/MS-49-007-review.md` ✓
- `.ai/artifacts/run/MS-49-008-commit.md` ✓
- `.ai/artifacts/run/MS-49-009-pr.md` ✓

PR created on GitHub. Done.

---

**Version:** 3.0 (explicit Task sequence, no branching)  
**Last updated:** 2026-04-20  
**Status:** READY FOR EXECUTION
