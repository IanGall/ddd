package cn.iantech.trigger.rpc;

import cn.iantech.api.IPlatformAccountService;
import cn.iantech.api.model.rbac.PlatformCreateAccountReq;
import cn.iantech.api.model.rbac.RbacAccountDTO;
import cn.iantech.cases.rbac.model.RbacCaseCommands.AccountResult;
import cn.iantech.cases.rbac.model.RbacCaseCommands.CreateAccount;
import cn.iantech.cases.rbac.service.RbacCaseService;
import cn.iantech.common.constant.Constants;
import cn.iantech.common.exception.AppException;
import cn.iantech.common.util.Sha256;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Value;

import java.security.MessageDigest;
import java.util.Objects;

/**
 * 平台开户 RPC 入口，Provider 必须在进入领域用例前完成平台凭据校验。
 */
@DubboService(version = "1.0.0", protocol = "dubbo", timeout = 3000)
public class PlatformAccountService implements IPlatformAccountService {

    private final RbacCaseService rbacCaseService;
    private final byte[] platformTokenHash;

    public PlatformAccountService(RbacCaseService rbacCaseService,
                                  @Value("${platform.security.admin-token}") String platformAdminToken) {
        this.rbacCaseService = rbacCaseService;
        if (StringUtils.isBlank(platformAdminToken)) {
            throw new IllegalStateException("平台开户凭据不能为空，请配置 platform.security.admin-token");
        }
        this.platformTokenHash = Sha256.digest(platformAdminToken);
    }

    @Override
    public RbacAccountDTO createAccount(PlatformCreateAccountReq req) {
        if (Objects.isNull(req)
                || !MessageDigest.isEqual(platformTokenHash, Sha256.digest(StringUtils.defaultString(req.getPlatformToken())))) {
            throw new AppException(Constants.ResponseCode.ACCESS_DENIED.getCode(), "平台凭据无效");
        }
        AccountResult result = rbacCaseService.createAccount(new CreateAccount(req.getUsername(), req.getPassword(),
                req.getDisplayName(), req.getEmail(), req.getMobile()));
        return RbacAccountDTO.builder().accountId(result.accountId()).username(result.username())
                .loginName(result.loginName()).build();
    }
}
