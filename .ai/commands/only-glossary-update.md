# Command — Glossary Update

## Trigger

```
only-glossary-update "<feature-description>"
```

## Purpose

Build or update project glossary with new domain terminology as codebase grows.

## Flow

Executes **glossary-builder** agent:
- Analyzes codebase for new concepts
- Scans documentation for new terms
- Proposes new glossary entries
- Gets user approval
- Updates GLOSSARY.md

## Expected Input

- Feature description (e.g., "End-to-end encryption for messages")
- Or just run to audit current glossary completeness

## Output

- Updated GLOSSARY.md artifact with:
  - New terms discovered
  - Definitions in project context
  - Code examples
  - Relationship diagrams (if applicable)
  - User approval
