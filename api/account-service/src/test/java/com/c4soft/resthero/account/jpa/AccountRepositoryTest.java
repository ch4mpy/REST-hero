package com.c4soft.resthero.account.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.c4soft.resthero.account.AccountFixtures;
import com.c4soft.resthero.account.CacheConfiguration;
import com.c4soft.resthero.account.domain.Account;
import com.c4soft.resthero.account.jpa.AccountRepository;
import com.c4soft.resthero.account.jpa.JpaAccountRepository;
import com.c4soft.resthero.commons.domain.Amount;
import com.c4soft.resthero.commons.domain.Currency;
import com.c4soft.resthero.commons.domain.Iban;

@SpringBootTest(classes = {CacheConfiguration.class, AccountRepository.class})
@ActiveProfiles("h2")
class AccountRepositoryTest {

  @MockitoBean
  JpaAccountRepository jpaAccountRepo;

  @Autowired
  AccountRepository accountRepo;

  Map<String, Account> accountDatabase = new HashMap<>();

  Account customerXpfAccount;

  @BeforeEach
  void setUp() {
    customerXpfAccount = AccountFixtures.createCustomersXpfAccount(100000);

    final var accounts = new ConcurrentHashMap<Iban, Account>();
    accounts.put(customerXpfAccount.getIban(), customerXpfAccount);

    when(jpaAccountRepo.existsByIban(any(Iban.class)))
        .thenAnswer(invocation -> accounts.containsKey(invocation.getArgument(0, Iban.class)));
    when(jpaAccountRepo.findByIban(any(Iban.class)))
        .thenAnswer(
            invocation -> Optional.ofNullable(accounts.get(invocation.getArgument(0, Iban.class))));
    when(jpaAccountRepo.findByCustomerId(anyString()))
        .thenAnswer(
            invocation -> accounts
                .values()
                .stream()
                .filter(a -> a.getCustomerId().equals(invocation.getArgument(0)))
                .toList());
    when(jpaAccountRepo.save(any(Account.class))).thenAnswer(invocation -> {
      Account account = invocation.getArgument(0);
      accounts.put(account.getIban(), account);
      return account;
    });
  }

  /** The path variable's converter reads past the cache: each call reaches the database. */
  @Test
  @DirtiesContext
  void whenFindStoredByIbanTwice_thenTheDatabaseIsReadTwiceAndNothingIsCached() {
    final var iban = customerXpfAccount.getIban();
    accountRepo.findStoredByIban(iban);
    accountRepo.findStoredByIban(iban);

    verify(jpaAccountRepo, times(2)).findByIban(iban);
    accountRepo.findByIban(iban);
    verify(jpaAccountRepo, times(3)).findByIban(iban);
  }

  @Test
  @DirtiesContext
  // prevent cache operation conflict between tests
  void givenFindByIdCalledTwiceWithSameIban_whenSaveAccountWithSameIbanAndCallFindByIdAgain_thenCacheUpdatedAndFindByIdCalledOnlyOnceOverall() {
    // accountService.findById called twice, but underlying jpaAccountRepo.findById should be
    // called only once.
    var actual = accountRepo.findByIban(customerXpfAccount.getIban());
    var actual2 = accountRepo.findByIban(customerXpfAccount.getIban());
    assertEquals(100000L, actual.get().getBalance().getDigits());
    assertEquals(100000L, actual2.get().getBalance().getDigits());

    // save a new Account instance with the same iban and a different balance
    // (do not work with a reference to the instance already in the cache)
    var updatedAccount =
        Account.create(
            customerXpfAccount.getIban(), customerXpfAccount.getCustomerId(), Currency.XPF);
    updatedAccount.credit(new Amount(200000, Currency.XPF));

    var account = accountRepo.save(updatedAccount);
    assertEquals(200000L, account.getBalance().getDigits());

    // retrieve the account from the cache to verify that it was updated when saving the new
    // instance
    var actual3 = accountRepo.findByIban(customerXpfAccount.getIban());
    assertEquals(200000L, actual3.get().getBalance().getDigits());

    // only the 1st call to accountService.findById should delegate to jpaAccountRepo.findById
    // (save should @CachePut here, not @CacheEvict)
    verify(jpaAccountRepo, times(1)).findByIban(customerXpfAccount.getIban());
  }

  @Test
  @DirtiesContext
  // prevent cache operation conflict between tests
  void givenExistsByIdReturnedFalseBeforeSavingAccount_whenSaveAccountWithSameIbanAndCallExistsByIdAgain_thenCacheEvictedAndExistsByIdCalledOnlyTwiceOverall() {
    var customerEurAccount = AccountFixtures.createCustomersEurAccount(200000);

    // accountService.existsById called twice, but underlying jpaAccountRepo.existsById should be
    // called only once.
    var actual = accountRepo.existsByIban(customerEurAccount.getIban());
    assertFalse(actual);


    // save a new Account instance with the same iban
    accountRepo.save(customerEurAccount);

    // call existsById again to verify that the cache was evicted when saving
    var actual2 = accountRepo.existsByIban(customerEurAccount.getIban());
    assertTrue(actual2);

    // only the 1st and 3rd calls to accountService.existsById should delegate to
    // jpaAccountRepo.existsById (save should @CacheEvict here)
    verify(jpaAccountRepo, times(2)).existsByIban(customerEurAccount.getIban());
  }

  @Test
  @DirtiesContext
  // prevent cache operation conflict between tests
  void givenFindByCustomerIdCalledTwiceWithSameCustomerId_whenSaveAccountWithSameCustomerIdAndCallFindByCustomerIdAgain_thenCacheEvictedAndFindByCustomerIdCalledOnlyTwiceOverall() {
    var customerEurAccount = AccountFixtures.createCustomersEurAccount(200000);

    assertEquals(customerXpfAccount.getCustomerId(), customerEurAccount.getCustomerId());

    // accountService.findByCustomerId called twice, but underlying
    // jpaAccountRepo.findByCustomerId should be called only once.
    var actual = accountRepo.findByCustomerId(customerXpfAccount.getCustomerId());
    actual = accountRepo.findByCustomerId(customerXpfAccount.getCustomerId());
    assertEquals(1, actual.size());

    // save a new Account instance with the same customerId
    accountRepo.save(customerEurAccount);

    // retrieve the accounts from the cache to verify that it was evicted when saving the new
    // instance
    var actual3 = accountRepo.findByCustomerId(customerXpfAccount.getCustomerId());
    assertEquals(2, actual3.size());

    // only the 1st and 3rd calls to accountService.findByCustomerId should delegate to
    // jpaAccountRepo.findByCustomerId (save should @CacheEvict here)
    verify(jpaAccountRepo, times(2)).findByCustomerId(customerXpfAccount.getCustomerId());
  }

}
