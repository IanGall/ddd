# 网关 k8s 部署

本目录是 `ian-ddd-gateway` 的 k8s 清单（原生 YAML，`kubectl apply -f` 直接使用）。
命名空间刻意**不写进清单**，由 `kubectl apply -n <namespace>` 决定。

| 文件 | 内容 |
| --- | --- |
| `deployment.yaml` | 2 副本、HTTP 8092、`/actuator/health` 探针、emptyDir、优雅停机 |
| `service.yaml` | ClusterIP，`http 8092` |
| `configmap.yaml` | 非敏感配置（注册中心地址与用户名） |
| `secret.yaml.example` | 敏感配置**键名样例**，不含真实值 |
| `hpa.yaml` | CPU 70%，2 → 6 |
| `pdb.yaml` | `minAvailable: 1` |
| `ingress.yaml` | 唯一对外入口，全量路径透传给网关 |

## 1. 前置：构建镜像

网关模块没有 `build.sh`，在仓库根执行：

```bash
docker build -t system/ian-ddd-gateway:1.0-SNAPSHOT \
  -f ian-ddd-gateway/gateway-app/Dockerfile ian-ddd-gateway/gateway-app
```

镜像内已固定 `SPRING_PROFILES_ACTIVE=prod` 与 `TZ=Asia/Shanghai`；`application-prod.yml` 要求的注册中心地址与凭证必须由
ConfigMap / Secret 注入。

## 2. 部署顺序

```bash
kubectl create namespace ian-ddd          # 命名空间由集群管理员决定，不放进清单
NS=ian-ddd

# ① 凭证（见 secret.yaml.example 头部注释里的提取命令）
kubectl create secret generic ian-ddd-gateway-secret -n "$NS" --from-env-file=/tmp/ian-ddd-gateway.secret.env

# ② 配置 + 工作负载 + 入口
kubectl apply -n "$NS" -f ian-ddd-gateway/dev-ops/k8s/configmap.yaml
kubectl apply -n "$NS" -f ian-ddd-gateway/dev-ops/k8s/deployment.yaml
kubectl apply -n "$NS" -f ian-ddd-gateway/dev-ops/k8s/service.yaml
kubectl apply -n "$NS" -f ian-ddd-gateway/dev-ops/k8s/ingress.yaml
kubectl apply -n "$NS" -f ian-ddd-gateway/dev-ops/k8s/hpa.yaml
kubectl apply -n "$NS" -f ian-ddd-gateway/dev-ops/k8s/pdb.yaml
```

`secret.yaml.example` 是**示例**：扩展名不是 `.yaml`，`kubectl apply -f <目录>` 不会把它纳入（否则会把真实 Secret 覆盖成
`REPLACE_ME`）。核对清单用：

```bash
kubectl apply --dry-run=server -n "$NS" -f ian-ddd-gateway/dev-ops/k8s/
```

`ingress.yaml` 的 `spec.ingressClassName` 与 `host` 都是需要按环境替换的占位（本机 OrbStack 为 `apisix`）。

## 3. 对外暴露与路由

- Ingress 只把 `path: /` 全量转给网关，**不复制路径白名单、不加路径级鉴权**：网关自己就是路由与鉴权的唯一真相
  （`GatewayAuthFilter` 的 `ROUTE_PREFIXES` / `ANONYMOUS_ROUTES`，并有契约测试交叉校验）。
- `GET /actuator/health` 经 Ingress 可公开访问，这是有意为之：actuator 只暴露 health，`show-details` 默认 `never`，
  响应仅 `{"status":"UP"}`。若不接受，可在 Ingress 做路径级拒绝，而不要改应用侧白名单。
- 网关不持有数据源，因此 `health` 为 UP 即代表真正可服务，不会出现「端口通了但依赖没通」的假就绪。

## 4. 客户端 IP：一个已知限制

应用取客户端 IP 走 `servletRequest.getRemoteAddr()`（登录风控按 IP 计数，30 次/分钟），且测试明确**不接受**客户端提交的
`X-Forwarded-For`。在 k8s 里这意味着：

- 默认情况下 `getRemoteAddr()` 拿到的是 Ingress Controller 的 Pod IP —— 所有真实用户会被算成同一个 IP，**风控误杀**；
- 但如果盲目开启 `server.forward-headers-strategy=FRAMEWORK`，而 Controller 又只是追加客户端自带的 XFF，攻击者就能伪造
  XFF **绕过**风控。两害相权，当前选择「宁误杀不放过」，即**不在应用配置里开这个开关**。

