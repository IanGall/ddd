package cn.iantech.infrastructure.auth;

import cn.iantech.id.GlobalIdGenerator;
import cn.iantech.id.GlobalIdGeneratorProvider;
import cn.iantech.id.IdGenerationException;
import cn.iantech.infrastructure.id.AuthIdBusiness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthIdGeneratorAdapterTest {

    @Test
    void shouldResolveAuthSessionGeneratorAndDelegateNextId() {
        GlobalIdGenerator sessionGenerator = mock(GlobalIdGenerator.class);
        when(sessionGenerator.nextId()).thenReturn(501L);
        GlobalIdGeneratorProvider provider = mock(GlobalIdGeneratorProvider.class);
        when(provider.forBusiness(AuthIdBusiness.AUTH_SESSION)).thenReturn(sessionGenerator);

        AuthIdGeneratorAdapter adapter = new AuthIdGeneratorAdapter(provider);

        assertEquals(501L, adapter.nextId());
        verify(sessionGenerator).nextId();
    }

    @Test
    void shouldPropagateUnknownBusinessFailure() {
        GlobalIdGeneratorProvider provider = mock(GlobalIdGeneratorProvider.class);
        when(provider.forBusiness(AuthIdBusiness.AUTH_SESSION))
                .thenThrow(new IdGenerationException("未声明的业务 ID 生成器：auth-session"));

        IdGenerationException exception = assertThrows(IdGenerationException.class,
                () -> new AuthIdGeneratorAdapter(provider));

        assertTrue(exception.getMessage().contains("auth-session"));
    }
}
