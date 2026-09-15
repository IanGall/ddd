# 认证服务 k8s 部署

本目录是 `ian-ddd-auth` 的 k8s 清单（原生 YAML，`kubectl apply -f` 直接使用，不需要 Helm/Kustomize）。
命名空间刻意**不写进清单**，由 `kubectl apply -n <namespace>` 决定，避免把集群拓扑固化进公开仓。

| 文件 | 内容 |
| --- | --- |
| `deployment.yaml` | 2 副本、Dubbo 20880、TCP 探针、emptyDir、优雅停机 |
| `service.yaml` | ClusterIP，仅 20880（运维入口，非 Dubbo 数据面） |
| `configmap.yaml` | 非敏感配置（键名与 `application-prod.yml` / `sharding-jdbc-prod.yaml` 的 `${...}` 一一对应） |
| `secret.yaml.example` | 敏感配置**键名样例**，不含真实值 |
| `hpa.yaml` | CPU 70%，2 → 8 |
| `pdb.yaml` | `minAvailable: 1` |

## 1. 前置：构建镜像

以下命令默认在**仓库根**（`ddd`）执行。

```bash
bash ian-ddd-auth/ian-ddd-auth-boot/build.sh        # system/ian-ddd-auth-boot:1.0-SNAPSHOT
```

镜像内已固定 `SPRING_PROFILES_ACTIVE=prod` 与 `TZ=Asia/Shanghai`，因此 `application-prod.yml` 要求的所有中间件地址与凭证**必须**由
ConfigMap / Secret 注入；缺任何一项都会以配置错误启动失败（刻意不给可用默认值）。

## 2. 部署顺序

```bash
kubectl create namespace ian-ddd          # 命名空间由集群管理员决定，不放进清单
NS=ian-ddd

# ① 凭证（此步骤与仓库无关，见 secret.yaml.example 头部注释里的提取命令）
kubectl create secret generic ian-ddd-auth-secret -n "$NS" --from-env-file=/tmp/ian-ddd-auth.secret.env

# ② 配置 + 工作负载 + 网络入口
kubectl apply -n "$NS" -f ian-ddd-auth/docs/dev-ops/k8s/configmap.yaml
kubectl apply -n "$NS" -f ian-ddd-auth/docs/dev-ops/k8s/deployment.yaml
kubectl apply -n "$NS" -f ian-ddd-auth/docs/dev-ops/k8s/service.yaml
kubectl apply -n "$NS" -f ian-ddd-auth/docs/dev-ops/k8s/hpa.yaml
kubectl apply -n "$NS" -f ian-ddd-auth/docs/dev-ops/k8s/pdb.yaml
```

`secret.yaml.example` 是**示例**：它的扩展名不是 `.yaml`，因此 `kubectl apply -f <目录>` 不会把它纳入（否则会把真实 Secret 覆盖成
`REPLACE_ME`）。需要核对清单时用：

```bash
kubectl apply --dry-run=server -n "$NS" -f ian-ddd-auth/docs/dev-ops/k8s/
```

## 3. 端口：认证服务没有 HTTP 端口

**认证服务运行期只有 Dubbo Provider，不存在 servlet 容器。** `spring-boot-starter-web` 只声明在 `ian-ddd-auth-trigger` 的
`http` profile（默认不激活），`ian-ddd-auth-boot.jar` 内也没有 tomcat / spring-webmvc。因此：

- `application.yml` 的 `server.port: 8091` 是惰性配置，8091 **从不被监听**；
- 探针探的是 **Dubbo 20880**，它在 `ServiceBean` 导出时才绑定，正好是网关需要它 ready 的时刻；
- 不要把 8091 加回 Service 或探针，那会让 Pod 直接进入 CrashLoopBackOff。

**实测印证（2026-09-15 本地集群）**：MySQL 不可达时 Pod 仍然 `Ready`（TCP 20880 探针通过），但登录接口返回
`504 RPC_TIMEOUT`——即 TCP 端口就绪 ≠ 依赖就绪。依赖侧由 `dubbo.consumer.check: false` + 注册中心推送 + 多副本共同兜住；
若要更准的就绪语义，需要给认证服务引入 actuator（此前经决策明确不做）。

## 4. Dubbo 与 Nacos

- Provider 默认向 Nacos 注册 **podIP:20880**，网关作为同集群 consumer 直连 Pod，**不需要** `DUBBO_IP_TO_REGISTER`，**不需要**
  headless Service —— 服务发现由 Nacos 承担，再加一层 DNS 发现只会产生双份真相。
- 本目录的 Service 只作运维入口：`kubectl port-forward -n "$NS" svc/ian-ddd-auth 20880:20880`。
- `DUBBO_QOS_ENABLED` 必须保持 `false`：QoS 端口固定 22222，多副本落在同一节点会端口冲突。
- Nacos 2.x 客户端还需要 gRPC **9848**（= 8848 + 1000）。若后续加 NetworkPolicy，只放行 8848 会出现「注册成功但收不到实例推送」。

