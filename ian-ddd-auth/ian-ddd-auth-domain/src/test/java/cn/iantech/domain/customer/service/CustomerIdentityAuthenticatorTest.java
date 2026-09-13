package cn.iantech.domain.customer.service;

import cn.iantech.domain.auth.infra.IPasswordEncoder;
import cn.iantech.domain.customer.infra.ICustomerUserRepository;
import cn.iantech.domain.customer.model.CustomerUserEntity;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class CustomerIdentityAuthenticatorTest {

    private final ICustomerUserRepository repository = mock(ICustomerUserRepository.class);
    private final IPasswordEncoder passwordEncoder = mock(IPasswordEncoder.class);
    private final CustomerIdentityAuthenticator authenticator =
            new CustomerIdentityAuthenticator(repository, passwordEncoder);

    @Test
    void shouldAuthenticateUsableCustomerAndUpdateLastLoginTime() {
        CustomerUserEntity customer = customer(true, false);
        when(repository.findByLoginName("customer")).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("password", "hash")).thenReturn(true);

        var identity = authenticator.authenticate(" CUSTOMER ", "password");

        assertEquals(3001L, identity.userId());
        assertEquals("CUSTOMER", identity.userType());
        assertNull(identity.accountId());
        verify(repository).updateLastLoginAt(3001L);
    }

    @Test
    void shouldRejectDisabledCustomerWhenReloading() {
        when(repository.findById(3001L)).thenReturn(Optional.of(customer(false, false)));

        assertNull(authenticator.reload(3001L));
        verifyNoInteractions(passwordEncoder);
    }

    private CustomerUserEntity customer(boolean status, boolean deleted) {
        return CustomerUserEntity.builder().id(3001L).loginName("customer").passwordHash("hash")
                .status(status).deleted(deleted).build();
    }
}
