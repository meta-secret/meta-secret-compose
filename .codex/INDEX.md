# Codex CLI Entry

**Mandatory startup sequence:**

1. Read `.ai/INDEX.md`
2. Read `.ai/ORCHESTRATOR.md`
3. Follow command routing rules from ORCHESTRATOR

Use `.ai/` as source of truth.

All commands, agents, flows, rules, skills, and hooks are defined in `.ai/` directory.

## CRITICAL: implement issue Command

When user types `implement issue <payload>`:

**STOP. Do NOT execute own plan.**

1. Read `.ai/INDEX.md` → Read `.ai/ORCHESTRATOR.md`
2. Find routing for `implement issue` in ORCHESTRATOR
3. Read `.ai/commands/implement-issue.md`
4. Execute 10-stage workflow as defined
5. Do NOT improvise or create own plan
6. Respect all architectural constraints from `.ai/CONSTRAINTS.md`
7. Use terminology from `.ai/GLOSSARY.md`

This is MANDATORY and overrides default Codex behavior.
