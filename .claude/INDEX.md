# Claude Code — Automated Workflow Entry Point

> Execute automated 9-stage workflow: Issue → PR
> Brain: `.ai/WORKFLOW.md` · Execution: `.claude/ORCHESTRATE.md`

---

## 🚀 Quick Start

### Run Full Workflow

```bash
/run issue 123
```

→ Automatic execution (9 stages, auto-retry, ANSI output)  
→ All artifacts: `.ai/artifacts/run/MS-<id>/`

---

## 🎯 What Happens

```
Stage 1: Understanding (read issue)
  ↓
Stage 2: Planning (create plan)
  ↓
Stage 3: Implementation (write code)
  ↓
Stage 4: Testing (write tests)
  ↓
Stage 5: Build (compile)
  │ ❌ FAIL? → Debug/RCA → Replan (max 2 times)
  ↓
Stage 6: Test Run (execute tests)
  │ ❌ FAIL? → Replan (max 2 times)
  ↓
Stage 7: Code Review (review changes)
  │ ❌ FAIL? → Replan (max 2 times)
  ↓
Stage 8: Commit (git commit + push)
  ↓
Stage 9: PR (create pull request)
  ↓
✅ Done
```

---

## 📚 Documentation

| Document | Purpose |
|----------|---------|
| **AGENTS.md** | Entry point overview (root level) |
| **.ai/WORKFLOW.md** | Main orchestration brain |
| **.ai/PIPELINE.md** | Detailed stage specifications |
| **.claude/ORCHESTRATE.md** | Claude Code execution details |
| **.ai/agents/** | Individual agent definitions |
| **.ai/rules/** | Architecture & style rules |

---

## 🔧 Commands

| Command | Effect |
|---------|--------|
| `/run issue 123` | Full pipeline (stages 1-9) |
| `/run issue 123 --from stage-6` | Resume from stage 6 |
| `/run issue "my task"` | Custom task (timestamp-based) |

---

## ⚙️ How It Works (For Developers)

1. **Parse input** — issue ID or custom text
2. **Create artifacts dir** — `.ai/artifacts/run/`
3. **For each stage:**
   - Spawn isolated Task (fresh context)
   - Task reads `.ai/WORKFLOW.md` for stage definition
   - Task runs agent from `.ai/agents/`
   - Check artifact for failure markers
   - Handle retries if needed
4. **All artifacts** — stored in `.ai/artifacts/run/MS-<id>/`

---

## ✅ Checklist Before Running

- [ ] Repository is clean (`git status`)
- [ ] You're on `main` branch
- [ ] GitHub issue number exists (or use text description)
- [ ] All agents in `.ai/agents/` are available

---

**Status:** Ready to use  
**Last updated:** 2026-04-20

→ Read **AGENTS.md** for full overview
