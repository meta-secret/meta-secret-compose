# Pipeline — Detailed Stage Specifications

> Reference document for all 9 stages of automated workflow.
> **Do not duplicate** this content in `.claude/`, `.cursor/`, `.codex/` — link to this file instead.

---

## 📋 Stage 1: Understanding

**Purpose:** Understand the task, problem, and acceptance criteria.

**Agent:** `github-issue-coordinator`

**Input:**
- GitHub issue number (e.g., `123`)
- OR custom text description (use timestamp)

**Actions (Agent Responsibilities):**
1. Fetch issue using `gh issue view <id>` (if numeric ID)
2. Extract: title, description, acceptance criteria
3. Identify: goal, requirements, constraints
4. List: affected areas, dependencies
5. Note: any assumptions or missing information

**Output Artifact:**
- File: `.ai/artifacts/run/MS-<id>-001-understanding.md`
- Format: markdown with sections
- Sections:
  - Problem statement
  - Goal / what success looks like
  - Requirements (numbered)
  - Assumptions
  - Constraints / dependencies
  - Affected areas in codebase
  - Missing information (if any)

**Failure Markers** (stop pipeline if found):
```
Status: FAILED
**FAIL**
❌
Return to Planning: YES
```

**No failure = continue to Stage 2**

---

## 📋 Stage 2: Planning

**Purpose:** Create detailed implementation plan.

**Agent:** `feature-planner`

**Input:**
- Artifact from Stage 1: `.ai/artifacts/run/MS-<id>-001-understanding.md`

**Agent reads:**
- `.ai/rules/kmp-principles.md` (architecture)
- `.ai/rules/` (all rules)
- `PROJECT_CONTEXT.md`, `ARCHITECTURE.md`, `SECURITY.md` (project docs)

**Actions (Agent Responsibilities):**
1. Analyze understanding artifact
2. Review architecture rules
3. Create step-by-step plan
4. List files to modify (with relative paths)
5. Identify tech stack alignment
6. Note any deferred items
7. Estimate risks

**Output Artifact:**
- File: `.ai/artifacts/run/MS-<id>-002-planning.md`
- Format: markdown with numbered steps
- Sections:
  - **Goal** — success criteria from Stage 1
  - **Context** — assumptions, constraints
  - **Scope** — numbered list of implementation steps
    - Each step: file to modify + description
    - Example: `1. Update ViewModel to add state` 
    - Example: `2. Create UI Composable in androidMain/`
  - **Tech stack alignment** — KMM, MVVM, FFI boundaries
  - **Deferred items** — what's out of scope
  - **Risks** — potential issues

**Critical Rules:**
- **Do not write code** — plan only
- **List files by path** — relative to repo root
- **Stay within KMM bounds** — no Rust changes
- **Respect FFI boundary** — crypto stays in FFI layer

**No failure = continue to Stage 3**

---

## 📋 Stage 3: Implementation

**Purpose:** Write code changes per plan.

**Agent:** `code-implementer`

**Input:**
- Artifact from Stage 2: `.ai/artifacts/run/MS-<id>-002-planning.md`

**Agent reads:**
- Plan (to know exactly what to implement)
- `.ai/rules/kmp-principles.md` (arch rules)
- Relevant source files (to match patterns)

**Actions (Agent Responsibilities):**
1. Implement each step from plan in order
2. Follow existing patterns (MVVM, DI, ViewModels)
3. Keep changes minimal and scoped
4. Update only files listed in plan
5. Don't refactor beyond plan scope
6. Run quick validation (no full build)

**Output Artifact:**
- File: `.ai/artifacts/run/MS-<id>-003-implementation.md`
- Format: markdown with summary
- Sections:
  - **Summary** — what was implemented
  - **Files modified** — list with brief description
  - **Deviations** — any changes from plan (with reason)
  - **Next steps** — ready for testing

**Critical Rules:**
- **Implement only plan** — don't add extras
- **No FFI modifications** — don't touch Rust FFI
- **No build-breaking changes** — avoid obvious errors
- **Minimal diffs** — keep changes focused

**No failure = continue to Stage 4**

---

## 📋 Stage 4: Test Writing

**Purpose:** Write tests for changes.

**Agent:** `test-author`

**Input:**
- Artifact from Stage 3: `.ai/artifacts/run/MS-<id>-003-implementation.md`
- Look at actual code changes

**Agent reads:**
- Implementation changes
- Existing test patterns in repo
- Test framework setup (Kotlin test, Compose test, XCTest)

**Actions (Agent Responsibilities):**
1. Write unit tests for new functions
2. Write integration tests if needed
3. Cover edge cases
4. Update existing tests if needed
5. Follow existing test patterns

**Output Artifact:**
- File: `.ai/artifacts/run/MS-<id>-004-testing.md`
- Format: markdown with summary
- Sections:
  - **Summary** — tests written
  - **Test files** — list of files created/modified
  - **Coverage** — what's tested
  - **Edge cases** — what scenarios covered

