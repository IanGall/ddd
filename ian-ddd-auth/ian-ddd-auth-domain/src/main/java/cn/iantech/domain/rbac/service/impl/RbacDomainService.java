package cn.iantech.domain.rbac.service.impl;

import cn.iantech.domain.model.DomainPage;
import cn.iantech.domain.rbac.infra.*;
import cn.iantech.domain.rbac.model.entity.RbacPermissionEntity;
import cn.iantech.domain.rbac.model.entity.RbacRoleEntity;
import cn.iantech.domain.rbac.model.entity.RbacUserEntity;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static cn.iantech.domain.rbac.service.impl.RbacValidationSupport.SYSTEM_PERMISSION_PREFIX;
import static cn.iantech.domain.rbac.service.impl.RbacValidationSupport.USERNAME_PATTERN;
import static cn.iantech.domain.rbac.service.impl.RbacValidationSupport.checkAccountId;
import static cn.iantech.domain.rbac.service.impl.RbacValidationSupport.checkCustomPermission;
import static cn.iantech.domain.rbac.service.impl.RbacValidationSupport.checkPermissionId;
import static cn.iantech.domain.rbac.service.impl.RbacValidationSupport.checkPermType;
import static cn.iantech.domain.rbac.service.impl.RbacValidationSupport.checkRoleId;
import static cn.iantech.domain.rbac.service.impl.RbacValidationSupport.checkTextLength;
import static cn.iantech.domain.rbac.service.impl.RbacValidationSupport.checkUserId;
import static cn.iantech.domain.rbac.service.impl.RbacValidationSupport.conflict;
import static cn.iantech.domain.rbac.service.impl.RbacValidationSupport.illegalParameter;
import static cn.iantech.domain.rbac.service.impl.RbacValidationSupport.normalizeIds;
import static cn.iantech.domain.rbac.service.impl.RbacValidationSupport.normalizeNullableText;
import static cn.iantech.domain.rbac.service.impl.RbacValidationSupport.normalizeOptionalText;
import static cn.iantech.domain.rbac.service.impl.RbacValidationSupport.normalizePageNum;
import static cn.iantech.domain.rbac.service.impl.RbacValidationSupport.normalizePageSize;
import static cn.iantech.domain.rbac.service.impl.RbacValidationSupport.normalizeParentId;
import static cn.iantech.domain.rbac.service.impl.RbacValidationSupport.normalizePermType;
import static cn.iantech.domain.rbac.service.impl.RbacValidationSupport.notFound;

@RequiredArgsConstructor
@Service
public class RbacDomainService {

    private static final int MAX_PERMISSION_DEPTH = 64;
    private static final int MAX_USERNAME_LENGTH = 64;
    private static final int MAX_DISPLAY_NAME_LENGTH = 128;
    private static final int MAX_EMAIL_LENGTH = 128;
    private static final int MAX_MOBILE_LENGTH = 32;
    private static final int MAX_ROLE_CODE_LENGTH = 64;
    private static final int MAX_ROLE_NAME_LENGTH = 128;
    private static final int MAX_ROLE_DESC_LENGTH = 255;
    private static final int MAX_PERMISSION_CODE_LENGTH = 64;
    private static final int MAX_PERMISSION_NAME_LENGTH = 128;
    private static final int MAX_PERMISSION_PATH_LENGTH = 255;
    private static final int MAX_HTTP_METHOD_LENGTH = 32;

    private final IRbacUserRepository rbacUserRepository;
    private final IRbacRoleRepository rbacRoleRepository;
    private final IRbacPermissionRepository rbacPermissionRepository;
    private final IRbacRelationRepository rbacRelationRepository;
    private final IRbacAccountRepository rbacAccountRepository;

