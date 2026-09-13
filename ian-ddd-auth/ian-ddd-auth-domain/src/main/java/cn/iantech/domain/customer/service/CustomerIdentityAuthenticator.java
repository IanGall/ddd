package cn.iantech.domain.customer.service;

import cn.iantech.domain.auth.infra.IPasswordEncoder;
import cn.iantech.domain.auth.model.AuthUserTypes;
import cn.iantech.domain.auth.model.AuthenticatedIdentity;
import cn.iantech.domain.auth.service.ICustomerIdentityAuthenticator;
import cn.iantech.domain.customer.infra.ICustomerUserRepository;
import cn.iantech.domain.customer.model.CustomerUserEntity;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CustomerIdentityAuthenticator implements ICustomerIdentityAuthenticator {

    private final ICustomerUserRepository customerUserRepository;
    private final IPasswordEncoder passwordEncoder;

    @Override
    public AuthenticatedIdentity authenticate(String loginName, String password) {
        if (StringUtils.isBlank(loginName) || StringUtils.isBlank(password)) {
            return null;
        }
        CustomerUserEntity customer = customerUserRepository
                .findByLoginName(loginName.trim().toLowerCase(Locale.ROOT)).orElse(null);
        if (!isUsable(customer) || !passwordEncoder.matches(password, customer.getPasswordHash())) {
            return null;
        }
        customerUserRepository.updateLastLoginAt(customer.getId());
        return identity(customer);
    }

    @Override
    public AuthenticatedIdentity reload(Long customerId) {
        if (customerId == null || customerId <= 0) {
            return null;
        }
        CustomerUserEntity customer = customerUserRepository.findById(customerId).orElse(null);
        return isUsable(customer) ? identity(customer) : null;
    }

    private AuthenticatedIdentity identity(CustomerUserEntity customer) {
        return new AuthenticatedIdentity(customer.getId(), null, customer.getLoginName(),
                AuthUserTypes.CUSTOMER, List.of(), List.of());
    }

    private boolean isUsable(CustomerUserEntity customer) {
        return customer != null && Boolean.TRUE.equals(customer.getStatus())
                && !Boolean.TRUE.equals(customer.getDeleted());
    }
}