## 5. 副本数与全局 ID

`ddd.id-generator.businesses` 声明了 5 个业务、`worker-id-block-size: 64`，因此**每个业务的副本数不得超过 64**；超限时新副本拿不到
Worker ID 租约，启动期抛 `IdGenerationException`。HPA 上限取 8，留足余量。所有副本必须连同一个 Redis 且共享
`DDD_ID_GENERATOR_NAMESPACE`。

## 6. 变更生效方式

原生 YAML 没有配置校验和触发器：修改 ConfigMap / Secret 后 **必须** 手动滚动重启，否则容器仍用旧环境变量。

```bash
kubectl rollout restart deployment/ian-ddd-auth -n "$NS"
kubectl rollout status  deployment/ian-ddd-auth -n "$NS"
```

`imagePullPolicy: IfNotPresent` 下，同名 tag（如 `1.0-SNAPSHOT`）不会自动拉新镜像：生产请改用不可变 tag 或 digest。

## 7. 为什么 JAVA_OPTS 里必须有 `-Duser.home=/tmp`

容器以**没有 passwd 条目的 uid 10001** 运行，JVM 的 `user.home` 是空串，于是 Dubbo 把元数据缓存写到
`${user.home}/.dubbo`（即 `/.dubbo`）、Nacos 客户端把日志写到 `${user.home}/logs/nacos`（即 `/logs/nacos`）。在
`readOnlyRootFilesystem: true` 下这两处都不可写，**Pod 会直接启动失败**（实测报
`Invalid service store file /.dubbo/dubbo-metadata-....cache, cause: Failed to create directory /.dubbo!`，两个服务都中招）。

把 `user.home` 指向已挂 emptyDir 的 `/tmp` 可同时解决两者。注意只配 `-Ddubbo.registry.file` **不够**——它只覆盖注册中心缓存，
覆盖不到元数据缓存与 Nacos 客户端日志。

## 8. 切换部署环境（prod / dev）

**profile 只有一个开关：ConfigMap 的 `SPRING_PROFILES_ACTIVE`**（容器 env 优先级高于镜像里 Dockerfile 固定的 prod）。
而"环境的差异"全部落在 ConfigMap / Secret 的取值上——两个环境用的**键名完全相同**（`DUBBO_REGISTRY_*` / `REDIS_*` /
`MYSQL_*` / `KAFKA_*`），所以切换环境不需要改 Deployment。

| | prod（`configmap.yaml` 默认） | dev |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` | `dev` |
| 应用配置 | `application-prod.yml` | `application-dev.yml` |
| 分片配置 | `sharding-jdbc-prod.yaml`（地址全部来自环境变量） | `sharding-jdbc-dev.yaml`（**库地址写死 `127.0.0.1`**） |
| 密钥/令牌 | 必须是独立的强值，否则 `SecretConfigurationValidator` 拒绝启动 | 允许本地开发密钥与示例令牌（本地 profile 直接放行） |

**集群内跑 dev 的关键一步——数据源必须覆盖**，因为 dev 的分片配置把库地址写死成 `127.0.0.1`，而容器里没有 MySQL。
同一个镜像里两份分片配置都在，两种覆盖方式可选：

| 方式 | 注入 | 说明 |
| --- | --- | --- |
| B. 走分片配置（**推荐**） | `SPRING_DATASOURCE_URL=jdbc:shardingsphere:classpath:sharding/sharding-jdbc-prod.yaml?placeholder-type=environment` | 保留分片能力；库地址/库名/账号口令全部来自 `MYSQL_*` 环境变量。已在本地集群实测可连（预热通过、登录返回业务错误码） |
| A. 普通 JDBC | `SPRING_DATASOURCE_DRIVER_CLASS_NAME=com.mysql.cj.jdbc.Driver` + `SPRING_DATASOURCE_URL=jdbc:mysql://mysql.infra.svc.cluster.local:3306/ddd_rbac?...` + `SPRING_DATASOURCE_USERNAME/PASSWORD` | 只在不需要分片时用（例如只想验证接口），**代价：分片表 `user_order` 不可用** |

最小 dev 流程（本地集群示例，密钥取自仓库根 `.env.local`）：

