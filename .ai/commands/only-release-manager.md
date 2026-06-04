# Command — Release Manager

## Trigger

```
only-release-manager <payload>
```

## Purpose

Create branch, commit changes, and create pull request.

## Flow

Executes **release-manager** agent:
- Creates feature branch (auto-named from issue number)
- Stages and commits changes
- Creates pull request
- Requires explicit approval before push

## Expected Input

- GitHub issue number (e.g., `#42`)
- GitHub issue URL
- Or explicit branch name

## Output

- Pull Request created