    /**
     * @param passwordHash 调用方在事务外完成编码的口令摘要，本方法不再做校验与编码
     */
    public RbacUserEntity createUser(Long accountId, String username, String passwordHash, String displayName, String email, String mobile, Boolean status) {
        checkAccountId(accountId);
        String finalUsername = StringUtils.trimToNull(username);
        if (StringUtils.isBlank(finalUsername) || !USERNAME_PATTERN.matcher(finalUsername).matches()) {
            throw illegalParameter("子账号用户名格式非法");
        }
        checkTextLength(finalUsername, "子账号用户名", MAX_USERNAME_LENGTH);
        String finalDisplayName = normalizeOptionalText(displayName, "显示名称", MAX_DISPLAY_NAME_LENGTH);
        String finalEmail = normalizeOptionalText(email, "邮箱", MAX_EMAIL_LENGTH);
        String finalMobile = normalizeOptionalText(mobile, "手机号", MAX_MOBILE_LENGTH);

        if (rbacUserRepository.findByUsername(accountId, finalUsername).isPresent()) {
            throw conflict("用户名已存在");
        }
        if (rbacAccountRepository.findByUsername(accountId, finalUsername).isPresent()) {
            throw conflict("子账号用户名不能与主账号相同");
        }

        RbacUserEntity createEntity = RbacUserEntity.builder()
                .accountId(accountId)
                .username(finalUsername)
                .passwordHash(passwordHash)
                .displayName(finalDisplayName)
                .email(finalEmail)
                .mobile(finalMobile)
                .status(Objects.isNull(status) ? Boolean.TRUE : status)
                .deleted(Boolean.FALSE)
                .build();

        RbacUserEntity savedEntity = rbacUserRepository.save(accountId, createEntity);
        if (Objects.isNull(savedEntity.getId())) {
            throw illegalParameter("创建用户失败");
        }
        return savedEntity;
    }

    public RbacUserEntity queryUserById(Long accountId, Long id) {
        checkAccountId(accountId);
        checkUserId(id);
        return rbacUserRepository.findById(accountId, id)
                .orElseThrow(() -> notFound("用户不存在"));
    }

    public DomainPage<RbacUserEntity> queryUserPage(Long accountId, Integer pageNum, Integer pageSize, String username, Boolean status) {
        checkAccountId(accountId);
        Integer finalPageNum = normalizePageNum(pageNum);
        Integer finalPageSize = normalizePageSize(pageSize);
        String finalUsername = StringUtils.trimToNull(username);
        Integer offset = (finalPageNum - 1) * finalPageSize;

        long total = rbacUserRepository.countPage(accountId, finalUsername, status);
        if (total <= 0) {
            return new DomainPage<>(0L, finalPageNum, finalPageSize, List.of());
        }

        List<RbacUserEntity> userList = rbacUserRepository.queryPage(accountId, finalUsername, status, offset, finalPageSize);
        return new DomainPage<>(total, finalPageNum, finalPageSize, userList);
    }

    /**
     * @param passwordHash 调用方在事务外完成编码的口令摘要；传 {@code null} 表示不修改口令
     */
    public RbacUserEntity updateUser(Long accountId, Long id, String passwordHash, String displayName,
                                     String email, String mobile, Boolean status) {
        checkAccountId(accountId);
        checkUserId(id);

        if (Objects.isNull(passwordHash)
                && Objects.isNull(displayName)
                && Objects.isNull(email)
                && Objects.isNull(mobile)
                && Objects.isNull(status)) {
            throw illegalParameter("更新内容不能为空");
        }
        String finalDisplayName = normalizeNullableText(displayName, "显示名称", MAX_DISPLAY_NAME_LENGTH);
        String finalEmail = normalizeNullableText(email, "邮箱", MAX_EMAIL_LENGTH);
        String finalMobile = normalizeNullableText(mobile, "手机号", MAX_MOBILE_LENGTH);

        RbacUserEntity updateEntity = RbacUserEntity.builder()
                .id(id)
                .accountId(accountId)
                .passwordHash(passwordHash)
                .displayName(finalDisplayName)
                .email(finalEmail)
                .mobile(finalMobile)
                .status(status)
                .build();

        int updateCount = rbacUserRepository.updateById(accountId, updateEntity);
        if (updateCount <= 0) {
            throw notFound("用户不存在");
        }

        return queryUserById(accountId, id);
    }

    public boolean deleteUser(Long accountId, Long id) {
        checkAccountId(accountId);
        checkUserId(id);
        int updateCount = rbacUserRepository.logicDeleteById(accountId, id);
        if (updateCount <= 0) {
            throw notFound("用户不存在");
        }
        rbacRelationRepository.deleteAllUserRoles(accountId, id);
        return true;
    }

