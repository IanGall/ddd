#!/usr/bin/env bash
# ============================================================================
# 隔离套件共享函数库（被 init-isolation.sh / check-isolation.sh source）
#
# 设计：优先在 k8s 环境中执行（kubectl exec 进中间件 Pod），
#        k8s 不可用时回退到宿主机直连（mysql 客户端 / curl）。
#       全程不硬编码项目名 —— 前缀由 iso_detect_prefix 推断或被调用方显式传入。
# ============================================================================

# 中间件所在命名空间（本机 OrbStack 集群约定为 infra）
ISO_INFRA_NS="${ISO_INFRA_NS:-infra}"

# ---------------------------------------------------------------------------
# 项目前缀：从项目根目录名推断（/path/to/ian-<前缀> → <前缀>）
# 入参：项目根目录（可选，默认由脚本位置推导）
# ---------------------------------------------------------------------------
iso_detect_prefix() {
  local root="${1:-}"
  [[ -n "${root}" ]] || return 1
  local base; base="$(basename "${root}")"
  if [[ "${base}" =~ ^ian-(.+)$ ]]; then
    printf '%s' "${BASH_REMATCH[1]}"
    return 0
  fi
  return 1
}

# ---------------------------------------------------------------------------
# k8s 可用性
# ---------------------------------------------------------------------------
iso_k8s_available() {
  command -v kubectl >/dev/null 2>&1 || return 1
  kubectl get ns >/dev/null 2>&1 || return 1
  return 0
}

# 在 k8s 中查找中间件 Pod（按标签优先，回退到名字前缀）
# 用法：iso_find_pod <app>   （app: mysql|nacos|redis|kafka）
iso_find_pod() {
  local app="$1" pod
  pod="$(kubectl get pods -n "${ISO_INFRA_NS}" -l "app=${app}" \
        -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)"
  [[ -n "${pod}" ]] && { printf '%s' "${pod}"; return 0; }
  # 回退：按名字前缀（本机集群是 mysql-0 / redis-0 / kafka-0 这类 StatefulSet）
  pod="$(kubectl get pods -n "${ISO_INFRA_NS}" --no-headers 2>/dev/null \
        | awk -v a="${app}" '$1 ~ "^"a {print $1; exit}')"
  [[ -n "${pod}" ]] && { printf '%s' "${pod}"; return 0; }
  return 1
}

# 读取 .env.local（不入库的本地凭证）
# 用法：iso_load_env_local <ddd_dir>
iso_load_env_local() {
  local ddd_dir="$1"
  local f="${ddd_dir}/.env.local"
  [[ -f "${f}" ]] || return 1
  set -a
  # shellcheck disable=SC1090
  . "${f}"
  set +a
  return 0
}

# ---------------------------------------------------------------------------
# 执行 SQL 文件
#   优先：kubectl exec 进 mysql Pod（要求集群内有 mysql 可执行文件）
#   回退：宿主机 mysql 客户端
# 返回：0 成功；1 无可用途径；2 执行失败
# ---------------------------------------------------------------------------
iso_exec_sql_file() {
  local sql_file="$1"
  [[ -f "${sql_file}" ]] || { echo "SQL 文件不存在: ${sql_file}" >&2; return 1; }
  local user="${MYSQL_USERNAME:-root}" pw="${MYSQL_PASSWORD:-}"

  if iso_k8s_available; then
    local pod; pod="$(iso_find_pod mysql || true)"
    if [[ -n "${pod}" ]]; then
      kubectl exec -i -n "${ISO_INFRA_NS}" "${pod}" -- \
        mysql -u "${user}" ${pw:+-p"${pw}"} < "${sql_file}"
      return $?
    fi
    echo "k8s 可用但未找到 mysql Pod（ns=${ISO_INFRA_NS}）" >&2
  fi

  if command -v mysql >/dev/null 2>&1; then
    mysql -h "${MYSQL_HOST:-127.0.0.1}" -P "${MYSQL_PORT:-3306}" -u "${user}" \
      ${pw:+-p"${pw}"} < "${sql_file}"
    return $?
  fi

  echo "既无 k8s mysql Pod，也无宿主机 mysql 客户端" >&2
  return 1
}

