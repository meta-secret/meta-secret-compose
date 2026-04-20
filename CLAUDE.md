# CLAUDE.md — meta-secret-compose

> Entry point for Claude Code.
> Automated 9-stage workflow: GitHub Issue → PR

---

## 🚀 Quick Start

```bash
/run issue 123                # Full pipeline
/run issue 123 --from stage-6 # Resume from stage 6
/run issue "my custom task"   # Use text instead of issue
```

---

## 📋 What It Does

Executes complete workflow automatically:

1. **Understanding** — Read issue/prompt
2. **Planning** — Create implementation plan
3. **Implementation** — Write code
4. **Testing** — Write tests
5. **Build** — Compile (auto-retry on failure)
6. **Test Run** — Execute tests (auto-retry on failure)
7. **Code Review** — Review changes (auto-retry on failure)
8. **Commit** — Git commit + push
9. **PR** — Create pull request

**All artifacts:** `.ai/artifacts/run/MS-<timestamp>-<stage>-<name>.md`

---

## 📖 Read First

1. **AGENTS.md** — Overview of workflow
2. **.ai/WORKFLOW.md** — Orchestration details
3. **.ai/PIPELINE.md** — Stage-by-stage specifications
4. **.ai/rules/kmp-principles.md** — Architecture rules

---

## 🔗 Where Things Live

| What | Where |
|------|-------|
| Workflow orchestration | `.ai/WORKFLOW.md` |
| Stage specifications | `.ai/PIPELINE.md` |
| Claude Code execution | `.claude/ORCHESTRATE.md` |
| Agents (10 total) | `.ai/agents/<name>.md` |
| Architecture rules | `.ai/rules/` |
| Skills (helpers) | `.ai/skills/*/SKILL.md` |
| Generated artifacts | `.ai/artifacts/run/` |

---

## 🎨 Terminal Output

Each stage prints colored ANSI output:

```
🟢 Starting Stage 3: Implementation
🟡 Implementing... (in progress)
✅ Stage 3 completed successfully
```

**On failure:**
```
❌ Stage 5: Build failed
Reason: Compilation error in xyz.kt
Next: run issue 123 --from stage-2 (to replan)
```

---

## 🔄 Auto-Retry Logic

| Stage | Fails? | Retry? | Max |
|-------|--------|--------|-----|
| Build (5) | ✓ | Debug/RCA → Replan | 2 |
| Test Run (6) | ✓ | Replan | 2 |
| Code Review (7) | ✓ | Replan | 2 |

**Other stages:** No retry (stop on failure)

---

## ⚡ Advanced

### Resume From Specific Stage

If stage 6 fails:
```bash
/run issue 123 --from stage-2
```

Skips stages 1-5 (already done).  
All prior artifacts read automatically.

### Check Artifacts

```bash
cat .ai/artifacts/run/MS-<timestamp>-001-understanding.md
cat .ai/artifacts/run/MS-<timestamp>-005-build.md
```

### Debug Failed Build

```bash
cat .ai/artifacts/run/MS-<timestamp>-005-build-rca-retry-1.md
```

RCA analysis explains root cause.

---

## 🎯 Project Context

- **Type:** KMM (Kotlin Multiplatform Mobile)
- **Platforms:** iOS (SwiftUI) + Android (Jetpack Compose)
- **Architecture:** MVVM + Coordinator
- **Build:** Gradle + KMM plugin
- **FFI:** UniFFI for Rust integration

---

## ✅ Before Running

- [ ] You're on `main` branch
- [ ] Working tree is clean
- [ ] GitHub issue exists (or use text)
- [ ] `gh` CLI is available (for fetching issues)

---

## 📚 More Info

See **AGENTS.md** for complete system overview.

---

**Status:** Production-ready  
**Last updated:** 2026-04-20
