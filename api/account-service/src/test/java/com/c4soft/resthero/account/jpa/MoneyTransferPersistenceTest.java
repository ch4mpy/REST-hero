package com.c4soft.resthero.account.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.c4soft.resthero.account.domain.MoneyTransfer;
import com.c4soft.resthero.commons.domain.Amount;
import com.c4soft.resthero.commons.domain.Currency;
import com.c4soft.resthero.commons.domain.Iban;
import com.c4soft.resthero.commons.domain.IbanStringMapperImpl;

/** The schema matches the entity, and a transfer comes back as it was saved. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@ActiveProfiles("h2")
@Import({IbanStringMapperImpl.class})
class MoneyTransferPersistenceTest {

  @Autowired
  MoneyTransferRepository repo;

  @Autowired
  TestEntityManager em;

  @Test
  void givenATransfer_whenSavedAndReloaded_thenItsValuesAreKept() {
    final var at = Instant.parse("2026-10-24T08:00:00Z");
    final var saved = repo
        .saveAndFlush(
            MoneyTransfer
                .of(
                    Iban.of("FR761111222233334444"),
                    Iban.of("FR762222333344445555"),
                    new Amount(4200, Currency.XPF),
                    "Rent",
                    at));
    em.clear();

    final var reloaded = repo.findById(saved.getId()).orElseThrow();

    assertThat(reloaded.getSourceIban()).isEqualTo(Iban.of("FR761111222233334444"));
    assertThat(reloaded.getDestinationIban()).isEqualTo(Iban.of("FR762222333344445555"));
    assertThat(reloaded.getAmount()).isEqualTo(new Amount(4200, Currency.XPF));
    assertThat(reloaded.getLabel()).isEqualTo("Rent");
    assertThat(reloaded.getTimestamp()).isEqualTo(at);
  }
}
