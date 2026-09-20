#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
CONSTRAINTS="$ROOT_DIR/.ai/CONSTRAINTS.md"
VAULT_MODEL="$ROOT_DIR/.ai/constraints-vault-model.md"
GLOSSARY="$ROOT_DIR/.ai/GLOSSARY.md"

require_text() {
  local file="$1"
  local expected="$2"
  if ! grep -Fq -- "$expected" "$file"; then
    echo "Missing policy text in ${file#$ROOT_DIR/}: $expected" >&2
    exit 1
  fi
}

require_text "$CONSTRAINTS" "Sharing scheme: **k = 1 for 1 device, k = 1 for 2 devices, and k = 2 for 3 devices**"
require_text "$CONSTRAINTS" "A fourth or later device is rejected by Core"
require_text "$VAULT_MODEL" 'there is no generic `k=n−1` rule'
require_text "$GLOSSARY" 'n=3: k=2'

# The quick-reference constraints must never reintroduce the old generic rule
# or an unsupported 3+ device state.
if grep -Eiq 'k[[:space:]]*=[[:space:]]*n[[:space:]]*[-−][[:space:]]*1|3\+[[:space:]]+devices' "$CONSTRAINTS"; then
  echo "Unsupported generic K-of-N wording found in .ai/CONSTRAINTS.md" >&2
  exit 1
fi

echo "Vault K-of-N documentation is consistent (1/2/3 devices: k=1/k=1/k=2)."
