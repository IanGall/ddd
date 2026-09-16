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
| `ddd-id-generator-starter`      | JAR      | 基于 Redis 租约分配实例级 WorkerId 并生成 64 位 ID   |
| `ddd-mysql-starter`             | JAR      | 按持久化对象上的注解在 insert 时自动填充主键         |
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

需要将同一用户的多个 Key 固定到一个 Redis Cluster slot 时，使用 `RedisKeyBuilder.scope(userId)`。生成的 Hash Tag 为
`{userId}`；业务不得自行拼接花括号。传给它的 ID 必须在该 Key 命名空间内唯一——相同的作用域值会让两批 Key 落到同一个
Hash Tag 上从而互相覆盖，所以**跨表复用的标识字段（如 auth 的 `userId`）必须由同一个生成器业务产出**（见下节划界判据）。

### 全局唯一 ID

需要全局唯一 ID 的微服务依赖公共 Starter。版本已经由 `ddd-base-bom` 管理，下游不得重复声明：

```xml
<dependency>
    <groupId>cn.iantech</groupId>
    <artifactId>ddd-id-generator-starter</artifactId>
</dependency>
```

Starter 复用应用现有的 `RedissonClient`，通过 Redis 租约在**服务级命名空间内**自动分配 WorkerId，无需为 Pod、容器或
物理机手工配置机器号。ID 布局为
`((时间戳 - 基础时间) << (workerIdBitLength + sequenceBitLength)) + (workerId << sequenceBitLength) + 序列`，
算法实现内置于本 Starter，不再引入第三方 ID 生成库。

#### WorkerId 是实例级资源

**一个应用实例只租用一个 WorkerId，实例内所有业务共用它。**

| 约束 | 取值 |
|---|---|
| 业务（生成器）数量 | 无上限，只取决于声明了多少业务 |
| 实例（副本）数量上限 | `2^workerIdBitLength`（默认 1024） |
| 单个业务每毫秒产出 | `2^sequenceBitLength`（默认 4096） |

因为共用同一个 WorkerId 与同一套序列起点，**不同业务在同一毫秒内会产出完全相同的 ID 序列**。所以本 Starter 保证的是
**「业务域内唯一」**：同一张表内不重复，跨业务表允许数值相同。需要跨表比较 ID 的场景必须靠业务类型区分，不能靠数值比较。

#### 划界判据：ID 是否会流入同一个字段

这是划分业务的唯一判据。凡是可能出现在同一个字段、同一列或同一个 Redis 作用域里的 ID，**必须由同一个业务生成器产出**，
否则它们的数值会重复。例如 auth 的 `rbac_account` / `rbac_user` / `customer_user` 的 ID 都会流进 `AuthSession.userId`
（再由 `userType` 分派），因此合并为单个 `identity` 业务。

不满足这条判据时**不要合并**：合并会共享序列与位宽配置，牺牲吞吐与独立调优能力。

#### 单生成器模式（默认）

不声明 `businesses` 时整个进程共用一个生成器，WorkerId 仍从整个池中租用。

```yaml
ddd:
  id-generator:
    enabled: true
    namespace: ian-ddd-auth          # 省略则由 spring.application.name 派生
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

声明 `businesses` 后，每个业务得到**独立的生成器实例**（独立锁、独立序列），并可以各自配置序列位宽：

```yaml
ddd:
  id-generator:
    enabled: true
    worker-id-bit-length: 10
    sequence-bit-length: 12       # 单生成器模式使用；多业务模式下是未声明位宽时的回退值
    lease-duration: 30s
    renew-interval: 10s
    businesses:
      identity: 12
      auth-session: 12
      channel-credential: 8
