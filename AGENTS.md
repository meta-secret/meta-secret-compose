# AGENTS.md — meta-secret-compose

> Entry point for automated AI workflows across Claude Code, Cursor, and Codex CLI.
> Brain: `.ai/WORKFLOW.md` · Execution: agent-specific rules in `.ai/agents/`

---

## 🚀 Quick Start

### Run Full Workflow (Issue → PR)

```bash
run issue 123                  # Full pipeline from GitHub issue #123
run issue 123 --from stage-7   # Resume from specific stage
run issue "my custom task"     # Use timestamp instead of issue number
```

### Result

- All artifacts written to: `.ai/artifacts/run/MS-<timestamp>-<stage>-<name>.md`
- Automatic stage progression (no confirmation needed)
- On failure: debug → replan → retry (max 2 retries)
- Terminal output: ANSI colors + emojis for status

---

## 📋 Pipeline Stages (Automatic)

| # | Stage | Agent | Output | Notes |
|---|-------|-------|--------|-------|
| 1 | **Understanding** | github-issue-coordinator | `001-understanding.md` | Read issue/prompt |
| 2 | **Planning** | feature-planner | `002-planning.md` | Create implementation plan |
| 3 | **Implementation** | code-implementer | `003-implementation.md` | Write code changes |
| 4 | **Testing** | test-author | `004-testing.md` | Write test files |
| 5 | **Build** | *(bash)* | `005-build.md` | Compile project |
|   | └─ *Debug/RCA* | debug-rca | `005-build-rca-retry-1.md` | If build fails |
| 6 | **Test Run** | test-verifier | `006-test-run.md` | Execute tests |
|   | └─ *Retry (max 2)* | planner | `002-planning-retry-1.md` | If tests fail |
| 7 | **Code Review** | code-reviewer | `007-review.md` | Review changes |
|   | └─ *Retry (max 2)* | planner | `002-planning-retry-1.md` | If review fails |
| 8 | **Commit** | release-manager | `008-commit.md` | Create git commit |
| 9 | **PR** | release-manager | `009-pr.md` | Create pull request |

---

## 🔄 Retry Logic

### Build Failure (Stage 5)

```
Build fails → Debug/RCA agent analyzes → Replan → Implement fix → Build again
Max retries: 2 (then stop and ask user)
```

### Test Failure (Stage 6)

```
Tests fail → Replan → Implement fixes → Tests again
Max retries: 2 (then stop and ask user)
```

### Code Review Failure (Stage 7)

```
Review finds issues → Replan → Implement fixes → Review again
Max retries: 2 (then stop and ask user)
```

---

## 📂 Artifact Storage

All artifacts stored in one directory per run:

```
.ai/artifacts/run/
├── MS-20260420143022-001-understanding.md
├── MS-20260420143022-002-planning.md
├── MS-20260420143022-003-implementation.md
├── MS-20260420143022-004-testing.md
├── MS-20260420143022-005-build.md
├── MS-20260420143022-005-build-rca-retry-1.md
├── MS-20260420143022-005-build-retry-1.md
├── MS-20260420143022-006-test-run.md
├── MS-20260420143022-007-review.md
├── MS-20260420143022-008-commit.md
└── MS-20260420143022-009-pr.md
```

**Format:** `MS-<timestamp>-<stage-number>-<stage-name>.md`

---

## 🎯 How It Works (For Developers)

### In Claude Code

```bash
/run issue 123                 # Automatic execution
```

Invokes `.claude/ORCHESTRATE.md` which:
1. Parses issue ID or timestamp
2. Spawns each stage as isolated Task
3. Checks artifacts for failure markers
4. Handles retries with colored output
5. Returns final status

### In Cursor

```
Cmd+K: run issue 123
```

Cursor reads `.cursor/WORKFLOW.md` which redirects to `.ai/WORKFLOW.md`

### In Codex CLI

```bash
codex run issue 123            # Automatic execution
```

Invokes `.codex/ORCHESTRATE.md` for CLI-specific execution

---

## 📝 Agent Responsibilities

### Understanding Stage (1)

**Agent:** `github-issue-coordinator`

- Fetch issue from GitHub using `gh issue view <id>`
- Extract: title, description, acceptance criteria
- Write: `.ai/artifacts/run/MS-<id>-001-understanding.md`

**Artifact contains:**
- Problem statement
- Goals
- Requirements
- Assumptions
- Affected areas

### Planning Stage (2)

**Agent:** `feature-planner`

- Read understanding artifact
- Create detailed implementation plan
- Write: `.ai/artifacts/run/MS-<id>-002-planning.md`

