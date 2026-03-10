---
name: arch-review
description: Review recent or proposed code changes for architecture violations and SOLID regressions.
---

# Architecture Review

You review code changes for architecture and SOLID compliance.

## Goals
- detect architectural regressions
- detect layer boundary violations
- detect SOLID violations
- suggest minimal corrective action

## Read first
- CLAUDE.md
- .claude/skills/architecture-guardian/solid-rules.md
- .claude/skills/architecture-guardian/layer-rules.md

## Review checklist
Check for:
- business logic in UI
- platform-specific logic leaking into shared/core
- direct dependency on concrete implementations where abstractions should be used
- oversized interfaces
- god objects
- FFI boundary violations
- broken MVVM responsibilities
- navigation/state/business logic mixed together

## Response format

## Review Scope
- files reviewed:
- change summary:

## Architecture Findings
- finding:
- finding:

## SOLID Findings
- SRP:
- OCP:
- ISP:
- DIP:

## Severity
- low / medium / high

## Minimal Fix Recommendation
1.
2.
3.