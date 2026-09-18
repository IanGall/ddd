#!/usr/bin/env bash
# k8s 一键运维脚本：构建部署 / 查看状态 / 看日志 / 强制重启 / 清理。
#
# 用法：
#   bash scripts/deploy-local.sh                  # 默认动作 deploy：构建 jar → 构建镜像 → 刷配置 → apply → 按需滚动 → 等就绪
#   bash scripts/deploy-local.sh status           # 查看部署状态（Deployment/Pod/HPA + 最近事件）
#   bash scripts/deploy-local.sh logs [--follow]  # 看日志（默认两个服务各取各 Pod 最后 100 行）
#   bash scripts/deploy-local.sh restart          # 强制滚动重启（改了 Secret 但内容摘要没变时用）
#   bash scripts/deploy-local.sh clean            # 清理工作负载与配置（保留命名空间）
#   bash scripts/deploy-local.sh clean --purge    # 连命名空间一起删
#   bash scripts/deploy-local.sh clean --images   # 连本地镜像一起删（下次部署会重新构建）
#
# 选项：
#   --service all|auth|gateway   默认 all；clean 时只清单个服务的资源（命名空间与另一个服务保留）
#   --profile dev|prod           默认 dev；prod 需要自备强密钥（见下）
#   --k8s-namespace NAME         k8s 命名空间，默认**从项目目录名派生**（/path/to/ian-<前缀> → ian-<前缀>，
#                                例：ian-rcs → ian-rcs）；目录名不符合 ian-* 约定时回退 ian-ddd（等价别名 --namespace）
#
# 项目前缀（PROJECT_PREFIX）由项目目录名派生，是以下三项的**单一来源**，使复制出的新项目无需改脚本：
#   · 镜像名（system/ian-<前缀>-auth-boot:1.0-SNAPSHOT，避免与源项目互相覆盖）
#   · k8s 命名空间（ian-<前缀>）
#   · Nacos 命名空间与 ID 生成器命名空间（ian-<前缀> / ian-<前缀>-auth-boot）
#   --nacos-namespace NAME       Nacos 命名空间（=注册中心的租户 ID），默认 dev-test；
#                                传 public 或空字符串表示用默认命名空间（地址里不加 namespace 参数）
#   --replicas N|keep            本地副本数，默认 1（两个服务各 1 个 Pod，HPA minReplicas 同步收到 N）；
#                                keep = 完全按清单（HPA 2→8/2→6），需要验证 HPA 伸缩时用它
#   --skip-build                 deploy 时跳过 Maven 与镜像构建，只刷配置并滚动
#   --follow                     logs 时持续跟随（跟随最新的那个 Pod）
#
# 幂等：deploy 把「镜像内容摘要（层 + 镜像配置）+ 配置内容」的摘要打成 Deployment 的 Pod 模板注解，
#       内容没变就跳过滚动更新；改代码、改 .env.local、换 profile 都会自动触发滚动，因此重复执行安全。
#       判据刻意不用镜像 ID：BuildKit 每次构建都会重写镜像 config 里的 created 时间戳，
#       即使所有层命中缓存、镜像内容逐字节一致，镜像 ID 也会变（已实测），那样每次部署都会白滚一遍。
#
# 两个命名空间是两件独立的事（参数默认值可用环境变量覆盖，也可写进 .env.local）：
#   · k8s 命名空间：部署到哪个集群命名空间（--k8s-namespace / NAMESPACE）
#   · Nacos 命名空间：注册到哪个注册中心租户（--nacos-namespace / DUBBO_REGISTRY_NAMESPACE，默认 dev-test）
#
#   为什么集群要单独占一个 Nacos 命名空间：本机覆盖率流水线
#   （ddd-base/ian-ddd-coverage/coverage-e2e.sh）跑在默认命名空间、用的是**测试库**
#   （autotest profile → ddd_rbac_test / ian_test_tech_db_*），而本脚本部署的集群用**开发库**。
#   两者若同处一个命名空间，本机网关会把认证 RPC 按随机负载均衡分给两边的实例：在一边注册/开户的账号
#   落到另一边就查不到，表现为随机的「账号或密码错误」，同时本机覆盖率静默漏采（请求根本没打到本机）。
#   注意 Dubbo 的 namespace 参数是命名空间 **ID**（不是显示名）：Nacos 里没有以该值为 ID 的记录时服务
#   照样注册，但控制台按 ID 选命名空间、列不出这个租户（看起来像「部署的服务不在命名空间里」）。
#   deploy 前会自动确保该命名空间存在（用 NACOS_API_URL 调 Nacos 3.x 的 admin API；失败只告警不阻断）。
#
# 可覆盖的环境变量（默认按本机集群的 infra 命名空间）：
#   MYSQL_HOST MYSQL_PORT MYSQL_DATABASE_00 MYSQL_DATABASE_01 MYSQL_DATABASE_RBAC
#   REDIS_HOST REDIS_PORT NACOS_HOST NACOS_API_URL DUBBO_REGISTRY_NAMESPACE KAFKA_BOOTSTRAP_SERVERS KAFKA_ENABLED
#   DDD_ID_GENERATOR_NAMESPACE CHANNEL_ENCRYPTION_MASTER_KEY PLATFORM_ADMIN_TOKEN IMAGE_PREFIX
#   中间件口令默认读仓库根 .env.local；显式传入的同名环境变量优先。
#
# NACOS_API_URL（默认 http://127.0.0.1:8848）：宿主机可达的同一套 Nacos 的 HTTP 入口，仅用于上面这步
#   命名空间检查。跑远端集群时本脚本不适用（见下），也不会依赖它。
#
# 依赖：docker（含 buildx）、kubectl、JDK 21 + Maven（仅 deploy 且未 --skip-build 时需要）、.env.local。
# 适用范围：本机/联调集群（直接用本地 Docker 里构建的镜像，不推仓库）。
#           远端集群请先推镜像、用 kubectl create secret 注入真实凭证，不要用本脚本。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "${REPO_ROOT}"

