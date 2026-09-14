# DDD 基础组件

`ddd-base` 为 DDD 样例工程和网关提供共享类型、依赖版本及构建约束。仓库自身不承载业务逻辑，也不负责启动应用。

## 模块说明

| 模块                            | 产物     | 职责                                                 |
|---------------------------------|----------|------------------------------------------------------|
| `ddd-common`                    | JAR      | 提供通用响应、分页模型、持久化基类、常量和应用异常   |
| `ddd-context`                   | 聚合 POM | 聚合协议无关上下文及其边界适配器，不作为业务依赖引入 |
| `ddd-context/ddd-context-core`  | JAR      | 提供协议无关的请求上下文、访问器、作用域和快照能力   |
| `ddd-context/ddd-context-dubbo` | JAR      | 在 Dubbo 3 Attachment 与请求上下文之间进行转换       |
| `ddd-context/ddd-context-web`   | JAR      | 在 Spring Web 请求边界建立、回写并清理请求上下文     |
| `ddd-redis-starter`             | JAR      | 提供技术无关的 Redis API、Redisson 实现与自动装配    |
| `ddd-id-generator-starter`      | JAR      | 基于 Redis 租约分配机器号并生成全局唯一的 64 位 ID   |
| `ddd-dependencies`              | BOM      | 统一第三方依赖版本，供基础 BOM 导入                  |
| `ddd-base-bom`                  | BOM      | 汇总第三方依赖版本及基础组件版本                     |

## 构建要求

- JDK 21，构建时由 Maven Enforcer 强制校验。
- Maven 3.9 或更高版本。
- 测试默认执行；仅在明确需要时通过 `-DskipTests` 跳过。
- 主源码和测试源码的方法名必须使用英文 lowerCamelCase，构建时由 Maven Checkstyle 强制校验。

完整构建并安装到本地 Maven 仓库：

```bash
mvn clean install
```

只验证编译和单元测试：

```bash
mvn verify
```

## 下游使用

业务工程通过 `dependencyManagement` 导入基础 BOM：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>cn.iantech</groupId>
            <artifactId>ddd-base-bom</artifactId>
            <version>1.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

需要统一构建约束的工程应继承 `ddd-base`，并按需导入 `ddd-base-bom`。依赖版本只在基础 BOM 中维护，下游模块不重复声明已受管版本。

需要 Redis 能力的微服务直接依赖公共 Starter，无需自行定义 Redis 接口或装配 Redisson：

```xml
<dependency>
    <groupId>cn.iantech</groupId>
    <artifactId>ddd-redis-starter</artifactId>
</dependency>
```

Starter 在容器中存在 `RedissonClient` 时自动提供 `cn.iantech.redis.IRedisService`。业务可以声明自己的
`IRedisService` Bean 覆盖默认实现；公共接口不会暴露 Redisson 的锁、队列、脚本等客户端类型。

需要将同一全局用户的多个 Key 固定到一个 Redis Cluster slot 时，使用 `RedisKeyBuilder.scope(userId)`。生成的 Hash Tag 为
`{userId}`；业务不得自行拼接花括号，也不得把非全局唯一的局部 ID 用作用户作用域。

### 全局唯一 ID

需要全局唯一 ID 的微服务依赖公共 Starter。版本已经由 `ddd-base-bom` 管理，下游不得重复声明：

```xml
<dependency>
    <groupId>cn.iantech</groupId>
    <artifactId>ddd-id-generator-starter</artifactId>
</dependency>
```

Starter 复用应用现有的 `RedissonClient`，通过 Redis 租约在同一命名空间内自动分配 WorkerId，无需为 Pod、容器或物理机
手工配置机器号。ID 布局为「时间戳 41 位 + WorkerId 10 位 + 序列 12 位」，算法实现内置于本 Starter，不再引入第三方
ID 生成库。

#### 单生成器模式（默认）

不声明 `businesses` 时保持原行为：整个进程共用一个生成器，WorkerId 从整个池中租用。

```yaml
ddd:
  id-generator:
    enabled: true
    namespace: ddd-global-id
    worker-id-bit-length: 10
    sequence-bit-length: 12
    lease-duration: 30s
    renew-interval: 10s
```

业务通过构造器注入公共契约生成 ID：

```java
import cn.iantech.id.GlobalIdGenerator;

public class OrderIdService {

    private final GlobalIdGenerator globalIdGenerator;

    public OrderIdService(GlobalIdGenerator globalIdGenerator) {
        this.globalIdGenerator = globalIdGenerator;
    }

    public long nextOrderId() {
        return globalIdGenerator.nextId();
    }
}
```

#### 多业务模式

按业务声明 WorkerId 区间后，每个业务得到**独立的生成器实例**（独立锁、独立序列、独立续租），互不影响：
某个业务的租约续期失败只会让该业务停发。

```yaml
ddd:
  id-generator:
    enabled: true
    namespace: ddd-global-id
    worker-id-bit-length: 10
    sequence-bit-length: 12
    lease-duration: 30s
    renew-interval: 10s
    worker-id-block-size: 64
    businesses:
      order: 0
      user: 1
```

块序号 `i` 占用 WorkerId 区间 `[i * worker-id-block-size, (i + 1) * worker-id-block-size)`：上例中 `order` 为
`[0, 64)`、`user` 为 `[64, 128)`。业务通过 Provider 取生成器：

