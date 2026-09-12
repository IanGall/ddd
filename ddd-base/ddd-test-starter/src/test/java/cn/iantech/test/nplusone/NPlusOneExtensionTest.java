package cn.iantech.test.nplusone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NPlusOneExtensionTest {

    private final NPlusOneExtension extension = new NPlusOneExtension();

    @Test
    void shouldPreferMethodLevelBudgetOverClassLevel() throws Exception {
        ExtensionContext context = contextWithStore(
                Optional.of(method(AnnotatedCases.class, "methodLevel")), Optional.of(AnnotatedCases.class));
        ExtensionContext.Store store = context.getStore(ExtensionContext.Namespace.create("probe"));

        extension.beforeTestExecution(context);

        ObservationSession session = captureSession(store);
        try {
            NPlusOneContext.recordSelect("SELECT id FROM rbac_user WHERE id = 1");
            NPlusOneContext.recordSelect("SELECT id FROM rbac_user WHERE id = 2");
            assertThatThrownBy(session::assertWithinLimits).isInstanceOf(AssertionError.class);
        } finally {
            NPlusOneContext.clear(session);
        }
    }

    @Test
    void shouldFallBackToClassLevelAnnotation() throws Exception {
        ExtensionContext context = contextWithStore(
                Optional.of(method(AnnotatedCases.class, "inheritsClassLevel")), Optional.of(AnnotatedCases.class));
        ExtensionContext.Store store = context.getStore(ExtensionContext.Namespace.create("probe"));

        extension.beforeTestExecution(context);

        ObservationSession session = captureSession(store);
        try {
            NPlusOneContext.recordSelect("SELECT id FROM rbac_user WHERE id = 1");
            NPlusOneContext.recordSelect("SELECT id FROM rbac_user WHERE id = 2");
            assertThatCode(session::assertWithinLimits).doesNotThrowAnyException();
        } finally {
            NPlusOneContext.clear(session);
        }
    }

    @Test
    void shouldNotObserveWhenAnnotationMissing() throws Exception {
        ExtensionContext context = contextWithStore(
                Optional.of(method(UnautotatedCases.class, "plain")), Optional.of(UnautotatedCases.class));
        ExtensionContext.Store store = context.getStore(ExtensionContext.Namespace.create("probe"));

        extension.beforeTestExecution(context);

        verifyNoInteractions(store);
        ObservationSession probe = new ObservationSession(DefaultBudget.class.getAnnotation(DetectNPlusOne.class));
        NPlusOneContext.start(probe);
        NPlusOneContext.clear(probe);
    }

    @Test
    void shouldPublishSummaryAndAssertLimitsAfterExecution() throws Exception {
        ExtensionContext context = contextWithStore(
                Optional.of(method(AnnotatedCases.class, "methodLevel")), Optional.of(AnnotatedCases.class));
        ExtensionContext.Store store = context.getStore(ExtensionContext.Namespace.create("probe"));
        when(context.getExecutionException()).thenReturn(Optional.empty());

        extension.beforeTestExecution(context);
        ObservationSession session = captureSession(store);
        when(store.remove("session", ObservationSession.class)).thenReturn(session);
        NPlusOneContext.recordSelect("SELECT id FROM rbac_user WHERE id = 1");
        NPlusOneContext.recordSelect("SELECT id FROM rbac_user WHERE id = 2");

        assertThatThrownBy(() -> extension.afterTestExecution(context))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("重复 SELECT超过阈值，actual=2, expected=1");

        verify(context).publishReportEntry(eq("n-plus-one"), anyString());
        assertObservationContextCleared();
    }

    @Test
    void shouldKeepOriginalFailureWhenTestAlreadyFailed() throws Exception {
        ExtensionContext context = contextWithStore(
                Optional.of(method(AnnotatedCases.class, "methodLevel")), Optional.of(AnnotatedCases.class));
        ExtensionContext.Store store = context.getStore(ExtensionContext.Namespace.create("probe"));
        when(context.getExecutionException()).thenReturn(Optional.of(new IllegalStateException("原用例已失败")));

        extension.beforeTestExecution(context);
        ObservationSession session = captureSession(store);
        when(store.remove("session", ObservationSession.class)).thenReturn(session);
        NPlusOneContext.recordSelect("SELECT id FROM rbac_user WHERE id = 1");
        NPlusOneContext.recordSelect("SELECT id FROM rbac_user WHERE id = 2");

        assertThatCode(() -> extension.afterTestExecution(context)).doesNotThrowAnyException();

        verify(context).publishReportEntry(eq("n-plus-one"), anyString());
    }

    @Test
    void shouldDoNothingWhenNoSessionWasStarted() throws Exception {
        ExtensionContext context = contextWithStore(Optional.empty(), Optional.empty());
        ExtensionContext.Store store = context.getStore(ExtensionContext.Namespace.create("probe"));
        when(store.remove("session", ObservationSession.class)).thenReturn(null);

        extension.afterTestExecution(context);

        verify(context, never()).publishReportEntry(anyString(), anyString());
    }

    private static ExtensionContext contextWithStore(Optional<AnnotatedElement> element,
                                                     Optional<Class<?>> testClass) {
        ExtensionContext context = mock(ExtensionContext.class);
        when(context.getElement()).thenReturn(element);
        when(context.getTestClass()).thenReturn(testClass);
        when(context.getStore(any())).thenReturn(mock(ExtensionContext.Store.class));
        return context;
    }

    private static ObservationSession captureSession(ExtensionContext.Store store) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(store).put(eq("session"), captor.capture());
        return (ObservationSession) captor.getValue();
    }

    /**
     * 会话已被清空时才能再次开启观测，否则 {@link NPlusOneContext} 会拒绝并发会话。
     */
    private static void assertObservationContextCleared() {
        ObservationSession probe = new ObservationSession(DefaultBudget.class.getAnnotation(DetectNPlusOne.class));
        NPlusOneContext.start(probe);
        NPlusOneContext.clear(probe);
    }

    private static Method method(Class<?> type, String name) throws NoSuchMethodException {
        return type.getDeclaredMethod(name);
    }

    @DetectNPlusOne(maxRepeatedSelects = 5)
    static class AnnotatedCases {

        @DetectNPlusOne
        void methodLevel() {
        }

        void inheritsClassLevel() {
        }
    }

    static class UnautotatedCases {

        void plain() {
        }
    }

    @DetectNPlusOne
    static class DefaultBudget {
    }
}