**Critical Rules:**
- **Write real tests** — not just placeholder comments
- **Match existing patterns** — use repo test style
- **Test edge cases** — not just happy path
- **No logic in tests** — keep tests simple and clear

**No failure = continue to Stage 5**

---

## 📋 Stage 5: Build

**Purpose:** Compile project and verify no build errors.

**Command:** `./gradlew build -x test`

**Why `-x test`:**
- We run full test suite separately in Stage 6
- Build just verifies compilation

**Actions:**
1. Run gradle build command
2. Capture all output (stdout + stderr)
3. Check for compilation errors

**Output Artifact:**
- File: `.ai/artifacts/run/MS-<id>-005-build.md`
- Format: markdown report
- Sections:
  - **Command** — exact command run
  - **Result** — SUCCESS or FAILED
  - **Duration** — how long build took
  - **Notes** — any warnings or issues

**Failure Handling (AUTOMATIC RETRY):**

```
If build fails (contains errors):

1. Stop build processing
2. Spawn Debug/RCA Task
   - Agent: debug-rca
   - Input: build error output
   - Output: MS-<id>-005-build-rca-retry-1.md
   - Task: analyze root cause, suggest fixes

3. Go back to Stage 2 (Replan)
   - Feature planner reviews Stage 1 + RCA insights
   - Adjust plan based on RCA
   - New plan: MS-<id>-002-planning-retry-1.md

4. Continue with:
   - Stage 3: Re-implement (MS-<id>-003-implementation-retry-1.md)
   - Stage 4: Re-test if needed (MS-<id>-004-testing-retry-1.md)
   - Stage 5: Retry build (MS-<id>-005-build-retry-1.md)

5. If STILL fails:
   - RCA again (2nd time): MS-<id>-005-build-rca-retry-2.md
   - Replan (2nd time): MS-<id>-002-planning-retry-2.md
   - Re-implement, re-test, retry build

6. If STILL fails after 2 retries:
   ⛔ STOP — return control to user
   Display: Artifact with error summary + suggest manual debugging
```

---

## 📋 Stage 5a: Debug/RCA (If Build Fails)

**Purpose:** Analyze build failure root cause.

**Agent:** `debug-rca`

**Input:**
- Build error output from Stage 5
- Implementation artifacts from Stage 3

**Actions:**
1. Parse error message
2. Identify root cause
3. Suggest specific fixes
4. Categorize error type

**Output Artifact:**
- File: `.ai/artifacts/run/MS-<id>-005-build-rca-retry-N.md`
- Format: markdown with analysis
- Sections:
  - **Error summary** — what failed
  - **Root cause** — why it failed
  - **Suggested fix** — specific solution
  - **Type** — compilation/linking/gradle error

---

## 📋 Stage 6: Test Run

**Purpose:** Execute test suite and verify all tests pass.

**Agent:** `test-verifier`

**Input:**
- Build completed (Stage 5)
- Tests written (Stage 4)

**Agent command:**
```bash
./gradlew testDebugUnitTest --no-daemon
```

(Or project-specific test command)

**Actions:**
1. Execute test suite
2. Capture results (passed/failed count)
3. List any failed tests

**Output Artifact:**
- File: `.ai/artifacts/run/MS-<id>-006-test-run.md`
- Format: markdown report
- Sections:
  - **Command** — exact test command
  - **Result** — PASSED or FAILED
  - **Summary** — X passed, Y failed
  - **Failed tests** — list if any
  - **Duration** — how long tests took

**Failure Handling (AUTOMATIC RETRY):**

```
If tests fail:

1. Stop test processing
2. Go back to Stage 2 (Replan)
   - Feature planner reviews failures
   - New plan accounts for test insights
   - New artifact: MS-<id>-002-planning-retry-1.md

3. Continue with:
   - Stage 3: Re-implement (MS-<id>-003-implementation-retry-1.md)
   - Stage 5: Retry build (MS-<id>-005-build-retry-1.md)
   - Stage 6: Retry tests (MS-<id>-006-test-run-retry-1.md)

4. If STILL fails after 2 retries:
   ⛔ STOP — return control to user
   Display: Which tests fail + suggest debugging
```

---

## 📋 Stage 7: Code Review

**Purpose:** Review code for architecture, style, security.

**Agent:** `code-reviewer`

**Input:**
- All changes from Stage 3
- Project rules (`.ai/rules/`)

**Agent reads:**
- `.ai/rules/kmp-principles.md` — architecture
- `.ai/rules/` — all rules
- `ARCHITECTURE.md`, `SECURITY.md` — project docs

**Actions:**
1. Review architecture (MVVM, FFI boundary)
2. Review code style (Kotlin/Swift conventions)
3. Check security (no secrets, proper error handling)
4. Identify dead code or issues
5. Suggest improvements

