package cn.iantech.domain.rbac.service;

import cn.iantech.domain.auth.infra.IPasswordEncoder;
import cn.iantech.domain.auth.model.AuthUserTypes;
import cn.iantech.domain.auth.model.AuthenticatedIdentity;
import cn.iantech.domain.auth.service.IAdminIdentityAuthenticator;
import cn.iantech.domain.rbac.infra.IRbacAccountRepository;
import cn.iantech.domain.rbac.infra.IRbacAuthorizationRepository;
import cn.iantech.domain.rbac.infra.IRbacUserRepository;
import cn.iantech.domain.rbac.model.entity.RbacAccountEntity;
import cn.iantech.domain.rbac.model.entity.RbacUserEntity;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析账号化登录名并认证主账号或子账号。
 */
public class RbacIdentityAuthenticator implements IAdminIdentityAuthenticator {

    public static final String PRIMARY_USER_TYPE = AuthUserTypes.PRIMARY;
    public static final String SUB_ACCOUNT_USER_TYPE = AuthUserTypes.SUB_ACCOUNT;
    private static final Pattern LOGIN_NAME_PATTERN = Pattern.compile(
            "^([A-Za-z0-9_.-]{1,64})@([1-9][0-9]{0,18})\\.com$");

    private final IRbacAccountRepository accountRepository;
    private final IRbacUserRepository userRepository;
    private final IRbacAuthorizationRepository authorizationRepository;
    private final IPasswordEncoder passwordEncoder;

    public RbacIdentityAuthenticator(IRbacAccountRepository accountRepository,
                                     IRbacUserRepository userRepository,
                                     IRbacAuthorizationRepository authorizationRepository,
                                     IPasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.authorizationRepository = authorizationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public AuthenticatedIdentity authenticate(String loginName, String password) {
        LoginPrincipal principal = parseLoginName(loginName);
        if (principal == null || password == null) {
            return null;
        }

        RbacAccountEntity account = accountRepository.findByUsername(principal.accountId(), principal.username())
                .orElse(null);
        if (isUsableAccount(account) && passwordEncoder.matches(password, account.getPasswordHash())) {
            return new AuthenticatedIdentity(account.getId(), account.getId(), account.getUsername(),
                    PRIMARY_USER_TYPE, List.of(), List.of());
        }

        RbacAccountEntity ownerAccount = accountRepository.findById(principal.accountId()).orElse(null);
        if (!isUsableAccount(ownerAccount)) {
            return null;
        }
        RbacUserEntity user = userRepository.findByUsername(principal.accountId(), principal.username()).orElse(null);
        if (!isUsableUser(user) || !passwordEncoder.matches(password, user.getPasswordHash())) {
            return null;
        }
        List<String> roleCodes = safeCopy(authorizationRepository.findRoleCodes(principal.accountId(), user.getId()));
        List<String> permissionCodes = safeCopy(
                authorizationRepository.findPermissionCodes(principal.accountId(), user.getId()));
        return new AuthenticatedIdentity(user.getId(), principal.accountId(), user.getUsername(),
                SUB_ACCOUNT_USER_TYPE, roleCodes, permissionCodes);
    }

    @Override
    public AuthenticatedIdentity reload(Long accountId, Long userId, String userType) {
        if (userId == null || userId <= 0 || userType == null) {
            return null;
        }
        if (accountId == null || accountId <= 0) {
            return null;
        }
        RbacAccountEntity account = accountRepository.findById(accountId).orElse(null);
        if (!isUsableAccount(account)) {
            return null;
        }
        if (PRIMARY_USER_TYPE.equals(userType)) {
            return accountId.equals(userId)
                    ? new AuthenticatedIdentity(accountId, accountId, account.getUsername(), PRIMARY_USER_TYPE,
                    List.of(), List.of())
                    : null;
        }
        if (!SUB_ACCOUNT_USER_TYPE.equals(userType)) {
            return null;
        }
        RbacUserEntity user = userRepository.findById(accountId, userId).orElse(null);
        if (!isUsableUser(user)) {
            return null;
        }
        List<String> roleCodes = safeCopy(authorizationRepository.findRoleCodes(accountId, userId));
        List<String> permissionCodes = safeCopy(authorizationRepository.findPermissionCodes(accountId, userId));
        return new AuthenticatedIdentity(userId, accountId, user.getUsername(), SUB_ACCOUNT_USER_TYPE,
                roleCodes, permissionCodes);
    }

    private LoginPrincipal parseLoginName(String loginName) {
        if (StringUtils.isBlank(loginName)) {
            return null;
        }
        Matcher matcher = LOGIN_NAME_PATTERN.matcher(loginName);
        if (!matcher.matches()) {
            return null;
        }
        try {
            long accountId = Long.parseLong(matcher.group(2));
            return accountId > 0 ? new LoginPrincipal(matcher.group(1), accountId) : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean isUsableAccount(RbacAccountEntity account) {
        return account != null && Boolean.TRUE.equals(account.getStatus())
                && !Boolean.TRUE.equals(account.getDeleted());
    }

    private boolean isUsableUser(RbacUserEntity user) {
        return user != null && Boolean.TRUE.equals(user.getStatus()) && !Boolean.TRUE.equals(user.getDeleted());
    }

    private List<String> safeCopy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private record LoginPrincipal(String username, Long accountId) {
    }
}
