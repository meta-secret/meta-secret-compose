# 🚀 Quick Start — 9-Stage Pipeline

---

## Claude Code

```bash
/run issue 49
```

**What happens:**
- 9 Tasks spawned in sequence
- Each task reads its stage definition from `.claude/ORCHESTRATE.md`
- Artifacts written to `.ai/artifacts/run/MS-49/`
- Auto-retry on build/test/review failure (max 2 times)

**Resume from stage 6:**
```bash
/run issue 49 --from stage-6
```

---

## Cursor

```
Cmd+K: run issue 49
```

**Then follow prompts in `.cursor/WORKFLOW.md`**

Each stage has explicit copy-paste instruction:
- Stage 1: Read `.ai/agents/github-issue-coordinator.md` + fetch issue
- Stage 2: Read `.ai/agents/feature-planner.md` + create plan
- ... (stages 3-9)

---

## Codex CLI

```bash
codex run issue 49
```

**What happens:**
- Reads `.codex/ORCHESTRATE.md` for exact steps
- Each stage has explicit command/prompt
- Follow the STAGE-BY-STAGE EXECUTION section

---

## 📂 Output

All artifacts in:
```
.ai/artifacts/run/
├── MS-49-001-understanding.md
├── MS-49-002-planning.md
├── MS-49-003-implementation.md
├── MS-49-004-testing.md
├── MS-49-005-build.md
├── MS-49-006-test-run.md
├── MS-49-007-review.md
├── MS-49-008-commit.md
└── MS-49-009-pr.md
```

---

## ⚠️ If Failed

**Build failed (Stage 5)?**
- Check: `.ai/artifacts/run/MS-49-005-build.md`
- RCA: `.ai/artifacts/run/MS-49-005-build-rca-retry-1.md`
- Re-run: `/run issue 49 --from stage-2` (replans, retries)

**Tests failed (Stage 6)?**
- Check: `.ai/artifacts/run/MS-49-006-test-run.md`
- Re-run: `/run issue 49 --from stage-2` (replans, retries)

**Review failed (Stage 7)?**
- Check: `.ai/artifacts/run/MS-49-007-review.md`
- Re-run: `/run issue 49 --from stage-2` (replans, retries)

---

## 🔍 Full Details

For complete stage definitions:
- **Claude Code:** `.claude/ORCHESTRATE.md`
- **Cursor:** `.cursor/WORKFLOW.md`
- **Codex CLI:** `.codex/ORCHESTRATE.md`

---

**Last updated:** 2026-04-20