正式做法（后续独立任务）：先在 Ingress Controller 层统一 strip 再重写 `X-Forwarded-For`（保证 Pod 收到的是不可被客户端污染的
链路），验证 Controller 行为后，**只在 ConfigMap** 注入 `SERVER_FORWARD_HEADERS_STRATEGY=FRAMEWORK`（Spring Boot 松散绑定直接
映射到 `server.forward-headers-strategy`），绝不落到 `application.yml` —— 这样「是否信任代理」是环境属性，同一次构建的镜像在
受信/不受信环境都能用。

## 5. 为什么 JAVA_OPTS 里必须有 `-Duser.home=/tmp`

容器以**没有 passwd 条目的 uid 10001** 运行，JVM 的 `user.home` 是空串，于是 Dubbo 把元数据缓存写到
`${user.home}/.dubbo`（即 `/.dubbo`）、Nacos 客户端把日志写到 `${user.home}/logs/nacos`（即 `/logs/nacos`）。在
`readOnlyRootFilesystem: true` 下这两处都不可写，**Pod 会直接启动失败**（实测报
`Invalid service store file /.dubbo/dubbo-metadata-....cache, cause: Failed to create directory /.dubbo!`，两个服务都中招）。

把 `user.home` 指向已挂 emptyDir 的 `/tmp` 可同时解决两者。注意只配 `-Ddubbo.registry.file` **不够**——它只覆盖注册中心缓存，
覆盖不到元数据缓存与 Nacos 客户端日志。

## 6. 变更生效方式

原生 YAML 没有配置校验和触发器：修改 ConfigMap / Secret 后 **必须** 手动滚动重启。

```bash
kubectl rollout restart deployment/ian-ddd-gateway -n "$NS"
kubectl rollout status  deployment/ian-ddd-gateway -n "$NS"
```

`imagePullPolicy: IfNotPresent` 下，同名 tag（如 `1.0-SNAPSHOT`）不会自动拉新镜像：生产请改用不可变 tag 或 digest。

## 7. 切换部署环境（prod / dev）

**profile 只有一个开关：ConfigMap 的 `SPRING_PROFILES_ACTIVE`**（容器 env 优先级高于镜像里 Dockerfile 固定的 prod）。
网关的 dev 与 prod 用的是**同一组键名**（`DUBBO_REGISTRY_ADDRESS` / `DUBBO_REGISTRY_USERNAME` / `DUBBO_REGISTRY_PASSWORD`），
所以切换环境只需要改这一个值——把 ConfigMap 里的 `SPRING_PROFILES_ACTIVE` 改成 `dev` 再 `kubectl rollout restart` 即可：

```bash
kubectl patch configmap ian-ddd-gateway-config -n "$NS" --type merge -p '{"data":{"SPRING_PROFILES_ACTIVE":"dev"}}'
kubectl rollout restart deployment/ian-ddd-gateway -n "$NS"
```

两个环境的差异只有：应用配置（`application-dev.yml` / `application-prod.yml`）与网关对下游的启动检查宽松度（两者都是
`dubbo.consumer.check: false`）。网关不连接任何数据源，因此**不存在认证服务那种"dev 的分片配置写死 127.0.0.1"的问题**。
认证服务切 dev 的完整配方（含必须额外注入的 `SPRING_DATASOURCE_URL`）见
`ian-ddd-auth/docs/dev-ops/k8s/README.md` 的「切换部署环境」。

## 8. 从宿主机访问（不需要 port-forward）

宿主机上直接可用 `127.0.0.1:80`（apisix 的 LoadBalancer 被 OrbStack 映射到本机）+ Ingress 的 Host 头访问网关，不需要
`kubectl port-forward`：

```bash
curl -H 'Host: gateway.example.com' http://127.0.0.1/actuator/health     # 200 / {"status":"UP"}
```

`infra` 里的 MySQL（3306）、Redis（6379）、Nacos（8848/9848）、Kafka（9092）同样映射在宿主 `127.0.0.1` 上。
注意 LoadBalancer 的 EXTERNAL-IP `192.168.139.2` 在宿主机**不可达**，别用它；要么 `127.0.0.1:<port>`，要么在集群内访问
（`*.infra.svc.cluster.local`）。

## 9. 本机验证的限制

OrbStack 自带集群没有 metrics-server，HPA 对象能被创建但一直显示 `<unknown>`、不会伸缩；
`kubectl apply --dry-run=server` 只校验 schema 与 admission 规则，不能替代真实伸缩验证。