```java
import cn.iantech.id.GlobalIdGenerator;
import cn.iantech.id.GlobalIdGeneratorProvider;

public class OrderRepository {

    private final GlobalIdGenerator orderIdGenerator;

    public OrderRepository(GlobalIdGeneratorProvider provider) {
        // 构造期解析一次，之后与单生成器模式的用法完全一致
        this.orderIdGenerator = provider.forBusiness("order");
    }

    public long nextOrderId() {
        return orderIdGenerator.nextId();
    }
}
```

`forBusiness` 对同一业务始终返回同一实例；业务名未声明时启动阶段（构造业务组件时）即抛出 `IdGenerationException`。

#### 是否需要全局 ID

不是所有表都需要本 Starter。选型判据如下，**同一张表只能二选一**：不能既声明 `AUTO_INCREMENT` 又由应用赋值（MySQL 允许显式
写自增列，不报错，但会掩盖"漏赋值"的代码缺陷，并让自增计数器被顶到 ID 生成器量级）。

| 判据 | 结论 |
|---|---|
| 没有数据库行、纯应用生成标识（如会话 ID） | 必须用生成器 —— 自增无从谈起 |
| 需要跨写库/分片唯一，或同一 ID 空间存在多个写入源 | 必须用生成器 —— 自增只在单表内唯一 |
| `insert` 之前就必须拿到 ID | 必须用生成器 |
| ID 是对外暴露的稳定业务标识 | 优先用生成器 —— 不暴露自增序号，便于将来拆库/合并 |
| 内部字典表、从属数据、关联表（ID 不出服务、不跨库） | 用数据库自增 |

两点补充：

- **按表选择，不是按配置选择**。生成器是进程级基础设施：服务里只要有表需要，就保持 `enabled=true` 并声明对应业务块；一张表都不需要时才
  设 `enabled=false`（此时容器中不提供 `GlobalIdGenerator` 与 `GlobalIdGeneratorProvider`，也不占 Redis WorkerId）。
- 分片表如果要保留自增主键，必须另设业务唯一键承担全局唯一（自增只在单个分片内唯一）。

配置约束：

- `worker-id-bit-length` 默认使用 10 位，同一 `namespace` 下的 WorkerId 池共 1024 个，多业务模式按块切分该池。
- `namespace` 不能包含空白、花括号或冒号。默认 Redis Key 使用 `{ddd-global-id}:worker:cursor`、
  `{ddd-global-id}:worker:layout` 和 `{ddd-global-id}:worker:lease:<workerId>` 格式；多业务模式额外使用
  `{ddd-global-id}:worker:cursor:<块序号>`，而 `layout` 与 `lease` 键由所有业务共享。花括号内的 namespace 作为 Redis
  Cluster Hash Tag，确保租约脚本涉及的 Key 位于同一 Slot。
- `worker-id-bit-length` 与 `sequence-bit-length` 之和必须等于 22；默认 10/12 用满可用位数，在实例容量与单实例吞吐之间取得平衡。
- 业务名只允许小写字母、数字与连字符且以字母开头；块序号不得为负、不得重复，且
  `(最大块序号 + 1) * worker-id-block-size` 不得超过 WorkerId 池容量。
- `businesses` **只能写在 YAML 中**：映射键没有单一属性名，用环境变量覆盖会得到 `AUTH_SESSION` 这类大写键从而绑定不上，
  因此业务名的严格校验会把这种写法变成启动期错误。
- `renew-interval` 必须小于 `lease-duration`。实例会在租约有效期内续约，正常关闭时主动释放 WorkerId。
- 所有需要保证 ID 全局唯一的实例必须连接同一个 Redis，并使用相同的 `namespace` 和位长配置。不同系统应使用不同
  `namespace`，避免互相占用 WorkerId。
- 生产环境可以通过 `DDD_ID_GENERATOR_NAMESPACE`、`DDD_ID_GENERATOR_LEASE_DURATION` 和
  `DDD_ID_GENERATOR_RENEW_INTERVAL` 等环境变量覆盖 Spring Boot 配置。
- Redis 不可用、WorkerId 已耗尽、租约丢失或续约失败时，生成器会严格停发并抛出异常，不会退化为本地默认机器号，防止生成重复
  ID。调用方不得吞掉异常后自行生成替代 ID。
- 设置 `ddd.id-generator.enabled=false` 会关闭自动装配；关闭后容器中不提供 `GlobalIdGenerator` 与
  `GlobalIdGeneratorProvider`，需要 ID 的测试应自行提供确定性替身。

业务区间的边界与发布注意事项：

- ID 的真实唯一性由 Redis 租约（`SET NX`）保证，**业务区间只是软预留**：任何仍按整个池扫描的参与者（未升级的实例、
  共用同一 `namespace` 的其它服务）都可能占用区间内的 WorkerId。区间带来的是容量可预期与扫描局部性。
- 从单生成器模式切到多业务模式不会产生重复 ID，但可能出现「区间被占满导致启动失败」。因此
  `worker-id-block-size` 必须显著大于单个业务的副本数，且不要为已有服务更换 `namespace`——新旧 `namespace` 下相同编号的
  WorkerId 可被两个实例同时持有，位宽与基础时间相同时必然重复。
- 调整 `worker-id-bit-length` / `sequence-bit-length` 会改变 ID 布局，`layout` 键会让旧实例失败关闭而不是重复发号；
  这类变更需要先完全停机再发布。
