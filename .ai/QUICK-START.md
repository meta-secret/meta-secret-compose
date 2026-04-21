# Quick Start

## Run full flow

- `run issue 123`
- `run issue "my custom task"`

## Resume from stage

- `run issue 123 --from stage-4`

## Source of truth

- `.ai/WORKFLOW.md`
- `.ai/PIPELINE.md`

## Stage list

1. Issue Intake (+ Figma optional)
2. Planning
3. Implementation (Logic + UI)
4. Build (no tests, 10 min timeout)
5. Code Review
6. Design Review (if Figma)
7. Test Authoring
8. Test Run
9. Branch + Commit + PR

## Artifacts

- `.ai/artifacts/run/MS-<run-id>-<stage>-<name>.md`

## Mandatory stage logs

- `Start stage <n>: <name>`
- `Stage <n>: <name> completed`

Last updated: 2026-04-22
