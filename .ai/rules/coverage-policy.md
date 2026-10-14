# Mobile coverage policy

Overall 80% line coverage is an advisory goal, not a release threshold. The user approved this policy after measuring 15.49% overall and finding generated bindings/resources and screen rendering in the report.

- Generate a valid, non-empty report with `./gradlew koverVerify`; invalid or missing reports fail.
- Low percentage alone emits a warning. All applicable tests must pass.
- Review important changed behavior and record uncovered risks; do not write superficial tests or hide code to inflate coverage.
- Preserve the overall metric, including its measured scope. Platform OS behavior and real Core contracts require integration/E2E evidence.
- Optional strict audit: `python3 .ai/scripts/verify-coverage.py --strict composeApp/build/reports/kover/report.xml` enforces 80%.
- This policy applies only to Compose, not Rust Core. Revisit a mandatory numeric threshold after measuring representative client logic separately.
