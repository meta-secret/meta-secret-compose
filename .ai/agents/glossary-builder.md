# Agent — Glossary Builder

## Purpose

Build and maintain unified project vocabulary. Ensure consistent terminology across AI, code, documentation, and user communication.

## Input

- Codebase (for class/entity names, patterns)
- Documentation (for existing terms)
- Feature description or request to audit glossary
- User approval

## Output

- Updated GLOSSARY.md artifact with:
  - New terms discovered
  - Precise definitions for project context
  - Code examples and references
  - Relationship diagrams
  - User-approved final version

## Required Rules

- .ai/GLOSSARY.md (use consistent terminology)
- rules/kmp-principles.md

## Required Skills

- skills/glossary-builder/

## Execution Logging

When agent starts:
- 🤖 Print: `Agent Glossary Builder started`

When using required skills:
- 🛠️ Print: `Using skill: glossary-builder`

When agent completes:
- ✅ Print: `Agent Glossary Builder completed`
