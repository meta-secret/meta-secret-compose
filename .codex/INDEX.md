# Codex CLI — Automated Workflow Entry Point

> Execute automated 9-stage workflow from CLI.
> Brain: `.ai/WORKFLOW.md` · Execution: `.codex/ORCHESTRATE.md`

---

## 🚀 Quick Start

### Run Full Workflow

```bash
codex run issue 123
```

→ Automatic execution (9 stages, auto-retry)  
→ All artifacts: `.ai/artifacts/run/MS-<id>/`

---

## 🎯 Commands

| Command | Effect |
|---------|--------|
| `codex run issue 123` | Full pipeline (stages 1-9) |
| `codex run issue 123 --from stage-6` | Resume from stage 6 |
| `codex run issue "my task"` | Custom task (timestamp-based) |

---

## 📚 Documentation

| Document | Purpose |
|----------|---------|
| **AGENTS.md** | Entry point overview (root) |
| **.ai/WORKFLOW.md** | Main orchestration |
| **.ai/PIPELINE.md** | Stage specifications |
| **.codex/ORCHESTRATE.md** | CLI execution details |
| **.ai/agents/** | Agent definitions |

---

## 🔄 Workflow Stages

```
1. Understanding (read issue)
2. Planning (create plan)
3. Implementation (write code)
4. Testing (write tests)
5. Build (compile)
   └─ Auto-retry on failure (max 2)
6. Test Run (execute tests)
   └─ Auto-retry on failure (max 2)
7. Code Review (review)
   └─ Auto-retry on failure (max 2)
8. Commit (git commit + push)
9. PR (create pull request)
```

---

## 📦 Artifacts

All output stored in:

```
.ai/artifacts/run/
├── MS-20260420143022-001-understanding.md
├── MS-20260420143022-002-planning.md
├── ...
└── MS-20260420143022-009-pr.md
```

---

**Status:** Ready to use  
**Last updated:** 2026-04-20

→ Read **AGENTS.md** for full overview