    public RbacRoleEntity createRole(Long accountId, String roleCode, String roleName, String roleDesc, Boolean status) {
        checkAccountId(accountId);
        String finalRoleCode = StringUtils.trimToNull(roleCode);
        if (StringUtils.isBlank(finalRoleCode)) {
            throw illegalParameter("角色编码不能为空");
        }
        checkTextLength(finalRoleCode, "角色编码", MAX_ROLE_CODE_LENGTH);

        String finalRoleName = StringUtils.trimToNull(roleName);
        if (StringUtils.isBlank(finalRoleName)) {
            throw illegalParameter("角色名称不能为空");
        }
        checkTextLength(finalRoleName, "角色名称", MAX_ROLE_NAME_LENGTH);
        String finalRoleDesc = normalizeOptionalText(roleDesc, "角色描述", MAX_ROLE_DESC_LENGTH);

        if (rbacRoleRepository.findByRoleCode(accountId, finalRoleCode).isPresent()) {
            throw conflict("角色编码已存在");
        }

        RbacRoleEntity createEntity = RbacRoleEntity.builder()
                .accountId(accountId)
                .roleCode(finalRoleCode)
                .roleName(finalRoleName)
                .roleDesc(finalRoleDesc)
                .status(Objects.isNull(status) ? Boolean.TRUE : status)
                .deleted(Boolean.FALSE)
                .build();

        RbacRoleEntity savedEntity = rbacRoleRepository.save(accountId, createEntity);
        if (Objects.isNull(savedEntity.getId())) {
            throw illegalParameter("创建角色失败");
        }

        return savedEntity;
    }

    public RbacRoleEntity queryRoleById(Long accountId, Long id) {
        checkAccountId(accountId);
        checkRoleId(id);
        return rbacRoleRepository.findById(accountId, id)
                .orElseThrow(() -> notFound("角色不存在"));
    }

    public DomainPage<RbacRoleEntity> queryRolePage(Long accountId, Integer pageNum, Integer pageSize, String roleCode, String roleName, Boolean status) {
        checkAccountId(accountId);
        Integer finalPageNum = normalizePageNum(pageNum);
        Integer finalPageSize = normalizePageSize(pageSize);
        Integer offset = (finalPageNum - 1) * finalPageSize;
        String finalRoleCode = StringUtils.trimToNull(roleCode);
        String finalRoleName = StringUtils.trimToNull(roleName);

        long total = rbacRoleRepository.countPage(accountId, finalRoleCode, finalRoleName, status);
        if (total <= 0) {
            return new DomainPage<>(0L, finalPageNum, finalPageSize, List.of());
        }

        List<RbacRoleEntity> list = rbacRoleRepository.queryPage(accountId, finalRoleCode, finalRoleName, status, offset, finalPageSize);
        return new DomainPage<>(total, finalPageNum, finalPageSize, list);
    }

    public RbacRoleEntity updateRole(Long accountId, Long id, String roleCode, String roleName, String roleDesc, Boolean status) {
        checkAccountId(accountId);
        checkRoleId(id);

        if (Objects.isNull(roleCode)
                && Objects.isNull(roleName)
                && Objects.isNull(roleDesc)
                && Objects.isNull(status)) {
            throw illegalParameter("更新内容不能为空");
        }

        String finalRoleCode = null;
        if (Objects.nonNull(roleCode)) {
            finalRoleCode = StringUtils.trimToNull(roleCode);
            if (StringUtils.isBlank(finalRoleCode)) {
                throw illegalParameter("角色编码不能为空");
            }
            checkTextLength(finalRoleCode, "角色编码", MAX_ROLE_CODE_LENGTH);
            rbacRoleRepository.findByRoleCode(accountId, finalRoleCode)
                    .filter(item -> !Objects.equals(item.getId(), id))
                    .ifPresent(item -> {
                        throw conflict("角色编码已存在");
                    });
        }

        String finalRoleName = null;
        if (Objects.nonNull(roleName)) {
            finalRoleName = StringUtils.trimToNull(roleName);
            if (StringUtils.isBlank(finalRoleName)) {
                throw illegalParameter("角色名称不能为空");
            }
            checkTextLength(finalRoleName, "角色名称", MAX_ROLE_NAME_LENGTH);
        }
        String finalRoleDesc = normalizeNullableText(roleDesc, "角色描述", MAX_ROLE_DESC_LENGTH);

        RbacRoleEntity updateEntity = RbacRoleEntity.builder()
                .id(id)
                .accountId(accountId)
                .roleCode(finalRoleCode)
                .roleName(finalRoleName)
                .roleDesc(finalRoleDesc)
                .status(status)
                .build();

        int updateCount = rbacRoleRepository.updateById(accountId, updateEntity);
        if (updateCount <= 0) {
            throw notFound("角色不存在");
        }

        return queryRoleById(accountId, id);
    }

