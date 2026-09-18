#!/usr/bin/env bash
# ============================================================================
# 检查当前项目的隔离状态（项目无关 · 只读 · k8s 感知）
#
# 用法：
#   bash ddd/scripts/isolation/check-isolation.sh [--prefix <前缀>] [--k8s-namespace <ns>]
#
# 前缀默认从项目根目录名推断（/path/to/ian-<前缀> → <前缀>）。
# 检查会优先走 k8s（kubectl exec 进中间件 Pod），k8s 不可用时回退宿主机直连。
# 本脚本只读，不修改任何配置或数据。
# ============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=isolation-lib.sh
. "${SCRIPT_DIR}/isolation-lib.sh"

DDD_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
PROJECT_ROOT="$(cd "${DDD_DIR}/.." && pwd)"
ENV_LOCAL="${DDD_DIR}/.env.local"

PREFIX=""
K8S_NS_CLI=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --prefix)       PREFIX="$2"; shift 2 ;;
    --k8s-namespace) K8S_NS_CLI="$2"; shift 2 ;;
    -h|--help)      sed -n '2,13p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "未知参数: $1" >&2; exit 2 ;;
  esac
done

[[ -n "${PREFIX}" ]] || PREFIX="$(iso_detect_prefix "${PROJECT_ROOT}" || true)"
if [[ -z "${PREFIX}" ]]; then
  echo "无法从目录名推断项目前缀（期望 ${PROJECT_ROOT} 形如 ian-<前缀>）" >&2
  echo "请显式指定：--prefix <前缀>" >&2
  exit 2
fi

K8S_NS="${K8S_NS_CLI:-ian-${PREFIX}}"
NACOS_NS_EXPECTED="ian-${PREFIX}"

PASS=0; FAIL=0; WARN=0; SKIP=0
ok()   { printf '  \033[32m✓\033[0m %s\n' "$*"; PASS=$((PASS+1)); }
bad()  { printf '  \033[31m✗\033[0m %s\n' "$*"; FAIL=$((FAIL+1)); }
warn() { printf '  \033[33m!\033[0m %s\n' "$*"; WARN=$((WARN+1)); }
skip() { printf '  \033[2m-\033[0m %s\n' "$*"; SKIP=$((SKIP+1)); }

echo "=== 隔离状态检查 ==="
echo "项目根  : ${PROJECT_ROOT}"
echo "项目前缀: ${PREFIX}"
if iso_k8s_available; then
  echo "k8s     : 可用（context: $(kubectl config current-context 2>/dev/null)，中间件 ns: ${ISO_INFRA_NS}）"
else
  echo "k8s     : 不可用 → 将回退宿主机直连"
fi
echo

iso_load_env_local "${DDD_DIR}" >/dev/null 2>&1 || true

# ---------------------------------------------------------------------------
echo "[1] 🔴 P0 —— ID 生成器命名空间"
APP_YML="$(ls "${DDD_DIR}"/*-auth/*-boot/src/main/resources/application.yml 2>/dev/null | head -1 || true)"
APP_NAME=""
[[ -n "${APP_YML}" && -f "${APP_YML}" ]] && APP_NAME="$(grep -E '^\s+name:' "${APP_YML}" | head -1 | awk '{print $2}')"

if [[ -n "${APP_NAME}" && "${APP_NAME}" == *"ian-${PREFIX}"* ]]; then
  ok "spring.application.name=${APP_NAME}（已按前缀改名 → ID 池天然隔离）"
else
  [[ -n "${APP_NAME}" ]] && warn "spring.application.name=${APP_NAME} 未含 ian-${PREFIX}"
  if grep -qE '^[[:space:]]*DDD_ID_GENERATOR_NAMESPACE=' "${ENV_LOCAL}" 2>/dev/null; then
    v="$(grep -E '^[[:space:]]*DDD_ID_GENERATOR_NAMESPACE=' "${ENV_LOCAL}" | tail -1 | cut -d= -f2-)"
    if [[ "${v}" == *"${PREFIX}"* ]]; then ok "本机兜底：DDD_ID_GENERATOR_NAMESPACE=${v}"
    else bad "本机 DDD_ID_GENERATOR_NAMESPACE=${v} 未含 ${PREFIX} → **ID 重复风险**"; fi
  else
    bad "未设 DDD_ID_GENERATOR_NAMESPACE 且服务名未改名 → **ID 重复风险**"
  fi
fi

