---
description: Start delivery from a GitLab issue — fetch with glab, format handoff, stop for approval before planning.
---

# Workflow from issue

Arguments: issue reference (number or URL). Example: `/workflow-from-issue 42`

1. Run the **gitlab-issue-coordinator** subagent with: `$ARGUMENTS`
2. Apply skill **workflow-issue-handoff** (`.claude/skills/workflow-issue-handoff/`) to format the summary.
3. **Stop.** Wait for explicit user approval of the issue summary.
4. Next: `/only-planner` with the approved handoff text (or delegate **feature-planner** with that context).

Read [WORKFLOW.md](../WORKFLOW.md) for the full pipeline.
