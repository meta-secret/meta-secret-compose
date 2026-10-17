# Stage completeness and applicability

Use WORKFLOW.md for stage order, rules/coverage-policy.md for mobile coverage and rules/ui-migration-profile.md for approved UI migration applicability. Every stage has an artifact; a documented Not applicable decision must identify the reason and relevant replacement evidence. Task size alone does not justify skipping a review.

- Clarification records existing user decisions and asks only unresolved consequential questions. Do not repeat settled questions or impose a minimum discussion duration.
- Planner and constraint validation precede implementation. Keep meaningful behavior tests in the existing TDD stages; verify appearance/motion through approved-reference captures rather than implementation-mirroring tests.
- Code review, applicable visual/motion review, configured coverage-report validation and relevant test execution remain required. Overall 80% coverage is an advisory goal; a lower percentage alone does not fail a gate.
- Compile changed clients and use cheap checks before expensive platform E2E. Reuse unchanged native artifacts; do not rebuild unrelated Rust for Vue/CSS changes.
- Final applicable UI E2E remains before release through the existing root skill; select scenarios/roles/repetitions explicitly and report failures visibly.
- Release records explicit authorization for each action. Existing session permission to commit is sufficient for commits; it does not grant push/PR/merge/deploy. Ask only for an unauthorized action once the result is concrete and reviewable.

Preserve the workflow's actual stage sequence, including constraint validation and the conditional final UI E2E stage. No fabricated success, missing artifacts or silent coverage measurement claims.
