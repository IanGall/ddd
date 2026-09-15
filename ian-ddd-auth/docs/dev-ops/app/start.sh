#!/usr/bin/env bash
# 认证服务容器部署脚本。
#
# 前置：镜像已构建（在 ian-ddd-auth-boot 下 docker build）。
# 凭证：通过 --env-file 注入，默认读取仓库根 .env.local（不入库，模板见 .env.example）；
#       文件不存在时回退到当前环境变量，生产由部署平台注入同名变量。
#
# 用法：
#   bash docs/dev-ops/app/start.sh
#   IMAGE_NAME=system/ian-ddd-auth-boot:v1 PORT=20880 bash docs/dev-ops/app/start.sh
#
# 端口说明：认证服务运行期只有 Dubbo Provider，没有 servlet 容器，
# application.yml 的 server.port: 8091 从不被监听，因此这里映射的是 Dubbo 端口。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$(cd "${SCRIPT_DIR}/../../.." && pwd)"    # ian-ddd-auth
REPO_DIR="$(cd "${MODULE_DIR}/.." && pwd)"            # 仓库根（ddd）

CONTAINER_NAME="${CONTAINER_NAME:-ian-ddd-auth}"
IMAGE_NAME="${IMAGE_NAME:-system/ian-ddd-auth-boot:latest}"
PORT="${PORT:-20880}"
ENV_FILE="${ENV_FILE:-${REPO_DIR}/.env.local}"

echo "容器部署开始 ${CONTAINER_NAME}（镜像 ${IMAGE_NAME}，端口 ${PORT}）"

# 幂等：同名容器存在则先停止并删除
docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true

RUN_ARGS=(
  --name "${CONTAINER_NAME}"
  -p "${PORT}:${PORT}"
  # 日志轮转：单文件 10MB、保留 3 份，避免容器日志无限增长
  --log-opt max-size=10m
  --log-opt max-file=3
)
if [[ -f "${ENV_FILE}" ]]; then
  RUN_ARGS+=(--env-file "${ENV_FILE}")
  echo "凭证来源：${ENV_FILE}"
else
  echo "[提示] 未找到 ${ENV_FILE}，凭证直接从当前环境变量读取（模板见仓库根 .env.example）"
fi

# SPRING_PROFILES_ACTIVE=prod 已由镜像固定（见 Dockerfile）
docker run "${RUN_ARGS[@]}" -d "${IMAGE_NAME}"

echo "容器部署成功 ${CONTAINER_NAME}"
docker logs -f "${CONTAINER_NAME}"
