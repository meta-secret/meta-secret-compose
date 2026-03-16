# AI Development Skills

This document explains how to use the AI development skills available in this repository.

These commands help engineers work with Claude Code while preserving project architecture, SOLID principles, and development conventions.

---

# Recommended Development Workflow

When implementing new features or fixing issues, follow this sequence:

feature idea  
→ /feature-brainstorm  
→ /write-implementation-plan  
→ generate code  
→ /arch-review  
→ build  
→ /kmp-doctor (if build fails)  
→ run app  
→ /ios-device-doctor (if runtime fails)

---

# Commands

## /feature-brainstorm

Purpose  
Design a feature before writing code.

What it does
- clarifies the feature goal
- determines correct architectural placement
- proposes implementation options
- checks SOLID compliance
- recommends the best approach

Use when
- starting a new feature
- unsure where functionality belongs
- multiple design options exist

---

## /write-implementation-plan

Purpose  
Convert an approved feature design into a concrete implementation plan.

What it does
- identifies affected modules
- lists files to modify
- defines new interfaces and use-cases
- outlines implementation steps
- specifies verification strategy

Use when
- a feature design is already chosen
- you want a safe roadmap before coding

---

## /arch-review

Purpose  
Review generated code for architectural correctness.

What it checks
- layer boundary violations
- SOLID compliance
- business logic inside UI
- dependency inversion violations
- creation of "god objects"

Use when
- new code has been generated
- large changes were made
- architecture integrity must be verified

---

## /kmp-doctor

Purpose  
Diagnose and repair Kotlin Multiplatform + Swift build failures.

What it does
- runs the narrowest possible build command
- analyzes build output
- performs root-cause analysis
- proposes a minimal repair plan
- applies fixes after approval

Use when
- Gradle build fails
- KMP compilation errors occur
- Swift integration breaks

---

## /ios-device-doctor

Purpose  
Diagnose runtime failures on physical iOS devices.

What it does
- analyzes device logs
- performs root-cause analysis
- proposes minimal runtime fixes

Use when
- the app builds but fails on a real iPhone
- runtime crashes occur

---

# Additional Tools

## /systematic-debugging

Purpose  
Perform structured root-cause analysis for complex bugs.

Debugging model

Symptoms → Observations → Hypotheses → Evidence → Root Cause → Fix → Verification

Use when
- debugging complex failures
- the root cause is unclear
- multiple hypotheses exist

---

## /redeploy-main

Purpose  
Rebuild and redeploy the Rust backend server.

What it does
- verifies git state
- builds and pushes the Docker image
- deploys to Kubernetes
- verifies pod health

Use when
- deploying a new backend version
- updating the server image in the cluster

---

# Principles Behind These Skills

The skill system enforces several engineering principles:

- architecture-first development
- design before coding
- minimal fixes over refactors
- root-cause debugging
- strict layer isolation
- SOLID principles

This ensures that AI-assisted development behaves closer to a disciplined senior engineering workflow rather than ad-hoc code generation.