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

#### 10-Stage Pipeline Commands
- `.ai/commands/implement-issue.md` — Execute full 10-stage workflow
- `.ai/commands/only-issue-coordinator.md` — Run stage 1 only
- `.ai/commands/only-grill-me.md` — Run stage 2 only
- `.ai/commands/only-planner.md` — Run stage 3 only
- `.ai/commands/only-constraint-validator.md` — Run stage 3.5 only
- `.ai/commands/only-tdd-test-author.md` — Run stage 4a only
- `.ai/commands/only-tdd-implementer.md` — Run stage 4b only
- `.ai/commands/only-tdd-refactorer.md` — Run stage 4c only
- `.ai/commands/only-reviewer.md` — Run stage 6 only
- `.ai/commands/only-release-manager.md` — Run stage 10 only

#### Maestro Testing Commands
- `.ai/commands/write-test-flow.md` — Write a new Maestro test
- `.ai/commands/check-test-flow.md` — Run a Maestro test
- `.ai/commands/check-simulators.md` — Check device availability

#### Quick Launch Commands
- `.ai/commands/launch-ios.md` — Build, install, launch iOS app
- `.ai/commands/launch-android.md` — Build, install, launch Android app

#### Utility Commands
- `.ai/commands/only-from-prompt.md` — Start from manual description
- `.ai/commands/only-debug-rca.md` — Debug failed artifacts
- `.ai/commands/only-release-notes.md` — Generate release notes
- `.ai/commands/only-workflow-pattern-capture.md` — Capture workflow patterns
- `.ai/commands/only-glossary-update.md` — Build/update project glossary
- `.ai/commands/help.md` — Help command

- `.ai/flows/` — Workflow and stage orchestrations

### Agents & Skills

#### Workflow Agents (10-Stage Pipeline)
- `.ai/agents/github-issue-coordinator.md` — Stage 1
- `.ai/agents/requirements-clarifier.md` — Stage 2
- `.ai/agents/feature-planner.md` — Stage 3
- `.ai/agents/constraint-validator.md` — Stage 3.5
- `.ai/agents/tdd-test-author.md` — Stage 4a
- `.ai/agents/tdd-implementer.md` — Stage 4b
- `.ai/agents/tdd-refactorer.md` — Stage 4c
- `.ai/agents/code-reviewer.md` — Stage 6
- `.ai/agents/design-reviewer.md` — Stage 7
- `.ai/agents/test-verifier.md` — Stage 9
- `.ai/agents/release-manager.md` — Stage 10

#### Maestro Testing Agents
- `.ai/agents/maestro-test-author.md` — Write Maestro YAML tests
- `.ai/agents/maestro-test-runner.md` — Run Maestro tests
- `.ai/agents/simulator-checker.md` — Check device availability

#### Quick Launch Agents
- `.ai/agents/app-launcher-ios.md` — Build, install, launch iOS app
- `.ai/agents/app-launcher-android.md` — Build, install, launch Android app

#### Skills
- `.ai/skills/` — Reusable skill implementations
  - `skills/grill-me/` — Relentless interviewing methodology
  - `skills/requirements-probing/` — Structured question categories
  - `skills/test-driven-development/` — TDD methodology
  - `skills/red-green-refactor/` — Red-Green-Refactor execution
  - `skills/maestro-testing/` — Maestro E2E testing methodology

- `.ai/hooks/` — Lifecycle hooks and callbacks

### Rules & Templates

#### Architecture & Workflow Rules
- `.ai/rules/` — Architecture and design-system rules
  - `constraint-checking.md` — Mandatory constraint validation workflow
  - `tdd-principles.md` — Test-Driven Development principles
  - `kmp-principles.md` — KMM architecture principles (file size, reusability, parameters, visibility)
  - `kmp-code-style.md` — Kotlin/Compose code style (strings, typography, naming, comments)
  - `artifact-writing-guide.md` — How to create and name artifacts
  - `agent-artifact-integration.md` — How agents integrate artifacts

#### Maestro Testing Rules
- `.ai/rules/maestro-setup-guide.md` — All iOS/Android device commands
- `.ai/rules/maestro-test-writing.md` — How to write Maestro YAML tests
- `.ai/rules/test-flow-naming.md` — Maestro test file naming conventions

#### Artifact Templates

##### 10-Stage Pipeline Templates
- `.ai/artifacts/issue-analysis-template.md` — Stage 1
- `.ai/artifacts/clarification-template.md` — Stage 2
- `.ai/artifacts/implementation-plan-template.md` — Stage 3
- `.ai/artifacts/constraint-validation-template.md` — Stage 3.5
- `.ai/artifacts/test-authoring-template.md` — Stage 4a
- `.ai/artifacts/implementation-template.md` — Stage 4b
- `.ai/artifacts/refactoring-template.md` — Stage 4c
- `.ai/artifacts/build-report-template.md` — Stage 5
- `.ai/artifacts/review-report-template.md` — Stage 6
- `.ai/artifacts/design-review-report-template.md` — Stage 7
- `.ai/artifacts/coverage-report-template.md` — Stage 8
- `.ai/artifacts/test-report-template.md` — Stage 9
- `.ai/artifacts/pr-template.md` — Stage 10

##### Maestro Testing Templates
- `.ai/artifacts/maestro-test-template.md` — Maestro test results
- `.ai/artifacts/simulator-check-template.md` — Device availability report
- `.ai/artifacts/app-launch-template.md` — App launch results

- `.ai/artifacts/run/` — Generated artifacts from runs (one per stage, with Status field)

### IDE Entry Points
- `.claude/INDEX.md` — Claude Code entry (→ read `.ai/INDEX.md`)
- `.cursor/INDEX.md` — Cursor IDE entry (→ read `.ai/INDEX.md`)
- `.codex/INDEX.md` — Codex CLI entry (→ read `.ai/INDEX.md`)

Last updated: 2026-06-10
