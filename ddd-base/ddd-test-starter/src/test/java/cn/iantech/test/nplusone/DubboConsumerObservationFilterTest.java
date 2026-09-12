package cn.iantech.test.nplusone;

import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DubboConsumerObservationFilterTest {

    private final DubboConsumerObservationFilter filter = new DubboConsumerObservationFilter();

    @Test
    void shouldRecordRemoteOperationAndDelegateInvocation() {
        Invoker<SampleService> invoker = mock(Invoker.class);
        Invocation invocation = mock(Invocation.class);
        Result expected = mock(Result.class);
        when(invoker.getInterface()).thenReturn(SampleService.class);
        when(invocation.getMethodName()).thenReturn("validate");
        when(invoker.invoke(invocation)).thenReturn(expected);

        ObservationSession session = new ObservationSession(DefaultBudget.class.getAnnotation(DetectNPlusOne.class));
        NPlusOneContext.start(session);
        Result actual;
        try {
            actual = filter.invoke(invoker, invocation);
        } finally {
            NPlusOneContext.clear(session);
        }

        assertThat(actual).isSameAs(expected);
        verify(invoker).invoke(invocation);
        assertThat(session.summary())
                .contains("remoteCalls=1")
                .contains(SampleService.class.getName() + "#validate × 1");
    }

    @Test
    void shouldIgnoreInvocationWithoutActiveSession() {
        Invoker<SampleService> invoker = mock(Invoker.class);
        Invocation invocation = mock(Invocation.class);
        when(invoker.getInterface()).thenReturn(SampleService.class);
        when(invocation.getMethodName()).thenReturn("validate");

        assertThatCode(() -> filter.invoke(invoker, invocation)).doesNotThrowAnyException();
    }

    @Test
    void shouldBeRegisteredAsDubboFilterSpi() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/META-INF/dubbo/org.apache.dubbo.rpc.Filter")) {
            assertThat(in).isNotNull();
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .contains("dddTestObservation=" + DubboConsumerObservationFilter.class.getName());
        }
    }

    interface SampleService {

        void validate();
    }

    @DetectNPlusOne
    static class DefaultBudget {
    }
}