    public boolean deleteRole(Long accountId, Long id) {
        checkAccountId(accountId);
        checkRoleId(id);
        queryRoleById(accountId, id);
        int updateCount = rbacRoleRepository.logicDeleteById(accountId, id);
        if (updateCount <= 0) {
            throw notFound("角色不存在");
        }
        rbacRelationRepository.deleteAllRoleRelations(accountId, id);
        return true;
    }

    public RbacPermissionEntity createPermission(Long accountId, String permCode, String permName, Integer permType, Long parentId, String path, String method, Boolean status) {
        checkAccountId(accountId);
        String finalPermCode = StringUtils.trimToNull(permCode);
        if (StringUtils.isBlank(finalPermCode)) {
            throw illegalParameter("权限编码不能为空");
        }
        checkTextLength(finalPermCode, "权限编码", MAX_PERMISSION_CODE_LENGTH);
        if (finalPermCode.toLowerCase(Locale.ROOT).startsWith(SYSTEM_PERMISSION_PREFIX)) {
            throw illegalParameter("自定义权限不能使用 rbac: 系统前缀");
        }

        String finalPermName = StringUtils.trimToNull(permName);
        if (StringUtils.isBlank(finalPermName)) {
            throw illegalParameter("权限名称不能为空");
        }
        checkTextLength(finalPermName, "权限名称", MAX_PERMISSION_NAME_LENGTH);
        String finalPath = normalizeOptionalText(path, "权限路径", MAX_PERMISSION_PATH_LENGTH);
        String finalMethod = normalizeOptionalText(method, "请求方法", MAX_HTTP_METHOD_LENGTH);

        Integer finalPermType = normalizePermType(permType);
        Long finalParentId = normalizeParentId(parentId);
        checkPermissionParent(accountId, null, finalParentId);

        if (rbacPermissionRepository.findByPermCode(accountId, finalPermCode).isPresent()) {
            throw conflict("权限编码已存在");
        }

        RbacPermissionEntity createEntity = RbacPermissionEntity.builder()
                .accountId(accountId)
                .permCode(finalPermCode)
                .permName(finalPermName)
                .permType(finalPermType)
                .parentId(finalParentId)
                .path(finalPath)
                .method(finalMethod)
                .status(Objects.isNull(status) ? Boolean.TRUE : status)
                .systemManaged(Boolean.FALSE)
                .deleted(Boolean.FALSE)
                .build();

        RbacPermissionEntity savedEntity = rbacPermissionRepository.save(accountId, createEntity);
        if (Objects.isNull(savedEntity.getId())) {
            throw illegalParameter("创建权限失败");
        }

        return savedEntity;
    }

    public RbacPermissionEntity queryPermissionById(Long accountId, Long id) {
        checkAccountId(accountId);
        checkPermissionId(id);
        return rbacPermissionRepository.findById(accountId, id)
                .orElseThrow(() -> notFound("权限不存在"));
    }

    public DomainPage<RbacPermissionEntity> queryPermissionPage(Long accountId, Integer pageNum, Integer pageSize, String permCode, String permName, Integer permType, Long parentId, Boolean status) {
        checkAccountId(accountId);
        Integer finalPageNum = normalizePageNum(pageNum);
        Integer finalPageSize = normalizePageSize(pageSize);
        Integer offset = (finalPageNum - 1) * finalPageSize;

        String finalPermCode = StringUtils.trimToNull(permCode);
        String finalPermName = StringUtils.trimToNull(permName);
        Integer finalPermType = Objects.isNull(permType) ? null : checkPermType(permType);
        Long finalParentId = Objects.isNull(parentId) ? null : normalizeParentId(parentId);

        long total = rbacPermissionRepository.countPage(accountId, finalPermCode, finalPermName, finalPermType, finalParentId, status);
        if (total <= 0) {
            return new DomainPage<>(0L, finalPageNum, finalPageSize, List.of());
        }

        List<RbacPermissionEntity> list = rbacPermissionRepository.queryPage(
                accountId,
                finalPermCode,
                finalPermName,
                finalPermType,
                finalParentId,
                status,
                offset,
                finalPageSize
        );

        return new DomainPage<>(total, finalPageNum, finalPageSize, list);
    }

