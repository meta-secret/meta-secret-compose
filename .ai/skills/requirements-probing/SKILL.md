# Skill — Requirements Probing

## Purpose

Structure and guide adaptive clarification questions. Identify what's unclear in the issue and ask ONLY relevant questions. Help agent dig deep without overwhelming user.

## Approach (ADAPTIVE)

**DO NOT follow all phases.** Adapt to the issue:

### Phase 1: Issue Review (ALWAYS)
1. Summarize what you understand from issue
2. State assumptions you're making
3. Ask: "Did I understand this correctly?"
4. **Identify unclear areas** — what's ambiguous or vague?
5. **Identify risk areas** — what could go wrong?

### Phase 2-5: Ask ONLY about unclear/risk areas

**Constraint Discovery** (if relevant)
- Ask ONLY about constraints mentioned or implied in issue
- Skip if issue is straightforward

**Boundary Exploration** (if relevant)
- Ask ONLY about edge cases likely for this feature
- Skip if scope is simple

**Dependency Mapping** (if relevant)
- Ask ONLY about visible dependencies
- Skip if feature is isolated

**Confirmation** (ALWAYS)
1. Summarize clarifications made
2. Ask: "Are we ready to proceed?"
3. Document all answers

## Output Format

```
## Clarifications Made

### Constraints
- [constraint 1]
- [constraint 2]

### Boundary Cases
- [case 1]
- [case 2]

### Error Handling
- [error 1]: [handling]
- [error 2]: [handling]

### Dependencies
- [dependency 1]
- [dependency 2]

### User Approval
[ ] Requirements fully clarified
[ ] User confirmed understanding
```

## Tips

- Ask one group of questions per turn
- Use "Why" and "What if" liberally
- Write answers in user's own words
- Stop when user says "yes, that's it"
