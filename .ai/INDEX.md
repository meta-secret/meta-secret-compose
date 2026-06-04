# AI Index

## Overview

meta-secret-compose is a KMM (Kotlin Multiplatform Mobile) application for iOS and Android.

**Architecture:** MVVM + Coordinator pattern  
**Languages:** Kotlin (shared), Swift (iOS), Kotlin (Android)  
**Key constraint:** Do not edit Rust sources; consume via UniFFI from `meta-secret-core`

## Command Routing

See `.ai/ORCHESTRATOR.md` for command routing and agent execution.

## Structure

### Orchestrator
- `.ai/ORCHESTRATOR.md` — Command routing and execution logic

### Workflows & Pipelines
- `.ai/WORKFLOW.md` — End-to-end workflow orchestration
- `.ai/PIPELINE.md` — Stage-by-stage specifications
- `.ai/QUICK-START.md` — Quick reference guide

### Commands & Flows
- `.ai/commands/run.md` — Execute full 10-stage workflow
- `.ai/commands/only-issue-coordinator.md` — Run stage 1 only
- `.ai/commands/only-grill-me.md` — Run stage 2 (clarification) only
- `.ai/commands/only-planner.md` — Run stage 3 only
- `.ai/commands/only-implementer.md` — Run stage 4 only
- `.ai/commands/only-reviewer.md` — Run stage 6 only
- `.ai/commands/only-test-author.md` — Run stage 8 only
- `.ai/commands/only-test-verifier.md` — Run stage 9 only
- `.ai/commands/only-release-manager.md` — Run stage 10 only
- `.ai/commands/only-from-prompt.md` — Start from manual description
- `.ai/commands/only-debug-rca.md` — Debug failed artifacts
- `.ai/commands/only-release-notes.md` — Generate release notes
- `.ai/commands/only-workflow-pattern-capture.md` — Capture workflow patterns
- `.ai/commands/help.md` — Help command
- `.ai/flows/` — Workflow and stage orchestrations

### Agents & Skills
- `.ai/agents/` — Agent definitions and behaviors
- `.ai/skills/` — Reusable skill implementations
  - `skills/grill-me/` — Relentless interviewing methodology (Stage 2)
  - `skills/requirements-probing/` — Structured question categories
- `.ai/hooks/` — Lifecycle hooks and callbacks

### Rules & Templates
- `.ai/rules/` — Architecture and design-system rules
- `.ai/artifacts/` — Output templates for workflows
- `.ai/artifacts/run/` — Generated artifacts from runs

### IDE Entry Points
- `.claude/INDEX.md` — Claude Code entry (→ read `.ai/INDEX.md`)
- `.cursor/INDEX.md` — Cursor IDE entry (→ read `.ai/INDEX.md`)
- `.codex/INDEX.md` — Codex CLI entry (→ read `.ai/INDEX.md`)

Last updated: 2026-06-04
