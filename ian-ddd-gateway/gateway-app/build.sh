#!/usr/bin/env bash
# 镜像构建脚本。Dockerfile 本身与架构无关（基础镜像是 amd64/arm64 双清单），这里只决定构建哪些架构。
#
# 默认：**自动跟随本机（Docker 服务端）架构**——arm64 机器上出 arm64 镜像、amd64 机器上出 amd64 镜像，
#       并载入本地镜像库（可直接 docker run，本地 k8s 也能直接用）
#   bash gateway-app/build.sh
#
# 指定架构（例如在 arm64 机器上出 amd64 镜像，会走 QEMU/Rosetta 仿真，较慢）：
#   PLATFORMS=linux/amd64 bash gateway-app/build.sh
#
# 双架构（linux/amd64 + linux/arm64）：多平台镜像无法 --load 到本地，必须推到镜像仓库
#   IMAGE=<可推送的仓库>/ian-ddd-gateway PLATFORMS=linux/amd64,linux/arm64 bash gateway-app/build.sh
#   首次还需要一个支持 manifest list 的 builder：
#   docker buildx create --name multiarch --driver docker-container --bootstrap --use
set -euo pipefail

# 允许从任意目录调用（例如在仓库根执行 bash <模块>/build.sh）：切到脚本所在目录，
# 因为下面的 -f ./Dockerfile 与构建上下文 . 都是相对当前目录解析的
cd "$(dirname "$0")"

# 镜像名带**项目前缀**（从项目目录名派生），与 scripts/deploy-local.sh 的派生规则一致。
# 原因：本机 Docker 镜像库全局共享，两个项目构建同名镜像会互相覆盖，
# 导致 k8s 在 Pod 重启/扩容时把别的项目的镜像拉进本命名空间（Pod 名相同，极难排查）。
_PROJ_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
_PROJ_BASE="$(basename "${_PROJ_ROOT}")"
case "${_PROJ_BASE}" in ian-*) _PROJ_PREFIX="${_PROJ_BASE#ian-}" ;; *) _PROJ_PREFIX="ddd" ;; esac
IMAGE="${IMAGE:-system/ian-${_PROJ_PREFIX}-gateway}"
TAG="${TAG:-1.0-SNAPSHOT}"

# 未显式指定架构时跟随 Docker 服务端架构；守护进程不可达时退回本机 uname
detect_platform() {
  local arch
  arch="$(docker version --format '{{.Server.Arch}}' 2>/dev/null || true)"
  [ -n "${arch}" ] || arch="$(uname -m)"
  case "${arch}" in
    x86_64 | amd64) echo "linux/amd64" ;;
    aarch64 | arm64) echo "linux/arm64" ;;
    armv7l | armv7) echo "linux/arm/v7" ;;
    *) echo "linux/${arch}" ;;
  esac
}
PLATFORMS="${PLATFORMS:-$(detect_platform)}"

echo "构建 ${IMAGE}:${TAG}（${PLATFORMS}）"
case "${PLATFORMS}" in
  *,*)
    # 多平台镜像无法 --load 到本地，只能推送（manifest list）
    docker buildx build --platform "${PLATFORMS}" -t "${IMAGE}:${TAG}" -f ./Dockerfile . --push
    ;;
  *)
    docker buildx build --platform "${PLATFORMS}" --load -t "${IMAGE}:${TAG}" -f ./Dockerfile .
    ;;
esac
