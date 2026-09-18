#!/usr/bin/env bash
# ============================================================================
# 为新复制的项目生成隔离配置（项目无关 · 前缀驱动 · 幂等）
#
# 用法：
#   bash ddd/scripts/isolation/init-isolation.sh --prefix <前缀> [选项]
#
# 选项：
#   --prefix <p>          必填。新项目前缀，小写字母/数字/短横线，如 rcs
#   --port-base <n>       可选。端口基准，默认 8100
#                         → 网关 = n，coverage = n+10，gateway agent = n+20，auth agent = n+21
#   --redis-db <n>        可选。Redis database index，默认 2（原项目惯用 0/1）
#   --nacos-namespace <s> 可选。默认 ian-<前缀>
#   --k8s-namespace <s>   可选。默认 ian-<前缀>
#   --out-dir <path>      可选。输出目录，默认 ./isolation-out
#   --apply               可选。生成后**直接在 k8s 中执行**：建库 + 建 Nacos 命名空间 + 建 k8s 命名空间
#                         （不带该选项时只生成文件，不改动任何环境）
#   -h | --help           打印本用法
#
# 说明：不带 --apply 时本脚本只**生成文件**，不会执行 DDL、不会修改 .env.local。
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=isolation-lib.sh
. "${SCRIPT_DIR}/isolation-lib.sh"

PREFIX=""
PORT_BASE=8100
REDIS_DB=2
OUT_DIR="./isolation-out"
APPLY="no"

usage() { sed -n '3,19p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --prefix)          PREFIX="$2"; shift 2 ;;
    --port-base)       PORT_BASE="$2"; shift 2 ;;
    --redis-db)        REDIS_DB="$2"; shift 2 ;;
    --nacos-namespace) NACOS_NS="$2"; shift 2 ;;
    --k8s-namespace)   K8S_NS="$2"; shift 2 ;;
    --out-dir)         OUT_DIR="$2"; shift 2 ;;
    --apply)           APPLY="yes"; shift ;;
    -h|--help)         usage ;;
    *) echo "未知参数: $1（-h 查看用法）" >&2; exit 2 ;;
  esac
done

[[ -n "${PREFIX}" ]] || { echo "错误：必须指定 --prefix（如 --prefix rcs）" >&2; exit 2; }
if ! [[ "${PREFIX}" =~ ^[a-z][a-z0-9-]*$ ]]; then
  echo "错误：--prefix 必须以小写字母开头，只含小写字母/数字/短横线（当前：${PREFIX}）" >&2
  exit 2
fi
if ! [[ "${PORT_BASE}" =~ ^[0-9]+$ ]] || (( PORT_BASE < 1024 || PORT_BASE > 64000 )); then
  echo "错误：--port-base 必须是 1024–64000 的整数（当前：${PORT_BASE}）" >&2
  exit 2
fi

NACOS_NS="${NACOS_NS:-ian-${PREFIX}}"
K8S_NS="${K8S_NS:-ian-${PREFIX}}"
GW_PORT="${PORT_BASE}"
CTRL_PORT=$(( PORT_BASE + 10 ))
GW_AGENT_PORT=$(( PORT_BASE + 20 ))
AUTH_AGENT_PORT=$(( PORT_BASE + 21 ))

mkdir -p "${OUT_DIR}"
SQL="${OUT_DIR}/init-databases-${PREFIX}.sql"
ENVF="${OUT_DIR}/env.isolation.${PREFIX}"
STEPS="${OUT_DIR}/NEXT-STEPS-${PREFIX}.md"

# ---------------------------------------------------------------- SQL
cat > "${SQL}" <<SQL_EOF
-- ============================================================================
-- ${PREFIX} 项目专用数据库（与源项目隔离）
-- 由 ddd/scripts/isolation/init-isolation.sh 生成
-- 执行：mysql -h 127.0.0.1 -P 3306 -u root -p < ${SQL}
-- ============================================================================

