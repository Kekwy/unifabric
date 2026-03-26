#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

PROTO_DIR="${ROOT_DIR}/unifabric-protos/proto"
PY_OUT="${ROOT_DIR}/unifabric-protos/unifabric-proto-python/gen"

if ! command -v python >/dev/null 2>&1; then
  echo "Error: python is not installed or not in PATH."
  exit 1
fi

if ! python -c "import grpc_tools.protoc" >/dev/null 2>&1; then
  echo "Error: grpc_tools is not installed."
  echo "Please run: python -m pip install grpcio-tools protobuf"
  exit 1
fi

mkdir -p "${PY_OUT}"

mapfile -t PROTO_FILES < <(find "${PROTO_DIR}" -type f -name "*.proto" | sort)

if [[ ${#PROTO_FILES[@]} -eq 0 ]]; then
  echo "Error: no .proto files found in ${PROTO_DIR}"
  exit 1
fi

echo "==> Generating Python protobuf and gRPC code..."
python -m grpc_tools.protoc \
  -I "${PROTO_DIR}" \
  --python_out="${PY_OUT}" \
  --grpc_python_out="${PY_OUT}" \
  "${PROTO_FILES[@]}"

echo "==> Ensuring Python package markers..."
while IFS= read -r -d '' dir; do
  touch "${dir}/__init__.py"
done < <(find "${PY_OUT}" -type d -print0)

echo "==> Python proto code generated to ${PY_OUT}"
