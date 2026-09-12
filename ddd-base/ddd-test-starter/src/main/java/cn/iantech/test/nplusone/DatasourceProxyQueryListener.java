package cn.iantech.test.nplusone;

import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;

import java.util.List;

/**
 * 数据源代理查询监听器：把执行完成的 SELECT/WITH 语句上报给 N+1 观测上下文。
 *
 * <p>由 {@code DddTestAutoConfiguration} 注册到被包装的数据源上，未开启观测时上报为空操作。</p>
 */
public final class DatasourceProxyQueryListener implements QueryExecutionListener {

    @Override
    public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        // 只统计执行完成的语句，无需处理执行前事件
    }

    @Override
    public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        queryInfoList.stream()
                .map(QueryInfo::getQuery)
                .filter(DatasourceProxyQueryListener::isSelect)
                .forEach(NPlusOneContext::recordSelect);
    }

    private static boolean isSelect(String sql) {
        String trimmed = sql == null ? "" : sql.trim();
        return trimmed.regionMatches(true, 0, "select", 0, 6) || trimmed.regionMatches(true, 0, "with", 0, 4);
    }
}
