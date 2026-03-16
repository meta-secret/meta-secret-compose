# Generation Rules

When generating NEW code:

1. Identify the correct layer first.
2. Prefer extending existing abstractions over inventing parallel structures.
3. Keep interfaces narrow and focused.
4. Keep ViewModels orchestration-focused, not business-logic-heavy.
5. Keep Views/UI free from business logic.
6. Do not bypass core interfaces.
7. Do not leak platform-specific details into shared/core without explicit design.
8. Prefer minimal new types over giant multifunction classes.
9. Public APIs should expose abstractions, not concrete implementations.
10. If architecture placement is unclear, stop and ask for direction instead of generating code.