ACTION="deploy"
SERVICE="all"
PROFILE="dev"
NAMESPACE=""                 # 空 = 未指定，稍后由项目目录名派生（见下），使复制出的新项目无需改脚本
NACOS_NAMESPACE_CLI=""      # 由 --nacos-namespace 赋值；空 = 未指定，走环境变量/.env.local/默认值
SKIP_BUILD="no"
REPLICAS="1"
PURGE="no"
WITH_IMAGES="no"
FOLLOW="no"

log() { printf '\033[32m[deploy]\033[0m %s\n' "$*"; }
warn() { printf '\033[33m[deploy]\033[0m %s\n' "$*"; }
die() {
  printf '\033[31m[deploy][ERROR]\033[0m %s\n' "$*" >&2
  exit 1
}

# -h 打印文件头的全部注释（选项与环境变量说明），新增说明行不用同步改行号
usage() { awk 'NR == 1 { next } /^[^#]/ { exit } { print }' "$0" | sed -e 's/^# \{0,1\}//'; }

# 允许省略动作（bash deploy-local.sh --service auth 等价于 deploy --service auth）
if [ $# -gt 0 ]; then
  case "$1" in
    -*) ;;
    *) ACTION="$1" && shift ;;
  esac
fi

while [ $# -gt 0 ]; do
  case "$1" in
    --service)
      SERVICE="${2:?--service 需要取值：all|auth|gateway}"
      shift
      ;;
    --profile)
      PROFILE="${2:?--profile 需要取值：dev|prod}"
      shift
      ;;
    --namespace | --k8s-namespace)
      NAMESPACE="${2:?--k8s-namespace 需要取值（k8s 命名空间，默认 ian-ddd）}"
      shift
      ;;
    --nacos-namespace)
      NACOS_NAMESPACE_CLI="${2:?--nacos-namespace 需要取值（Nacos 命名空间 ID，默认 dev-test；public 表示默认命名空间）}"
      shift
      ;;
    --skip-build) SKIP_BUILD="yes" ;;
    --replicas)
      REPLICAS="${2:?--replicas 需要取值：1|2|... 或 keep}"
      shift
      ;;
    --purge) PURGE="yes" ;;
    --images) WITH_IMAGES="yes" ;;
    --follow | -f) FOLLOW="yes" ;;
    -h | --help)
      usage
      exit 0
      ;;
    *) die "未知参数：$1（-h 看用法）" ;;
  esac
  shift
done

case "${ACTION}" in deploy | status | logs | restart | clean) ;; *) die "未知动作：${ACTION}（支持 deploy|status|logs|restart|clean）" ;; esac
case "${SERVICE}" in all | auth | gateway) ;; *) die "--service 只支持 all|auth|gateway" ;; esac
case "${PROFILE}" in dev | prod) ;; *) die "--profile 只支持 dev|prod" ;; esac
case "${REPLICAS}" in
  keep) ;;
  '' | *[!0-9]*) die "--replicas 只支持 keep 或非负整数（当前：${REPLICAS}）" ;;
esac
# 项目前缀：从项目目录名派生（/path/to/ian-<前缀> → <前缀>）。
# 它是「复制本项目派生的新项目无需改脚本」的**单一来源**——镜像名与两个命名空间都由它派生。
if [ -z "${PROJECT_PREFIX:-}" ]; then
  _derived_base="$(basename "$(cd "${REPO_ROOT}/.." && pwd)")"
  case "${_derived_base}" in
    ian-*) PROJECT_PREFIX="${_derived_base#ian-}" ;;
    *)     PROJECT_PREFIX="ddd" ;;   # 目录名不符合约定时回退到历史默认值
  esac
