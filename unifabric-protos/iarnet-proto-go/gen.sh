#!/usr/bin/env bash
# 将 proto 生成到本目录 generated/ 下。
# 需安装: protoc, protoc-gen-go, protoc-gen-go-grpc
#   go install google.golang.org/protobuf/cmd/protoc-gen-go@latest
#   go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest
# 各 .proto 需包含 option go_package = "…";
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROTO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
OUT="$SCRIPT_DIR/generated"
mkdir -p "$OUT"
echo "Go 生成目录: $OUT"
echo "在 $PROTO_ROOT/proto 下执行 protoc，并确保 proto 中已配置 go_package。"
echo "示例: protoc -I proto --go_out=$OUT --go-grpc_out=$OUT proto/iarnet/ir/*.proto …"
