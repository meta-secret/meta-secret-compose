# Rule — Questioning Guide

## Purpose

Guide adaptive requirements clarification process. Ask only relevant questions that arise from issue analysis. Ensure comprehensive understanding before implementation.

## Grill Me Approach (CRITICAL)

**Interview relentlessly until shared understanding is reached.** Use the "Grill Me" methodology:

1. **Ask one question at a time** — walk down the decision tree
2. **Drill deep into each branch** — resolve dependencies one by one
3. **Provide recommendations** — for each question, give your best answer
4. **Use the codebase** — if a question can be answered by code exploration, do it
5. **Track decisions** — maintain a map of decisions and their dependencies
6. **Confirm understanding** — "Is that right?" frequently

**Duration (Adaptive):**
- Simple: 5-10 min (button color, text fix)
- Medium: 15-25 min (feature addition)
- Complex: 30-45 min (architecture, security)
- Stop when shared understanding reached

**Output:** Shared understanding with all decision branches explored and dependencies resolved

**Examples:**
- Issue: "Fix button color on login screen"
  - **5 min:** Which button? Which color? Which platforms?
  - Done.
  
- Issue: "Implement end-to-end encryption for messages"
  - **45 min:** Key generation → storage → rotation → error handling → interop → integration points
  - Thorough exploration of all decision branches

## Categories of Questions (Use Selectively)

### 1. Constraints
- What are hard limits? (performance, memory, network, battery)
- What platform-specific constraints exist?
- Are there legal/security constraints?
- What legacy integrations must we support?

### 2. Boundary Cases
- What happens at min/max values?
- What happens with concurrent access?
- What happens with offline mode?
- What happens with network failures?
- What happens with invalid user input?

### 3. Error Handling
- What should happen on network timeout?
- What should happen on invalid data?
- How should errors be logged?
- Should errors be visible to user?
- What's the recovery strategy?

### 4. Dependencies
- Does this depend on other features?
- What features depend on this?
- Are there API contract changes?
- Do we need to update documentation?

### 5. Non-functional Requirements
- Performance expectations?
- Accessibility requirements?
- Internationalization needed?
- Analytics/logging requirements?
- Testing coverage expectations?

## Questioning Approach

1. **Ask as a group** — combine related questions
2. **Explore deeply** — "why" and "what if" questions
3. **Confirm understanding** — repeat back what you heard
4. **Get explicit approval** — "Are we aligned?" before proceeding
5. **Document** — record all clarifications in artifact

## Halt Conditions

Stop asking and report `Status: FAILED` if:
- Requirement is contradictory or impossible
- User cannot provide clarity
- Scope is too large (suggest breaking into smaller tasks)