-- 开发环境
CREATE DATABASE IF NOT EXISTS \`${PREFIX}_dev_tech_db_00\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS \`${PREFIX}_dev_tech_db_01\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS \`${PREFIX}_rbac\`           DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- 测试环境（⚠ E2E 会写这些库，必须与源项目分开）
CREATE DATABASE IF NOT EXISTS \`${PREFIX}_test_tech_db_00\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS \`${PREFIX}_test_tech_db_01\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS \`${PREFIX}_rbac_test\`        DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- 生产环境
CREATE DATABASE IF NOT EXISTS \`${PREFIX}_prod_tech_db_00\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS \`${PREFIX}_prod_tech_db_01\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- 业务库（按需改名；此处沿用 <前缀>_biz 约定）
CREATE DATABASE IF NOT EXISTS \`${PREFIX}_biz\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- 校验（应返回 9 行）
SELECT schema_name FROM information_schema.schemata
 WHERE schema_name IN ('${PREFIX}_dev_tech_db_00','${PREFIX}_dev_tech_db_01','${PREFIX}_rbac',
                       '${PREFIX}_test_tech_db_00','${PREFIX}_test_tech_db_01','${PREFIX}_rbac_test',
                       '${PREFIX}_prod_tech_db_00','${PREFIX}_prod_tech_db_01','${PREFIX}_biz')
 ORDER BY schema_name;
SQL_EOF

# ---------------------------------------------------------------- env 片段
cat > "${ENVF}" <<ENV_EOF
# ============================================================================
# ${PREFIX} 项目隔离环境变量（并入 ddd/.env.local，该文件已被 gitignore）
# 由 ddd/scripts/isolation/init-isolation.sh 生成
# ============================================================================

# --- P0：ID 生成器命名空间 -----------------------------------------------
# 未显式配置时取 spring.application.name；若该名称仍与源项目相同，
# 两项目会各自租到同一个 WorkerId，**生成的 ID 必然重复**。
# ⚠ 变更该值必须先停机（见 ddd/ddd-base/README.md）。
# ⚠ 服务改名为 ian-${PREFIX}-* 之后，本变量可删除（自动派生即已隔离）。
DDD_ID_GENERATOR_NAMESPACE=ian-${PREFIX}-auth-boot

# --- P0：Redis ------------------------------------------------------------
REDIS_DATABASE=${REDIS_DB}

# --- P1：MySQL 库名 -------------------------------------------------------
MYSQL_DATABASE_00=${PREFIX}_dev_tech_db_00
MYSQL_DATABASE_01=${PREFIX}_dev_tech_db_01
MYSQL_DATABASE_RBAC=${PREFIX}_rbac
RBAC_TEST_MYSQL_DATABASE=${PREFIX}_rbac_test

# --- P1：Nacos 命名空间（需先创建，见 NEXT-STEPS）-------------------------
DUBBO_REGISTRY_NAMESPACE=${NACOS_NS}

# --- P2：端口（勿与已运行项目冲突）---------------------------------------
GATEWAY_PORT=${GW_PORT}
CONTROLLER_PORT=${CTRL_PORT}
GATEWAY_AGENT_PORT=${GW_AGENT_PORT}
AUTH_AGENT_PORT=${AUTH_AGENT_PORT}

# --- P2：Kafka ------------------------------------------------------------
KAFKA_TOPIC=${PREFIX}-mq
KAFKA_CONSUMER_GROUP=${PREFIX}-group

# --- P2：Docker -----------------------------------------------------------
IMAGE_NAME=system/ian-${PREFIX}-auth-boot:latest
CONTAINER_NAME=ian-${PREFIX}-auth
ENV_EOF

# ---------------------------------------------------------------- 后续步骤
cat > "${STEPS}" <<STEPS_EOF
# ${PREFIX} 项目隔离接入步骤

生成时间：$(date '+%Y-%m-%d %H:%M:%S')

## 1. 建库

\`\`\`bash
mysql -h 127.0.0.1 -P 3306 -u root -p < ${SQL}
\`\`\`

## 2. 建 Nacos 命名空间

命名空间 **ID** 必须是 \`${NACOS_NS}\`（Dubbo 的 namespace 参数取的是 ID，不是显示名）。

\`\`\`bash
curl -X POST 'http://127.0.0.1:8848/nacos/v1/console/namespaces' \\
  -d 'customNamespaceId=${NACOS_NS}&namespaceName=${NACOS_NS}&namespaceDesc=${PREFIX}'
\`\`\`

或控制台：http://127.0.0.1:8848/nacos → 命名空间 → 新建

## 3. 合并环境变量

把 \`${ENVF}\` 的内容并入 **ddd/.env.local**（不要直接覆盖该文件，它是 gitignored 的本地凭证）。

## 4.（如需部署）建 k8s 命名空间

\`\`\`bash
kubectl create namespace ${K8S_NS}
\`\`\`

## 5. 自检

\`\`\`bash
bash ddd/scripts/isolation/check-isolation.sh
\`\`\`

## 6. 与源项目同时运行的验证

| # | 验证项 | 判定 |
|---|---|---|
| V1 | 两项目服务同时启动 | 无端口冲突、无 Nacos 注册覆盖 |
| V2 | 各自跑 E2E | 在源项目测试库中查不到本项目的写入 |
| V3 | Nacos 控制台 | 实例分属不同 namespace |
| V4 | Redis KEYS 抽样 | key 无重叠 |
| V5 | 两侧 ID 抽样对比 | **无重复** |
| V6 | 触发一次 start.sh | 不会删掉对方容器 |

## 7. 本次分配的资源速查

| 项 | 值 |
|---|---|
| 项目前缀 | \`${PREFIX}\` |
| 网关端口 | \`${GW_PORT}\` |
| coverage controller | \`${CTRL_PORT}\` |
| gateway agent | \`${GW_AGENT_PORT}\` |
| auth agent | \`${AUTH_AGENT_PORT}\` |
| Redis database | \`${REDIS_DB}\` |
| Nacos namespace | \`${NACOS_NS}\` |
| k8s namespace | \`${K8S_NS}\` |
| ID 生成器 namespace | \`ian-${PREFIX}-auth-boot\` |
STEPS_EOF

echo "✅ 已生成 ${PREFIX} 项目的隔离配置："
echo "   ${SQL}"
echo "   ${ENVF}"
echo "   ${STEPS}"
echo

if [[ "${APPLY}" != "yes" ]]; then
  echo "下一步（二选一）："
  echo "  A) 手工执行 —— 按 ${STEPS} 操作"
  echo "  B) 自动执行 —— 加 --apply 直接在 k8s 中建库 / 建命名空间："
  echo "     bash $0 --prefix ${PREFIX} --apply"
  exit 0
fi

# ---------------------------------------------------------------- --apply
echo "=== 应用隔离配置（--apply）==="
echo

DDD_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
iso_load_env_local "${DDD_DIR}" >/dev/null 2>&1 || echo "  ! 未找到 ${DDD_DIR}/.env.local，MySQL 凭证将从环境变量读取"
FAILED=0

# ① 建库
echo "[1/3] 建库"
if iso_k8s_available; then
  echo "  目标：k8s ns=${ISO_INFRA_NS}  pod=$(iso_find_pod mysql 2>/dev/null || echo '<未找到>')"
fi
if iso_exec_sql_file "${SQL}" >/dev/null 2>&1; then
  echo "  ✓ 已创建/确认 ${PREFIX}_* 库"
else
  echo "  ✗ 建库失败 → 手工执行：mysql ... < ${SQL}"
  FAILED=1
fi

# ② Nacos 命名空间
echo
echo "[2/3] 创建 Nacos 命名空间 ${NACOS_NS}"
if iso_ensure_nacos_namespace "${NACOS_NS}"; then
  echo "  ✓ 已存在或创建成功"
else
  echo "  ! 未能确认 → 见 ${STEPS} 的手工命令（可能凭证缺失或 Nacos 版本不符）"
  FAILED=1
fi

# ③ k8s 命名空间
echo
echo "[3/3] 创建 k8s 命名空间 ${K8S_NS}"
if iso_k8s_available; then
  if iso_ensure_k8s_namespace "${K8S_NS}"; then
    echo "  ✓ 已存在或创建成功"
  else
    echo "  ✗ 创建失败"
    FAILED=1
  fi
else
  echo "  - k8s 不可用，跳过"
fi

echo
if [[ "${FAILED}" -eq 0 ]]; then
  echo "✅ 隔离配置已应用。剩余手工步骤："
  echo "   1) 把 ${ENVF} 的内容并入 ${DDD_DIR}/.env.local"
  echo "   2) 自检：bash ${SCRIPT_DIR}/check-isolation.sh"
else
  echo "⚠ 部分步骤未成功，请按 ${STEPS} 手工补齐后再自检"
fi
