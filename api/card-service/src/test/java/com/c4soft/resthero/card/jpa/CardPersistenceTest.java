package com.c4soft.resthero.card.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.c4soft.resthero.card.domain.Card;
import com.c4soft.resthero.commons.domain.Iban;
import com.c4soft.resthero.commons.domain.IbanStringMapperImpl;

/**
 * The schema matches the entity, a card comes back as it was saved, and each update increments its
 * version.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@ActiveProfiles("h2")
@Import({IbanStringMapperImpl.class})
class CardPersistenceTest {

  @Autowired
  JpaCardRepository repo;

  @Autowired
  TestEntityManager em;

  @Test
  void givenACard_whenSavedAndReloaded_thenItsValuesAreKept() {
    repo.saveAndFlush(Card.create("4111-1", Iban.of("FR761111222233334444"), ceilings(1000, 5000)));
    em.clear();

    final var reloaded = repo.findById("4111-1").orElseThrow();

    assertThat(reloaded.getIban()).isEqualTo(Iban.of("FR761111222233334444"));
    assertThat(reloaded.getCeilings()).isEqualTo(ceilings(1000, 5000));
    assertThat(reloaded.isActive()).isFalse();
    assertThat(reloaded.getVersion()).isZero();
  }

  @Test
  void givenASavedCard_whenItsCeilingsChange_thenItsVersionIsIncremented() {
    final var saved = repo.saveAndFlush(Card.create("4111-2", Iban.of("FR761111222233334444"), ceilings(1000, 5000)));

    saved.changeCeilings(ceilings(2000, 8000));
    repo.saveAndFlush(saved);
    em.clear();

    final var reloaded = repo.findById("4111-2").orElseThrow();
    assertThat(reloaded.getCeilings()).isEqualTo(ceilings(2000, 8000));
    assertThat(reloaded.getVersion()).isEqualTo(1L);
  }

  private static Card.Ceilings ceilings(int transaction, int rolling30) {
    return Card.Ceilings.builder().transaction(transaction).rolling30(rolling30).build();
  }
}
