# AGENTS.md — meta-secret-compose

> Entry point for Codex (multi-agent mode).
> Brain: `.ai/` · Execution: `.codex/EXEC.md`

---

## Quick Start

```
run issue <id>              # полный pipeline: issue → PR
run issue <id> --from <stage>  # resume с конкретной стадии
run fix "<description>"     # bug fix pipeline
```

## Command Interpretation

Если получена команда `run ...` — это project pipeline command. Не задавай вопросов, выполняй.

### Полный pipeline

`run issue <id>` — выполни полный pipeline из `.ai/ORCHESTRATOR.md`. Прочитай `.codex/EXEC.md` для механизма запуска суб-агентов.

### Отдельные стадии (для суб-агентов)

Когда суб-агент получает `run <stage> <id>`:
1. Прочитай `.ai/agents/<stage>.md` полностью
2. Выполни все Mandatory First Actions
3. Выполни все обязанности агента
4. Запиши артефакт в `.ai/artifacts/runs/MS-<id>/`
5. Завершись

| Команда | Агент | Артефакт |
|---------|-------|---------|
| `run issue-reader <id>` | `.ai/agents/issue-reader.md` | `issue-analysis.md` |
| `run planner` | `.ai/agents/planner.md` | `implementation-plan.md` |
| `run implementer` | `.ai/agents/implementer.md` | *(code changes)* |
| `run kmp-reviewer <id>` | `.ai/agents/kmp-reviewer.md` | `kmp-review-report.md` |
| `run tester` | `.ai/agents/tester.md` | *(test files)* |
| `run reviewer` | `.ai/agents/reviewer.md` | `review-report.md` |
| `run committer <id>` | `.ai/agents/committer.md` | *(branch + commit + push)* |
| `run pr-author <id>` | `.ai/agents/pr-author.md` | *(PR)* |
| `run build-fixer <id>` | `.ai/agents/build-fixer.md` | *(build fix)* |
| `run ios-tester <id>` | `.ai/agents/ios-tester.md` | `ios-test-report.md` |

## Orient

| What | Where |
|------|-------|
| Pipeline logic | `.ai/ORCHESTRATOR.md` |
| Sub-agent invocation (Codex) | `.codex/EXEC.md` |
| System map | `.ai/INDEX.md` |
| Agent roles | `.ai/agents/<name>.md` |
| Project rules | `.ai/rules/` |
| Artifacts | `.ai/artifacts/runs/MS-<id>/` |

## First Read

1. `.ai/rules/kmp-principles.md`
2. `.ai/ORCHESTRATOR.md`
3. `.codex/EXEC.md` — механизм запуска суб-агентов (Codex only)

## Execution Rules

- Всегда читай agent doc перед выполнением стадии
- Не запускай `implementer` без passed `implementation-plan.md`
- Не запускай `committer` без passing KMP review + build
- Все артефакты → `.ai/artifacts/runs/MS-<id>/`
- Не изобретай требования, не расширяй scope
- iOS device тестирование обязательно перед release