fi

# k8s 命名空间：默认 ian-<前缀>；可用 --k8s-namespace 或环境变量 NAMESPACE 覆盖。
if [ -z "${NAMESPACE}" ]; then
  NAMESPACE="ian-${PROJECT_PREFIX}"
fi
[ -n "${NAMESPACE}" ] || die "命名空间不能为空"
case "${NAMESPACE}" in default | kube-system | kube-public | kube-node-lease) die "拒绝操作系统命名空间 ${NAMESPACE}" ;; esac
command -v kubectl >/dev/null || die "找不到 kubectl"

service_module() {
  case "$1" in
    auth) echo "ian-ddd-auth/ian-ddd-auth-boot" ;;
    gateway) echo "ian-ddd-gateway/gateway-app" ;;
  esac
}
service_dir() {
  case "$1" in
    auth) echo "ian-ddd-auth/docs/dev-ops/k8s" ;;
    gateway) echo "ian-ddd-gateway/dev-ops/k8s" ;;
  esac
}
service_prefix() {
  case "$1" in
    auth) echo "ian-ddd-auth" ;;
    gateway) echo "ian-ddd-gateway" ;;
  esac
}
service_image() {
  # 镜像名带**项目前缀**。本机 Docker 镜像库是全局共享的：若两个项目构建同名同 tag 的镜像，
  # 后者会静默覆盖前者，而 k8s 用 imagePullPolicy: IfNotPresent —— 已在跑的 Pod 不受影响，
  # 但**重启 / HPA 扩容 / 节点漂移**产生的新 Pod 会拉到被覆盖的镜像，即「A 命名空间跑 B 项目的代码」。
  # 由于两个项目的 Deployment / Pod / Service 名相同，这类问题极难排查，故镜像名必须隔离。
  case "$1" in
    auth) echo "${IMAGE_PREFIX:-system}/ian-${PROJECT_PREFIX}-auth-boot:1.0-SNAPSHOT" ;;
    gateway) echo "${IMAGE_PREFIX:-system}/ian-${PROJECT_PREFIX}-gateway:1.0-SNAPSHOT" ;;
  esac
}
# 把 all 展开成具体服务列表
service_list() {
  if [ "${SERVICE}" = "all" ]; then echo "auth gateway"; else echo "${SERVICE}"; fi
}

# ---------------------------------------------------------------- status / logs / restart

do_status() {
  log "命名空间 ${NAMESPACE} 的部署状态："
  if ! kubectl get deploy -n "${NAMESPACE}" -o name >/dev/null 2>&1; then
    warn "命名空间 ${NAMESPACE} 不存在"
    return
  fi
  if [ -z "$(kubectl get deploy -n "${NAMESPACE}" -o name 2>/dev/null)" ]; then
    warn "命名空间 ${NAMESPACE} 里没有 Deployment（先执行 deploy）"
    return
  fi
  kubectl get deploy -n "${NAMESPACE}" \
    -o custom-columns='DEPLOY:.metadata.name,READY:.status.readyReplicas,DESIRED:.spec.replicas,IMAGE:.spec.template.spec.containers[0].image' 2>/dev/null | sed 's/^/  /' || true
  kubectl get pods -n "${NAMESPACE}" \
    -o custom-columns='POD:.metadata.name,READY:.status.containerStatuses[0].ready,STATUS:.status.phase,RESTARTS:.status.containerStatuses[0].restartCount' 2>/dev/null | sed 's/^/  /' || true
  kubectl get hpa,ingress -n "${NAMESPACE}" 2>/dev/null | sed 's/^/  /' || true
  # 各服务实际生效的注册中心地址（含 Nacos 命名空间），排查实例互相发现时先看这里
  local cm addr
  for cm in $(kubectl get cm -n "${NAMESPACE}" -o name 2>/dev/null | grep -- '-config$' || true); do
    addr="$(kubectl get "${cm}" -n "${NAMESPACE}" -o jsonpath='{.data.DUBBO_REGISTRY_ADDRESS}' 2>/dev/null || true)"
    [ -n "${addr}" ] && printf '  %s 注册中心: %s\n' "${cm#configmap/}" "${addr}"
  done
  log "最近事件（尾部 10 条）："
  kubectl get events -n "${NAMESPACE}" --sort-by=.lastTimestamp 2>/dev/null | tail -10 | sed 's/^/  /' || true
}

