package cn.iantech.test.nplusone;

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * {@link DetectNPlusOne} 的 JUnit 扩展：在测试执行期间开启观测会话，执行结束后输出摘要并校验预算。
 *
 * <p>仅当测试本身通过时才做 N+1 断言，避免覆盖原始失败原因；无论是否违规都会输出观测摘要，
 * 便于校准预算。</p>
 */
public final class NPlusOneExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(NPlusOneExtension.class);

    private static final String SESSION_KEY = "session";

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        DetectNPlusOne annotation = context.getElement()
                .map(element -> element.getAnnotation(DetectNPlusOne.class))
                .orElse(null);
        if (annotation == null) {
            annotation = context.getTestClass()
                    .map(testClass -> testClass.getAnnotation(DetectNPlusOne.class))
                    .orElse(null);
        }
        if (annotation == null) {
            return;
        }
        ObservationSession session = new ObservationSession(annotation);
        NPlusOneContext.start(session);
        context.getStore(NAMESPACE).put(SESSION_KEY, session);
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        ObservationSession session = context.getStore(NAMESPACE).remove(SESSION_KEY, ObservationSession.class);
        if (session == null) {
            return;
        }
        NPlusOneContext.clear(session);
        context.publishReportEntry("n-plus-one", session.summary());
        if (context.getExecutionException().isEmpty()) {
            session.assertWithinLimits();
        }
    }
}
