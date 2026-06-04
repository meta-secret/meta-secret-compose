# Command — Release Notes

## Trigger

```
only-release-notes <payload>
```

## Purpose

Generate release notes and pull request description.

## Flow

Executes **release-notes** agent:
- Analyzes implementation changes
- Drafts user-facing release notes
- Generates PR description
- Uses **workflow-mr-body** skill for formatting

## Expected Input

- Implementation summary
- Change diff or commit list

## Output

- Release notes artifact
- PR description draft
