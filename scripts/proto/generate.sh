#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==> Generating all proto code..."

bash "${SCRIPT_DIR}/generate-java.sh"
bash "${SCRIPT_DIR}/generate-python.sh"
bash "${SCRIPT_DIR}/generate-go.sh"

echo "==> All proto code generated successfully."