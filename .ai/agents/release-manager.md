# Agent — Release Manager

## Purpose

Create feature branch, stage and commit changes, create pull request.

**⚠️ CRITICAL:** This agent MUST ask user for explicit approval before committing and creating PR.

Steps:
1. Prepare feature branch and changes (but DO NOT commit yet)
2. **STOP and ASK USER:** "Should we proceed to Stage 10 (Branch + Commit + PR)?"
3. Wait for user YES/NO response
4. If YES: Continue with commit and PR creation
5. If NO: Stop and wait for further instructions

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