do_logs() {
  local svc prefix pods
  for svc in $(service_list); do
    prefix="$(service_prefix "${svc}")"
    pods="$(kubectl get pods -n "${NAMESPACE}" -l "app.kubernetes.io/name=${prefix}" \
      --sort-by=.metadata.creationTimestamp -o name 2>/dev/null || true)"
    [ -n "${pods}" ] || {
      warn "${prefix}：没有 Pod"
      continue
    }
    if [ "${FOLLOW}" = "yes" ]; then
      # 跟随只能跟一个 Pod，取最新的那个
      local newest
      newest="$(echo "${pods}" | tail -1)"
      log "${newest#pod/} 日志（follow，Ctrl-C 退出）"
      kubectl logs -n "${NAMESPACE}" "${newest}" --tail=100 -f
    else
      for p in ${pods}; do
        log "${p#pod/} 日志（最后 100 行）"
        kubectl logs -n "${NAMESPACE}" "${p}" --tail=100 2>&1 | sed 's/^/  /' || true
      done
    fi
  done
}

do_restart() {
  local svc prefix
  for svc in $(service_list); do
    prefix="$(service_prefix "${svc}")"
    if ! kubectl get deploy "${prefix}" -n "${NAMESPACE}" >/dev/null 2>&1; then
      warn "${prefix}：不存在，跳过"
      continue
    fi
    log "滚动重启 ${prefix}"
    kubectl rollout restart "deploy/${prefix}" -n "${NAMESPACE}" >/dev/null
    kubectl rollout status "deploy/${prefix}" -n "${NAMESPACE}" --timeout=240s
  done
}

# ---------------------------------------------------------------- clean

do_clean() {
  local targets cms svc prefix image
  if [ "${SERVICE}" = "all" ]; then
    targets="deploy,svc,ingress,hpa,pdb,secret"
  else
    targets="deploy,svc,ingress,hpa,pdb"
  fi
  log "清理命名空间 ${NAMESPACE} 中的部署与配置（--service ${SERVICE}）："
  kubectl get ${targets},cm -n "${NAMESPACE}" 2>/dev/null | sed 's/^/  /' || true
  if [ "${SERVICE}" = "all" ]; then
    kubectl delete ${targets} -n "${NAMESPACE}" --all --ignore-not-found 2>&1 | sed 's/^/  /' || true
    # ConfigMap 单独删：跳过 kube-root-ca.crt（由控制器维护，删了也会立刻重建）
    cms="$(kubectl get cm -n "${NAMESPACE}" -o name 2>/dev/null | grep -v 'kube-root-ca.crt' || true)"
    if [ -n "${cms}" ]; then
      echo "${cms}" | xargs kubectl delete -n "${NAMESPACE}" 2>&1 | sed 's/^/  /' || true
    fi
  else
    for svc in $(service_list); do
      prefix="$(service_prefix "${svc}")"
      kubectl delete deploy,svc,ingress,hpa,pdb "${prefix}" -n "${NAMESPACE}" --ignore-not-found 2>&1 | sed 's/^/  /' || true
      kubectl delete cm "${prefix}-config" -n "${NAMESPACE}" --ignore-not-found 2>&1 | sed 's/^/  /' || true
      kubectl delete secret "${prefix}-secret" -n "${NAMESPACE}" --ignore-not-found 2>&1 | sed 's/^/  /' || true
    done
  fi
  if [ "${WITH_IMAGES}" = "yes" ]; then
    # Pod 还在 Terminating 时镜像被容器引用着，docker rmi 会失败；先等 Pod 真正消失（最多 90s）
    for svc in $(service_list); do
      prefix="$(service_prefix "${svc}")"
      kubectl wait --for=delete pod -l "app.kubernetes.io/name=${prefix}" -n "${NAMESPACE}" \
        --timeout=90s >/dev/null 2>&1 || true
    done
    for svc in $(service_list); do
      image="$(service_image "${svc}")"
      if docker image inspect "${image}" >/dev/null 2>&1; then
        log "删除本地镜像 ${image}"
        docker rmi "${image}" >/dev/null 2>&1 || warn "删除 ${image} 失败（可能仍被容器/其它 tag 引用）"
      fi
    done
  fi
  if [ "${PURGE}" = "yes" ]; then
    log "删除命名空间 ${NAMESPACE}"
    kubectl delete namespace "${NAMESPACE}" --ignore-not-found --wait=true 2>&1 | sed 's/^/  /' || true
  else
    log "保留命名空间 ${NAMESPACE}（要连命名空间一起删：clean --purge）"
  fi
  log "清理完成"
}

# ---------------------------------------------------------------- deploy

