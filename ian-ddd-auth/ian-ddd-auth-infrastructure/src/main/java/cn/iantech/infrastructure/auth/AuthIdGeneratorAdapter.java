package cn.iantech.infrastructure.auth;

import cn.iantech.domain.auth.infra.IAuthIdGenerator;
import cn.iantech.id.GlobalIdGenerator;
import cn.iantech.id.GlobalIdGeneratorProvider;
import cn.iantech.infrastructure.id.AuthIdBusiness;
import org.springframework.stereotype.Component;

/**
 * 使用基础 Starter 为 Auth 会话生成全局唯一 ID。
 */
@Component
public class AuthIdGeneratorAdapter implements IAuthIdGenerator {

    private final GlobalIdGenerator globalIdGenerator;

    public AuthIdGeneratorAdapter(GlobalIdGeneratorProvider provider) {
        this.globalIdGenerator = provider.forBusiness(AuthIdBusiness.AUTH_SESSION.businessName());
    }

    @Override
    public long nextId() {
        return globalIdGenerator.nextId();
    }
}
