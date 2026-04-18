# AI Architecture — meta-secret-compose

🎯 **Single source of truth for AI automation** across Claude Code, Cursor, and OpenAI Codex CLI.

---

## 📦 Structure

```
.ai/                           ← Canonical AI configuration
├── agents/                    ← 10 subagents for different roles
│   ├── feature-planner.md
│   ├── code-implementer.md
│   ├── code-reviewer.md
│   ├── test-author.md
│   ├── test-verifier.md
│   ├── debug-rca.md
│   ├── release-manager.md
│   ├── release-notes.md
│   ├── github-issue-coordinator.md
│   └── workflow-pattern-capture.md
│
├── commands/                  ← Slash commands (only-*)
│   ├── README.md             ← Command index
│   ├── help.md               ← /help command behavior
│   ├── only-planner.md
│   ├── only-implementer.md
│   ├── only-reviewer.md
│   ├── only-test-author.md
│   ├── only-test-verifier.md
│   ├── only-debug-rca.md
│   ├── only-release-notes.md
│   ├── only-release-manager.md
│   ├── only-issue-coordinator.md
│   ├── only-from-prompt.md
│   ├── only-workflow-pattern-capture.md
│   ├── only-generate-uniffi.md
│   └── git_compose.md        ← Git wrapper for this repo
│
├── skills/                    ← Reusable workflows
│   ├── workflow-manual-task-brief/
│   ├── workflow-plan-output/
│   ├── workflow-issue-handoff/
│   ├── workflow-mr-body/
│   ├── systematic-debugging/
│   ├── write-implementation-plan/
│   ├── workflow-pattern-capture/
│   ├── kmp-doctor/           ← KMP-specific
│   ├── ios-device-doctor/    ← iOS-specific
│   └── feature-brainstorm/   ← App-specific
│
├── rules/                     ← IDE-specific rules (Cursor, Codex)
│   ├── RULES.md              ← Rules index
│   ├── code-style.md         ← Kotlin/Swift style guide
│   ├── kmp-principles.md     ← KMM architecture
│   ├── ios-guidelines.md     ← iOS-specific
│   ├── android-guidelines.md ← Android-specific
│   └── ...
│
├── ARCHITECTURE.md           ← This file
└── README.md                 ← Quick reference
```

---

## 🔗 IDE Integration

Each IDE gets **symlinks** to `.ai/`:

| IDE | Links to `.ai/` | Understands |
|-----|---|---|
| **Claude Code** | `.claude/agents` → `.ai/agents` | agents/, commands/, skills/ |
| | `.claude/commands` → `.ai/commands` | |
| | `.claude/skills` → `.ai/skills` | |
| **Cursor** | `.cursor/agents` → `.ai/agents` | agents/, rules/ |
| | `.cursor/rules` → `.ai/rules` | |
| **OpenAI Codex CLI** | `.codex/agents` → `.ai/agents` | agents/, commands/, rules/ |
| | `.codex/commands` → `.ai/commands` | |
| | `.codex/rules` → `.ai/rules` | |

### Why symlinks?

✅ **Single source of truth** — Edit in `.ai/`, instantly reflected everywhere  
✅ **No duplication** — One agent, one command, synced across all IDEs  
✅ **Easy to maintain** — Change once, works in Claude Code, Cursor, Codex  
✅ **Git-friendly** — Symlinks are preserved in git; actual files don't duplicate  

---

## 🚀 How to Use

### From Claude Code

```bash
# At repo root (meta-secret-compose/)
/help                          # List all commands
/only-planner <context>        # Start planning
/only-implementer              # Implement approved plan
/only-reviewer                 # Review changes
/only-test-author              # Write tests
/only-from-prompt              # Manual task workflow
/only-generate-uniffi          # Generate FFI bindings from core
```

### From Cursor

Cursor respects agents and rules from `.cursor/agents` and `.cursor/rules` (both symlinks to `.ai/`).

Define custom rules in `.ai/rules/` that Cursor will automatically find.

### From OpenAI Codex CLI

```bash
# Codex CLI reads agents, commands, and rules from .codex/
codex --agent feature-planner --context "add login UI"
codex --command only-implementer
codex --rule code-style
```

---

## 📝 For Developers

### Adding a new agent

1. Create `.ai/agents/my-agent.md`
2. Automatically available in:
   - Claude Code: `/help` will list it
   - Cursor: Can reference it in rules
   - Codex CLI: `codex --agent my-agent`

### Adding a new skill

1. Create `.ai/skills/my-skill/SKILL.md`
2. Referenced in agents via: `Use skill **my-skill**`
3. Works in all three IDEs

### Updating a command

1. Edit `.ai/commands/only-*.md`
2. Changes apply to:
   - Claude Code: `/only-*` command
   - Codex CLI: `codex --command only-*`

### Adding IDE-specific rules

1. Create `.ai/rules/my-rule.md`
2. Cursor and Codex will auto-discover via symlinks
3. Document context: "For Cursor" or "For Codex CLI"

---

## 🔄 Symlink Setup

### macOS / Linux

Already set up:
```bash
.claude/agents → ../.ai/agents
.claude/commands → ../.ai/commands
.claude/skills → ../.ai/skills
.cursor/agents → ../.ai/agents
.cursor/rules → ../.ai/rules
.codex/agents → ../.ai/agents
.codex/commands → ../.ai/commands
.codex/rules → ../.ai/rules
```

Verify:
```bash
ls -la .claude/agents    # Should show: agents -> ../.ai/agents
```

### Windows (if needed)

Use junction (directory symlink):
```powershell
mklink /J .claude\agents .ai\agents
mklink /J .claude\commands .ai\commands
mklink /J .claude\skills .ai\skills
mklink /J .cursor\agents .ai\agents
mklink /J .cursor\rules .ai\rules
mklink /J .codex\agents .ai\agents
mklink /J .codex\commands .ai\commands
mklink /J .codex\rules .ai\rules
```

---

## 📌 Important Notes

- **Don't edit in `.claude/`, `.cursor/`, or `.codex/` directly** — always edit in `.ai/`
- **Symlinks are transparent** — you can open files from any IDE and edits sync
- **Git preserves symlinks** — the actual folder `.ai/` is what's tracked; symlinks point to it
- **Independent repo** — Nothing shared with `meta-secret-core`; this is fully self-contained
- **FFI boundary** — Uses UniFFI bindings from core; see `only-generate-uniffi` command

---

## 🔗 Related

- Parent workspace: See `MetaSecret/AI_ARCHITECTURE.md` for multi-repo orchestration
- Core structure: See `meta-secret-core/.ai/ARCHITECTURE.md` (independent)
- Root CLAUDE.md: `../CLAUDE.md` explains task routing

---

✅ **Last updated:** 2026-04-18  
🚀 **Ready for:** Claude Code, Cursor, OpenAI Codex CLI