env_value() { sed -n "s/^$1=//p" .env.local | head -1; }
# resolve <变量名> <默认值>：显式传入的环境变量 > .env.local > 默认值
resolve() {
  local name="$1" fallback="${2:-}" current value
  eval "current=\"\${${name}:-}\""
  if [ -n "${current}" ]; then
    eval "export ${name}=\"\${current}\""
    return
  fi
  value="$(env_value "${name}")"
  [ -n "${value}" ] || value="${fallback}"
  eval "export ${name}=\"\${value}\""
}

load_config() {
  [ -f .env.local ] || die "缺少 .env.local（放中间件口令）；先 cp .env.example .env.local 再填值"
  resolve MYSQL_HOST "mysql.infra.svc.cluster.local"
  resolve MYSQL_PORT "3306"
  resolve MYSQL_DATABASE_00 "ian_dev_tech_db_00"
  resolve MYSQL_DATABASE_01 "ian_dev_tech_db_01"
  resolve MYSQL_DATABASE_RBAC "ddd_rbac"
  resolve REDIS_HOST "redis.infra.svc.cluster.local"
  resolve REDIS_PORT "6379"
  resolve NACOS_HOST "nacos.infra.svc.cluster.local"
  # 宿主机可达的 Nacos HTTP 入口，仅用于「确保命名空间存在」这一步
  resolve NACOS_API_URL "http://127.0.0.1:8848"
  # 注册中心命名空间：--nacos-namespace > 环境变量/.env.local 的 DUBBO_REGISTRY_NAMESPACE > 默认 ian-<前缀>
  # 默认值按项目前缀派生：两个项目若共用同一 Nacos 命名空间，Dubbo 会把**同名服务**视为
  # 「同一服务的多个实例」并随机负载均衡，表现为随机的「账号或密码错误」。
  # 既有项目需在 .env.local 显式声明自己的命名空间以保持原状（如源项目用 dev-test）。
  if [ -n "${NACOS_NAMESPACE_CLI}" ]; then
    DUBBO_REGISTRY_NAMESPACE="${NACOS_NAMESPACE_CLI}"
  else
    resolve DUBBO_REGISTRY_NAMESPACE "ian-${PROJECT_PREFIX}"
  fi
  # public 就是默认命名空间，等价于「不加 namespace 参数」
  if [ "${DUBBO_REGISTRY_NAMESPACE}" = "public" ]; then
    DUBBO_REGISTRY_NAMESPACE=""
  fi
  export DUBBO_REGISTRY_NAMESPACE
  resolve KAFKA_BOOTSTRAP_SERVERS "kafka.infra.svc.cluster.local:9092"
  # 本地 infra 的 Kafka 通告地址是 kafka:9092，跨命名空间解析不了；默认关掉降噪
  resolve KAFKA_ENABLED "false"
  # ID 生成器命名空间（P0）：两个项目若取同一命名空间，会各自租到**同一个 WorkerId**，
  # 生成的雪花 ID 必然重复。默认按项目前缀派生。
  resolve DDD_ID_GENERATOR_NAMESPACE "ian-${PROJECT_PREFIX}-auth-boot"
  resolve CHANNEL_ENCRYPTION_KEY_ID "dev-key-v1"
  resolve IMAGE_PREFIX "system"
  resolve MYSQL_USERNAME ""
  resolve MYSQL_PASSWORD ""
  resolve REDIS_PASSWORD ""
  resolve DUBBO_REGISTRY_PASSWORD ""
  resolve CHANNEL_ENCRYPTION_MASTER_KEY ""
  resolve PLATFORM_ADMIN_TOKEN ""
  local key
  for key in MYSQL_USERNAME MYSQL_PASSWORD REDIS_PASSWORD DUBBO_REGISTRY_PASSWORD; do
    eval "[ -n \"\${${key}:-}\" ]" || die "缺少 ${key}（填到 .env.local，或用环境变量传入）"
  done
  # 启动期有强校验（SecretConfigurationValidator）：prod 不接受全零渠道主密钥与示例平台令牌
  if [ "${PROFILE}" = "prod" ]; then
    [ "${CHANNEL_ENCRYPTION_MASTER_KEY}" != "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=" ] \
      || die "profile=prod 不接受全零渠道主密钥。用环境变量覆盖后重跑：
  CHANNEL_ENCRYPTION_MASTER_KEY=\"\$(openssl rand -base64 32)\" PLATFORM_ADMIN_TOKEN=\"\$(openssl rand -hex 24)\" bash scripts/deploy-local.sh --profile prod
  注意：换主密钥后，库里用旧密钥加密的渠道凭证将无法解密"
    case "${PLATFORM_ADMIN_TOKEN}" in
      "" | dev-platform-token | test-platform-token) die "profile=prod 需要独立的 PLATFORM_ADMIN_TOKEN（当前为空或示例值）" ;;
    esac
  fi
}

