#!/usr/bin/env bash
# iarnet-actor-java 构建与镜像脚本
# 使用方式:
#   ./scripts/actor/build-java [tag_name]
# 从仓库根目录执行，或直接执行本脚本（会自动定位仓库根目录）

set -e

DEFAULT_TAG="iarnet-actor-java:latest"
IMAGE_TAG="${1:-$DEFAULT_TAG}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ACTOR_JAVA_DIR="${ROOT_DIR}/iarnet-actors/iarnet-actor-java"

echo -e "${YELLOW}开始构建 iarnet-actor-java...${NC}"
echo -e "${YELLOW}仓库根目录: $ROOT_DIR${NC}"
echo -e "${YELLOW}模块目录: $ACTOR_JAVA_DIR${NC}"
echo -e "${YELLOW}目标镜像标签: $IMAGE_TAG${NC}"

# 构建可执行 JAR
echo -e "${YELLOW}构建可执行 JAR...${NC}"
cd "$ROOT_DIR"
mvn -f iarnet-actors/iarnet-actor-java/pom.xml package -q

# 构建 Docker 镜像
if ! docker info >/dev/null 2>&1; then
    echo -e "${RED}错误: Docker daemon 未运行${NC}"
    exit 1
fi

echo -e "${YELLOW}构建 Docker 镜像...${NC}"
cd "$ACTOR_JAVA_DIR"
if docker build -t "$IMAGE_TAG" .; then
    echo -e "${GREEN}✅ iarnet-actor-java 镜像构建成功!${NC}"
    echo -e "${GREEN}镜像标签: $IMAGE_TAG${NC}"
    docker images "$IMAGE_TAG"
    echo -e "${YELLOW}运行示例:${NC}"
    echo "  docker run --rm -e ACTOR_SERVER_PORT=9000 -p 9000:9000 $IMAGE_TAG"
else
    echo -e "${RED}❌ iarnet-actor-java 镜像构建失败!${NC}"
    exit 1
fi
