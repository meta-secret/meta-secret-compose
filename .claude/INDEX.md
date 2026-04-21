# Claude Code Entry

Use `/run issue ...`.

Single source of truth:
- `.ai/WORKFLOW.md`
- `.ai/PIPELINE.md`

Stage order:
1. Issue Intake (+ optional Figma)
2. Planning
3. Implementation (Logic + UI)
4. Build (no tests, max 10 min)
5. Code Review
6. Design Review (if Figma)
7. Test Authoring
8. Test Run
9. Branch + Commit + PR

Required stage logs:
- `Start stage <n>: <name>`
- `Stage <n>: <name> completed`

Artifacts:
- `.ai/artifacts/run/MS-<run-id>-<stage>-<name>.md`

Entrypoint executor:
- `.claude/ORCHESTRATE.md`