# k8s 部署态：从 ConfigMap 读取实际生效值
if iso_k8s_available && kubectl get ns "${K8S_NS}" >/dev/null 2>&1; then
  cm_id="$(for cm in $(kubectl get cm -n "${K8S_NS}" -o name 2>/dev/null | grep -- '-config$' || true); do
             kubectl get "${cm}" -n "${K8S_NS}" -o jsonpath='{.data.DDD_ID_GENERATOR_NAMESPACE}' 2>/dev/null | grep . && break
           done)"
  if [[ -n "${cm_id}" ]]; then
    if [[ "${cm_id}" == *"${PREFIX}"* ]]; then ok "k8s 部署态（${K8S_NS}）：DDD_ID_GENERATOR_NAMESPACE=${cm_id}"
    else bad "k8s 部署态 DDD_ID_GENERATOR_NAMESPACE=${cm_id} 未含 ${PREFIX} → **已部署实例会与源项目 ID 冲突**"; fi
  else
    skip "k8s 命名空间 ${K8S_NS} 内未找到 -config ConfigMap（尚未部署？）"
  fi
fi

# ---------------------------------------------------------------------------
echo
echo "[2] 🔴 P0 —— Redis database"
if grep -qE '^[[:space:]]*REDIS_DATABASE=' "${ENV_LOCAL}" 2>/dev/null; then
  v="$(grep -E '^[[:space:]]*REDIS_DATABASE=' "${ENV_LOCAL}" | tail -1 | cut -d= -f2-)"
  if [[ "${v}" == "0" || "${v}" == "1" ]]; then warn "本机 REDIS_DATABASE=${v}（源项目惯用值，可能冲突）"
  else ok "本机 REDIS_DATABASE=${v}"; fi
else
  warn "未设 REDIS_DATABASE（默认 0，可能与源项目冲突）"
fi

# ---------------------------------------------------------------------------
echo
echo "[3] 🟠 P1 —— MySQL 库"
if iso_k8s_available && [[ -n "$(iso_find_pod mysql 2>/dev/null || true)" ]]; then
  echo "  检查途径：kubectl exec -n ${ISO_INFRA_NS} $(iso_find_pod mysql) -- mysql"
fi
if out="$(iso_list_databases 2>/dev/null)"; then
  n_all="$(printf '%s\n' "$out" | grep -c . || true)"
  # 识别「源项目」：使用历史命名（ian_dev_tech_db_*），且没有按 <prefix>_ 前缀建库。
  # 源项目本身就是隔离的基准，不需要（也不应该）按前缀再建一套库。
  if ! printf '%s\n' "$out" | grep -qE "^${PREFIX}_dev_tech_db_00$" \
     && printf '%s\n' "$out" | grep -qE '^ian_dev_tech_db_00$'; then
    SOURCE_PROJECT="yes"
  fi
  if [[ "${SOURCE_PROJECT:-no}" == "yes" ]]; then
    skip "检测到历史命名库（ian_dev_tech_db_*）→ 当前是**源项目**，无需按 ${PREFIX}_ 前缀建库"
  else
    n_own="$(printf '%s\n' "$out" | grep -cE "^${PREFIX}_|^ian_${PREFIX}$" || true)"
    if [[ "${n_own}" -ge 9 ]]; then
      ok "${PREFIX} 侧库已建 ${n_own} 个（总库数 ${n_all}）"
    else
      bad "${PREFIX} 侧库仅 ${n_own} 个（应 9 个）→ init-isolation.sh --prefix ${PREFIX} --apply"
    fi
  fi
else
  skip "无法查询数据库（k8s 与宿主机均不可用）"
fi

# ---------------------------------------------------------------------------
echo
echo "[4] 🟠 P1 —— Nacos 命名空间 ${NACOS_NS_EXPECTED}"
_nacos_list_ns() {
  local api="${NACOS_API_URL:-http://127.0.0.1:8848}" tok
  tok="$(curl -s -m 5 -X POST "${api}/nacos/v1/auth/login" \
    --data-urlencode "username=nacos" --data-urlencode "password=${DUBBO_REGISTRY_PASSWORD:-}" 2>/dev/null \
    | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"
  [[ -n "${tok}" ]] || return 1
  curl -s -m 5 "${api}/nacos/v3/admin/core/namespace/list?pageNo=1&pageSize=200&accessToken=${tok}" 2>/dev/null
}
ns_listing="$(_nacos_list_ns || true)"
if [[ -z "${ns_listing}" ]] && iso_k8s_available; then
  pod="$(iso_find_pod nacos 2>/dev/null || true)"
  if [[ -n "${pod}" ]]; then
    ns_listing="$(kubectl exec -n "${ISO_INFRA_NS}" "${pod}" -- sh -c '
      api=http://127.0.0.1:8848
      tok=$(curl -s -m 5 -X POST "$api/nacos/v1/auth/login" --data-urlencode "username=nacos" \
            --data-urlencode "password='"${DUBBO_REGISTRY_PASSWORD:-}"'" 2>/dev/null | sed -n "s/.*\"accessToken\":\"\([^\"]*\)\".*/\1/p")
      [ -n "$tok" ] && curl -s -m 5 "$api/nacos/v3/admin/core/namespace/list?pageNo=1&pageSize=200&accessToken=$tok" 2>/dev/null
    ' 2>/dev/null || true)"
    [[ -n "${ns_listing}" ]] && echo "  检查途径：kubectl exec -n ${ISO_INFRA_NS} ${pod}"
  fi
