#!/usr/bin/env bash
# Regenerates UniFFI Kotlin and Swift/C bindings from meta-secret-core.
#
# Xcode: if the IDE reports DVTDeviceIneligibilityError Code 27 ("iOS … is not installed"),
# install the matching platform in Xcode > Settings > Platforms (or Components), then retry.
# Usage: META_SECRET_CORE_ROOT=<path> ./scripts/sync-uniffi-from-core.sh
#    or: ./scripts/sync-uniffi-from-core.sh <path-to-meta-secret-core>
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

CORE_ROOT="${1:-${META_SECRET_CORE_ROOT:-}}"
if [[ -z "${CORE_ROOT}" ]]; then
  echo "Usage: $0 <path-to-meta-secret-core>" >&2
  echo "   or: META_SECRET_CORE_ROOT=<path> $0" >&2
  exit 1
fi

if ! command -v cargo >/dev/null 2>&1; then
  echo "ERROR: cargo not found. Install Rust (https://rustup.rs/) and ensure cargo is on PATH." >&2
  exit 1
fi

CORE_ROOT="$(cd "${CORE_ROOT}" && pwd)"
WORKSPACE="${CORE_ROOT}/meta-secret"
UDL="${WORKSPACE}/mobile/uniffi/src/mobile_uniffi.udl"

if [[ ! -f "${UDL}" ]]; then
  echo "ERROR: UDL not found at ${UDL}. Is META_SECRET_CORE_ROOT the repo root (contains meta-secret/)?" >&2
  exit 1
fi

KOTLIN_BASE="${COMPOSE_ROOT}/composeApp/src/androidMain/kotlin/com/metasecret/core/uniffi"
SWIFT_OUT_A="${COMPOSE_ROOT}/iosApp/iosApp/UniffiGenerated"
SWIFT_OUT_B="${COMPOSE_ROOT}/iosApp/iosApp/MetaSecretCoreService/UniffiGenerated"

TMP_GEN="$(mktemp -d)"
cleanup() { rm -rf "${TMP_GEN}"; }
trap cleanup EXIT

(
  cd "${WORKSPACE}"
  cargo run -p uniffi-bindgen-runner -- generate -n -l kotlin -l swift -o "${TMP_GEN}" "${UDL}"
)

rm -rf "${KOTLIN_BASE}/uniffi"
mkdir -p "${KOTLIN_BASE}"
cp -R "${TMP_GEN}/uniffi" "${KOTLIN_BASE}/"

for d in "${SWIFT_OUT_A}" "${SWIFT_OUT_B}"; do
  mkdir -p "${d}"
  cp -f "${TMP_GEN}/mobile_uniffi.swift" "${TMP_GEN}/mobile_uniffiFFI.h" "${TMP_GEN}/mobile_uniffiFFI.modulemap" "${d}/"
done

# Swift 6: UniFFI emits `private var initializationResult` which fails as nonisolated global
# mutable state. The value is never reassigned; `let` is correct and satisfies the compiler.
patch_swift6_uniffi_init() {
  local f="$1"
  [[ -f "${f}" ]] || return 0
  grep -q 'private var initializationResult' "${f}" || return 0
  if sed --version >/dev/null 2>&1; then
    sed -i 's/^private var initializationResult/private let initializationResult/' "${f}"
  else
    sed -i '' 's/^private var initializationResult/private let initializationResult/' "${f}"
  fi
}
for d in "${SWIFT_OUT_A}" "${SWIFT_OUT_B}"; do
  patch_swift6_uniffi_init "${d}/mobile_uniffi.swift"
done

echo "UniFFI bindings written to Kotlin and both UniffiGenerated directories."
