package com.c4soft.resthero.customer.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.c4soft.resthero.commons.domain.Iban;
import com.c4soft.resthero.commons.domain.IbanStringMapperImpl;
import com.c4soft.resthero.customer.domain.Beneficiary;

/**
 * Envers records a deletion: a {@code DEL} revision ({@code revtype = 2}) whose business columns
 * are {@code null} (only the id is stored). The audit table must accept those nulls, or the
 * deletion itself fails.
 *
 * <p>
 * Not in a test transaction ({@code NOT_SUPPORTED}): Envers writes the audit rows when a
 * transaction commits, so each repository call must commit on its own.
 * </p>
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@ActiveProfiles("h2")
@Import({IbanStringMapperImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BeneficiaryRepositoryTest {

  @Autowired
  BeneficiaryRepository repo;

  @Autowired
  JdbcTemplate jdbc;

  @AfterEach
  void cleanUp() {
    repo.deleteAll();
  }

  @Test
  void givenASavedBeneficiary_whenDeleted_thenADeleteRevisionIsRecorded() {
    final var saved = repo.save(Beneficiary.of("customer-del", Iban.of("FR761111222233334444"), "Gone"));

    repo.delete(saved);

    final var deletions = jdbc
        .queryForObject(
            "select count(*) from beneficiaries_aud where id = ? and revtype = 2",
            Integer.class,
            saved.getId());
    assertThat(deletions).isEqualTo(1);
    assertThat(repo.findById(saved.getId())).isEmpty();
  }
}
