package com.c4soft.resthero.account.jpa;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.*;
import org.springframework.stereotype.Repository;
import com.c4soft.resthero.account.domain.Account;
import com.c4soft.resthero.commons.domain.Iban;
import java.util.List;
import java.util.Optional;

/**
 * Caching decorator for {@link JpaAccountRepository} to avoid hitting the database too often.
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Repository
@RequiredArgsConstructor
@CacheConfig(cacheNames = {AccountRepository.ACCOUNT_EXISTS_BY_IBAN_CACHE,
    AccountRepository.ACCOUNTS_BY_IBAN_CACHE, AccountRepository.ACCOUNTS_BY_CUSTOMER_ID_CACHE})
public class AccountRepository {
  static final String ACCOUNT_EXISTS_BY_IBAN_CACHE = "accountExistsByIban";
  static final String ACCOUNTS_BY_IBAN_CACHE = "accountsByIban";
  static final String ACCOUNTS_BY_CUSTOMER_ID_CACHE = "accountsByCustomerId";

  private final JpaAccountRepository jpaRepository;

  @Cacheable(cacheNames = ACCOUNT_EXISTS_BY_IBAN_CACHE, key = "#iban")
  public boolean existsByIban(Iban iban) {
    return jpaRepository.existsByIban(iban);
  }

  @Cacheable(cacheNames = ACCOUNTS_BY_IBAN_CACHE, key = "#iban")
  public Optional<Account> findByIban(Iban iban) {
    return jpaRepository.findByIban(iban);
  }

  /**
   * The account as stored, read past the cache: a request is authorized on the database's state (a
   * cached instance keeps what a rolled-back request changed in it), and an unknown IBAN leaves no
   * entry in the cache.
   */
  public Optional<Account> findStoredByIban(Iban iban) {
    return jpaRepository.findByIban(iban);
  }

  @Cacheable(cacheNames = ACCOUNTS_BY_CUSTOMER_ID_CACHE, key = "#customerId")
  public List<Account> findByCustomerId(String customerId) {
    return jpaRepository.findByCustomerId(customerId);
  }

  // LAB:5:TODO:START mettre à jour et invalider les caches lors d'une sauvegarde
  @Caching(put = @CachePut(cacheNames = ACCOUNTS_BY_IBAN_CACHE, key = "#account.iban"),
      evict = {@CacheEvict(cacheNames = ACCOUNT_EXISTS_BY_IBAN_CACHE, key = "#account.iban"),
          @CacheEvict(cacheNames = ACCOUNTS_BY_CUSTOMER_ID_CACHE, key = "#account.customerId")})
  // LAB:5:TODO:END
  public Account save(Account account) {
    return jpaRepository.save(account);
  }
}
