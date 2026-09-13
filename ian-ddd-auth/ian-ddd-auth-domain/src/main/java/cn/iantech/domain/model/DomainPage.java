package cn.iantech.domain.model;

import java.util.List;

/**
 * 领域层通用分页结果。
 */
public record DomainPage<T>(long total, int pageNum, int pageSize, List<T> list) {
}