# ---------------------------------------------------------------------------
# 查询「已存在的库名」列表（每行一个）
# 返回：0 成功（结果通过 stdout）；1 无可用途径
# ---------------------------------------------------------------------------
iso_list_databases() {
  local user="${MYSQL_USERNAME:-root}" pw="${MYSQL_PASSWORD:-}"
  local q="SELECT schema_name FROM information_schema.schemata"

  if iso_k8s_available; then
    local pod; pod="$(iso_find_pod mysql || true)"
    if [[ -n "${pod}" ]]; then
      kubectl exec -n "${ISO_INFRA_NS}" "${pod}" -- \
        mysql -u "${user}" ${pw:+-p"${pw}"} -N -B -e "${q}" 2>/dev/null
      return $?
    fi
  fi

  if command -v mysql >/dev/null 2>&1; then
    mysql -h "${MYSQL_HOST:-127.0.0.1}" -P "${MYSQL_PORT:-3306}" -u "${user}" \
      ${pw:+-p"${pw}"} -N -B -e "${q}" 2>/dev/null
    return $?
  fi
  return 1
}

# ---------------------------------------------------------------------------
# 确保 Nacos 命名空间存在（Nacos 3.x admin API）
#   优先：宿主机 NACOS_API_URL（默认 http://127.0.0.1:8848）
#   回退：kubectl exec 进 nacos Pod 调 localhost:8848
# 返回：0 成功/已存在；1 失败（仅告警用）
# ---------------------------------------------------------------------------
iso_ensure_nacos_namespace() {
  local ns="$1"
  [[ -n "${ns}" && "${ns}" != "public" ]] || return 0
  command -v curl >/dev/null 2>&1 || { echo "找不到 curl" >&2; return 1; }

  local base="${NACOS_API_URL:-http://127.0.0.1:8848}"
  local pw="${DUBBO_REGISTRY_PASSWORD:-}"

  _nacos_create_and_verify() {
    local api="$1" pre="$2"
    local token
    token="$(curl -s -m 5 -X POST "${api}/nacos/v1/auth/login" \
      --data-urlencode "username=nacos" --data-urlencode "password=${pw}" 2>/dev/null \
      | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"
    [[ -n "${token}" ]] || return 1
    curl -s -m 5 -X POST "${pre}/nacos/v3/admin/core/namespace?accessToken=${token}" \
      --data-urlencode "namespaceId=${ns}" \
      --data-urlencode "namespaceName=${ns}" \
      --data-urlencode "namespaceDesc=由 isolation 套件创建" >/dev/null 2>&1 || true
    local listing
    listing="$(curl -s -m 5 "${pre}/nacos/v3/admin/core/namespace/list?pageNo=1&pageSize=200&accessToken=${token}" 2>/dev/null || true)"
    case "${listing}" in
      *"\"namespace\":\"${ns}\""*|*"\"namespaceId\":\"${ns}\""*) return 0 ;;
      *) return 1 ;;
    esac
  }

  # 路径 1：宿主机直连
  if _nacos_create_and_verify "${base}" "${base}" 2>/dev/null; then
    return 0
  fi

  # 路径 2：进 nacos Pod 内部调
  if iso_k8s_available; then
    local pod; pod="$(iso_find_pod nacos || true)"
    if [[ -n "${pod}" ]]; then
      local script='
apibase=http://127.0.0.1:8848
tok=$(curl -s -m 5 -X POST "$apibase/nacos/v1/auth/login" --data-urlencode "username=nacos" --data-urlencode "password='"${pw}"'" 2>/dev/null | sed -n "s/.*\"accessToken\":\"\([^\"]*\)\".*/\1/p")
[ -n "$tok" ] || exit 1
curl -s -m 5 -X POST "$apibase/nacos/v3/admin/core/namespace?accessToken=$tok" \
  --data-urlencode "namespaceId='"${ns}"'" --data-urlencode "namespaceName='"${ns}"'" >/dev/null 2>&1 || true
curl -s -m 5 "$apibase/nacos/v3/admin/core/namespace/list?pageNo=1&pageSize=200&accessToken=$tok" 2>/dev/null | grep -q "'"${ns}"'" && exit 0 || exit 1
'
      kubectl exec -n "${ISO_INFRA_NS}" "${pod}" -- sh -c "${script}" >/dev/null 2>&1 && return 0
    fi
  fi
  return 1
}

# ---------------------------------------------------------------------------
# 确保 k8s 命名空间存在（幂等）
# ---------------------------------------------------------------------------
iso_ensure_k8s_namespace() {
  local ns="$1"
  [[ -n "${ns}" ]] || return 1
  if kubectl get namespace "${ns}" >/dev/null 2>&1; then
    return 0
  fi
  kubectl create namespace "${ns}" >/dev/null 2>&1 && return 0
  return 1
}

# ---------------------------------------------------------------------------
# 检测端口是否被监听（本机）
# ---------------------------------------------------------------------------
iso_port_listening() {
  lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
}