sha256() {
  if command -v sha256sum >/dev/null; then sha256sum | cut -c1-16; else shasum -a 256 | cut -c1-16; fi
}
# 镜像「内容」标识：层摘要（文件内容）+ 镜像配置（Env / Entrypoint / Cmd 等）。
# 刻意不用镜像 ID：BuildKit 每次构建都会重写镜像 config 里的 created 时间戳，
# 即使全部层命中缓存、镜像内容逐字节一致，镜像 ID 也会变（已实测），拿它当判据会每次白滚一遍。
image_content_id() {
  docker image inspect "$1" >/dev/null 2>&1 || {
    echo missing
    return
  }
  docker image inspect "$1" --format '{{json .RootFS.Layers}}|{{json .Config}}' | sha256
}

# 注册中心地址：Dubbo 的 Nacos 注册中心把 namespace 当 URL 参数解析，它同时作用于服务发现与元数据
# （两个服务的 dubbo.registry.use-as-metadata-center 都是 true），因此只改这一处即可整链路隔离。
registry_address() {
  if [ -n "${DUBBO_REGISTRY_NAMESPACE}" ]; then
    echo "nacos://${NACOS_HOST}:8848?namespace=${DUBBO_REGISTRY_NAMESPACE}"
  else
    echo "nacos://${NACOS_HOST}:8848"
  fi
}

build_jars() {
  [ "${SKIP_BUILD}" = "yes" ] && return
  log "构建 $2 的 jar：mvn -pl $1 -am package -DskipTests"
  mvn -B -q -f pom.xml package -DskipTests -pl "$1" -am
}

build_image() {
  [ "${SKIP_BUILD}" = "yes" ] && return
  log "构建镜像（脚本自动识别本机架构）：$1"
  (cd "$1" && bash ./build.sh >"${TMP_DIR}/build.log" 2>&1) || {
    tail -20 "${TMP_DIR}/build.log" >&2
    die "镜像构建失败"
  }
  grep -E '^构建 ' "${TMP_DIR}/build.log" | tail -1 || true
}

write_config() {
  # $1 = auth|gateway → ${TMP_DIR}/<svc>.cm.env 与 <svc>.secret.env
  local svc="$1" cm="${TMP_DIR}/$1.cm.env" secret="${TMP_DIR}/$1.secret.env"
  if [ "${svc}" = "auth" ]; then
    {
      echo "SPRING_PROFILES_ACTIVE=${PROFILE}"
      if [ "${PROFILE}" = "dev" ]; then
        # dev 的分片配置把库地址写死成 127.0.0.1，集群内必须换成地址全部来自环境变量的 prod 分片配置
        echo "SPRING_DATASOURCE_URL=jdbc:shardingsphere:classpath:sharding/sharding-jdbc-prod.yaml?placeholder-type=environment"
      fi
      echo "DUBBO_QOS_ENABLED=false"
      echo "DUBBO_PROTOCOL_PORT=20880"
      echo "DUBBO_REGISTRY_ADDRESS=$(registry_address)"
      echo "DUBBO_REGISTRY_USERNAME=nacos"
      echo "REDIS_HOST=${REDIS_HOST}"
      echo "REDIS_PORT=${REDIS_PORT}"
      echo "REDIS_DATABASE=0"
      echo "DDD_ID_GENERATOR_NAMESPACE=${DDD_ID_GENERATOR_NAMESPACE}"
      echo "MYSQL_HOST=${MYSQL_HOST}"
      echo "MYSQL_PORT=${MYSQL_PORT}"
      echo "MYSQL_DATABASE_00=${MYSQL_DATABASE_00}"
      echo "MYSQL_DATABASE_01=${MYSQL_DATABASE_01}"
      echo "MYSQL_DATABASE_RBAC=${MYSQL_DATABASE_RBAC}"
      echo "MYSQL_USERNAME=${MYSQL_USERNAME}"
      echo "KAFKA_ENABLED=${KAFKA_ENABLED}"
      echo "KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS}"
      echo "CHANNEL_ENCRYPTION_KEY_ID=${CHANNEL_ENCRYPTION_KEY_ID}"
      echo "XXL_JOB_ENABLED=false"
    } >"${cm}"
    {
      echo "MYSQL_PASSWORD=${MYSQL_PASSWORD}"
      echo "REDIS_PASSWORD=${REDIS_PASSWORD}"
      echo "DUBBO_REGISTRY_PASSWORD=${DUBBO_REGISTRY_PASSWORD}"
      echo "CHANNEL_ENCRYPTION_MASTER_KEY=${CHANNEL_ENCRYPTION_MASTER_KEY}"
      echo "PLATFORM_ADMIN_TOKEN=${PLATFORM_ADMIN_TOKEN}"
    } >"${secret}"
  else
    {
      echo "SPRING_PROFILES_ACTIVE=${PROFILE}"
      echo "DUBBO_REGISTRY_ADDRESS=$(registry_address)"
      echo "DUBBO_REGISTRY_USERNAME=nacos"
    } >"${cm}"
    echo "DUBBO_REGISTRY_PASSWORD=${DUBBO_REGISTRY_PASSWORD}" >"${secret}"
  fi
}

