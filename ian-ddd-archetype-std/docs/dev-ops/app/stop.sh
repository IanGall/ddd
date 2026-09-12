#!/usr/bin/env bash
# 停止并删除标准服务容器（不存在时静默跳过）。
set -euo pipefail

CONTAINER_NAME="${CONTAINER_NAME:-ian-frame-archetype-std}"

docker rm -f "${CONTAINER_NAME}"
echo "容器已停止并删除 ${CONTAINER_NAME}"
