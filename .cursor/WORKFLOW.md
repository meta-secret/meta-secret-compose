# ⚠️ EXECUTE THIS PIPELINE - DO NOT SKIP STAGES

**This is NOT documentation. This is EXECUTABLE INSTRUCTIONS for Cursor.**

You MUST execute all 9 stages in exact sequence via Cmd+K prompts.
After each stage completes → send next stage prompt.
No branching. No choices. Just execute.

---

## REQUIRED SEQUENCE

```
Stage 1: Understanding
  ↓ (after you see artifact)
Stage 2: Planning
  ↓
Stage 3: Implementation
  ↓
Stage 4: Testing
  ↓
Stage 5: Build
  ├─ If FAIL → Stage 5a (RCA) → back to Stage 2
  ↓ (if SUCCESS)
Stage 6: Test Run
  ├─ If FAIL → back to Stage 2 (max 2 times)
  ↓
Stage 7: Code Review
  ├─ If FAIL → back to Stage 2 (max 2 times)
  ↓
Stage 8: Commit
  ↓
Stage 9: PR
  ↓
DONE
```

---

# 🚀 START EXECUTION

## STAGE 1: Understanding

**Send this exact prompt to Cursor (Cmd+K):**

```
Execute STAGE 1: Understanding

Steps:
1. Read .ai/agents/github-issue-coordinator.md (full file)
2. Fetch issue 49: gh issue view 49 --json title,body,number
3. Extract: problem, goal, requirements, assumptions, affected areas
4. Write to: .ai/artifacts/run/MS-49-001-understanding.md
5. Include sections: Problem, Goal, Requirements, Assumptions, Affected Areas
6. When done, tell me you finished and show me the artifact location

Do NOT proceed to next stage. Wait for my instruction.
```

### After Stage 1 completes:
1. Check artifact: `.ai/artifacts/run/MS-49-001-understanding.md`
2. Look for: FAILED, **FAIL**, ❌
3. If FAILED → STOP. Report error.
4. If SUCCESS → **Send Stage 2 prompt below**

---

## STAGE 2: Planning

**When Stage 1 is done, send this prompt:**

```
Execute STAGE 2: Planning

Prerequisites:
- You have artifact: .ai/artifacts/run/MS-49-001-understanding.md
- Read it now

Steps:
1. Read .ai/agents/feature-planner.md (full file)
2. Read .ai/artifacts/run/MS-49-001-understanding.md (Stage 1)
3. Read .ai/rules/kmp-principles.md (architecture)
4. Create plan:
   - Scope: numbered list of implementation steps
   - Each step: file path + description
   - Tech stack: KMM, MVVM, FFI boundary
   - Deferred: out of scope
   - Risks: what could go wrong
5. Write to: .ai/artifacts/run/MS-49-002-planning.md
6. When done, tell me you finished

Do NOT proceed to next stage. Wait for my instruction.
```

### After Stage 2 completes:
1. Check artifact: `.ai/artifacts/run/MS-49-002-planning.md`
2. Check for FAILED markers
3. If FAILED → STOP.
4. If SUCCESS → **Send Stage 3 prompt below**

---

## STAGE 3: Implementation

**When Stage 2 is done, send this prompt:**

```
Execute STAGE 3: Implementation

Steps:
1. Read .ai/agents/code-implementer.md (full file)
2. Read .ai/artifacts/run/MS-49-002-planning.md (Stage 2 plan)
3. Implement code changes:
   - Follow plan exactly
   - Modify only files in plan
   - Keep changes minimal
   - No refactoring beyond plan
4. Write to: .ai/artifacts/run/MS-49-003-implementation.md
5. Include:
   - Summary of changes
   - List of modified files with paths
   - Any deviations + reason
6. When done, tell me you finished

Do NOT proceed. Wait for instruction.
```

### After Stage 3 completes:
1. Check artifact
2. Check for FAILED markers
3. If SUCCESS → **Send Stage 4 prompt**

---

## STAGE 4: Testing

**When Stage 3 is done, send this prompt:**

```
Execute STAGE 4: Test Writing

Steps:
1. Read .ai/agents/test-author.md (full file)
2. Read .ai/artifacts/run/MS-49-003-implementation.md (Stage 3)
3. Write tests:
   - Unit tests for new functions
   - Integration tests if needed
   - Edge cases
   - Update existing tests if affected
4. Write to: .ai/artifacts/run/MS-49-004-testing.md
5. Include:
   - Test files created/modified
   - Coverage summary
   - Edge cases tested
6. When done, tell me you finished

Wait for next instruction.
```

### After Stage 4 completes:
1. Check for FAILED markers
2. If SUCCESS → **Send Stage 5 prompt**

---

## STAGE 5: Build

**When Stage 4 is done, send this prompt:**

```
Execute STAGE 5: Build

Command to run:
./gradlew build -x test --no-daemon --parallel --console=plain

Steps:
1. Run build command above
2. Capture FULL output
3. Write to: .ai/artifacts/run/MS-49-005-build.md
4. Include:
   - Command
   - Result: SUCCESS or FAILED
   - Full build output
   - Duration
5. When done, tell me the result (SUCCESS or FAILED)

Important: Make sure artifact clearly shows SUCCESS or FAILED.
```

### After Stage 5 completes:
Check artifact:
```
If contains "SUCCESS":
→ Send Stage 6 prompt

If contains "FAILED":
→ Send Stage 5a prompt (RCA)
```

---

