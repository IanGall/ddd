package cn.iantech.domain.auth.infra;

/**
 * Auth 业务 ID 生成端口。
 */
@FunctionalInterface
public interface IAuthIdGenerator {

    /**
     * 生成全局唯一的正数 ID。
     */
    long nextId();
}
