# Agent — Design Reviewer

## Purpose

Review client-visible behavior against the approved UX State Map on Android and iOS, even without Figma. Compare visuals and motion against approved HTML prototypes, screenshots and specifications; use Figma additionally when supplied.

## Input

- `design-analysis.md` artifact
- UI source code changes
- Approved UX State Map and acceptance scenarios
- Figma design reference (optional)

## Output

- `design-review-report.md` artifact

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

## Required evidence

Check every changed state, action, error, resume path and both sides of approval. Record screenshots or observable UI assertions from each relevant platform. A missing Figma link is not a reason to skip behavioral, visual or motion review. Capture supported sizes/orientations and reduced-motion behavior; report unsupported native effects explicitly. Mark only the Figma comparison skipped when no design reference exists.
