#!/usr/bin/env bash
# 带 JaCoCo Agent 启动 Gateway，用于分布式 E2E 覆盖率采集。
#
# Agent 以 tcpserver 模式在本机端口监听，由 coverage-controller 主动连接 dump，
# 因此 Gateway 无需感知控制器地址，也不要求控制器先启动。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

# 加载仓库根目录的 .env.local（本地开发凭证，不入库；模板见仓库根 .env.example）。
# 生产环境由部署平台注入同名环境变量，不依赖本文件。
load_local_env() {
  local probe="${SCRIPT_DIR}" i=0
  while (( i < 8 )); do
    if [[ -f "${probe}/.env.local" ]]; then
      set -a
      # shellcheck disable=SC1091
      . "${probe}/.env.local"
      set +a
      return 0
    fi
    probe="$(dirname "${probe}")"
    (( i++ )) || true
  done
  return 1
}
load_local_env || echo "[提示] 未找到仓库根的 .env.local，凭证直接读取当前环境变量（模板见 .env.example）" >&2

# Agent 唯一标识，需与 coverage-controller 配置的 coverage.agents[].name 一致
COVERAGE_AGENT_NAME="${COVERAGE_AGENT_NAME:-gateway}"
# Agent 监听端口，约定 gateway 使用 6300，其余服务依次递增
COVERAGE_AGENT_PORT="${COVERAGE_AGENT_PORT:-6300}"
COVERAGE_INCLUDES="${COVERAGE_INCLUDES:-cn.iantech.*}"

# JaCoCo 版本唯一来源：仓库根目录的 .mvn/jacoco-version（与 ddd-base/pom.xml 的 jacoco.version 保持一致）
read_jacoco_version() {
  local probe="${SCRIPT_DIR}"
  local i=0
  while (( i < 12 )); do
    if [[ -f "${probe}/.mvn/jacoco-version" ]]; then
      tr -d '[:space:]' < "${probe}/.mvn/jacoco-version"
      return 0
    fi
    probe="$(dirname "${probe}")"
    (( i++ )) || true
  done
  return 1
}

JACOCO_VERSION="${JACOCO_VERSION:-$(read_jacoco_version || true)}"
if [[ -z "${JACOCO_VERSION}" ]]; then
  echo "无法确定 JaCoCo 版本：请确认仓库根目录存在 .mvn/jacoco-version，或设置 JACOCO_VERSION" >&2
  exit 1
fi
JACOCO_AGENT_JAR="${JACOCO_AGENT_JAR:-${HOME}/.m2/repository/org/jacoco/org.jacoco.agent/${JACOCO_VERSION}/org.jacoco.agent-${JACOCO_VERSION}-runtime.jar}"

if [[ ! -f "${JACOCO_AGENT_JAR}" ]]; then
  echo "未找到 jacocoagent: ${JACOCO_AGENT_JAR}" >&2
  echo "请先执行: mvn dependency:get -Dartifact=org.jacoco:org.jacoco.agent:${JACOCO_VERSION}:jar:runtime" >&2
  exit 1
fi

AGENT_OPTS="-javaagent:${JACOCO_AGENT_JAR}=output=tcpserver,address=127.0.0.1,port=${COVERAGE_AGENT_PORT},sessionid=${COVERAGE_AGENT_NAME},includes=${COVERAGE_INCLUDES},dumponexit=true"

echo "启动 Gateway，覆盖率 Agent: ${COVERAGE_AGENT_NAME}@127.0.0.1:${COVERAGE_AGENT_PORT}"

# 与标准服务保持同一套自动化测试配置（网关本身不持有数据源）：dev,autotest
COVERAGE_SPRING_PROFILES="${COVERAGE_SPRING_PROFILES:-dev,autotest}"

cd "${PROJECT_DIR}/gateway-app"
exec mvn -q spring-boot:run \
  -Dspring-boot.run.profiles="${COVERAGE_SPRING_PROFILES}" \
  -Dspring-boot.run.jvmArguments="${AGENT_OPTS}"
