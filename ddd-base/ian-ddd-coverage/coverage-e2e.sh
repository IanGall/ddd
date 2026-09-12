#!/usr/bin/env bash
#
# 分布式覆盖率端到端流水线：clean 构建 → 启动服务（带 JaCoCo Agent）→ 跑 E2E 测试 → 输出报告位置。
#
# 用法：
#   ./coverage-e2e.sh              全流程；结束时自动关停本次启动的所有服务
#   ./coverage-e2e.sh --keep       同上，但保留服务运行（便于手工查看报告）
#   ./coverage-e2e.sh start        只 clean 构建并启动服务
#   ./coverage-e2e.sh test         只跑测试（服务需已启动）
#   ./coverage-e2e.sh report       只打印最近一次的报告位置
#   ./coverage-e2e.sh stop         停止服务
#   ./coverage-e2e.sh status       查看服务与最近会话状态
#
# 运行时注册（控制器需已启动，注册结果落盘、重启自动恢复）：
#   ./coverage-e2e.sh register <服务名> <Agent端口> [classes目录,逗号分隔] [源码目录,逗号分隔] [主机]
#   ./coverage-e2e.sh list                          列出控制器中生效的全部服务
#   ./coverage-e2e.sh unregister <服务名>            注销运行时注册的服务
#
# 前置条件：
#   • Nacos(8848) / Redis(6379) / MySQL(3306) 已启动，且自动化测试库已建好（脚本不建库）：
#       ddd_rbac_test（RBAC/客户/渠道）+ ian_test_tech_db_00/01（user_order 分片），见 application-autotest.yml
#   • 凭证（MySQL/Redis/Nacos 口令、渠道主密钥、平台开户令牌）从仓库根 .env.local 读取
#     （模板见仓库根 .env.example），或直接以环境变量注入
#
# 可选环境变量：
#   COVERAGE_SPRING_PROFILES    服务激活的 Spring profile，默认 dev,autotest（后者优先，指向测试库）
#   COVERAGE_E2E_LOGIN_NAME      管理员登录名；不填则自动开户并缓存凭证
#   COVERAGE_E2E_LOGIN_PASSWORD  管理员密码；不填则读取 .env.local 的同名变量
#   COVERAGE_PLATFORM_TOKEN      平台开户令牌；与标准服务 PLATFORM_ADMIN_TOKEN 一致，不填则读取 .env.local
#   COVERAGE_SKIP_BUILD          设为 1 跳过 clean 构建（快速重跑）
#   WORKSPACE_DIR                覆盖工作区根目录探测结果
#   EXTRA_SERVICES               追加启动的业务服务，逗号分隔，每项格式：
#                                  名称:项目目录:Agent端口[:健康检查URL]
#                                目录为绝对路径或相对工作区根目录；省略健康检查 URL 时只等 Agent 端口
#                                示例：EXTRA_SERVICES="order:ian-ddd-order:6302,pay:./pay:6303:http://127.0.0.1:8093/actuator/health"
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COVERAGE_DIR="${SCRIPT_DIR}"                              # <workspace>/ddd-base/ian-ddd-coverage

# 加载仓库根（ddd/）的 .env.local：本地开发凭证统一从该文件读取，不入库（模板见仓库根 .env.example）。
# 生产/CI 环境直接以环境变量注入，不依赖该文件。
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
load_local_env || echo "[e2e] 未找到仓库根的 .env.local，凭证直接读取当前环境变量（模板见 .env.example）" >&2

# JaCoCo 版本唯一来源：仓库根目录的 .mvn/jacoco-version（与 ddd-base/pom.xml 的 jacoco.version 保持一致）。
# 脚本不再硬编码版本号，避免升级时漏改导致 Agent 与报告解析版本不一致（表现为覆盖率静默为 0）。
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
[[ -n "${JACOCO_VERSION}" ]] || {
    echo "[e2e] 无法确定 JaCoCo 版本：请确认仓库根目录存在 .mvn/jacoco-version，或设置 JACOCO_VERSION" >&2
    exit 1
}
JACOCO_AGENT_JAR="${JACOCO_AGENT_JAR:-${HOME}/.m2/repository/org/jacoco/org.jacoco.agent/${JACOCO_VERSION}/org.jacoco.agent-${JACOCO_VERSION}-runtime.jar}"

# 工作区根目录：包含 ddd-base 与 Gateway 工程的目录。
# 默认从当前模块位置推导，可用 WORKSPACE_DIR 覆盖。
detect_workspace_root() {
    local candidate
    candidate="$(dirname "$(dirname "${COVERAGE_DIR}")")"   # 候选：<workspace>/ddd-base 的上一级
    local probe="${candidate}"
    local i=0
    while (( i < 5 )); do
        if [[ -d "${probe}/ddd-base" && -d "${probe}/ian-ddd-gateway" ]]; then
            printf '%s' "${probe}"
            return 0
        fi
        probe="$(dirname "${probe}")"
        (( i++ )) || true
    done
    return 1
}

