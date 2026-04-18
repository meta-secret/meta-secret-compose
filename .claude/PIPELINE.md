# Claude Code — Sub-Agent Pipeline

> Claude Code-specific execution strategy.
> Each stage runs as an isolated Task (fresh context window).
> Brain: `.ai/ORCHESTRATOR.md` · Stage docs: `.ai/agents/<name>.md`

---

## Why Sub-Agents

Claude Code runs stages via the `Task` tool — each Task spawns a sub-agent
with a clean context window. This eliminates context exhaustion on long pipelines.
The pipeline state lives entirely in artifacts on disk.

---

## How To Run

When the user says `run issue <id>` or `run issue <id> --from <stage>`:

1. Read `.ai/ORCHESTRATOR.md` for the full stage list and failure rules
2. Create artifact dir: `mkdir -p .ai/artifacts/runs/MS-<id>/`
3. Spawn each stage as a `Task` (see table below)
4. After each Task returns: check artifact for failure markers
5. On failure: stop and print resume command

---

## Stage → Task Mapping

For each stage, spawn a Task with the following prompt:

| Stage | Task prompt |
|-------|-------------|
| issue-reader | `"Read .ai/agents/issue-reader.md and execute it for issue MS-<id>. Write output to .ai/artifacts/runs/MS-<id>/issue-analysis.md"` |
| planner | `"Read .ai/agents/planner.md and execute it for issue MS-<id>. Read .ai/artifacts/runs/MS-<id>/issue-analysis.md first. Write output to .ai/artifacts/runs/MS-<id>/implementation-plan.md"` |
| implementer | `"Read .ai/agents/implementer.md and execute it for issue MS-<id>. Read .ai/artifacts/runs/MS-<id>/implementation-plan.md first. Write output to .ai/artifacts/runs/MS-<id>/implementer.md"` |
| kmp-reviewer | `"Read .ai/agents/kmp-reviewer.md and execute it for issue MS-<id>. Write output to .ai/artifacts/runs/MS-<id>/kmp-review.md"` |
| tester | `"Read .ai/agents/tester.md and execute it for issue MS-<id>. Read .ai/artifacts/runs/MS-<id>/implementer.md first. Write output to .ai/artifacts/runs/MS-<id>/tester.md"` |
| test-runner | `"Run: ./gradlew :composeApp:testDebugUnitTest --no-daemon --console=plain 2>&1 \| tee .ai/artifacts/runs/MS-<id>/test-runner.log — then write .ai/artifacts/runs/MS-<id>/test-report.md"` |
| reviewer | `"Read .ai/agents/reviewer.md and execute it for issue MS-<id>. Write output to .ai/artifacts/runs/MS-<id>/code-review.md"` |
| builder | `"Run: ./gradlew :composeApp:assembleDebug --no-daemon --console=plain 2>&1 \| tee .ai/artifacts/runs/MS-<id>/builder.log — then write .ai/artifacts/runs/MS-<id>/build-report.md"` |
| ios-tester | `"Read .ai/agents/ios-tester.md and execute it for issue MS-<id>. Write output to .ai/artifacts/runs/MS-<id>/ios-test-report.md"` |
| committer | `"Read .ai/agents/committer.md and execute it for issue MS-<id>. Write output to .ai/artifacts/runs/MS-<id>/committer.md"` |
| pr-author | `"Read .ai/agents/pr-author.md and execute it for issue MS-<id>. Read .ai/artifacts/runs/MS-<id>/committer.md first. Write output to .ai/artifacts/runs/MS-<id>/pr-author.md"` |

---

## Failure Check (after each Task)

Read the stage artifact. Stop if it contains any of:
```
Return to Planning: YES
Status: FAILED
**FAIL**
FAIL ❌
```

On failure:
```
⛔ Pipeline stopped at: <stage>
Cause: <one line from artifact>
Resume: run issue <id> --from <stage>
```

---

## Retry Loops (via sub-agents)

**reviewer retry (max 3):**
If `code-review.md` contains FAIL → spawn implementer Task (fix-pass) → spawn test-runner Task → spawn reviewer Task. Repeat up to 3×.

**builder retry (max 3):**
If `build-report.md` contains FAIL → spawn build-fixer Task → spawn builder Task. Repeat up to 3×.

---

## Conditional Stages

**kmp-reviewer** — always run, but flags KMP-specific issues (safe area, MVVM, FFI).

**ios-tester** — run before final commit to ensure iOS app builds and runs.

---

## --from <stage> Resume

When `--from <stage>` is given: skip all stages before `<stage>`.
All prior artifacts are already on disk — each Task reads them as needed.

---

## Mirrors

This file is the Claude Code equivalent of `.codex/EXEC.md`.
Stage logic lives in `.ai/agents/` — never duplicate it here.