## STAGE 5a: Debug/RCA (ONLY if Build Failed)

**Only send if Stage 5 FAILED.**

```
Execute STAGE 5a: Debug/RCA

Input:
Build error from: .ai/artifacts/run/MS-49-005-build.md

Steps:
1. Read .ai/agents/debug-rca.md (full file)
2. Analyze build error:
   - What failed?
   - Why?
   - Which file/line?
3. Suggest specific fixes
4. Write to: .ai/artifacts/run/MS-49-005-build-rca-retry-1.md
5. Include:
   - Error summary
   - Root cause
   - Suggested fixes (code changes)
6. When done, tell me you finished

After this, we go BACK TO STAGE 2 (Replan).
Max 2 total retries.
```

### After Stage 5a completes:
**Go back to Stage 2** with RCA insights
- Create planning-retry-1.md
- Then continue Stages 3-5 again
- Max 2 total retries

---

## STAGE 6: Test Run

**When Stage 5 BUILD SUCCESS, send this prompt:**

```
Execute STAGE 6: Test Run

Command to run:
./gradlew testDebugUnitTest --no-daemon --parallel --console=plain

Steps:
1. Run test command above
2. Capture FULL output
3. Write to: .ai/artifacts/run/MS-49-006-test-run.md
4. Include:
   - Command
   - Result: PASSED or FAILED
   - Test summary (X passed, Y failed)
   - Failed tests list
   - Duration
5. When done, tell me the result (PASSED or FAILED)

Make sure artifact shows PASSED or FAILED clearly.
```

### After Stage 6 completes:
```
If PASSED:
→ Send Stage 7 prompt

If FAILED:
→ Go back to Stage 2 (max 2 retries)
```

---

## STAGE 7: Code Review

**When Stage 6 PASSED, send this prompt:**

```
Execute STAGE 7: Code Review

Steps:
1. Read .ai/agents/code-reviewer.md (full file)
2. Review code against:
   - .ai/rules/kmp-principles.md (architecture)
   - ARCHITECTURE.md
   - CODE_STYLE.md
   - SECURITY.md
3. Check:
   - Architecture (MVVM, FFI)
   - Style (Kotlin/Swift)
   - Security (no secrets)
   - Dead code
4. Write to: .ai/artifacts/run/MS-49-007-review.md
5. Include sections:
   - Summary
   - Must-Fix
   - Should-Fix
   - Nice-to-Have
   - Status: PASSED or FAILED
6. When done, tell me the result (PASSED or FAILED)
```

### After Stage 7 completes:
```
If PASSED:
→ Send Stage 8 prompt

If FAILED:
→ Go back to Stage 2 (max 2 retries)
```

---

## STAGE 8: Commit

**When Stage 7 PASSED, send this prompt:**

```
Execute STAGE 8: Commit

Steps:
1. Read .ai/agents/release-manager.md (full file)
2. From Stage 1 artifact, get issue title prefix: [Task], [Feature], or [Bug]
3. Git operations:
   - git fetch origin
   - git checkout main
   - git checkout -b {Prefix}/kuklin/MS-49
   - git add [all modified files]
   - git commit -m '[Issue #49] Summary of changes'
   - git push -u origin {Prefix}/kuklin/MS-49
4. Write to: .ai/artifacts/run/MS-49-008-commit.md
5. Include:
   - Branch name
   - Commit SHA
   - Commit message
   - Push status: SUCCESS
6. When done, tell me the branch name and commit SHA

NEXT: Final stage (PR).
```

### After Stage 8 completes:
1. Check artifact
2. If SUCCESS → **Send Stage 9 prompt (FINAL)**

---

## STAGE 9: Create PR

**When Stage 8 SUCCESS, send this FINAL prompt:**

```
Execute STAGE 9: Create PR (FINAL STAGE)

Steps:
1. Read .ai/agents/release-manager.md (full file)
2. Create PR:
   gh pr create \
     --title '[Issue #49] Summary of changes' \
     --body 'Implementation of issue #49 requirements' \
     --base main
3. Capture PR info:
   - PR number
   - PR URL
   - Status
4. Write to: .ai/artifacts/run/MS-49-009-pr.md
5. Include:
   - PR number
   - PR URL
   - Base branch
   - Status: CREATED
6. When done, tell me the PR number and URL

PIPELINE COMPLETE AFTER THIS STAGE.
```

### After Stage 9 completes:
✅ **ENTIRE PIPELINE FINISHED**

All 9 artifacts created:
- MS-49-001-understanding.md ✓
- MS-49-002-planning.md ✓
- MS-49-003-implementation.md ✓
- MS-49-004-testing.md ✓
- MS-49-005-build.md ✓
- MS-49-006-test-run.md ✓
- MS-49-007-review.md ✓
- MS-49-008-commit.md ✓
- MS-49-009-pr.md ✓

PR created. Done.

---

## 📝 Key Rules

1. **DO NOT SKIP STAGES** — execute all 9 in sequence
2. **DO NOT BRANCH** — no "if you prefer, you could do..."
3. **DO NOT INTERPRET** — follow prompts exactly as written
4. **DO NOT OPTIMIZE** — no combining stages
5. **CHECK FOR FAILURES** — after each stage, look for FAILED/FAIL/❌
6. **REPORT PROGRESS** — tell me when each stage finishes

---

**Version:** 3.0 (explicit instructions, no branching)  
**Last updated:** 2026-04-20  
**Status:** READY FOR EXECUTION