WORKSPACE_DIR="${WORKSPACE_DIR:-$(detect_workspace_root || true)}"
[[ -n "${WORKSPACE_DIR}" ]] || {
    echo "[e2e] 无法定位工作区根目录（需同时存在 ddd-base 与 ian-ddd-gateway），请显式设置 WORKSPACE_DIR" >&2
    exit 1
}
DDD_BASE_DIR="${WORKSPACE_DIR}/ddd-base"
GATEWAY_DIR="${WORKSPACE_DIR}/ian-ddd-gateway"
STD_DIR="${WORKSPACE_DIR}/ian-ddd-archetype-std"

CONTROLLER_PORT="${CONTROLLER_PORT:-8099}"
GATEWAY_PORT="${GATEWAY_PORT:-8092}"
GATEWAY_AGENT_PORT="${GATEWAY_AGENT_PORT:-6300}"
STD_AGENT_PORT="${STD_AGENT_PORT:-6301}"

COVERAGE_HOME="${COVERAGE_HOME:-${WORKSPACE_DIR}/coverage}"
SESSIONS_DIR="${COVERAGE_HOME}/sessions"

# 本轮测试新增的 Session，以及合并后的并集 Session（由 run_tests / merge_sessions 填充）
TEST_RUN_SESSIONS=()
merged_session=""

LOG_DIR_ROOT="${TMPDIR:-/tmp}"
LOG_DIR="${LOG_DIR_ROOT%/}/coverage-e2e"
mkdir -p "${LOG_DIR}"

# 保存开户结果，避免每次运行重复开户
ACCOUNT_STATE_FILE="${LOG_DIR}/account.env"

DEFAULT_ACCOUNT_NAME="${COVERAGE_E2E_ACCOUNT_NAME:-coverage_admin}"
# 测试账号密码与平台开户令牌必须显式提供（仓库根 .env.local 或环境变量），不再内置默认值。
DEFAULT_ACCOUNT_PASSWORD="${COVERAGE_E2E_LOGIN_PASSWORD:-}"
PLATFORM_TOKEN="${COVERAGE_PLATFORM_TOKEN:-}"

KEEP_RUNNING=0
COMMAND="all"

# register / unregister 子命令的参数（未使用时保持空值，配合 set -u）
REGISTER_NAME=""
REGISTER_PORT=""
REGISTER_CLASSES=""
REGISTER_SOURCES=""
REGISTER_HOST="127.0.0.1"
UNREGISTER_NAME=""

# 追加启动的业务服务：逗号分隔的「名称:项目目录:Agent端口[:健康检查URL]」
EXTRA_SERVICES="${EXTRA_SERVICES:-}"

# ---------------------------------------------------------------- 追加服务解析

extra_service_count() {
    [[ -n "${EXTRA_SERVICES}" ]] || { echo 0; return; }
    local n=0 item
    IFS=',' read -ra _items <<< "${EXTRA_SERVICES}"
    for item in "${_items[@]}"; do
        [[ -n "${item// /}" ]] && (( n++ )) || true
    done
    echo "${n}"
}

# 按序号读取某个追加服务的字段。
# 字段：1=名称 2=项目目录 3=Agent端口 4=健康检查URL
# 前三个字段用冒号分隔，第四个字段是剩余全部内容（URL 自带冒号，不能按冒号切分）
extra_service_field() {
    local index="$1" field="$2" n=0 item
    IFS=',' read -ra _items <<< "${EXTRA_SERVICES}"
    for item in "${_items[@]}"; do
        [[ -n "${item// /}" ]] || continue
        if (( n == index )); then
            case "${field}" in
                1) echo "${item%%:*}" ;;
                2) local after_name rest2
                   after_name="${item#*:}"
                   # 没有更多冒号时整个剩余部分就是目录
                   if [[ "${after_name}" != *:* ]]; then echo "${after_name}"; else echo "${after_name%%:*}"; fi
                   ;;
                3) local after_port
                   after_port="${item#*:}"
                   after_port="${after_port#*:}"
                   echo "${after_port%%:*}" ;;
                4) [[ "${item}" == *:*:*:* ]] && echo "${item#*:*:*:}" || echo "" ;;
                *) echo "" ;;
            esac
            return 0
        fi
        (( n++ )) || true
    done
    echo ""
}