```

映射的**值是该业务的 `sequenceBitLength`**（不再是块序号）。业务通过 Provider 取生成器：

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

- **按表选择，不是按配置选择**。生成器是进程级基础设施：服务里只要有表需要，就保持 `enabled=true` 并按划界判据声明业务；
  一张表都不需要时才设 `enabled=false`（此时容器中不提供 `GlobalIdGenerator` 与 `GlobalIdGeneratorProvider`，也不占 Redis WorkerId）。
- **声明方式**：在持久化对象上标 `@IdGenerator`，主键由 insert 拦截器自动填充，不必在 Repository 里手写
  `setId(generator.nextId())`。详见下文「MySQL Starter」。
- 分片表如果要保留自增主键，必须另设业务唯一键承担唯一性（自增只在单个分片内唯一）。

配置约束：

- `worker-id-bit-length` 默认 10 位，可并存的实例（副本）数 = `2^workerIdBitLength`（默认 1024）。**它与业务数量无关**，
  因为一个实例只占一个 WorkerId。
- `namespace` 未显式配置时取 `spring.application.name`，两者都取不到则启动失败。不同服务因此天然落在各自的池里。
- `worker-id-bit-length + 每个业务的 sequence-bit-length` 不得超过 22（= `long` 可用的 63 位减去时间戳预留的 41 位）。
  未声明 `businesses` 时按全局 `sequence-bit-length` 判断同样约束。
- 业务名只允许小写字母、数字与连字符且以字母开头；每个业务必须显式声明序列位宽，取值 3..21。
- `namespace` 不能包含空白、花括号或冒号。Redis 键布局：
  | 键 | 值 | 含义 |
  |---|---|---|
  | `{ns}:worker:cursor` | 整数 | 整池轮转游标 |
  | `{ns}:worker:pool` | `workerIdBitLength` | 服务级守卫：WorkerId 的数值空间 |
  | `{ns}:worker:layout:<业务>` | `workerIdBitLength:sequenceBitLength` | 业务级守卫：该业务表的 ID 位布局 |
  | `{ns}:worker:lease:<workerId>` | 持有者令牌 | 独占仲裁 |

  花括号内的 namespace 作为 Redis Cluster Hash Tag，确保租约脚本涉及的 Key 位于同一 Slot。
- `businesses` **只能写在 YAML 中**：映射键没有单一属性名，用环境变量覆盖会得到 `AUTH_SESSION` 这类大写键从而绑定不上，
  因此业务名的严格校验会把这种写法变成启动期错误。
- `renew-interval` 必须小于 `lease-duration`。实例会在租约有效期内续约，正常关闭时主动释放 WorkerId。
- 生产环境可以通过 `DDD_ID_GENERATOR_NAMESPACE`、`DDD_ID_GENERATOR_LEASE_DURATION` 和
  `DDD_ID_GENERATOR_RENEW_INTERVAL` 等环境变量覆盖 Spring Boot 配置。
- Redis 不可用、池已耗尽、租约丢失或续约失败时，生成器会严格停发并抛出异常，不会退化为本地默认机器号，防止生成重复
  ID。调用方不得吞掉异常后自行生成替代 ID。
- 设置 `ddd.id-generator.enabled=false` 会关闭自动装配；关闭后容器中不提供 `GlobalIdGenerator` 与
  `GlobalIdGeneratorProvider`，需要 ID 的测试应自行提供确定性替身。

#### 租约失效的爆炸半径

租约是实例级的，因此**一次续租失败会同时停掉该实例内的全部业务**。对比「每个业务各持一个租约」的旧形态，这是隔离性上的
净损失：那边只停一个业务，这里全停。续租失败只打一条 WARN，并在下一个续租周期自动重试，因此 Redis 抖动或重启后可自愈。

#### 哪些变更不能滚动发布

| 变更 | 为什么必须停机 |
|---|---|
| `namespace` | 新旧命名空间是两个互不知晓的池，滚动期间两边可能各自租到同一个 WorkerId；位宽与基础时间相同时**必然重复** |
| 已声明业务的 `sequence-bit-length` | 会改变该业务表 ID 的位布局，业务级守卫让新实例 fail-closed |
| `worker-id-bit-length` | 会改变 WorkerId 数值空间，服务级守卫让新实例 fail-closed |

**新增业务是可以滚动发布的**：只写入新的业务级 `layout` 键，老实例从不读取它。

三级守卫（服务级 `pool`、业务级 `layout`、`lease` 排他）都只会让实例失败关闭，不会产生重复 ID。`namespace` 的冷切换
做完后，新命名空间天然是空的，因此切换过程本身是安全的；回退只需把 `namespace` 改回旧值并重新部署。

### MySQL Starter：在 insert 时填充主键

需要在插入前就有主键的表用 `ddd-mysql-starter`：在持久化对象上标一个注解，主键就由 insert 拦截器自动填充，
不必在 Repository 里手写 `setId(generator.nextId())`。

```xml
<dependency>
    <groupId>cn.iantech</groupId>
    <artifactId>ddd-mysql-starter</artifactId>
</dependency>
```

```java
import cn.iantech.mysql.annotation.IdGenerator;

@IdGenerator(AuthIdBusiness.IDENTITY)   // 业务名须与 ddd.id-generator.businesses 的键一致
public class RbacUserPO extends BasePO {
    // id 仍声明在 BasePO 里
}
```

**注解打在类上而不是 id 字段上**：本项目的 id 声明在共享基类 `BasePO` 里，字段级注解无法按表区分；
类级注解还让「这张表用不用生成器」可以直接 grep。

三条行为约定：

- **未注解的类一律不碰**，因此自增主键表的既有行为完全不变（含 `useGeneratedKeys` 回填）。
- **仅在 `id` 为 null 时生成**：调用方显式传入的 id 不被覆盖，重试同一个对象也不会重复消耗号段。
- 注解了但 INSERT 未绑定 id 列时**打 WARN 并跳过填充**：这种情况下填进去的值根本不会落库，
  跳过比填充更诚实，也避免在内存里造成「id 已生成」的假象。

`value` 可以留空，表示走单生成器模式的 `GlobalIdGenerator`（适用于未声明 `businesses` 的服务）：

```java
@IdGenerator
public class OrderPO extends BasePO {
}
```

关闭拦截器：设 `ddd.mysql.enabled=false`（例如纯单元测试环境）。生成器缺失时不会静默跳过，
而是在插入时抛出带实体类与业务名的 `IdGenerationException`。

#### 与分片表的关系

拦截器在 MyBatis 层、JDBC 驱动**之上**，`ShardingSphereDriver` 的 SQL 改写与路由发生在它之后，
因此**在 ShardingSphere 数据源下同样有效**（`user_order` 按 `user_id` 路由，与 id 无关）。

但**分片表不应该标这个注解**。项目的 DDL 政策要求分片表保留自增主键，`ian_dev_tech_db_00.sql` 的列注释写得很直接：

> 自增ID；【必须保留自增ID，不要将一些有随机特性的字段值设计为主键，例如 order_id，会导致 innodb 内部 page 分裂和大量随机 I/O，性能下降】

所以 `user_order_0..3` 保持自增、不加注解，全局唯一继续由 `order_id`/`uuid` 承担。
**`@IdGenerator` 的适用范围是「单库非分片表」。**

也不要与 ShardingSphere 自带的 `keyGenerateStrategy` 同时用于一张表：两套主键生成机制并存会让
「id 从哪来」变得不可判定。

> **已知隐患（未修）**：`user_order_mapper.xml` 用 `<select id="insert">` 承载 INSERT，命令类型因此是 `SELECT`，
> `useGeneratedKeys` 用不上、也拿不到受影响行数。它能跑只是因为 MyBatis 走 select 路径执行后丢弃结果；
> 任何按 `SqlCommandType` 判断的增强（包括本拦截器）都会静默跳过它。
