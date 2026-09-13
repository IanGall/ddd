/**
 * 入站适配层：解析可信上下文、转换 API DTO，并调用 {@code cn.iantech.cases} 用例服务
 * 或 {@code cn.iantech.domain} 领域服务。
 *
 * <p>本层不得直接调用基础设施实现，也不得承载事务与跨聚合编排。</p>
 */
package cn.iantech.trigger;