    public RbacPermissionEntity updatePermission(Long accountId, Long id, String permName, Integer permType, Long parentId, String path, String method, Boolean status) {
        checkAccountId(accountId);
        checkPermissionId(id);
        RbacPermissionEntity currentPermission = queryPermissionById(accountId, id);
        checkCustomPermission(currentPermission);

        if (Objects.isNull(permName)
                && Objects.isNull(permType)
                && Objects.isNull(parentId)
                && Objects.isNull(path)
                && Objects.isNull(method)
                && Objects.isNull(status)) {
            throw illegalParameter("更新内容不能为空");
        }

        String finalPermName = null;
        if (Objects.nonNull(permName)) {
            finalPermName = StringUtils.trimToNull(permName);
            if (StringUtils.isBlank(finalPermName)) {
                throw illegalParameter("权限名称不能为空");
            }
            checkTextLength(finalPermName, "权限名称", MAX_PERMISSION_NAME_LENGTH);
        }
        String finalPath = normalizeNullableText(path, "权限路径", MAX_PERMISSION_PATH_LENGTH);
        String finalMethod = normalizeNullableText(method, "请求方法", MAX_HTTP_METHOD_LENGTH);

        Integer finalPermType = Objects.isNull(permType) ? null : checkPermType(permType);
        Long finalParentId = Objects.isNull(parentId) ? null : normalizeParentId(parentId);
        if (finalParentId != null) {
            checkPermissionParent(accountId, id, finalParentId);
        }

        RbacPermissionEntity updateEntity = RbacPermissionEntity.builder()
                .id(id)
                .accountId(accountId)
                .permName(finalPermName)
                .permType(finalPermType)
                .parentId(finalParentId)
                .path(finalPath)
                .method(finalMethod)
                .status(status)
                .build();

        int updateCount = rbacPermissionRepository.updateById(accountId, updateEntity);
        if (updateCount <= 0) {
            throw notFound("权限不存在");
        }

        return queryPermissionById(accountId, id);
    }

    public boolean deletePermission(Long accountId, Long id) {
        checkAccountId(accountId);
        checkPermissionId(id);
        RbacPermissionEntity currentPermission = queryPermissionById(accountId, id);
        checkCustomPermission(currentPermission);
        int updateCount = rbacPermissionRepository.logicDeleteById(accountId, id);
        if (updateCount <= 0) {
            throw notFound("权限不存在");
        }
        rbacRelationRepository.deleteAllPermissionRelations(accountId, id);
        return true;
    }

    public boolean replaceUserRoles(Long accountId, Long userId, List<Long> roleIds) {
        checkAccountId(accountId);
        checkUserId(userId);
        rbacUserRepository.findById(accountId, userId)
                .orElseThrow(() -> notFound("用户不存在"));

        List<Long> targetRoleIds = normalizeIds(roleIds, "角色ID列表");
        checkRoleIdsExists(accountId, targetRoleIds);

        List<Long> currentRoleIds = rbacRelationRepository.queryRoleIdsByUserId(accountId, userId);
        Set<Long> currentRoleIdSet = new HashSet<>(currentRoleIds);
        Set<Long> targetRoleIdSet = new HashSet<>(targetRoleIds);

        List<Long> removeRoleIds = currentRoleIds.stream()
                .filter(roleId -> !targetRoleIdSet.contains(roleId))
                .toList();

        List<Long> addRoleIds = targetRoleIds.stream()
                .filter(roleId -> !currentRoleIdSet.contains(roleId))
                .toList();

        if (!removeRoleIds.isEmpty()) {
            rbacRelationRepository.deleteUserRoles(accountId, userId, removeRoleIds);
        }

        if (!addRoleIds.isEmpty()) {
            rbacRelationRepository.insertUserRoles(accountId, userId, addRoleIds);
        }

        return true;
    }

