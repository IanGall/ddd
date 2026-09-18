# 项目隔离套件

> 位置：`ddd/scripts/isolation/`（随 `ddd/` 一起被复制）
> 用途：**从本项目复制出新项目后，为新项目生成与原项目完全隔离的配置**

---

## 1. 为什么需要

本项目常被**整体复制**以派生新项目。复制出的项目与原项目同源、且通常跑在**同一台开发机、同一批中间件实例**上。若不隔离，会出现：

| 症状 | 机制 |
|---|---|
| **生成重复 ID** | 两项目服务名相同 ⇒ ID 生成器 namespace 相同 ⇒ 各自租到**同一个 WorkerId** |
| **E2E 互相污染** | 测试脚本写同一个测试库 |
| **状态串扰** | Redis 同 database index 的 key 冲突 |
| **端口冲突** | 网关 / coverage / agent 端口被两项目争抢 |
| **误删对方容器** | `docs/dev-ops/app/start.sh` 含 `docker rm -f ${CONTAINER_NAME}` |
| **服务互相顶替** | Nacos 同 namespace 下同名 service 注册覆盖 |

---

## 2. 设计原则

| 原则 | 含义 |
|---|---|
| **项目无关** | 套件内**不硬编码任何项目名/前缀**；所有资源名由 `--prefix` 派生 |
| **前缀驱动** | 库名、Nacos namespace、k8s namespace、ID 生成器 namespace、镜像/容器名均来自前缀 |
| **幂等** | 可重复执行；SQL 用 `IF NOT EXISTS`，env 片段可反复合并 |
| **只生成不改动** | `init-isolation.sh` 只**生成文件**，不自动执行 DDL、不改 `.env.local`（避免误改现状） |
| **可自检** | `check-isolation.sh` 只读检查，能自动检出 P0 级阻断项 |

---

## 3. 套件内容

| 文件 | 用途 |
|---|---|
| `README.md` | 本文件 |
| `isolation-lib.sh` | **共享函数库**：前缀探测、k8s 中间件定位、SQL 执行、Nacos/k8s 命名空间创建 |
| `init-isolation.sh` | **主脚本**：传入前缀，生成隔离 SQL / env 片段 / 操作命令；加 `--apply` 可直接在 k8s 执行 |
| `check-isolation.sh` | **自检脚本**：k8s 感知，分级输出 P0/P1/P2 问题 |

---

## 4. 新项目接入（4 步）

### 步骤 1：确定新项目前缀

前缀应短、小写、与既有项目不重复（如 `rcs`、`oms`、`wms`）。

### 步骤 2：生成隔离配置

```bash
# 在**新项目**根目录执行
bash ddd/scripts/isolation/init-isolation.sh --prefix <新前缀>

# 例：bash ddd/scripts/isolation/init-isolation.sh --prefix rcs
# 可选：--port-base 8100   端口基准（默认 8100）
#       --out-dir  ./isolation-out   输出目录（默认 ./isolation-out）
#       --redis-db 2           Redis database（默认 2）
```

生成物（在 `--out-dir` 下）：

| 文件 | 内容 |
|---|---|
| `init-databases-<prefix>.sql` | 建 9 个隔离库 |
| `env.isolation.<prefix>` | 需并入 `ddd/.env.local` 的环境变量片段 |
| `NEXT-STEPS-<prefix>.md` | Nacos namespace / k8s namespace 创建命令与检查清单 |

### 步骤 3：执行生成物

**方式 A —— 一键在 k8s 中执行（推荐）**：

```bash
bash ddd/scripts/isolation/init-isolation.sh --prefix <新前缀> --apply
```

`--apply` 依次完成：**建库**（`kubectl exec` 进 mysql Pod）→ **建 Nacos 命名空间**（宿主机直连失败则进 nacos Pod 内调 API）→ **建 k8s 命名空间**（幂等）。

**方式 B —— 手工执行**：

```bash
# ① 建库（在 k8s 内执行，本机无需 mysql 客户端）
kubectl exec -i -n infra mysql-0 -- mysql -u root -p'<密码>' < isolation-out/init-databases-<前缀>.sql

# ② 建 Nacos namespace（命令见 NEXT-STEPS-<前缀>.md）

# ③ 建 k8s namespace
kubectl create namespace ian-<前缀>
```

> **中间件位置约定**：本机 OrbStack 集群中，MySQL/Nacos/Redis/Kafka 均在 **`infra`** 命名空间（`mysql-0` / `nacos-*` / `redis-0` / `kafka-0`）。可用环境变量 `ISO_INFRA_NS` 覆盖。

### 步骤 4：并入环境变量并自检

把 `env.isolation.<前缀>` 的内容**合并**进 `ddd/.env.local`（不要覆盖，它含既有凭证），然后：

```bash
bash ddd/scripts/isolation/check-isolation.sh
```

全部通过（无 P0/P1 失败项）后，才可与原项目**同时运行**。

### 步骤 4：自检

```bash
bash ddd/scripts/isolation/check-isolation.sh
```

全部通过（无 P0/P1 失败项）后，才可与原项目**同时运行**。

---

## 5. 与原项目同时运行的验证

| # | 验证项 | 判定 |
|---|---|---|
| V1 | 两项目服务同时启动 | 无端口冲突、无 Nacos 注册覆盖 |
| V2 | 各自跑一遍 E2E | 在原项目测试库中**查不到**新项目的写入 |
| V3 | Nacos 控制台 | 两项目实例分属不同 namespace |
| V4 | Redis `KEYS` 抽样 | 两侧 key **无重叠** |
| V5 | 两侧各生成 ID 抽样对比 | **无重复** |
| V6 | 触发一次 `start.sh` | **不会**删掉对方容器 |

> V2 与 V5 是最容易被忽略、后果最严重的两项。V5 失败意味着两项目会产生**主键碰撞**。

---

## 6. 已复制项目的落地示例

`ian-rcs`（由 `ian-ddd` 复制）的落地记录见 `docs/isolation/`（该目录在原项目之外，用于记录**具体落地值**；套件本身在 `ddd/scripts/isolation/`，随项目复制）。

---

## 7. 与 `coverage-e2e.sh` 的关系

`ddd/ddd-base/ian-ddd-coverage/coverage-e2e.sh` 已改造为**项目无关**（工作区探测、模块路径、子模块类路径均不绑定项目前缀），因此复制后的新项目**无需改该脚本**即可运行。

但它读取的**端口与库名**仍来自 `.env.local` —— 这正是本套件生成的内容。