ensure_namespace() {
  kubectl get namespace "${NAMESPACE}" >/dev/null 2>&1 && return
  log "创建命名空间 ${NAMESPACE}"
  kubectl create namespace "${NAMESPACE}"
}

# 确保 Nacos 里存在 DUBBO_REGISTRY_NAMESPACE 这个命名空间（记录）。
#
# Dubbo 的 namespace 参数是**命名空间 ID**：ID 在 Nacos 里没有对应记录时客户端照样注册成功，
# 但那个租户在控制台里列不出来（控制台按 ID 选命名空间），表现为「部署的服务在命名空间里看不到」。
# 传 public/空值表示用默认命名空间，不需要这一步。失败只告警，不阻断部署。
ensure_registry_namespace() {
  [ -n "${DUBBO_REGISTRY_NAMESPACE}" ] || return 0
  command -v curl >/dev/null || { warn "找不到 curl，跳过 Nacos 命名空间检查"; return 0; }

  local api="${NACOS_API_URL}" token listing
  token="$(curl -s -m 5 -X POST "${api}/nacos/v1/auth/login" \
    --data-urlencode "username=nacos" --data-urlencode "password=${DUBBO_REGISTRY_PASSWORD}" 2>/dev/null \
    | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p' || true)"
  if [ -z "${token}" ]; then
    warn "无法登录 Nacos（${api}），跳过命名空间检查；若控制台看不到服务，见文件头「两个命名空间」说明"
    return 0
  fi
  listing="$(curl -s -m 5 "${api}/nacos/v3/admin/core/namespace/list?pageNo=1&pageSize=200&accessToken=${token}" || true)"
  case "${listing}" in
    *"\"namespace\":\"${DUBBO_REGISTRY_NAMESPACE}\""*) return 0 ;;
  esac

  log "创建 Nacos 命名空间 ${DUBBO_REGISTRY_NAMESPACE}"
  curl -s -m 5 -X POST "${api}/nacos/v3/admin/core/namespace?accessToken=${token}" \
    --data-urlencode "namespaceId=${DUBBO_REGISTRY_NAMESPACE}" \
    --data-urlencode "namespaceName=${DUBBO_REGISTRY_NAMESPACE}" \
    --data-urlencode "namespaceDesc=本地联调集群（scripts/deploy-local.sh）" >/dev/null 2>&1 || true
  listing="$(curl -s -m 5 "${api}/nacos/v3/admin/core/namespace/list?pageNo=1&pageSize=200&accessToken=${token}" || true)"
  case "${listing}" in
    *"\"namespace\":\"${DUBBO_REGISTRY_NAMESPACE}\""*) ;;
    *) warn "命名空间 ${DUBBO_REGISTRY_NAMESPACE} 仍不存在，请手工确认 Nacos 版本（本脚本按 Nacos 3.x API 调用）" ;;
  esac
}

apply_config() {
  local svc="$1" prefix="$2"
  write_config "${svc}"
  # 命令式创建 + apply：可重复执行，且真实凭证只进集群、不进仓库
  kubectl create configmap "${prefix}-config" -n "${NAMESPACE}" \
    --from-env-file="${TMP_DIR}/${svc}.cm.env" --dry-run=client -o yaml | kubectl apply -f - >/dev/null
  kubectl create secret generic "${prefix}-secret" -n "${NAMESPACE}" \
    --from-env-file="${TMP_DIR}/${svc}.secret.env" --dry-run=client -o yaml | kubectl apply -f - >/dev/null
  log "已刷新配置：${prefix}-config / ${prefix}-secret"
}

