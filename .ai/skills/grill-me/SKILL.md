# Skill — Grill Me

## Purpose

Interview user relentlessly about requirements until reaching shared understanding. Walk down each branch of the decision tree, resolving dependencies between decisions one by one.

## How It Works

Interview me relentlessly about every aspect of this plan until we reach a shared understanding. Walk down each branch of the design tree resolving dependencies between decisions one by one.

If a question can be answered by exploring the codebase, explore the codebase instead.

For each question, provide your recommended answer.

## Duration (Adaptive!)

- **Simple tasks** (button color, text change): 5-10 minutes
- **Medium tasks** (feature addition): 15-25 minutes  
- **Complex tasks** (architecture, E2E encryption): 30-45 minutes
- **Very complex tasks** (major redesign): 45+ minutes

**Stop when user says "I'm satisfied" or when shared understanding is reached.**

## Approach

### 1. Ask Questions (Only As Needed)
- One question per turn (not overwhelming)
- Dig deep into unclear/risky areas
- Follow the decision tree
- Don't move on until you understand
- **Skip areas that are clear** — don't ask if you already understand

### 2. Resolve Dependencies
- Identify which decisions depend on others
- Resolve in correct order
- Call out conflicts or contradictions
- Get explicit agreement on each decision

### 3. Provide Recommendations
- For each question, give your best recommendation
- Explain the reasoning
- Offer alternatives if appropriate
- Let user confirm or redirect

### 4. Use the Codebase
- When a question can be answered by code exploration, do it
- Don't guess about existing patterns
- Show code examples
- Propose aligned solutions

## Session Structure

**Duration:** ~45 minutes for thorough grilling

**Output:** Rich conversation with:
- Context and constraints identified
- All decision branches explored
- Dependencies resolved
- Shared understanding reached
- Summary of all clarifications

## Example Flow

1. "I understand you want to add E2E encryption. Let me ask about the first decision branch: Key Management. How should keys be generated and stored?"
2. *User answers*
3. "Got it. That means we need X, which depends on Y. Should we do Y first?"
4. *Continue until all branches explored*
5. "Here's my summary of our shared understanding..."

## Tips

- Start broad, then drill down
- Track all decisions and dependencies
- Confirm understanding frequently: "Is that right?"
- Be thorough but not exhausting
- Stop when user says "I'm satisfied"
