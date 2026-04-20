# ⚠️ EXECUTE THIS PIPELINE - DO NOT SKIP STAGES

**This is NOT documentation. This is EXECUTABLE INSTRUCTIONS.**

You MUST execute all 7 stages in this exact sequence.
After each stage completes → go to next stage.
No branching. No choices. Just execute.

**Note:** Build (compilation) and Test execution removed - user runs these manually.

---

## REQUIRED ORDER
```
Stage 1 ↓
Stage 2 ↓
Stage 3 ↓
Stage 4 ↓
Stage 5 ↓ (if fails → back to Stage 2, max 2 times)
Stage 6 ↓
Stage 7 ↓ END
```

---

# 🚀 START HERE: STAGE 1 - STAGE 4 (no changes, keep as is)
# 🚀 START HERE: STAGE 1

## STAGE 1: Understanding (Read Issue)

**MANDATORY. Execute now. Do not read ahead.**

### Agent
`github-issue-coordinator` from `.ai/agents/github-issue-coordinator.md`

### Input
GitHub issue ID (e.g., 49) or text description

### Output Location
`.ai/artifacts/run/MS-49-001-understanding.md`

### EXACT STEPS (Execute in order)

1. **Print status:**
   ```bash
   echo ""
   echo "🟢 Starting Stage 1: Understanding"
   echo "═════════════════════════════════════"
   ```

2. **Fetch issue:**
   ```bash
   gh issue view 49 --json title,body,number
   ```

