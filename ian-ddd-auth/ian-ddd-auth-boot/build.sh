#!/usr/bin/env bash
# 镜像构建脚本。Dockerfile 本身与架构无关（基础镜像是 amd64/arm64 双清单），这里只决定构建哪些架构。
#
# 默认：构建本机架构并载入本地镜像（可直接 docker run，本地 k8s 也能直接用）
#   bash build.sh
#
# 双架构（linux/amd64 + linux/arm64）：
#   IMAGE=<可推送的仓库>/ian-ddd-auth-boot PLATFORMS=linux/amd64,linux/arm64 bash build.sh
#   多平台镜像无法 --load 到本地，必须推送；首次还需要一个支持 manifest list 的 builder：
#   docker buildx create --name multiarch --driver docker-container --bootstrap --use
set -euo pipefail

IMAGE="${IMAGE:-system/ian-ddd-auth-boot}"
TAG="${TAG:-1.0-SNAPSHOT}"
PLATFORMS="${PLATFORMS:-}"

if [ -z "${PLATFORMS}" ]; then
  echo "构建本机架构镜像：${IMAGE}:${TAG}"
  docker buildx build --load -t "${IMAGE}:${TAG}" -f ./Dockerfile .
else
  echo "构建多架构镜像并推送：${IMAGE}:${TAG}（${PLATFORMS}）"
  docker buildx build --platform "${PLATFORMS}" -t "${IMAGE}:${TAG}" -f ./Dockerfile . --push
fi
