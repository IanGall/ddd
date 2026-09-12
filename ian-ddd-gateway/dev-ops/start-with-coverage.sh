#!/usr/bin/env bash
# 带 JaCoCo Agent 启动 Gateway，用于分布式 E2E 覆盖率采集。
#
# Agent 以 tcpserver 模式在本机端口监听，由 coverage-controller 主动连接 dump，
# 因此 Gateway 无需感知控制器地址，也不要求控制器先启动。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Agent 唯一标识，需与 coverage-controller 配置的 coverage.agents[].name 一致
COVERAGE_AGENT_NAME="${COVERAGE_AGENT_NAME:-gateway}"
# Agent 监听端口，约定 gateway 使用 6300，其余服务依次递增
COVERAGE_AGENT_PORT="${COVERAGE_AGENT_PORT:-6300}"
COVERAGE_INCLUDES="${COVERAGE_INCLUDES:-cn.iantech.*}"

JACOCO_VERSION="${JACOCO_VERSION:-0.8.13}"
JACOCO_AGENT_JAR="${JACOCO_AGENT_JAR:-${HOME}/.m2/repository/org/jacoco/org.jacoco.agent/${JACOCO_VERSION}/org.jacoco.agent-${JACOCO_VERSION}-runtime.jar}"

if [[ ! -f "${JACOCO_AGENT_JAR}" ]]; then
  echo "未找到 jacocoagent: ${JACOCO_AGENT_JAR}" >&2
  echo "请先执行: mvn dependency:get -Dartifact=org.jacoco:org.jacoco.agent:${JACOCO_VERSION}:jar:runtime" >&2
  exit 1
fi

AGENT_OPTS="-javaagent:${JACOCO_AGENT_JAR}=output=tcpserver,address=127.0.0.1,port=${COVERAGE_AGENT_PORT},sessionid=${COVERAGE_AGENT_NAME},includes=${COVERAGE_INCLUDES},dumponexit=true"

echo "启动 Gateway，覆盖率 Agent: ${COVERAGE_AGENT_NAME}@127.0.0.1:${COVERAGE_AGENT_PORT}"

cd "${PROJECT_DIR}/gateway-app"
exec mvn -q spring-boot:run -Dspring-boot.run.jvmArguments="${AGENT_OPTS}"
