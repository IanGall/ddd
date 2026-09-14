package cn.iantech.domain.rbac.service.impl;

import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import cn.iantech.domain.rbac.model.entity.RbacPermissionEntity;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * RBAC 用例无关的入参校验与归一化工具。
 *
 * <p>只承载纯函数：不访问仓储、不持有状态，供 {@link RbacDomainService} 复用；
 * 需要查询数据的校验（如关联ID存在性、权限父链）仍留在领域服务内。</p>
 */
final class RbacValidationSupport {

    static final int DEFAULT_PAGE_NUM = 1;
    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;
    /** pageNum 上界：防止 (pageNum - 1) * pageSize 整型溢出为负 offset，导致 SQL 语法错误。 */
    static final int MAX_PAGE_NUM = 10_000;
    static final int DEFAULT_PERM_TYPE = 2;
    static final int MIN_PASSWORD_BYTES = 8;
    static final int MAX_PASSWORD_BYTES = 72;
    static final String SYSTEM_PERMISSION_PREFIX = "rbac:";
    static final Set<Integer> VALID_PERM_TYPES = Set.of(1, 2, 3);
    /** 用户名/主账号名规则唯一来源：此处改动即对全部 RBAC 入口生效。 */
    static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]{1,64}$");

    private RbacValidationSupport() {
    }

    static Integer normalizePageNum(Integer pageNum) {
        if (Objects.isNull(pageNum) || pageNum <= 0) {
            return DEFAULT_PAGE_NUM;
        }
        return Math.min(pageNum, MAX_PAGE_NUM);
    }

    static Integer normalizePageSize(Integer pageSize) {
        if (Objects.isNull(pageSize) || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    static Integer normalizePermType(Integer permType) {
        if (Objects.isNull(permType)) {
            return DEFAULT_PERM_TYPE;
        }
        return checkPermType(permType);
    }

    static Integer checkPermType(Integer permType) {
        if (!VALID_PERM_TYPES.contains(permType)) {
            throw illegalParameter("权限类型非法");
        }
        return permType;
    }

    static Long normalizeParentId(Long parentId) {
        Long finalParentId = Objects.isNull(parentId) ? 0L : parentId;
        if (finalParentId < 0) {
            throw illegalParameter("父级权限ID非法");
        }
        return finalParentId;
    }

    static List<Long> normalizeIds(List<Long> ids, String fieldName) {
        List<Long> source = Objects.isNull(ids) ? List.of() : ids;
        if (source.stream().anyMatch(Objects::isNull)) {
            throw illegalParameter(fieldName + "存在空值");
        }
        if (source.stream().anyMatch(id -> id <= 0)) {
            throw illegalParameter(fieldName + "存在非法值");
        }
        return source.stream()
                .distinct()
                .toList();
    }

    static void checkUserId(Long userId) {
        if (Objects.isNull(userId) || userId <= 0) {
            throw illegalParameter("用户ID非法");
        }
    }

    static void checkAccountId(Long accountId) {
        if (Objects.isNull(accountId) || accountId <= 0) {
            throw illegalParameter("账号ID非法");
        }
    }

    static void checkRoleId(Long roleId) {
        if (Objects.isNull(roleId) || roleId <= 0) {
            throw illegalParameter("角色ID非法");
        }
    }

    static void checkPermissionId(Long id) {
        if (Objects.isNull(id) || id <= 0) {
            throw illegalParameter("权限ID非法");
        }
    }

    static void checkCustomPermission(RbacPermissionEntity permission) {
        if (Boolean.TRUE.equals(permission.getSystemManaged())
                || StringUtils.startsWithIgnoreCase(permission.getPermCode(), SYSTEM_PERMISSION_PREFIX)) {
            throw illegalParameter("系统权限禁止运行时更新或删除");
        }
    }

    static void checkPassword(String rawPassword) {
        int passwordBytes = StringUtils.defaultString(rawPassword).getBytes(StandardCharsets.UTF_8).length;
        if (StringUtils.isBlank(rawPassword)
                || passwordBytes < MIN_PASSWORD_BYTES || passwordBytes > MAX_PASSWORD_BYTES) {
            throw illegalParameter("密码长度必须为 8 至 72 个 UTF-8 字节");
        }
    }

    static String normalizeOptionalText(String value, String fieldName, int maxLength) {
        String normalized = StringUtils.defaultString(StringUtils.trimToNull(value));
        checkTextLength(normalized, fieldName, maxLength);
        return normalized;
    }

    static String normalizeNullableText(String value, String fieldName, int maxLength) {
        if (Objects.isNull(value)) {
            return null;
        }
        return normalizeOptionalText(value, fieldName, maxLength);
    }

    static void checkTextLength(String value, String fieldName, int maxLength) {
        int length = value.codePointCount(0, value.length());
        if (length > maxLength) {
            throw illegalParameter(fieldName + "长度不能超过 " + maxLength + " 个字符");
        }
    }

    static AppException illegalParameter(String info) {
        return new AppException(Constants.ResponseCode.INVALID_ARGUMENT.getCode(), info);
    }
}
