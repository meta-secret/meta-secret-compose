# AI Index

## Overview

meta-secret-compose is a KMM (Kotlin Multiplatform Mobile) application for iOS and Android.

**Architecture:** MVVM + Coordinator pattern  
**Languages:** Kotlin (shared), Swift (iOS), Kotlin (Android)  
**Key constraint:** Do not edit Rust sources; consume via UniFFI from `meta-secret-core`

## Architecture Constraints

**CONSTRAINTS.md** is the single source of truth for MetaSecret architecture and design rules.
- All planning must reference CONSTRAINTS.md
- All implementation must comply with CONSTRAINTS.md (35 confirmed rules)
- Validation is **MANDATORY** before coding (Stage 3.5)
- Use `only-constraint-validator` command to validate

See `.ai/CONSTRAINTS.md` for complete architecture specification.

## Unified Vocabulary

**GLOSSARY.md** is the single source of truth for project terminology.
- All agents use glossary terms in communication
- All code should reflect glossary naming
- Update glossary as codebase grows via `only-glossary-update` command

See `.ai/GLOSSARY.md` for complete project vocabulary.

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
- `.ai/commands/only-constraint-validator.md` — Run stage 3.5 (constraint validation) only
- `.ai/commands/only-tdd-test-author.md` — Run stage 4a (TDD test authoring) only
- `.ai/commands/only-tdd-implementer.md` — Run stage 4b (red-green-refactor) only
- `.ai/commands/only-tdd-refactorer.md` — Run stage 4c (major refactor) only
- `.ai/commands/only-reviewer.md` — Run stage 6 only
- `.ai/commands/only-release-manager.md` — Run stage 10 only
- `.ai/commands/only-from-prompt.md` — Start from manual description
- `.ai/commands/only-debug-rca.md` — Debug failed artifacts
- `.ai/commands/only-release-notes.md` — Generate release notes
- `.ai/commands/only-workflow-pattern-capture.md` — Capture workflow patterns
- `.ai/commands/only-glossary-update.md` — Build/update project glossary
- `.ai/commands/help.md` — Help command
- `.ai/flows/` — Workflow and stage orchestrations

### Agents & Skills
- `.ai/agents/` — Agent definitions and behaviors
- `.ai/skills/` — Reusable skill implementations
  - `skills/grill-me/` — Relentless interviewing methodology (Stage 2)
  - `skills/requirements-probing/` — Structured question categories
  - `skills/test-driven-development/` — TDD methodology and best practices (Stage 4)
  - `skills/red-green-refactor/` — Red-Green-Refactor cycle execution (Stage 4b)
- `.ai/hooks/` — Lifecycle hooks and callbacks

### Rules & Templates
- `.ai/rules/` — Architecture and design-system rules
  - `rules/constraint-checking.md` — Mandatory constraint validation workflow
  - `rules/tdd-principles.md` — Test-Driven Development principles and practices
  - `rules/kmp-principles.md` — KMM architecture principles
  - `rules/artifact-writing-guide.md` — How to create and name artifacts
  - `rules/agent-artifact-integration.md` — How agents integrate artifacts into workflow
- `.ai/artifacts/` — Output templates for all stages:
  - `issue-analysis-template.md` — Stage 1
  - `clarification-template.md` — Stage 2
  - `implementation-plan-template.md` — Stage 3
  - `constraint-validation-template.md` — Stage 3.5
  - `test-authoring-template.md` — Stage 4a
  - `implementation-template.md` — Stage 4b
  - `refactoring-template.md` — Stage 4c
  - `build-report-template.md` — Stage 5
  - `review-report-template.md` — Stage 6
  - `design-review-report-template.md` — Stage 7
  - `coverage-report-template.md` — Stage 8
  - `test-report-template.md` — Stage 9
  - `pr-template.md` — Stage 10
- `.ai/artifacts/run/` — Generated artifacts from runs (one per stage, with Status field)

### IDE Entry Points
- `.claude/INDEX.md` — Claude Code entry (→ read `.ai/INDEX.md`)
- `.cursor/INDEX.md` — Cursor IDE entry (→ read `.ai/INDEX.md`)
- `.codex/INDEX.md` — Codex CLI entry (→ read `.ai/INDEX.md`)

Last updated: 2026-06-10