apply_manifests() {
  local svc="$1" dir prefix files img
  dir="$(service_dir "${svc}")"
  prefix="$(service_prefix "${svc}")"
  img="$(service_image "${svc}")"
  # deployment.yaml 里的 image 是静态值，这里替换为按项目前缀派生的镜像名后再 apply。
  # 目的：即使清单文件里的名字与当前项目不符（例如清单被复制而漏改），
  # 也不会把别的项目的镜像名带进本命名空间。清单其余字段不动。
  sed "s|^\( *image:\).*|\1 ${img}|" "${dir}/deployment.yaml" \
    | kubectl apply -n "${NAMESPACE}" -f - >/dev/null
  # 刻意逐文件 apply，不用 -f <目录>：目录里的 configmap.yaml 是 prod 默认值，会把按 profile 生成的配置冲掉
  files=(-f "${dir}/service.yaml" -f "${dir}/hpa.yaml" -f "${dir}/pdb.yaml")
  [ "${svc}" = "gateway" ] && files+=(-f "${dir}/ingress.yaml")
  kubectl apply -n "${NAMESPACE}" "${files[@]}" >/dev/null
  log "已应用清单：${dir}（镜像 ${img}）"
}

# 本地副本数：默认收到 1 个，避免在开发机上白占内存；同时把 HPA 的 minReplicas 一起改，
# 否则 HPA 会在下一轮（默认 15s）把手动 scale 改回清单里的 2。
tune_replicas() {
  local svc="$1" prefix
  [ "${REPLICAS}" = "keep" ] && return
  prefix="$(service_prefix "${svc}")"
  if kubectl get hpa "${prefix}" -n "${NAMESPACE}" >/dev/null 2>&1; then
    kubectl patch hpa "${prefix}" -n "${NAMESPACE}" --type=merge \
      -p "{\"spec\":{\"minReplicas\":${REPLICAS}}}" >/dev/null
  fi
  kubectl scale "deploy/${prefix}" -n "${NAMESPACE}" --replicas="${REPLICAS}" >/dev/null
  log "${prefix}：副本数设为 ${REPLICAS}（HPA minReplicas 同步；要按清单 2→8 用 --replicas keep）"
}

# 内容没变就不滚动：把「镜像内容摘要 + 配置摘要」打成 Pod 模板注解，变了才触发滚动更新
rollout_if_changed() {
  local svc="$1" prefix image current live hash
  prefix="$(service_prefix "${svc}")"
  image="$(service_image "${svc}")"
  current="$(image_content_id "${image}")"
  [ "${current}" != "missing" ] || die "本地没有镜像 ${image}：去掉 --skip-build 重跑，或先手工构建"
  live="$(kubectl get deploy "${prefix}" -n "${NAMESPACE}" \
    -o jsonpath='{.spec.template.metadata.annotations.deploy-local\.hash}' 2>/dev/null || true)"
  hash="$({ echo "${current}"; cat "${TMP_DIR}/${svc}.cm.env" "${TMP_DIR}/${svc}.secret.env"; } | sha256)"
  if [ -n "${live}" ] && [ "${live}" = "${hash}" ]; then
    log "${prefix}：镜像与配置都没变，跳过滚动更新"
    return
  fi
  kubectl patch deploy "${prefix}" -n "${NAMESPACE}" --type=merge \
    -p "{\"spec\":{\"template\":{\"metadata\":{\"annotations\":{\"deploy-local.hash\":\"${hash}\"}}}}}" >/dev/null
  log "${prefix}：触发滚动更新（摘要 ${hash}）"
}

deploy_service() {
  local svc="$1" module
  module="$(service_module "${svc}")"
  build_jars "${module}" "${svc}"
  build_image "${module}"
  apply_config "${svc}" "$(service_prefix "${svc}")"
  apply_manifests "${svc}"
  tune_replicas "${svc}"
  rollout_if_changed "${svc}"
  kubectl rollout status "deploy/$(service_prefix "${svc}")" -n "${NAMESPACE}" --timeout=240s
}

do_deploy() {
  load_config
  TMP_DIR="$(mktemp -d)"
  trap 'rm -rf "${TMP_DIR}"' EXIT
  log "部署到 k8s 命名空间 ${NAMESPACE}，注册中心命名空间 ${DUBBO_REGISTRY_NAMESPACE:-public（默认）}"
  ensure_namespace
  ensure_registry_namespace
  local svc
  for svc in $(service_list); do deploy_service "${svc}"; done
  do_status
  if [ "${SERVICE}" != "auth" ]; then
    log "验证（宿主机经 Ingress，不需要 port-forward）："
    printf '  curl -H "Host: gateway.example.com" http://127.0.0.1/actuator/health\n'
  fi
  warn "配置只由本脚本管理：手工 kubectl apply -f <目录> 会覆盖按 profile 生成的 ConfigMap，并绕开摘要检查"
  if [ "${REPLICAS}" != "keep" ]; then
    warn "本地副本数已收到 ${REPLICAS}（HPA minReplicas=${REPLICAS}）；要验证 HPA 伸缩请用 --replicas keep"
  fi
}

case "${ACTION}" in
  deploy) do_deploy ;;
  status) do_status ;;
  logs) do_logs ;;
  restart) do_restart ;;
  clean) do_clean ;;
esac