**Output Artifact:**
- File: `.ai/artifacts/run/MS-<id>-007-review.md`
- Format: markdown report
- Sections:
  - **Summary** — what was reviewed
  - **Must-fix** — violations of rules
  - **Should-fix** — style/clarity issues
  - **Nice-to-have** — optional improvements
  - **Status** — PASSED or FAILED

**Failure Handling (AUTOMATIC RETRY):**

```
If review has FAIL markers:

1. Stop review processing
2. Go back to Stage 2 (Replan)
   - Feature planner reviews feedback
   - New plan addresses issues
   - New artifact: MS-<id>-002-planning-retry-1.md

3. Continue with:
   - Stage 3: Re-implement (MS-<id>-003-implementation-retry-1.md)
   - Stage 5: Retry build (MS-<id>-005-build-retry-1.md)
   - Stage 6: Retry tests (MS-<id>-006-test-run-retry-1.md)
   - Stage 7: Retry review (MS-<id>-007-review-retry-1.md)

4. If STILL fails after 2 retries:
   ⛔ STOP — return control to user
   Display: Review feedback + suggest manual review
```

---

## 📋 Stage 8: Commit

**Purpose:** Create git branch and commit changes.

**Agent:** `release-manager`

**Input:**
- All stages 1-7 passed
- All changes ready to commit

**Actions:**
1. Fetch latest from `main` branch
2. Create branch: `{Prefix}/kuklin/MS-{id}`
   - Prefix extracted from issue title: Task/Feature/Bug
   - Example: `Feature/kuklin/MS-123`
3. Stage all changes from diff
4. Create commit with message
5. Push to remote
6. Print commit SHA + branch name

**Output Artifact:**
- File: `.ai/artifacts/run/MS-<id>-008-commit.md`
- Format: markdown report
- Sections:
  - **Branch** — branch name created
  - **Commit** — commit SHA
  - **Message** — commit message
  - **Push status** — SUCCESS or notes
  - **Next** — ready for PR

**Commit Message Format:**
```
[Issue #123] Brief summary of changes

- Point 1
- Point 2

(auto-generated from stage summaries)
```

**No retries** — if fails, display error and ask user

---

## 📋 Stage 9: Create PR

**Purpose:** Create pull request targeting `main`.

**Agent:** `release-manager`

**Input:**
- Branch created and pushed (Stage 8)

**Actions:**
1. Use `gh pr create` (GitHub)
2. Target branch: `main`
3. PR title: from commit message
4. PR body: summary of changes
5. Print PR URL + number

**Output Artifact:**
- File: `.ai/artifacts/run/MS-<id>-009-pr.md`
- Format: markdown report
- Sections:
  - **PR number** — e.g., #456
  - **PR URL** — full GitHub link
  - **Status** — created
  - **Target** — main branch

**No retries** — if fails, display error and ask user

---

## 🔄 Retry Summary

| Stage | Failure? | Retry? | Max Retries | Goes Back To |
|-------|----------|--------|-------------|--------------|
| 1 | ✗ | — | — | N/A |
| 2 | ✗ | — | — | N/A |
| 3 | ✗ | — | — | N/A |
| 4 | ✗ | — | — | N/A |
| 5 Build | ✓ | ✓ Debug/RCA | 2 | Stage 2 |
| 6 Tests | ✓ | ✓ Replan | 2 | Stage 2 |
| 7 Review | ✓ | ✓ Replan | 2 | Stage 2 |
| 8 | ✗ | — | — | N/A |
| 9 | ✗ | — | — | N/A |

---

## 📊 Artifact Naming Reference

```
MS-{timestamp}-{stage-number}-{stage-name}.md

Components:
- MS:        prefix (MetaSecret)
- timestamp: YYYYMMDDhhmmss (20260420143022)
- stage-number: 001-009 (zero-padded)
- stage-name: understanding, planning, implementation, etc.

Examples:
MS-20260420143022-001-understanding.md
MS-20260420143022-002-planning.md
MS-20260420143022-005-build.md
MS-20260420143022-005-build-rca-retry-1.md
MS-20260420143022-005-build-retry-1.md
MS-20260420143022-007-review-retry-2.md
```

---

## ✅ All Stages at a Glance

| Stage | Agent | Input | Output | Fails? | Retries? |
|-------|-------|-------|--------|--------|----------|
| 1 | github-issue-coordinator | issue ID | 001-understanding.md | ✗ | — |
| 2 | feature-planner | 001 | 002-planning.md | ✗ | — |
| 3 | code-implementer | 002 | 003-implementation.md | ✗ | — |
| 4 | test-author | 003 | 004-testing.md | ✗ | — |
| 5 | *(bash build)* | — | 005-build.md | ✓ | RCA → Replan |
| 6 | test-verifier | build OK | 006-test-run.md | ✓ | Replan |
| 7 | code-reviewer | changes | 007-review.md | ✓ | Replan |
| 8 | release-manager | changes | 008-commit.md | — | — |
| 9 | release-manager | branch | 009-pr.md | — | — |

---

**Status:** Complete reference  
**Last updated:** 2026-04-20
