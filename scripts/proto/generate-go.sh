#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

PROTO_DIR="${ROOT_DIR}/unifabric-protos/proto"
GO_OUT="${ROOT_DIR}/unifabric-protos/unifabric-proto-go/gen"

if ! command -v protoc >/dev/null 2>&1; then
  echo "Error: protoc is not installed or not in PATH."
  exit 1
fi

if ! command -v protoc-gen-go >/dev/null 2>&1; then
  echo "Error: protoc-gen-go is not installed or not in PATH."
  exit 1
fi

if ! command -v protoc-gen-go-grpc >/dev/null 2>&1; then
  echo "Error: protoc-gen-go-grpc is not installed or not in PATH."
  exit 1
fi

mkdir -p "${GO_OUT}"

mapfile -t PROTO_FILES < <(find "${PROTO_DIR}" -type f -name "*.proto" | sort)

if [[ ${#PROTO_FILES[@]} -eq 0 ]]; then
  echo "Error: no .proto files found in ${PROTO_DIR}"
  exit 1
fi

echo "==> Generating Go protobuf code..."
protoc \
  -I "${PROTO_DIR}" \
  --go_out="${GO_OUT}" \
  --go_opt=paths=source_relative \
  "${PROTO_FILES[@]}"

echo "==> Generating Go gRPC code..."
protoc \
  -I "${PROTO_DIR}" \
  --go-grpc_out="${GO_OUT}" \
  --go-grpc_opt=paths=source_relative \
  "${PROTO_FILES[@]}"

echo "==> Go proto code generated to ${GO_OUT}"
