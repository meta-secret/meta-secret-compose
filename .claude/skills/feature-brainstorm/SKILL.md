---
name: feature-brainstorm
description: Explore and shape a new feature before code generation. Always determine architectural placement, design options, trade-offs, and recommended approach first.
---

# Feature Brainstorm

You help design new features for this project before any code is generated.

## Goals
- clarify the feature request
- identify the correct architectural layer
- propose 2-3 viable implementation options
- compare trade-offs
- recommend the minimal architecture-compliant approach

## Hard rules
- Do not write production code in this phase.
- Do not skip architectural placement.
- Respect all rules from CLAUDE.md and architecture-guardian.
- Do not suggest solutions that break current module boundaries.
- Do not touch Rust.

## Read first
- CLAUDE.md
- .claude/skills/architecture-guardian/solid-rules.md
- .claude/skills/architecture-guardian/layer-rules.md
- .claude/skills/architecture-guardian/generation-rules.md
- .claude/skills/feature-brainstorm/decision-template.md

## Workflow

### Phase 1 — Clarify
Determine:
- feature goal
- affected platform(s)
- user-facing behavior
- data flow
- architectural layer ownership

### Phase 2 — Explore options
Produce 2-3 implementation approaches if meaningful.

### Phase 3 — Recommend
Respond using exactly this structure:

## Feature Goal
- requested capability:
- affected platform(s):

## Architectural Placement
- correct layer:
- existing abstractions involved:
- new abstraction needed: yes / no

## Candidate Designs
### Option 1
- design:
- pros:
- cons:

### Option 2
- design:
- pros:
- cons:

### Option 3
- design:
- pros:
- cons:

## Recommended Option
- chosen option:
- why:

## SOLID Check
- SRP:
- OCP:
- ISP:
- DIP:

## Next Step
Say: "Next: use /write-implementation-plan for the approved direction."