package cn.iantech.api;

import cn.iantech.api.model.rbac.PlatformCreateAccountReq;
import cn.iantech.api.model.rbac.RbacAccountDTO;
import cn.iantech.common.exception.AppException;

/**
 * 平台级主账号开户服务，不属于租户 RBAC 权限域。
 */
public interface IPlatformAccountService {

    RbacAccountDTO createAccount(PlatformCreateAccountReq req) throws AppException;
}