**Artifact contains:**
- Scope (numbered steps)
- Tech stack alignment
- Deferred items

### Implementation Stage (3)

**Agent:** `code-implementer`

- Read plan artifact
- Implement changes per plan
- Write: `.ai/artifacts/run/MS-<id>-003-implementation.md`

**Artifact contains:**
- Summary of changes
- Files modified (list)
- Any deviations from plan

### Testing Stage (4)

**Agent:** `test-author`

- Read implementation artifact
- Write appropriate tests
- Write: `.ai/artifacts/run/MS-<id>-004-testing.md`

**Artifact contains:**
- Tests written (list)
- Coverage summary
- Edge cases tested

### Build Stage (5)

**Command:** `./gradlew build -x test`

- Compile project
- Write: `.ai/artifacts/run/MS-<id>-005-build.md`

**On failure:**
- Run Debug/RCA agent (see 5a)
- Go back to Planning stage (stage 2)
- Max 2 retries

### Build Debug/RCA (5a)

**Agent:** `debug-rca`

- Analyze build error
- Suggest fixes
- Write: `.ai/artifacts/run/MS-<id>-005-build-rca-retry-1.md`

### Test Run Stage (6)

**Agent:** `test-verifier`

- Execute tests
- Write: `.ai/artifacts/run/MS-<id>-006-test-run.md`

**On failure:**
- Go back to Planning (stage 2)
- Max 2 retries

### Code Review Stage (7)

**Agent:** `code-reviewer`

- Review all changes
- Check architecture/style
- Write: `.ai/artifacts/run/MS-<id>-007-review.md`

**On failure:**
- Go back to Planning (stage 2)
- Max 2 retries

### Commit Stage (8)

**Agent:** `release-manager`

- Create branch: `{Prefix}/kuklin/MS-{id}` (see below)
- Stage files
- Create commit
- Push to remote
- Write: `.ai/artifacts/run/MS-<id>-008-commit.md`

**Branch naming convention:**
```
{Prefix}/kuklin/MS-{issueNumber}
```
- `Prefix` from issue title: `[Task]`, `[Feature]`, or `[Bug]`
- Example: `Feature/kuklin/MS-123`

### PR Stage (9)

**Agent:** `release-manager`

- Create pull request
- Target: `main`
- Write: `.ai/artifacts/run/MS-<id>-009-pr.md`

---

## ⚠️ Execution Rules

**For all stages:**

1. Read agent definition in `.ai/agents/<agent-name>.md` fully
2. Follow all "Mandatory First Actions" in agent doc
3. Write artifact to `.ai/artifacts/run/MS-<id>/`
4. Check artifact for failure markers:
   ```
   Status: FAILED
   Return to Planning: YES
   **FAIL**
   ❌
   ```
5. Stop pipeline if any marker found

**Branch protection rules:**
- Never force-push to `main`
- Always create feature branch before commit
- Push current branch to remote
- Create PR (auto if on GitHub with `gh`)

**Security:**
- No secrets in commit messages
- No API keys in code
- No team ID changes

---

## 🔧 Resume From Stage

```bash
run issue 123 --from stage-6   # Skip to Test Run stage
```

All prior artifacts already on disk. Each stage reads its inputs from previous artifacts.

---

## 🎨 IDE-Specific Entry Points

| IDE | File | How to run |
|-----|------|-----------|
| **Claude Code** | `.claude/ORCHESTRATE.md` | `/run issue 123` |
| **Cursor** | `.cursor/WORKFLOW.md` | `Cmd+K: run issue 123` |
| **Codex CLI** | `.codex/ORCHESTRATE.md` | `codex run issue 123` |

All three redirect to `.ai/WORKFLOW.md` for single source of truth.

---

## 📚 Documentation Map

| Document | Purpose |
|----------|---------|
| **AGENTS.md** | This file (entry point) |
| **.ai/WORKFLOW.md** | Brain (orchestration logic) |
| **.ai/PIPELINE.md** | Detailed stage definitions |
| **.claude/ORCHESTRATE.md** | Claude Code execution |
| **.cursor/WORKFLOW.md** | Cursor rules |
| **.codex/ORCHESTRATE.md** | Codex CLI execution |
| **.ai/agents/<name>.md** | Individual agent definitions |
| **.ai/rules/kmp-principles.md** | Architecture guidelines |

---

## ✅ Status

- **Last updated:** 2026-04-20
- **Pipeline stages:** 9 (with 2-level retry)
- **Agent support:** 10 agents + bash build
- **IDE coverage:** Claude Code + Cursor + Codex CLI
- **Status:** Ready for implementation

🚀 **Next:** Read `.ai/WORKFLOW.md` for complete orchestration details
