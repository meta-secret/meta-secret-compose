# Build Commands

Use the narrowest useful command first.

## General commands

### Fast shared module build

Command:

    ./gradlew :shared:assemble --stacktrace

### Full project build

Command:

    ./gradlew build --stacktrace --info


## When the problem looks like Kotlin / shared code

Prefer running:

    ./gradlew :shared:assemble --stacktrace


## When the problem looks like Swift/iOS integration

Typical causes:

- framework import mismatch
- symbol mismatch
- nullability / signature mismatch
- generated artifacts stale

If needed, run a wider build:

    ./gradlew build --stacktrace --info


## Build policy

1. Start with the narrowest command.
2. Expand build scope only if necessary.
3. Do not start with the heaviest build unless the failure scope is unclear.