# 追加服务的项目目录：绝对路径原样使用，相对路径基于工作区根目录
extra_service_dir() {
    local index="$1" dir
    dir="$(extra_service_field "${index}" 2)"
    if [[ "${dir}" = /* ]]; then
        echo "${dir}"
    else
        echo "${WORKSPACE_DIR}/${dir}"
    fi
}

# ---------------------------------------------------------------- 工具函数

info()  { printf '\033[0;36m[e2e]\033[0m %s\n' "$*"; }
warn()  { printf '\033[0;33m[e2e]\033[0m %s\n' "$*" >&2; }
fail()  { printf '\033[0;31m[e2e]\033[0m %s\n' "$*" >&2; exit 1; }

# 是否有进程监听指定端口
port_listening() {
    lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
}

# 等待端口就绪；超时返回非 0
wait_for_port() {
    local port="$1" timeout="${2:-60}" label="${3:-端口 ${1}}" waited=0
    while (( waited < timeout )); do
        if port_listening "${port}"; then
            return 0
        fi
        sleep 1
        (( waited++ )) || true
    done
    return 1
}

wait_for_http() {
    local url="$1" timeout="${2:-60}" waited=0
    while (( waited < timeout )); do
        # 只要求「有 HTTP 响应」，不限制状态码：控制器某些端点仅支持 POST，会返回 405
        if curl -s -o /dev/null --max-time 3 "${url}"; then
            return 0
        fi
        sleep 1
        (( waited++ )) || true
    done
    return 1
}

# 端口已监听且能返回 HTTP 响应（任意状态码）即视为就绪
wait_for_service() {
    local port="$1" url="$2" timeout="${3:-60}"
    wait_for_port "${port}" "${timeout}" || return 1
    wait_for_http "${url}" "${timeout}"
}

# 按端口停止进程（先优雅后强杀），并清理父级 Maven 进程
stop_port() {
    local port="$1" label="$2" pids
    pids="$(lsof -nP -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null || true)"
    if [[ -z "${pids}" ]]; then
        return 0
    fi
    info "停止 ${label}（端口 ${port}，PID ${pids}）"
    # shellcheck disable=SC2086
    kill ${pids} 2>/dev/null || true
    local waited=0
    while (( waited < 15 )); do
        port_listening "${port}" || return 0
        sleep 1
        (( waited++ )) || true
    done
    # shellcheck disable=SC2086
    kill -9 ${pids} 2>/dev/null || true
    sleep 1
}

stop_maven_run() {
    # spring-boot:run 会派生子 JVM，父 Maven 进程也要一并清理
    pkill -f "spring-boot:run" 2>/dev/null || true
}

stop_all() {
    # 追加服务只需要清理 Agent 端口
    local count index agent_port name
    count="$(extra_service_count)"
    for (( index = 0; index < count; index++ )); do
        name="$(extra_service_field "${index}" 1)"
        agent_port="$(extra_service_field "${index}" 3)"
        [[ -n "${agent_port}" ]] && stop_port "${agent_port}" "${name} 的覆盖率 Agent"
    done
    stop_port "${GATEWAY_PORT}" "Gateway"
    stop_port "${GATEWAY_AGENT_PORT}" "Gateway 的覆盖率 Agent"
    stop_port "${STD_AGENT_PORT}" "标准服务的覆盖率 Agent"
    stop_port "${CONTROLLER_PORT}" "覆盖率控制器"
    stop_maven_run
}

cleanup_on_exit() {
    local exit_code=$?
    if (( KEEP_RUNNING == 0 )); then
        echo
        info "清理：关停本次启动的服务"
        stop_all
        info "服务已全部停止；报告文件仍保留在 ${SESSIONS_DIR}"
    else
        info "保留服务运行中（--keep）。停止请执行：$0 stop"
    fi
    exit ${exit_code}
}

# ---------------------------------------------------------------- 各阶段

build_all() {
    if [[ "${COVERAGE_SKIP_BUILD:-0}" == "1" ]]; then
        info "跳过构建（COVERAGE_SKIP_BUILD=1）"
        return 0
    fi
    info "清理并构建 ddd-base（含覆盖率模块）"
    (cd "${DDD_BASE_DIR}" && mvn -q -o clean install -DskipTests) \
        || fail "ddd-base 构建失败"
    info "清理并构建标准服务"
    (cd "${STD_DIR}" && mvn -q -o clean install -DskipTests) \
        || fail "标准服务构建失败"
    info "清理并构建 Gateway"
    (cd "${GATEWAY_DIR}" && mvn -q -o clean install -DskipTests) \
        || fail "Gateway 构建失败"
    info "构建完成"
}

preflight() {
    command -v lsof >/dev/null || fail "缺少 lsof"
    command -v curl >/dev/null || fail "缺少 curl"

    local jacoco_jar="${JACOCO_AGENT_JAR}"
    [[ -f "${jacoco_jar}" ]] || fail "缺少 jacocoagent: ${jacoco_jar}"

    for port in 8848 6379 3306; do
        port_listening "${port}" || warn "基础设施端口 ${port} 未监听，服务可能启动失败"
    done

    # 报告依赖各服务的 classes 目录，缺失会导致覆盖率静默为 0
    local missing=()
    for dir in \
        "${GATEWAY_DIR}/gateway-app/target/classes" \
        "${STD_DIR}/ian-frame-archetype-std-trigger/target/classes" \
        "${STD_DIR}/ian-frame-archetype-std-domain/target/classes" \
        "${STD_DIR}/ian-frame-archetype-std-infrastructure/target/classes" \
        "${STD_DIR}/ian-frame-archetype-std-api/target/classes"; do
        [[ -d "${dir}" ]] || missing+=("${dir}")
    done

    # 追加服务：检查项目目录与带覆盖率的启动脚本
    local count index name dir script
    count="$(extra_service_count)"
    for (( index = 0; index < count; index++ )); do
        name="$(extra_service_field "${index}" 1)"
        dir="$(extra_service_dir "${index}")"
        [[ -d "${dir}" ]] || { missing+=("${dir}"); continue; }
        script="$(extra_service_start_script "${dir}")"
        [[ -n "${script}" ]] || fail "追加服务 [${name}] 缺少启动脚本：${dir}/docs/dev-ops/start-with-coverage.sh 或 ${dir}/dev-ops/start-with-coverage.sh"
    done

    if (( ${#missing[@]} > 0 )); then
        fail "以下目录不存在，请先构建（不要用 COVERAGE_SKIP_BUILD=1）：$(printf '\n  %s' "${missing[@]}")"
    fi
}

# 定位服务的带覆盖率启动脚本（标准服务骨架在 docs/dev-ops，网关骨架在 dev-ops）
extra_service_start_script() {
    local dir="$1" candidate
    for candidate in "${dir}/docs/dev-ops/start-with-coverage.sh" "${dir}/dev-ops/start-with-coverage.sh"; do
        if [[ -f "${candidate}" ]]; then
            echo "${candidate}"
            return 0
        fi
    done
    echo ""
}

# 启动前先释放端口：上次异常退出可能留下监听进程，导致新实例启动失败
reclaim_ports() {
    local occupied=() port
    for port in "${CONTROLLER_PORT}" "${GATEWAY_PORT}" "${GATEWAY_AGENT_PORT}" "${STD_AGENT_PORT}"; do
        port_listening "${port}" && occupied+=("${port}")
    done
    local count index agent_port
    count="$(extra_service_count)"
    for (( index = 0; index < count; index++ )); do
        agent_port="$(extra_service_field "${index}" 3)"
        [[ -n "${agent_port}" ]] && port_listening "${agent_port}" && occupied+=("${agent_port}")
    done

    if (( ${#occupied[@]} == 0 )); then
        return 0
    fi
    warn "以下端口已被占用，先关停占用进程：${occupied[*]}"
    stop_all
    sleep 2
}

start_services() {
    info "启动覆盖率控制器（端口 ${CONTROLLER_PORT}，工作目录 ${COVERAGE_HOME}）"
    (
        cd "${COVERAGE_DIR}/coverage-controller"
        # 显式指定工作目录，保证脚本与控制器对报告位置的理解一致
        nohup java -jar target/coverage-controller-1.0-SNAPSHOT.jar \
            --coverage.session.work-directory="${COVERAGE_HOME}" \
            > "${LOG_DIR}/controller.log" 2>&1 &
    )
    wait_for_service "${CONTROLLER_PORT}" "http://127.0.0.1:${CONTROLLER_PORT}/api/coverage/sessions" 60 \
        || fail "控制器启动失败，日志：${LOG_DIR}/controller.log"
    grep -q "Started CoverageControllerApplication" "${LOG_DIR}/controller.log" \
        || fail "控制器未完成启动，日志：${LOG_DIR}/controller.log"

    start_std_service
    start_gateway
    start_extra_services

    info "所有服务已就绪"
}

# 标准服务：纯 Dubbo 提供方，无 web 端口，以 Agent 端口 + Spring 启动日志判定就绪
start_std_service() {
    info "启动标准服务（Agent 端口 ${STD_AGENT_PORT}）"
    nohup bash "${STD_DIR}/docs/dev-ops/start-with-coverage.sh" > "${LOG_DIR}/std.log" 2>&1 &
    wait_for_port "${STD_AGENT_PORT}" 120 "标准服务 Agent" \
        || fail "标准服务启动失败，日志：${LOG_DIR}/std.log"
    wait_for_log "${LOG_DIR}/std.log" "Started .*Application" 120 "标准服务"
}

# Gateway：HTTP 健康检查返回 2xx 即说明 Spring 上下文已就绪
start_gateway() {
    info "启动 Gateway（端口 ${GATEWAY_PORT}，Agent 端口 ${GATEWAY_AGENT_PORT}）"
    nohup bash "${GATEWAY_DIR}/dev-ops/start-with-coverage.sh" > "${LOG_DIR}/gateway.log" 2>&1 &
    wait_for_service "${GATEWAY_PORT}" "http://127.0.0.1:${GATEWAY_PORT}/actuator/health" 120 \
        || fail "Gateway 启动失败，日志：${LOG_DIR}/gateway.log"
}

# 追加服务：用各自项目内的 start-with-coverage.sh 启动
start_extra_services() {
    local count index name dir script agent_port health_url log
    count="$(extra_service_count)"
    (( count > 0 )) || return 0
    for (( index = 0; index < count; index++ )); do
        name="$(extra_service_field "${index}" 1)"
        dir="$(extra_service_dir "${index}")"
        agent_port="$(extra_service_field "${index}" 3)"
        health_url="$(extra_service_field "${index}" 4)"
        script="$(extra_service_start_script "${dir}")"
        log="${LOG_DIR}/${name}.log"

        if [[ -z "${agent_port}" ]]; then
            fail "追加服务 [${name}] 未指定 Agent 端口（EXTRA_SERVICES 格式：名称:目录:Agent端口[:健康检查URL]）"
        fi

        info "启动追加服务 ${name}（目录 ${dir}，Agent 端口 ${agent_port}）"
        nohup bash "${script}" > "${log}" 2>&1 &
        wait_for_port "${agent_port}" 120 "${name} 的 Agent" \
            || fail "追加服务 ${name} 启动失败，日志：${log}"
        if [[ -n "${health_url}" ]]; then
            wait_for_http "${health_url}" 120 \
                || fail "追加服务 ${name} 健康检查未通过（${health_url}），日志：${log}"
        fi
    done
}

# 等待日志中出现匹配内容
wait_for_log() {
    local file="$1" pattern="$2" timeout="${3:-120}" label="${4:-服务}" waited=0
    while (( waited < timeout )); do
        grep -qE "${pattern}" "${file}" 2>/dev/null && return 0
        sleep 1
        (( waited++ )) || true
    done
    return 1
}

# 保证存在可用的管理员账号，成功后把登录名写入全局变量 LOGIN_NAME / LOGIN_PASSWORD
ensure_account() {
    # 1) 显式提供优先
    if [[ -n "${COVERAGE_E2E_LOGIN_NAME:-}" ]]; then
        LOGIN_NAME="${COVERAGE_E2E_LOGIN_NAME}"
        LOGIN_PASSWORD="${COVERAGE_E2E_LOGIN_PASSWORD:-${DEFAULT_ACCOUNT_PASSWORD}}"
        [[ -n "${LOGIN_PASSWORD}" ]] || fail "未提供登录密码：请设置 COVERAGE_E2E_LOGIN_PASSWORD（可写入仓库根 .env.local，模板见 .env.example）"
        login_ok "${LOGIN_NAME}" "${LOGIN_PASSWORD}" \
            || fail "提供的账号无法登录：${LOGIN_NAME}"
        info "使用环境变量提供的管理员账号：${LOGIN_NAME}"
        return 0
    fi

    # 2) 复用上次开户结果（凭证保存在状态文件，避免重复开户）
    if [[ -f "${ACCOUNT_STATE_FILE}" ]]; then
        # shellcheck disable=SC1090
        source "${ACCOUNT_STATE_FILE}"
        if [[ -n "${LOGIN_NAME:-}" ]] && login_ok "${LOGIN_NAME}" "${LOGIN_PASSWORD}"; then
            info "复用已保存的管理员账号：${LOGIN_NAME}"
            return 0
        fi
        warn "已保存的账号不可用，将重新开户"
    fi

    # 3) 开户：接口返回的 loginName 形如「用户名@accountId.com」，必须完整使用
    [[ -n "${PLATFORM_TOKEN}" ]] || fail "未提供平台开户令牌：请设置 COVERAGE_PLATFORM_TOKEN（与标准服务 PLATFORM_ADMIN_TOKEN 一致，可写入仓库根 .env.local）"
    [[ -n "${DEFAULT_ACCOUNT_PASSWORD}" ]] || fail "未提供开户密码：请设置 COVERAGE_E2E_LOGIN_PASSWORD（可写入仓库根 .env.local，模板见 .env.example）"
    local response
    response="$(curl -s -X POST "http://127.0.0.1:${GATEWAY_PORT}/api/admin/platform/accounts" \
        -H 'Content-Type: application/json' \
        -H "X-Platform-Token: ${PLATFORM_TOKEN}" \
        -d "{\"username\":\"${DEFAULT_ACCOUNT_NAME}\",\"password\":\"${DEFAULT_ACCOUNT_PASSWORD}\",\"displayName\":\"覆盖率测试账号\"}" \
        || true)"

    LOGIN_NAME="$(extract_json_field "${response}" loginName)"
    LOGIN_PASSWORD="${DEFAULT_ACCOUNT_PASSWORD}"
    if [[ -z "${LOGIN_NAME}" ]]; then
        fail "开户失败（账号可能已存在但未保存凭证）：${response}
  处理：设置 COVERAGE_E2E_LOGIN_NAME=用户名@accountId.com 后重试，或删除该账号重新开户"
    fi
    login_ok "${LOGIN_NAME}" "${LOGIN_PASSWORD}" || fail "新开户账号无法登录：${LOGIN_NAME}"

    umask 077
    cat > "${ACCOUNT_STATE_FILE}" <<EOF
LOGIN_NAME='${LOGIN_NAME}'
LOGIN_PASSWORD='${LOGIN_PASSWORD}'
EOF
    info "管理员账号就绪：${LOGIN_NAME}"
}

# 验证账号可登录
login_ok() {
    local login_name="$1" password="$2" body
    body="$(curl -s -X POST "http://127.0.0.1:${GATEWAY_PORT}/api/admin/auth/login" \
        -H 'Content-Type: application/json' \
        -d "{\"loginName\":\"${login_name}\",\"password\":\"${password}\",\"clientType\":\"WEB\"}" \
        || true)"
    [[ -n "$(extract_json_field "${body}" accessToken)" ]]
}

# 从 JSON 中提取简单字符串字段（避免额外依赖 jq）
extract_json_field() {
    printf '%s' "$1" | sed -n "s/.*\"$2\":\"\([^\"]*\)\".*/\1/p"
}

run_tests() {
    ensure_account || fail "无法确定管理员账号，请设置 COVERAGE_E2E_LOGIN_NAME"

    # 记录测试前已存在的 Session，测试结束后把本轮新增的 Session 合并成并集报告
    local before_listing after_listing session
    before_listing="$(ls "${SESSIONS_DIR}" 2>/dev/null || true)"

    info "运行 E2E 覆盖率测试"
    # 按命名约定跑全部 E2E 覆盖率用例：GatewayCoverageE2eTest 与 cn.iantech.gateway.e2e 下的 *E2eTest。
    # 每个测试类各自建立 Session（beforeAll 建、afterAll 汇总），互不干扰。
    # 注意：登录接口有 IP 风控（同一 IP 60 秒内 30 次尝试），连续重跑需间隔 60 秒以上，
    # 否则会命中 429 AUTH_RATE_LIMITED 导致用例失败。
    (cd "${GATEWAY_DIR}" && RUN_COVERAGE_E2E=true mvn -pl gateway-app test -o \
        -Dtest='GatewayCoverageE2eTest,*E2eTest' \
        -Dcoverage.e2e.login-name="${LOGIN_NAME}" \
        -Dcoverage.e2e.login-password="${LOGIN_PASSWORD}" \
        -Dcoverage.e2e.platform-token="${PLATFORM_TOKEN}" \
        -Dgateway.base-url="http://127.0.0.1:${GATEWAY_PORT}") \
        | tee "${LOG_DIR}/test.log" \
        || fail "测试失败，完整日志：${LOG_DIR}/test.log"

    echo
    sed -n '/本次测试覆盖率汇总/,/^\[INFO\]/p' "${LOG_DIR}/test.log" | head -30

    after_listing="$(ls "${SESSIONS_DIR}" 2>/dev/null || true)"
    TEST_RUN_SESSIONS=()
    for session in ${after_listing}; do
        if ! printf '%s\n' "${before_listing}" | grep -qx "${session}"; then
            TEST_RUN_SESSIONS+=("${session}")
        fi
    done
    if [[ ${#TEST_RUN_SESSIONS[@]} -eq 0 ]]; then
        warn "本轮未采集到任何 Session，跳过并集报告"
        merged_session=""
        return 0
    fi
    merge_sessions
}

# 把本轮所有 Session 的 execution data 合并成一份并集报告。
# 单类 Session 只覆盖自己触达的路径，并集才是整轮测试的真实覆盖率。
merge_sessions() {
    local payload json merged_id covered total ratio
    payload="$(printf '"%s",' "${TEST_RUN_SESSIONS[@]}")"
    payload="{\"name\":\"e2e-run\",\"sessionIds\":[${payload%,}]}"

    info "合并 ${#TEST_RUN_SESSIONS[@]} 个 Session 生成并集报告"
    json="$(curl -s -X POST "http://127.0.0.1:${CONTROLLER_PORT}/api/coverage/reports/merge" \
        -H 'Content-Type: application/json' \
        -d "${payload}" || true)"
    merged_id="$(extract_json_field "${json}" sessionId)"
    if [[ -z "${merged_id}" ]]; then
        warn "并集报告生成失败：${json}"
        merged_session=""
        return 0
    fi
    # 用 overall 的绝对行数自行计算百分比，避免依赖派生字段的序列化形式
    covered="$(printf '%s' "${json}" \
        | grep -o '"overall":{[^}]*}' | grep -o '"lineCovered":[0-9]*' | head -1 | cut -d: -f2)"
    total="$(printf '%s' "${json}" \
        | grep -o '"overall":{[^}]*}' | grep -o '"lineTotal":[0-9]*' | head -1 | cut -d: -f2)"
    ratio="$(awk -v c="${covered:-0}" -v t="${total:-0}" 'BEGIN { printf "%.2f", (t > 0 ? 100 * c / t : 0) }')"
    merged_session="${merged_id}"
    echo
    echo "======================================================================"
    echo " 本轮 E2E 并集覆盖率（${#TEST_RUN_SESSIONS[@]} 个 Session）"
    echo "======================================================================"
    echo " 并集行覆盖率   : ${ratio}%（已覆盖 ${covered:-0}/${total:-0}）"
    echo " 并集 Session   : ${merged_id}"
    echo "======================================================================"
    if awk -v r="${ratio}" -v min="${COVERAGE_MIN_RATIO:-40}" 'BEGIN { exit !(r < min) }'; then
        warn "并集行覆盖率低于目标 ${COVERAGE_MIN_RATIO:-40}%，请补充 E2E 用例（目标可用 COVERAGE_MIN_RATIO 调整）"
    fi
}

latest_session_dir() {
    ls -dt "${SESSIONS_DIR}"/*/ 2>/dev/null | head -1
}

print_report() {
    local session_dir latest
    # 优先展示本轮并集报告：单类 Session 的覆盖率不代表整轮测试
    if [[ -n "${merged_session:-}" ]]; then
        latest="${merged_session}"
    else
        session_dir="$(latest_session_dir || true)"
        if [[ -z "${session_dir}" ]]; then
            fail "还没有任何会话产物，请先执行 $0"
        fi
        latest="$(basename "${session_dir}")"
    fi
    session_dir="${SESSIONS_DIR}/${latest}/"

    echo
    echo "======================================================================"
    echo " 测试报告位置"
    echo "======================================================================"
    echo " 会话 ID        : ${latest}"
    echo " 产物根目录      : ${session_dir}"
    echo
    echo " 报告文件："
    echo "   总览仪表盘     : ${session_dir}dashboard.html"
    echo "   整体 HTML 报告 : ${session_dir}reports/index.html"
    echo "   整体 XML 报告  : ${session_dir}reports/overall.xml"
    # 服务报告位于 reports/<服务名>/index.html，服务名以控制器配置为准
    local service_name
    for service_name in $(registered_service_names); do
        if [[ -f "${session_dir}reports/${service_name}/index.html" ]]; then
            printf '   %-16s: %s\n' "${service_name} 服务报告" \
                "${session_dir}reports/${service_name}/index.html"
        fi
    done
    echo "   类级明细       : ${session_dir}reports/<服务名>/<包名>/<类名>.java.html"
    echo
    echo " 通过控制器访问（服务运行期间）："
    echo "   报告入口      : http://127.0.0.1:${CONTROLLER_PORT}/api/coverage/sessions/${latest}/reports/index.html"
    echo "   仪表盘         : http://127.0.0.1:${CONTROLLER_PORT}/api/coverage/sessions/${latest}/reports/dashboard.html"
    echo
    echo " 原始数据："
    echo "   各服务 exec    : ${session_dir}<agent>.exec"
    echo "   合并结果       : ${session_dir}overall.exec"
    echo
    echo " 服务日志："
    echo "   controller    : ${LOG_DIR}/controller.log"
    echo "   std           : ${LOG_DIR}/std.log"
    echo "   gateway       : ${LOG_DIR}/gateway.log"
    echo "   测试输出       : ${LOG_DIR}/test.log"
    echo "======================================================================"
}

# 从控制器配置读取服务名（coverage.reports[].name）
registered_service_names() {
    local config="${COVERAGE_DIR}/coverage-controller/src/main/resources/application.yml"
    [[ -f "${config}" ]] || return 0
    # 取 reports 段中每个条目的 name 字段
    sed -n '/^  reports:/,/^  [a-z]/p' "${config}" | sed -n 's/^ *- name: *\(.*\)/\1/p'
}

show_status() {
    echo "路径："
    printf '  %-22s %s\n' "工作区根目录" "${WORKSPACE_DIR}"
    printf '  %-22s %s\n' "覆盖率模块" "${COVERAGE_DIR}"
    printf '  %-22s %s\n' "Gateway" "${GATEWAY_DIR}"
    printf '  %-22s %s\n' "标准服务" "${STD_DIR}"
    printf '  %-22s %s\n' "覆盖率产物" "${COVERAGE_HOME}"
    printf '  %-22s %s\n' "日志目录" "${LOG_DIR}"
    echo
    echo "服务状态："
    printf '  %-24s %s\n' "覆盖率控制器:${CONTROLLER_PORT}" "$(port_listening "${CONTROLLER_PORT}" && echo 运行中 || echo 未运行)"
    printf '  %-24s %s\n' "Gateway:${GATEWAY_PORT}" "$(port_listening "${GATEWAY_PORT}" && echo 运行中 || echo 未运行)"
    printf '  %-24s %s\n' "Gateway Agent:${GATEWAY_AGENT_PORT}" "$(port_listening "${GATEWAY_AGENT_PORT}" && echo 运行中 || echo 未运行)"
    printf '  %-24s %s\n' "标准服务 Agent:${STD_AGENT_PORT}" "$(port_listening "${STD_AGENT_PORT}" && echo 运行中 || echo 未运行)"
    local count index name agent_port
    count="$(extra_service_count)"
    for (( index = 0; index < count; index++ )); do
        name="$(extra_service_field "${index}" 1)"
        agent_port="$(extra_service_field "${index}" 3)"
        printf '  %-24s %s\n' "${name} Agent:${agent_port}" "$(port_listening "${agent_port}" && echo 运行中 || echo 未运行)"
    done
    echo
    local session_dir
    session_dir="$(latest_session_dir || true)"
    if [[ -n "${session_dir}" ]]; then
        echo "最近一次会话：$(basename "${session_dir}")"
    else
        echo "最近一次会话：无"
    fi
}

# ---------------------------------------------------------------- 运行时注册

# 注册一个服务到运行中的控制器，无需改 yml 重启
register_service() {
    local name="$1" port="$2" classes_csv="$3" sources_csv="$4" host="${5:-127.0.0.1}"

    [[ -n "${name}" && -n "${port}" ]] || fail "用法: $0 register <服务名> <Agent端口> [classes目录,逗号分隔] [源码目录,逗号分隔] [主机]"

    port_listening "${CONTROLLER_PORT}" || fail "控制器未运行，请先执行 $0 start"

    local classes_json sources_json
    classes_json="$(to_json_array "${classes_csv}")"
    sources_json="$(to_json_array "${sources_csv}")"

    local body response
    body="{\"name\":\"${name}\",\"host\":\"${host}\",\"agentPort\":${port},\"classesDirectories\":${classes_json},\"sourceDirectories\":${sources_json},\"replace\":true}"
    response="$(curl -s -X POST "http://127.0.0.1:${CONTROLLER_PORT}/api/coverage/services" \
        -H 'Content-Type: application/json' -d "${body}" -w '\n%{http_code}' || true)"

    local status="${response##*$'\n'}" payload="${response%$'\n'*}"
    if [[ "${status}" != "200" ]]; then
        fail "注册服务 [${name}] 失败（HTTP ${status}）：${payload}"
    fi

    info "服务 [${name}] 已注册（Agent ${host}:${port}），已落盘到 ${COVERAGE_HOME}/services/${name}.properties"
    local warnings
    warnings="$(printf '%s' "${payload}" | sed -n 's/.*"warnings":\[\(.*\)\].*/\1/p')"
    if [[ -n "${warnings}" ]]; then
        warn "注册告警：${warnings}"
    fi
}

# 列出控制器中生效的全部服务
list_services() {
    port_listening "${CONTROLLER_PORT}" || fail "控制器未运行，请先执行 $0 start"
    local payload
    payload="$(curl -s "http://127.0.0.1:${CONTROLLER_PORT}/api/coverage/services" || true)"
    if [[ -z "${payload}" || "${payload}" == "[]" ]]; then
        warn "控制器中没有已注册的服务"
        return 0
    fi

    local item name port runtime
    # 用重定向而不是管道，避免 while 在子 shell 中执行
    while IFS= read -r item; do
        # sed/grep 未匹配时返回非 0，用 || true 兜住 set -e
        name="$(printf '%s' "${item}" | sed -n 's/.*"name":"\([^"]*\)".*/\1/p' || true)"
        [[ -n "${name}" ]] || continue
        port="$(printf '%s' "${item}" | sed -n 's/.*"agentPort":\([0-9]*\).*/\1/p' || true)"
        runtime="$(printf '%s' "${item}" | grep -o '"runtimeRegistered":[a-z]*' | cut -d: -f2 || true)"
        if [[ "${runtime}" == "true" ]]; then
            printf '  %-24s Agent:%-6s 来源: 运行时注册\n' "${name}" "${port}"
        else
            printf '  %-24s Agent:%-6s 来源: 配置文件\n' "${name}" "${port}"
        fi
    done < <(printf '%s' "${payload}" | tr '{' '\n')
}

# 把逗号分隔的目录列表转成 JSON 数组
to_json_array() {
    local csv="${1:-}"
    if [[ -z "${csv}" ]]; then
        echo "[]"
        return 0
    fi
    local result="[" first=1 item
    IFS=',' read -ra items <<< "${csv}"
    for item in "${items[@]}"; do
        [[ -n "${item// /}" ]] || continue
        (( first == 0 )) && result+=","
        result+="\"${item}\""
        first=0
    done
    echo "${result}]"
}

while (( $# > 0 )); do
    case "$1" in
        --keep) KEEP_RUNNING=1 ;;
        all|start|test|report|stop|status|list) COMMAND="$1" ;;
        register)
            COMMAND="register"
            shift
            REGISTER_NAME="${1:-}"
            REGISTER_PORT="${2:-}"
            REGISTER_CLASSES="${3:-}"
            REGISTER_SOURCES="${4:-}"
            REGISTER_HOST="${5:-127.0.0.1}"
            break
            ;;
        unregister)
            COMMAND="unregister"
            shift
            UNREGISTER_NAME="${1:-}"
            break
            ;;
        -h|--help)
            # 只输出文件头的连续注释块（跳过 shebang 与空注释行）
            sed -n '2,/^[^#]/p' "${BASH_SOURCE[0]}" | sed '$d' | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *) fail "未知参数：$1（用 --help 查看用法）" ;;
    esac
    shift
done

case "${COMMAND}" in
    stop)
        stop_all
        info "服务已停止"
        ;;
    status)
        show_status
        ;;
    list)
        list_services
        ;;
    register)
        register_service "${REGISTER_NAME}" "${REGISTER_PORT}" \
            "${REGISTER_CLASSES}" "${REGISTER_SOURCES}" "${REGISTER_HOST}"
        ;;
    unregister)
        [[ -n "${UNREGISTER_NAME}" ]] || fail "用法: $0 unregister <服务名>"
        port_listening "${CONTROLLER_PORT}" || fail "控制器未运行，请先执行 $0 start"
        response="$(curl -s -X DELETE -w '\n%{http_code}' \
            "http://127.0.0.1:${CONTROLLER_PORT}/api/coverage/services/${UNREGISTER_NAME}" || true)"
        status_code="${response##*$'\n'}"
        payload="${response%$'\n'*}"
        case "${status_code}" in
            204) info "服务 [${UNREGISTER_NAME}] 已注销" ;;
            404) fail "服务 [${UNREGISTER_NAME}] 不存在于运行时注册表" ;;
            *)   fail "注销失败（HTTP ${status_code}）：$(extract_json_field "${payload}" detail)" ;;
        esac
        ;;
    report)
        print_report
        ;;
    start)
        preflight
        build_all
        reclaim_ports
        start_services
        print_report
        if (( KEEP_RUNNING == 1 )); then
            info "服务保持运行（--keep）"
        else
            info "服务已启动；如需关闭请执行：$0 stop"
        fi
        ;;
    test)
        run_tests
        print_report
        ;;
    all)
        trap cleanup_on_exit EXIT
        preflight
        build_all
        reclaim_ports
        start_services
        run_tests
        print_report
        ;;
esac
