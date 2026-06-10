# Skill — Glossary Builder

## Purpose

Build and maintain the project's unified vocabulary. Ensure AI, code, documentation, and user communication all use the same terminology to avoid confusion and improve clarity.

## How It Works

Analyze the codebase, documentation, and communication to:
1. **Extract domain terminology** — what are the core concepts in this system?
2. **Define precisely** — what does each term mean in this specific context?
3. **Identify context** — where and how is this term used?
4. **Document examples** — show real usage in code and docs
5. **Build glossary** — create single source of truth for terminology
6. **Review with user** — confirm terms match their mental model
7. **Make it binding** — ensure all AI communication uses this glossary

## Output Format

Create GLOSSARY.md with table:

```
| Term | Definition | Context | Example |
|------|-----------|---------|---------|
| [Term] | Precise definition in this project | Where/when used | Code or doc reference |
```

## Process

### Phase 1: Discovery
- Scan codebase for class/function names, entities, domain concepts
- Extract key abstractions and their relationships
- Identify user-facing terms and developer terms

### Phase 2: Definition
- Write precise definitions that fit THIS project
- Clarify edge cases and boundary conditions
- Show relationships between terms

### Phase 3: Documentation
- Create table with all terms
- Add real examples from codebase
- Link to relevant code locations

### Phase 4: Review
- Present glossary to user
- Get approval and corrections
- Make final version binding

## Why This Matters

- **AI Communication:** All responses use these terms consistently
- **Code Quality:** Variable/class names match glossary
- **Documentation:** Written in consistent terminology
- **Onboarding:** New team members learn the language first
- **Reduced Confusion:** Everyone talks about the same thing the same way
