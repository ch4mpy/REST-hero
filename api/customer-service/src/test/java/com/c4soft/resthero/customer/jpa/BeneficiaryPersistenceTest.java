package com.c4soft.resthero.customer.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import com.c4soft.resthero.commons.domain.Iban;
import com.c4soft.resthero.commons.domain.IbanStringMapperImpl;
import com.c4soft.resthero.customer.domain.Beneficiary;

/**
 * The schema Liquibase builds is the one the entity maps ({@code ddl-auto=validate} fails the
 * context otherwise), and its unique constraints are those of the business rules: a customer can't
 * register the same IBAN or label twice, but two customers can each have a beneficiary with the
 * same IBAN or label.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@ActiveProfiles("h2")
@Import({IbanStringMapperImpl.class})
class BeneficiaryPersistenceTest {

  private static final Iban MUMS_IBAN = Iban.of("FR761111222233334444");

  private static final Iban DADS_IBAN = Iban.of("FR762222333344445555");

  @Autowired
  BeneficiaryRepository repo;

  @Autowired
  TestEntityManager em;

  @Test
  void givenABeneficiary_whenSavedAndReloaded_thenItsValuesAreKept() {
    final var saved = repo.saveAndFlush(Beneficiary.of("customer-1", MUMS_IBAN, "Mum"));
    em.clear();

    final var reloaded = repo.findById(saved.getId()).orElseThrow();

    assertThat(reloaded.getCustomerId()).isEqualTo("customer-1");
    assertThat(reloaded.getIban()).isEqualTo(MUMS_IBAN);
    assertThat(reloaded.getLabel()).isEqualTo("Mum");
  }

  @Test
  void givenTwoCustomers_whenEachRegistersTheSameLabelAndIban_thenBothAreSaved() {
    repo.saveAndFlush(Beneficiary.of("customer-1", MUMS_IBAN, "Mum"));
    repo.saveAndFlush(Beneficiary.of("customer-2", MUMS_IBAN, "Mum"));

    assertThat(repo.findByCustomerId("customer-1")).hasSize(1);
    assertThat(repo.findByCustomerId("customer-2")).hasSize(1);
  }

  @Test
  void givenACustomer_whenRegisteringTheSameLabelTwice_thenTheDatabaseRefuses() {
    repo.saveAndFlush(Beneficiary.of("customer-1", MUMS_IBAN, "Mum"));

    assertThatThrownBy(() -> repo.saveAndFlush(Beneficiary.of("customer-1", DADS_IBAN, "Mum")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void givenACustomer_whenRegisteringTheSameIbanTwice_thenTheDatabaseRefuses() {
    repo.saveAndFlush(Beneficiary.of("customer-1", MUMS_IBAN, "Mum"));

    assertThatThrownBy(() -> repo.saveAndFlush(Beneficiary.of("customer-1", MUMS_IBAN, "Mother")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
