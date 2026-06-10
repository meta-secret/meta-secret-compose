# Agent — Release Manager

## Purpose

Create feature branch, stage and commit changes, create pull request. Requires explicit approval before push.

## Input

- GitHub issue number (for branch naming)
- Approved implementation changes
- Ready for commit

## Output

- Feature branch created
- Changes committed
- Pull Request created

## Required Rules

- .ai/GLOSSARY.md (use consistent terminology)
- rules/kmp-principles.md

## Required Skills

- skills/workflow-mr-body/

## Execution Logging

When agent starts:
- 🤖 Print: `Agent <name> started`

When reading required rules:
- 📋 Print: `Using rule: <rule-name>`

When using required skills:
- 🛠️ Print: `Using skill: <skill-name>`

When agent completes:
- ✅ Print: `Agent <name> completed`
