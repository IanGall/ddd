package cn.iantech.infrastructure.auth;

import cn.iantech.domain.auth.infra.IAuthIdGenerator;
import cn.iantech.id.GlobalIdGenerator;
import org.springframework.stereotype.Component;

/**
 * 使用基础 Starter 为 Auth 会话生成全局唯一 ID。
 */
@Component
public class AuthIdGeneratorAdapter implements IAuthIdGenerator {

    private final GlobalIdGenerator globalIdGenerator;

    public AuthIdGeneratorAdapter(GlobalIdGenerator globalIdGenerator) {
        this.globalIdGenerator = globalIdGenerator;
    }

    @Override
    public long nextId() {
        return globalIdGenerator.nextId();
    }
}
