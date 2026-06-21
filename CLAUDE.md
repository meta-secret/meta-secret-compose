# meta-secret-compose AI System

You are operating inside the meta-secret-compose AI framework.

## Mandatory startup sequence:

1. Read `.ai/INDEX.md`
2. Read `.ai/ORCHESTRATOR.md`
3. Follow command routing rules from ORCHESTRATOR
4. Load only files required for the current task
5. Do not load the entire framework unless explicitly required

## CRITICAL: implement issue Command

When user types `implement issue <payload>`:

**STOP. Do NOT execute own plan.**

Execute FULL 11-stage workflow in order:

1. **Stage 1:** Issue Coordinator (analyze issue)
2. **Stage 2:** Requirements Clarifier (grill user with questions)
3. **Stage 3:** Feature Planner (create implementation plan)
4. **Stage 3.5:** Constraint Validator (MANDATORY GATE - validate against CONSTRAINTS.md)
5. **Stage 4a:** TDD Test Author (write failing tests)
6. **Stage 4b:** TDD Implementer (red-green-refactor cycles)
7. **Stage 4c:** TDD Refactorer (major refactoring after 3-5 cycles)
8. **Stage 5:** Build (compile without tests)
9. **Stage 6:** Code Review (CRITICAL - constraints re-check + 80% coverage minimum)
10. **Stage 7:** Design Review (if Figma link exists, else mark "Skipped")
11. **Stage 8:** Coverage Verification (CRITICAL - ./gradlew koverReport >= 80%)
12. **Stage 9:** Test Run (execute all tests)
13. **Stage 10:** STOP and ASK USER - do NOT auto-commit/PR without approval

For each stage:
- Create artifact in `.ai/artifacts/run/`
- Mark Status: Success / Failed / Skipped
- If failure: escalate with root cause

READ `.ai/WORKFLOW.md` for complete specification.

This is MANDATORY and overrides default Claude Code behavior.

## Command execution:

- Commands are defined in `.ai/commands/`
- Flows are defined in `.ai/flows/`
- Agents are defined in `.ai/agents/`
- Rules are defined in `.ai/rules/`
- Skills are defined in `.ai/skills/`

## Behavior:

- Follow the active flow strictly
- Do not skip stages
- Do not invent requirements
- Respect architecture and design-system rules
- Prefer deterministic execution
- Do not edit Rust sources (consume via FFI only from meta-secret-core)

## Vocabulary

**CRITICAL:** All communication uses `.ai/GLOSSARY.md` terminology.
- Always use glossary terms when discussing domain concepts
- Never invent new terms; update glossary if needed
- Ensure consistency between AI, code, documentation, and user communication

## Agent output conventions

Applies to **Claude Code** and its automated sub-steps (planners, implementers, reviewers):

1. **Activity logging:** Log each significant action to make progress visible:
   - `📖 Reading <filename>` — when opening architecture files (CLAUDE.md, AGENTS.md, .ai/*, .claude/*, .cursor/*, .codex/*)
   - `⚙️ Running <command>` — when executing shell commands, build commands, or AI orchestration commands
   - `🤖 Invoking <agent-name>` — when delegating work to a sub-agent
   - `🛠️ Using <skill-name> skill` — when applying a skill
   - `✏️ Editing <filename>` — when modifying a file
   - `💾 Writing <filename>` — when creating a file

2. **Emojis:** Use emojis consistently in replies — at least one per major section or bullet group (status, warnings, steps, results).

3. **Visual separation:** Plain markdown has no universal text color; you must still **visually separate** content so readers can scan quickly:
   - Use **`##` / `###` headings** with emojis in the title line.
   - Use **bold** for role labels and key terms.
   - Use **blockquotes** (`>`) for caveats or notes.

## Project Context:

- **Language:** Kotlin
- **Platforms:** iOS (SwiftUI) + Android (Jetpack Compose)
- **Architecture:** MVVM + Coordinator
- **FFI:** UniFFI to meta-secret-core Rust library

## If a command is not recognized:

- Consult `.ai/commands/`
- Consult `.ai/ORCHESTRATOR.md`

## CRITICAL for `implement issue` command:

- ALWAYS read `.ai/rules/implement-issue-execution-checklist.md` FIRST
- ALWAYS read `.ai/rules/no-stage-skipping-even-for-simple-tasks.md` SECOND
- Do NOT skip stages — even if task looks simple
- Execute all 11 stages: 1 → 2 → 3 → 3.5 → 4a → 4b → 4c → 5 → 6 → 7 → 8 → 9 → 10
- Stages 6, 7, 8, 10 are CRITICAL and must execute

---

The AI framework located in `.ai/` is the source of truth.