    public boolean replaceRolePermissions(Long accountId, Long roleId, List<Long> permissionIds) {
        checkAccountId(accountId);
        checkRoleId(roleId);
        rbacRoleRepository.findById(accountId, roleId)
                .orElseThrow(() -> notFound("角色不存在"));

        List<Long> targetPermissionIds = normalizeIds(permissionIds, "权限ID列表");
        checkPermissionIdsExists(accountId, targetPermissionIds);

        List<Long> currentPermissionIds = rbacRelationRepository.queryPermissionIdsByRoleId(accountId, roleId);
        Set<Long> currentPermissionIdSet = new HashSet<>(currentPermissionIds);
        Set<Long> targetPermissionIdSet = new HashSet<>(targetPermissionIds);

        List<Long> removePermissionIds = currentPermissionIds.stream()
                .filter(permissionId -> !targetPermissionIdSet.contains(permissionId))
                .toList();

        List<Long> addPermissionIds = targetPermissionIds.stream()
                .filter(permissionId -> !currentPermissionIdSet.contains(permissionId))
                .toList();

        if (!removePermissionIds.isEmpty()) {
            rbacRelationRepository.deleteRolePermissions(accountId, roleId, removePermissionIds);
        }

        if (!addPermissionIds.isEmpty()) {
            rbacRelationRepository.insertRolePermissions(accountId, roleId, addPermissionIds);
        }

        return true;
    }

    public List<Long> queryUserRoleIds(Long accountId, Long userId) {
        checkAccountId(accountId);
        checkUserId(userId);
        rbacUserRepository.findById(accountId, userId)
                .orElseThrow(() -> notFound("用户不存在"));

        List<Long> roleIds = rbacRelationRepository.queryRoleIdsByUserId(accountId, userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }

        Set<Long> validRoleIdSet = new HashSet<>(rbacRoleRepository.queryExistingIds(accountId, roleIds));
        return roleIds.stream()
                .filter(validRoleIdSet::contains)
                .toList();
    }

    public List<Long> queryRolePermissionIds(Long accountId, Long roleId) {
        checkAccountId(accountId);
        checkRoleId(roleId);
        rbacRoleRepository.findById(accountId, roleId)
                .orElseThrow(() -> notFound("角色不存在"));

        List<Long> permissionIds = rbacRelationRepository.queryPermissionIdsByRoleId(accountId, roleId);
        if (permissionIds.isEmpty()) {
            return List.of();
        }

        Set<Long> validPermissionIdSet = new HashSet<>(rbacPermissionRepository.queryExistingIds(accountId, permissionIds));
        return permissionIds.stream()
                .filter(validPermissionIdSet::contains)
                .toList();
    }

    private void checkRoleIdsExists(Long accountId, List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return;
        }

        Set<Long> existsRoleIds = new HashSet<>(rbacRoleRepository.queryExistingIds(accountId, roleIds));
        List<Long> missingRoleIds = roleIds.stream()
                .filter(roleId -> !existsRoleIds.contains(roleId))
                .toList();

        if (!missingRoleIds.isEmpty()) {
            throw illegalParameter("角色不存在或已删除：" + missingRoleIds);
        }
    }

    private void checkPermissionIdsExists(Long accountId, List<Long> permissionIds) {
        if (permissionIds.isEmpty()) {
            return;
        }

        Set<Long> existsPermissionIds = new HashSet<>(rbacPermissionRepository.queryExistingIds(accountId, permissionIds));
        List<Long> missingPermissionIds = permissionIds.stream()
                .filter(permissionId -> !existsPermissionIds.contains(permissionId))
                .toList();

        if (!missingPermissionIds.isEmpty()) {
            throw illegalParameter("权限不存在或已删除：" + missingPermissionIds);
        }
    }

    private void checkPermissionParent(Long accountId, Long permissionId, Long parentId) {
        if (Objects.isNull(parentId) || parentId == 0L) {
            return;
        }
        // 一次加载账号全量权限，在内存中回溯父链，避免逐级 findById 的 N+1 查询
        Map<Long, Long> parentMap = rbacPermissionRepository.findAllByAccountId(accountId).stream()
                .collect(Collectors.toMap(RbacPermissionEntity::getId,
                        entity -> normalizeParentId(entity.getParentId())));
        Set<Long> visitedPermissionIds = new HashSet<>();
        if (Objects.nonNull(permissionId)) {
            visitedPermissionIds.add(permissionId);
        }
        Long currentParentId = parentId;
        for (int depth = 0; depth < MAX_PERMISSION_DEPTH; depth++) {
            if (!visitedPermissionIds.add(currentParentId)) {
                throw illegalParameter("权限父级存在循环引用");
            }
            Long nextParentId = parentMap.get(currentParentId);
            if (Objects.isNull(nextParentId)) {
                throw illegalParameter("父级权限不存在或不属于当前主账号");
            }
            if (nextParentId == 0L) {
                return;
            }
            currentParentId = nextParentId;
        }
        throw illegalParameter("权限层级不能超过 " + MAX_PERMISSION_DEPTH + " 层");
    }
}
