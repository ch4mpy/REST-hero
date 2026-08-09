/**
 *
 */
package com.c4soft.resthero.card;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import com.c4soft.resthero.account.api.AccountsApi;
import com.c4soft.resthero.commons.domain.Iban;
import com.c4soft.resthero.commons.domain.Iban.NotAnIbanException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  public static final String ACCESS_CONTROL_BEAN_NAME = "ac";

  @Component(ACCESS_CONTROL_BEAN_NAME)
  @RequiredArgsConstructor
  @Slf4j
  public static class AccessControlHelper {
    private final AccountsApi accountsApi;

    public boolean isAccountOwner(@Nullable String customerId, @Nullable String ibanString) {
      if (ibanString == null) {
        return false;
      }
      try {
        Iban.of(ibanString);
      } catch (NotAnIbanException e) {
        log
            .warn(
                "Invalid IBAN {} while checking ownership for customer {}",
                ibanString,
                customerId);
        return false;
      }
      try {
        var account = accountsApi.getAccount(ibanString).getBody();
        log.debug("Account {} retrieved to check ownership of {}", account, customerId);
        return Objects.equals(customerId, account.getCustomerId());

      } catch (HttpClientErrorException e) {
        if (Objects.equals(HttpStatus.NOT_FOUND, e.getStatusCode())) {
          log
              .warn(
                  "Account {} not found while checking ownership for customer {}",
                  ibanString,
                  customerId);
        } else {
          log
              .error(
                  "Error while checking account {} ownership for customer {}",
                  ibanString,
                  customerId,
                  e);
        }

        return false;
      }
    }

    public boolean ownsAccount(@Nullable String ibanString) {
      var auth = SecurityContextHolder.getContext().getAuthentication();
      return auth != null && isAccountOwner(auth.getName(), ibanString);
    }
  }
}
