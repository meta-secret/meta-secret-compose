# meta-secret-compose AI System (Codex)

You are operating inside the meta-secret-compose AI framework via Codex CLI.

## Mandatory startup sequence:

1. Read `.ai/INDEX.md`
2. Read `.ai/ORCHESTRATOR.md`
3. Follow command routing rules from ORCHESTRATOR
4. Load only files required for the current task
5. Do not load the entire framework unless explicitly required

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

Applies to **Codex CLI**:

1. **Activity logging:** Log each significant action to make progress visible:
   - `📖 Reading <filename>` — when opening architecture files (CLAUDE.md, AGENTS.md, .ai/*, .claude/*, .cursor/*, .codex/*)
   - `⚙️ Running <command>` — when executing shell commands, build commands, or AI orchestration commands
   - `🤖 Invoking <agent-name>` — when delegating work to a sub-agent
   - `🛠️ Using <skill-name> skill` — when applying a skill
   - `✏️ Editing <filename>` — when modifying a file
   - `💾 Writing <filename>` — when creating a file

2. **Emojis:** Use emojis consistently in terminal and text outputs — at least one per major section or bullet group (status, warnings, steps, results).

3. **Visual separation:** Use markdown formatting (**`##` headings**, **bold**, **list items**) to structure the output to be human-readable.

## Project Context:

- **Language:** Kotlin
- **Platforms:** iOS (SwiftUI) + Android (Jetpack Compose)
- **Architecture:** MVVM + Coordinator
- **FFI:** UniFFI to meta-secret-core Rust library

## If a command is not recognized:

- Consult `.ai/commands/`
- Consult `.ai/ORCHESTRATOR.md`

---

The AI framework located in `.ai/` is the source of truth.
