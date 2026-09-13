#!/usr/bin/env bash
# 停止并删除认证服务容器（不存在时静默跳过）。
set -euo pipefail

CONTAINER_NAME="${CONTAINER_NAME:-ian-ddd-auth}"

docker rm -f "${CONTAINER_NAME}"
echo "容器已停止并删除 ${CONTAINER_NAME}"
