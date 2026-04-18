# Codex — Sub-Agent Execution Strategy

> Codex-specific execution strategy.
> Each stage runs as an isolated sub-agent (fresh context).
> Brain: `.ai/ORCHESTRATOR.md` · Stage docs: `.ai/agents/<name>.md`

---

## How To Run

When CLI receives `run issue <id>` or `run issue <id> --from <stage>`:

1. Read `.ai/ORCHESTRATOR.md` for full stage list
2. Create artifact dir: `mkdir -p .ai/artifacts/runs/MS-<id>/`
3. For each stage (in order):
   - Spawn sub-agent: `codex --agent <stage> --context "MS-<id>"`
   - Wait for completion
   - Check artifact for failure markers
   - On failure: stop and print resume command

---

## Sub-Agent Invocation

```bash
codex --agent issue-reader --context "MS-<id>"
codex --agent planner --context "MS-<id>"
codex --agent implementer --context "MS-<id>"
# ... etc
```

Each sub-agent:
1. Reads `.ai/agents/<stage>.md`
2. Executes all Mandatory First Actions
3. Reads prior artifacts as needed (.ai/artifacts/runs/MS-<id>/)
4. Writes output to `.ai/artifacts/runs/MS-<id>/<output-file>`
5. Returns with status

---

## Stage Sequence

| # | Stage | Output | Reads |
|---|-------|--------|-------|
| 1 | issue-reader | issue-analysis.md | issue from GitLab |
| 2 | planner | implementation-plan.md | issue-analysis.md |
| 3 | implementer | *(code)* + implementer.md | implementation-plan.md |
| 4 | kmp-reviewer | kmp-review.md | code diffs |
| 5 | tester | *(tests)* + tester.md | implementer.md |
| 6 | test-runner | test-report.md | *(run tests)* |
| 7 | reviewer | code-review.md | code diffs |
| 8 | builder | build-report.md | *(run build)* |
| 9 | ios-tester | ios-test-report.md | *(run iOS)* |
| 10 | committer | committer.md | code + reviews |
| 11 | pr-author | *(PR)* + pr-author.md | committer.md |

---

## Failure Markers

Stop if artifact contains:
- `Status: FAILED`
- `FAIL ❌`
- `Return to Planning: YES`
- `KMP correctness: FAILED`

Print:
```
⛔ Pipeline stopped at: <stage>
Cause: <reason from artifact>
Resume: run issue <id> --from <stage>
```

---

## Retry Logic (in sub-agents)

**reviewer retry (max 3):**
If code-review.md fails → spawn implementer → spawn tester → spawn test-runner → spawn reviewer. Repeat up to 3×.

**builder retry (max 3):**
If build-report fails → spawn build-fixer → spawn builder. Repeat up to 3×.

---

## Context Passing

Each sub-agent receives issue ID via `--context MS-<id>` parameter.
All artifacts are read/written to `.ai/artifacts/runs/MS-<id>/`.

---

## Resume (--from)

```bash
run issue MS-<id> --from <stage>
```

CLI skips stages before `<stage>`.
Prior artifacts already on disk — sub-agents read as needed.