```bash
NS=ian-ddd
cat > /tmp/dev.cm.env <<'EOF'
SPRING_PROFILES_ACTIVE=dev
DUBBO_QOS_ENABLED=false
DUBBO_PROTOCOL_PORT=20880
DUBBO_REGISTRY_ADDRESS=nacos://nacos.infra.svc.cluster.local:8848
DUBBO_REGISTRY_USERNAME=nacos
REDIS_HOST=redis.infra.svc.cluster.local
REDIS_PORT=6379
REDIS_DATABASE=0
DDD_ID_GENERATOR_NAMESPACE=ddd-global-id
MYSQL_HOST=mysql.infra.svc.cluster.local
MYSQL_PORT=3306
MYSQL_DATABASE_00=ian_dev_tech_db_00
MYSQL_DATABASE_01=ian_dev_tech_db_01
MYSQL_DATABASE_RBAC=ddd_rbac
MYSQL_USERNAME=<你的库账号>
KAFKA_ENABLED=false
CHANNEL_ENCRYPTION_KEY_ID=dev-key-v1
# 数据源覆盖（方式 B，推荐；方式 A 的取值见上面的表）
SPRING_DATASOURCE_URL=jdbc:shardingsphere:classpath:sharding/sharding-jdbc-prod.yaml?placeholder-type=environment
EOF

# 敏感项用同一个 env 文件喂给 secret（键名与 prod 完全一致）
cat > /tmp/dev.secret.env <<'EOF'
MYSQL_PASSWORD=<...>
REDIS_PASSWORD=<...>
DUBBO_REGISTRY_PASSWORD=<...>
CHANNEL_ENCRYPTION_MASTER_KEY=<...>
PLATFORM_ADMIN_TOKEN=<...>
EOF

kubectl create configmap ian-ddd-auth-config -n "$NS" --from-env-file=/tmp/dev.cm.env --dry-run=client -o yaml | kubectl apply -f -
kubectl create secret generic ian-ddd-auth-secret -n "$NS" --from-env-file=/tmp/dev.secret.env
kubectl apply -n "$NS" -f ian-ddd-auth/docs/dev-ops/k8s/deployment.yaml
rm -f /tmp/dev.cm.env /tmp/dev.secret.env
```

注意：
- **不要用 `kubectl apply -f .../configmap.yaml`** 覆盖 dev 的 ConfigMap——那份是 prod 默认值；改了 ConfigMap 后必须
  `kubectl rollout restart`（`envFrom` 只在 Pod 创建时读取一次）。
- 环境差异只放进 ConfigMap / Secret，**不要用 `kubectl set env` 临时改 Deployment**：`env` 是带 merge key 的列表，
  下一次 `kubectl apply -f deployment.yaml` 会把这些临时项清掉，表现为"改好的配置莫名其妙又坏了"。
- `KAFKA_ENABLED=false` 只是本地降噪：infra 的 Kafka 通告地址是 `kafka:9092`，跨命名空间解析不了（与本清单无关）。
- 分片配置的占位符**必须带双冒号**（`$${VAR::默认值}`）：ShardingSphere 的正则只匹配带 `::` 的形式，少了就不替换、
  原样留在 YAML 里，表现为「环境变量怎么改都不生效」。`DeployConfigConsistencyTest` 已把这条规则与
  「配置引用的环境变量必须在 ConfigMap/Secret 里齐备」一起钉进构建。

## 9. 从宿主机访问（不需要 port-forward）

集群的 `infra` 中间件与网关入口都已经暴露，**宿主侧不需要 `kubectl port-forward`**：

| 目标 | 宿主侧地址 | 集群内地址 |
| --- | --- | --- |
| MySQL | `127.0.0.1:3306` | `mysql.infra.svc.cluster.local:3306` |
| Redis | `127.0.0.1:6379` | `redis.infra.svc.cluster.local:6379` |
| Nacos（HTTP / gRPC） | `127.0.0.1:8848` / `127.0.0.1:9848` | `nacos.infra.svc.cluster.local:8848` / `:9848` |
| Kafka | `127.0.0.1:9092` | `kafka.infra.svc.cluster.local:9092` |
| 网关（经 Ingress） | `127.0.0.1:80` + `Host: <Ingress 的 host>` | `192.168.194.216:80` |

原理：`infra` 里那个 `apisix-gateway` 是 LoadBalancer，OrbStack 会把它的端口映射到宿主机 `127.0.0.1`（`lsof -nP -iTCP -sTCP:LISTEN`
能看到是 OrbStack 进程在监听）。所以宿主机直接按上表访问即可，Ingress 也只是换个 Host 头：

```bash
curl -H 'Host: gateway.example.com' http://127.0.0.1/actuator/health
```

若要用真实域名而不是 `Host` 头，给 `/etc/hosts` 加 `127.0.0.1 gateway.example.com`，或用
`curl --resolve gateway.example.com:80:127.0.0.1`。

> 注意宿主的 `192.168.139.2`（LoadBalancer 的 EXTERNAL-IP）并不可达，别用它；要么 `127.0.0.1:<port>`，要么在集群内访问。

## 10. 本机验证的限制

OrbStack 自带集群没有 metrics-server（`kubectl top nodes` 会报 `Metrics API not available`），HPA 对象能被创建但一直显示
`<unknown>`、不会伸缩。`kubectl apply --dry-run=server` 只校验 schema 与 admission 规则，不能替代真实伸缩验证。
