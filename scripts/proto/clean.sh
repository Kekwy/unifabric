#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

JAVA_OUT="${ROOT_DIR}/unifabric-protos/unifabric-proto-java/src/main/generated"
PY_OUT="${ROOT_DIR}/unifabric-protos/unifabric-proto-python/gen"
GO_OUT="${ROOT_DIR}/unifabric-protos/unifabric-proto-go/gen"

clean_dir() {
  local dir="$1"
  if [[ -d "$dir" ]]; then
    echo "==> Cleaning ${dir}"
    find "$dir" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
  else
    echo "==> Directory does not exist, skipping: ${dir}"
  fi
}

clean_dir "${JAVA_OUT}"
clean_dir "${PY_OUT}"
clean_dir "${GO_OUT}"

echo "==> Proto generated code cleaned."
