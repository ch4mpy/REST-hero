package com.c4soft.resthero.account.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.c4soft.resthero.account.domain.Account;
import com.c4soft.resthero.commons.domain.Amount;
import com.c4soft.resthero.commons.domain.Currency;
import com.c4soft.resthero.commons.domain.Iban;
import com.c4soft.resthero.commons.domain.IbanStringMapperImpl;

/**
 * The schema Liquibase builds is the one the entity maps ({@code ddl-auto=validate} fails the
 * context otherwise), an account comes back as it was saved, and each update increments its
 * version.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@ActiveProfiles("h2")
@Import({IbanStringMapperImpl.class})
class AccountPersistenceTest {

  @Autowired
  JpaAccountRepository repo;

  @Autowired
  TestEntityManager em;

  @Test
  void givenAnAccount_whenSavedAndReloaded_thenItsValuesAreKept() {
    final var account = Account.create(Iban.of("FR761111222233334444"), "customer-1", Currency.EUR);
    account.credit(new Amount(1500, Currency.EUR));
    final var saved = repo.saveAndFlush(account);
    em.clear();

    final var reloaded = repo.findById(saved.getId()).orElseThrow();

    assertThat(reloaded.getIban()).isEqualTo(Iban.of("FR761111222233334444"));
    assertThat(reloaded.getCustomerId()).isEqualTo("customer-1");
    assertThat(reloaded.getBalance()).isEqualTo(new Amount(1500, Currency.EUR));
    assertThat(reloaded.getVersion()).isZero();
  }

  @Test
  void givenASavedAccount_whenCredited_thenItsVersionIsIncremented() {
    final var saved = repo.saveAndFlush(Account.create(Iban.of("FR761111222233334445"), "customer-1", Currency.EUR));

    saved.credit(new Amount(100, Currency.EUR));
    repo.saveAndFlush(saved);
    em.clear();

    assertThat(repo.findById(saved.getId()).orElseThrow().getVersion()).isEqualTo(1L);
  }
}