3. **Extract and write to artifact:**
   - Problem statement (what's the issue?)
   - Goal (what success looks like)
   - Requirements (numbered list)
   - Assumptions (what you assume to be true)
   - Affected areas (which parts of code)

4. **Write artifact:**
   ```
   File: .ai/artifacts/run/MS-49-001-understanding.md
   Format: markdown with sections above
   ```

5. **Check for FAILURE markers:**
   ```bash
   echo "🟡 Checking artifact for errors..."
   # If artifact contains: FAILED, **FAIL**, ❌, "Return to Planning: YES"
   # → STOP. Report error.
   # If SUCCESS → continue to next step
   ```

6. **Success message:**
   ```bash
   echo "✅ Stage 1: Understanding completed successfully"
   echo ""
   ```

7. **If SUCCESS:**
   → **PROCEED TO STAGE 2** (see below)
   → Do not wait for approval
   → Go immediately

---

# 📋 STAGE 2: Planning

**MANDATORY. Execute now.**

### Agent
`feature-planner` from `.ai/agents/feature-planner.md`

### Input
- Artifact from Stage 1: `.ai/artifacts/run/MS-49-001-understanding.md`
- Read: `.ai/rules/kmp-principles.md` (architecture rules)

### Output Location
`.ai/artifacts/run/MS-49-002-planning.md`

### EXACT STEPS

1. **Print status:**
   ```bash
   echo ""
   echo "🟢 Starting Stage 2: Planning"
   echo "═════════════════════════════════════"
   echo "🟡 Reading requirements..."
   ```

2. **Read Stage 1 artifact** (you just created it)

3. **Read architecture rules:**
   ```
   .ai/rules/kmp-principles.md
   ARCHITECTURE.md
   CODE_STYLE.md
   ```

4. **Create detailed plan:**
   - **Scope** — numbered list of implementation steps
   - Each step: file path + description
   - Example: `1. Update SignInScreenViewModel to add nameOccupiedJoinPrompt state`
   - **Tech stack alignment** — KMM, MVVM, FFI boundary
   - **Deferred items** — what's out of scope
   - **Risks** — what could go wrong

5. **Write artifact:**
   ```
   File: .ai/artifacts/run/MS-49-002-planning.md
   Format: markdown with sections above
   ```

6. **Success message:**
   ```bash
   echo "✅ Stage 2: Planning completed successfully"
   echo ""
   ```

7. **Check for FAILURE markers:**
   If found → STOP. Report.

8. **If SUCCESS:**
   → **PROCEED TO STAGE 3** immediately

---

# 💻 STAGE 3: Implementation

**MANDATORY. Execute now.**

### Agent
`code-implementer` from `.ai/agents/code-implementer.md`

### Input
Artifact from Stage 2: `.ai/artifacts/run/MS-49-002-planning.md`

### Output Location
`.ai/artifacts/run/MS-49-003-implementation.md`

### EXACT STEPS

1. **Print status:**
   ```bash
   echo ""
   echo "🟢 Starting Stage 3: Implementation"
   echo "═════════════════════════════════════"
   echo "🟡 Writing code changes..."
   ```

2. **Read Stage 2 artifact** (the plan)

3. **Implement code changes:**
   - Follow plan exactly
   - Modify only files listed in plan
   - Keep changes minimal and scoped
   - No refactoring beyond plan

4. **Write artifact:**
   ```
   File: .ai/artifacts/run/MS-49-003-implementation.md
   Include:
   - Summary of changes
   - List of modified files (with paths)
   - Any deviations from plan + reason
   ```

5. **Success message:**
   ```bash
   echo "✅ Stage 3: Implementation completed successfully"
   echo ""
   ```

6. **Check for FAILURE markers:**
   If found → STOP. Report.

7. **If SUCCESS:**
   → **PROCEED TO STAGE 4** immediately

---

# 🧪 STAGE 4: Test Writing

**MANDATORY. Execute now.**

### Agent
`test-author` from `.ai/agents/test-author.md`

### Input
Artifact from Stage 3: `.ai/artifacts/run/MS-49-003-implementation.md`

### Output Location
`.ai/artifacts/run/MS-49-004-testing.md`

### EXACT STEPS

1. **Print status:**
   ```bash
   echo ""
   echo "🟢 Starting Stage 4: Test Writing"
   echo "═════════════════════════════════════"
   echo "🟡 Writing test cases..."
   ```

2. **Read Stage 3 artifact** (the code changes)

3. **Write tests:**
   - Unit tests for new functions
   - Integration tests if needed
   - Cover edge cases
   - Update existing tests if affected

4. **Write artifact:**
   ```
   File: .ai/artifacts/run/MS-49-004-testing.md
   Include:
   - Test files created/modified
   - Coverage summary
   - Edge cases tested
   ```

5. **Success message:**
   ```bash
   echo "✅ Stage 4: Test Writing completed successfully"
   echo ""
   ```

6. **Check for FAILURE markers:**
   If found → STOP. Report.

7. **If SUCCESS:**
   → **PROCEED TO STAGE 5** immediately

---


---

# 👁️ STAGE 5: Code Review

**MANDATORY. Execute now.**

### Agent
`code-reviewer` from `.ai/agents/code-reviewer.md`

### Input
All code changes from Stage 3

### Output Location
`.ai/artifacts/run/MS-49-005-review.md`

### EXACT STEPS

1. **Print status:**
   ```bash
   echo ""
   echo "🟢 Starting Stage 5: Code Review"
   echo "═════════════════════════════════════"
   echo "🟡 Reviewing code against rules..."
   ```

2. **Review against rules:**
   ```
   .ai/rules/kmp-principles.md (architecture)
   ARCHITECTURE.md
   CODE_STYLE.md
   SECURITY.md
   ```

3. **Check:**
   - Architecture compliance (MVVM, FFI boundary)
   - Code style (Kotlin/Swift conventions)
   - Security (no secrets, proper error handling)
   - Dead code or issues

4. **Write artifact:**
   ```markdown
   # Code Review
   
   ## Summary
   [what was reviewed]
   
   ## Must-Fix
   [architecture/security violations]
   
   ## Should-Fix
   [style/clarity issues]
   
   ## Nice-to-Have
   [optional improvements]
   
   ## Status
   PASSED (or FAILED)
   ```

5. **Check result & print status:**
   ```bash
   # If PASSED:
   echo "✅ Stage 5: Code Review completed successfully"
   echo ""
   # → PROCEED TO STAGE 6
   
   # If FAILED:
   echo "❌ Stage 5: Code review failed"
   # → GO BACK TO STAGE 2 (Replan)
   # → Max 2 retries total
   ```

---

# 📝 STAGE 6: Commit

**MANDATORY. Execute now.**

### Agent
`release-manager` from `.ai/agents/release-manager.md`

### Input
All stages 1-5 completed

### Output Location
`.ai/artifacts/run/MS-49-006-commit.md`

### EXACT STEPS

1. **Print status:**
   ```bash
   echo ""
   echo "🟢 Starting Stage 6: Commit"
   echo "═════════════════════════════════════"
   echo "🟡 Creating git commit and pushing..."
   ```

2. **Parse issue title from Stage 1:**
   ```
   Extract prefix: [Task], [Feature], or [Bug]
   Example: [Feature] → prefix = "Feature"
   ```

3. **Create branch:**
   ```bash
   git fetch origin
   git checkout main
   git checkout -b Feature/kuklin/MS-49
   ```

4. **Stage changes:**
   ```bash
   git add [all modified files from Stage 3]
   ```

5. **Create commit:**
   ```bash
   git commit -m "[Issue #49] Summary of changes"
   ```

6. **Push to remote:**
   ```bash
   git push -u origin Feature/kuklin/MS-49
   ```

7. **Write artifact:**
   ```markdown
   # Commit Report
   
   ## Branch
   Feature/kuklin/MS-49
   
   ## Commit SHA
   [sha]
   
   ## Commit Message
   [Issue #49] Summary of changes
   
   ## Push Status
   SUCCESS
   ```

8. **Success message:**
   ```bash
   echo "✅ Stage 6: Commit completed successfully"
   echo ""
   ```

9. **If SUCCESS:**
   → **PROCEED TO STAGE 7** immediately

---

# 🔗 STAGE 7: Create PR

**MANDATORY. Execute now. FINAL STAGE.**

### Agent
`release-manager` from `.ai/agents/release-manager.md`

### Input
Branch pushed (Stage 6 completed)

### Output Location
`.ai/artifacts/run/MS-49-007-pr.md`

### EXACT STEPS

1. **Print status:**
   ```bash
   echo ""
   echo "🟢 Starting Stage 7: Create PR (FINAL)"
   echo "═════════════════════════════════════"
   echo "🟡 Creating pull request..."
   ```

2. **Create PR:**
   ```bash
   gh pr create \
     --title "[Issue #49] Summary of changes" \
     --body "Implementation of issue #49 requirements" \
     --base main
   ```

3. **Capture PR info:**
   - PR URL
   - PR number
   - Status

4. **Write artifact:**
   ```markdown
   # PR Report
   
   ## PR Number
   #456
   
   ## PR URL
   https://github.com/.../pull/456
   
   ## Status
   CREATED
   
   ## Base Branch
   main
   ```

5. **Final success message:**
   ```bash
   echo ""
   echo "✅ Stage 7: Create PR completed successfully"
   echo "════════════════════════════════════════════"
   echo "🎉 PIPELINE FINISHED SUCCESSFULLY! 🎉"
   echo "✨ All 7 stages completed!"
   echo "📝 PR created and ready for review"
   echo ""
   ```

6. **When complete:**
   ✅ **PIPELINE FINISHED**

---

## ✅ COMPLETION CHECKLIST

When all 7 stages complete, you should have:

- [ ] `.ai/artifacts/run/MS-49-001-understanding.md`
- [ ] `.ai/artifacts/run/MS-49-002-planning.md`
- [ ] `.ai/artifacts/run/MS-49-003-implementation.md`
- [ ] `.ai/artifacts/run/MS-49-004-testing.md`
- [ ] `.ai/artifacts/run/MS-49-005-review.md`
- [ ] `.ai/artifacts/run/MS-49-006-commit.md`
- [ ] `.ai/artifacts/run/MS-49-007-pr.md`
- [ ] PR created on GitHub
- [ ] All changes committed and pushed

---

**Version:** 4.0 (7 stages, build removed)  
**Last updated:** 2026-04-20  
**Status:** READY FOR EXECUTION