fi
if [[ -n "${ns_listing}" ]]; then
  if [[ "${SOURCE_PROJECT:-no}" == "yes" ]]; then
    skip "当前是源项目（命名空间用 dev-test），无需 ian-${PREFIX}"
  elif printf '%s' "${ns_listing}" | grep -q "\"${NACOS_NS_EXPECTED}\""; then
    ok "${NACOS_NS_EXPECTED} 存在"
  else
    bad "${NACOS_NS_EXPECTED} 不存在 → init-isolation.sh --prefix ${PREFIX} --apply"
  fi
  if printf '%s' "${ns_listing}" | grep -q '"dev-test"'; then
    ok "源项目命名空间 dev-test 仍存在（共存，已隔离）"
  fi
else
  skip "无法查询 Nacos 命名空间（宿主机与 Pod 内均失败）"
fi

# ---------------------------------------------------------------------------
echo
echo "[5] 🟠 P1 —— k8s 命名空间 ${K8S_NS}"
if ! iso_k8s_available; then
  skip "k8s 不可用"
elif kubectl get ns "${K8S_NS}" >/dev/null 2>&1; then
  ok "${K8S_NS} 存在"
  pods="$(kubectl get pods -n "${K8S_NS}" --no-headers 2>/dev/null | wc -l | tr -d ' ')"
  [[ "${pods}" -gt 0 ]] && echo "      其中 ${pods} 个 Pod"
else
  bad "${K8S_NS} 不存在 → init-isolation.sh --prefix ${PREFIX} --apply"
fi

# ---------------------------------------------------------------------------
echo
echo "[6] 🟡 P2 —— 本机端口占用"
for pair in "${GATEWAY_PORT:-8100}:网关" "${CONTROLLER_PORT:-8110}:coverage" \
            "${GATEWAY_AGENT_PORT:-8120}:gateway agent" "${AUTH_AGENT_PORT:-8121}:auth agent"; do
  p="${pair%%:*}"; label="${pair##*:}"
  if iso_port_listening "${p}"; then
    warn "端口 ${p}（${label}）已被占用：$(lsof -nP -iTCP:"${p}" -sTCP:LISTEN 2>/dev/null | awk 'NR==2{print $1}')"
  else
    ok "端口 ${p}（${label}）空闲"
  fi
done
if iso_port_listening 22222; then
  warn "端口 22222（Dubbo QoS 默认）被占用 → 各服务须显式错开或关闭 QoS"
else
  ok "端口 22222（Dubbo QoS 默认）空闲"
fi

# ---------------------------------------------------------------------------
echo
echo "[7] 🟡 P2 —— Docker 容器名"
if command -v docker >/dev/null 2>&1; then
  others="$(docker ps -a --format '{{.Names}}' 2>/dev/null | grep -vE "^ian-${PREFIX}-" | grep -cE '^ian-' || true)"
  if [[ "${others}" -gt 0 ]]; then
    warn "存在 ${others} 个其他 ian-* 容器（本项目用 ian-${PREFIX}-* 命名，不会误删）"
  else
    ok "无同名容器冲突"
  fi
else
  skip "未安装 docker"
fi

# ---------------------------------------------------------------------------
echo
echo "=== 结果：通过 ${PASS} / 失败 ${FAIL} / 警告 ${WARN} / 跳过 ${SKIP} ==="
if [[ "${FAIL}" -eq 0 ]]; then
  echo "✅ 无阻断项"
else
  echo "❌ 存在 ${FAIL} 个阻断项 → 见 ddd/scripts/isolation/README.md"
fi

cat <<TIP

--- 建议的手工验证（自动化无法覆盖）---
V5「ID 不重复」：两项目同时运行时各生成若干 ID 并比对，应无交集。
  最简做法：两边的 rbac_user 各插入若干条，导出主键做交集：
    kubectl exec -n ${ISO_INFRA_NS} $(iso_find_pod mysql 2>/dev/null || echo '<mysql-pod>') -- \\
      mysql -u root -p<密码> -N -B -e "SELECT id FROM <源项目库>.rbac_user ORDER BY id DESC LIMIT 100" > /tmp/src.ids
    kubectl exec -n ${ISO_INFRA_NS} $(iso_find_pod mysql 2>/dev/null || echo '<mysql-pod>') -- \\
      mysql -u root -p<密码> -N -B -e "SELECT id FROM ${PREFIX}_rbac.rbac_user ORDER BY id DESC LIMIT 100" > /tmp/new.ids
    comm -12 <(sort /tmp/src.ids) <(sort /tmp/new.ids)   # 应为空

V2「E2E 不互相污染」：跑完本项目 E2E 后，源项目测试库的记录数不应变化。
TIP
exit $(( FAIL > 0 ? 1 : 0 ))
