/**
 *
 */
package com.c4soft.resthero.account;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import com.c4soft.resthero.account.jpa.AccountRepository;
import com.c4soft.resthero.commons.domain.Iban;
import lombok.RequiredArgsConstructor;

/**
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  public static final String ACCESS_CONTROL_BEAN_NAME = "ac";

  @Component(ACCESS_CONTROL_BEAN_NAME)
  @RequiredArgsConstructor
  public static class AccessControlHelper {
    private final AccountRepository accountRepo;

    public boolean isAccountOwner(@Nullable String customerId, @Nullable String ibanString) {
      return ibanString == null ? false
          : accountRepo
              .findByIban(Iban.of(ibanString))
              .map(account -> Objects.equals(customerId, account.getCustomerId()))
              .orElse(false);
    }

    public boolean ownsAccount(@Nullable String ibanString) {
      var auth = SecurityContextHolder.getContext().getAuthentication();
      return auth != null && isAccountOwner(auth.getName(), ibanString);
    }
  }
}
