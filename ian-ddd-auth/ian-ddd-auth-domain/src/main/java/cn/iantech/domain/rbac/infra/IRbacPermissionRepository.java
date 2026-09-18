package cn.iantech.domain.rbac.infra;

import cn.iantech.domain.rbac.model.entity.RbacPermissionEntity;

import java.util.List;
import java.util.Optional;

public interface IRbacPermissionRepository {

    RbacPermissionEntity save(Long accountId, RbacPermissionEntity entity);

    /**
     * 批量写入权限，语义与逐条 {@link #save} 一致，但只产生一次 SQL 往返。
     *
     * <p><b>不请求主键回填</b>：本方法的调用场景（开户初始化内置权限目录）不使用权限 ID，
     * 因此批量语句刻意不声明 generated keys，避免依赖不同驱动与分片中间件的行为差异。</p>
     *
     * <p>空集合为无操作；账号归属以 {@code accountId} 参数为准；唯一键冲突按 {@code CONFLICT}
     * 语义抛出并触发调用方事务整体回滚。</p>
     */
    void saveAll(Long accountId, List<RbacPermissionEntity> entities);

    Optional<RbacPermissionEntity> findById(Long accountId, Long id);

    Optional<RbacPermissionEntity> findByPermCode(Long accountId, String permCode);

    List<RbacPermissionEntity> findAllByAccountId(Long accountId);

    long countPage(Long accountId, String permCode, String permName, Integer permType, Long parentId, Boolean status);

    List<RbacPermissionEntity> queryPage(Long accountId,
                                         String permCode,
                                         String permName,
                                         Integer permType,
                                         Long parentId,
                                         Boolean status,
                                         Integer offset,
                                         Integer pageSize);

    int updateById(Long accountId, RbacPermissionEntity entity);

    int logicDeleteById(Long accountId, Long id);

    List<Long> queryExistingIds(Long accountId, List<Long> permissionIds);

}
