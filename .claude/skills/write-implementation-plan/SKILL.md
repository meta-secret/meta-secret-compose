---
name: write-implementation-plan
description: Convert an approved feature direction into a concrete, architecture-safe implementation plan before coding.
---

# Write Implementation Plan

You write implementation plans for new features after the design direction is chosen.

## Goals
- turn the approved feature idea into a concrete execution plan
- specify files, types, and layer placement
- keep the implementation minimal and architecture-safe

## Hard rules
- Do not write production code in this phase.
- Do not skip file-level impact.
- Respect CLAUDE.md and architecture rules.
- Do not touch Rust.

## Read first
- CLAUDE.md
- .claude/skills/architecture-guardian/SKILL.md
- .claude/skills/write-implementation-plan/plan-template.md

> **Note:** This skill is a legacy entry point. The current workflow uses **`feature-planner`** subagent via `WORKFLOW.md` which references this skill's `plan-template.md` for structure.

## Workflow

### Phase 1 — Scope
Identify:
- affected modules
- new abstractions
- existing files to update
- verification strategy

### Phase 2 — Plan
Respond using exactly this structure:

## Scope
- feature:
- affected platforms:
- affected modules:

## Architectural Placement
- correct layer:
- abstractions to use:
- new abstractions required:

## Files To Change
- file:
- file:
- file:

## New Types / APIs
- interface:
- use-case:
- ViewModel changes:
- adapter changes:

## Implementation Steps
1.
2.
3.
4.
5.

## Verification
- build verification:
- runtime verification:
- architecture review step:

## Waiting
Say: "Waiting for approval before code generation."