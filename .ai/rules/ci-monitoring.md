# Rule: Compose CI Monitoring and Auto-Fix Boundaries

Compose CI is a post-PR release gate. Stage 11 must inspect the exact GitHub
Actions run associated with the PR head SHA and must not report a newer or
superseded run as evidence for the current change.

## Failure handling

| Classification | Retry policy | Automatic code change |
| --- | --- | --- |
| Passed | Continue | No |
| Cancelled/superseded | Do not retry; inspect newest SHA | No |
| Timeout/infrastructure | At most one bounded rerun | No |
| Dependency | No automatic loop | No |
| Permission | Fix credentials/policy manually | No |
| Product/source | Do not rerun blindly | Cursor only under the guard below |
| Unknown/mixed | Stop and ask for diagnosis | No |

## Cursor guard

`cursor-fix.yml` is intentionally separate from the read-only monitor. It may
run only for a failed `Compose CI` pull-request run where:

- `workflow_run.event` is `pull_request`;
- `workflow_run.head_repository.full_name == github.repository`;
- the evidence is classified as a single `product` class;
- `CURSOR_API_KEY` exists; and
- no unexpired `cursor-fix-attempt-<head_sha>` artifact exists.

The guard and prompt come from the default branch, not the untrusted PR. The
attempt marker is uploaded before Cursor starts. Cursor receives only bounded,
redacted logs and may change focused Kotlin/Swift source. It must not edit CI,
AI rules, secrets, generated FFI/native artifacts, Rust/Core code, or unrelated
dependencies. No token, key, share, password, or raw secret material may be
included in artifacts or prompts.

## Release gate

The monitor artifact is diagnostic and never converts a failed `Compose CI`
run to success. A product, unknown, permission, dependency, timeout, or
infrastructure classification blocks release until the appropriate rerun or
human review passes on a new/equivalent SHA.
