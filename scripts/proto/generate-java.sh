#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

PROTO_DIR="${ROOT_DIR}/unifabric-protos/proto"
JAVA_OUT="${ROOT_DIR}/unifabric-protos/unifabric-proto-java/src/main/generated"

if ! command -v protoc >/dev/null 2>&1; then
  echo "Error: protoc is not installed or not in PATH."
  exit 1
fi

if ! command -v protoc-gen-grpc-java >/dev/null 2>&1; then
  echo "Error: protoc-gen-grpc-java is not installed or not in PATH."
  exit 1
fi

mkdir -p "${JAVA_OUT}"

mapfile -t PROTO_FILES < <(find "${PROTO_DIR}" -type f -name "*.proto" | sort)

if [[ ${#PROTO_FILES[@]} -eq 0 ]]; then
  echo "Error: no .proto files found in ${PROTO_DIR}"
  exit 1
fi

echo "==> Generating Java protobuf code..."
protoc \
  -I "${PROTO_DIR}" \
  --java_out="${JAVA_OUT}" \
  "${PROTO_FILES[@]}"

echo "==> Generating Java gRPC code..."
protoc \
  -I "${PROTO_DIR}" \
  --plugin=protoc-gen-grpc-java="$(command -v protoc-gen-grpc-java)" \
  --grpc-java_out="${JAVA_OUT}" \
  "${PROTO_FILES[@]}"

echo "==> Java proto code generated to ${JAVA_OUT}"
