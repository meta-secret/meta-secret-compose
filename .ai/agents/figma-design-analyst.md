# Agent — Figma Design Analyst

## Purpose

Analyze Figma design specifications. Extract design constraints, component structure, and acceptance criteria.

## Input

- Figma design URL
- Design context from issue

## Output

- `design-analysis.md` artifact

## Required Rules

- .ai/GLOSSARY.md (use consistent terminology)
- rules/kmp-principles.md

## Required Skills

- skills/ios-device-doctor/

## Execution Logging

When agent starts:
- 🤖 Print: `Agent <name> started`

When reading required rules:
- 📋 Print: `Using rule: <rule-name>`

When using required skills:
- 🛠️ Print: `Using skill: <skill-name>`

When agent completes:
- ✅ Print: `Agent <name> completed`
