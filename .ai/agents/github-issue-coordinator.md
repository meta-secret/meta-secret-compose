---
name: github-issue-coordinator
description: Reads GitHub issue or free-text task and produces Stage 1 issue analysis artifact with optional Figma context.
model: inherit
permissionMode: plan
---

# GitHub issue coordinator

Stage: 1 (Issue Intake + Optional Figma Context)

## Inputs

- Issue number or issue URL, or free-text task.
- Repository from git remote (fallback: `meta-secret/meta-secret-compose`).

## Mandatory actions

1. Print: `Start stage 1: Issue Intake + Optional Figma Context`
2. If issue input:
   - load issue via `gh issue view <id-or-url> --json title,body,number,labels,state`
3. Detect Figma URLs in issue body.
4. If Figma URL exists:
   - call `figma-design-analyst` and include returned summary in output.
5. Write artifact using template:
   - `.ai/artifacts/issue-analysis-template.md`
   - output file: `.ai/artifacts/run/MS-<run-id>-001-understanding.md`
6. Include explicit fields:
   - `Figma Present: YES/NO`
   - `Figma Links:` list
7. Print: `Stage 1: Issue Intake + Optional Figma Context completed`

## Rules

- No code changes in this stage.
- If `gh` auth is missing, return `Status: FAILED` with remediation.
- If Figma URL exists but MCP call fails, include warning and continue with available data.
