package com.c4soft.resthero.card.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.c4soft.resthero.card.domain.Card;
import com.c4soft.resthero.card.domain.CardPayment;
import com.c4soft.resthero.commons.domain.Amount;
import com.c4soft.resthero.commons.domain.Currency;
import com.c4soft.resthero.commons.domain.Iban;
import com.c4soft.resthero.commons.domain.IbanStringMapperImpl;

/** The schema matches the entity, and a payment's acceptance is persisted. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@ActiveProfiles("h2")
@Import({IbanStringMapperImpl.class})
class CardPaymentPersistenceTest {

  @Autowired
  CardPaymentRepository paymentRepo;

  @Autowired
  JpaCardRepository cardRepo;

  @Autowired
  TestEntityManager em;

  @Test
  void givenAPayment_whenAcceptedAndReloaded_thenItIsAccepted() {
    final var card = cardRepo
        .saveAndFlush(
            Card
                .create(
                    "4111-3",
                    Iban.of("FR761111222233334444"),
                    Card.Ceilings.builder().transaction(1000).rolling30(5000).build()));
    final var payment = paymentRepo
        .saveAndFlush(
            CardPayment
                .builder()
                .card(card)
                .amount(new Amount(900, Currency.XPF))
                .destinationIban(Iban.of("FR762222333344445555"))
                .build());
    assertThat(payment.isAccepted()).isFalse();

    payment.accept();
    paymentRepo.saveAndFlush(payment);
    em.clear();

    final var reloaded = paymentRepo.findById(payment.getId()).orElseThrow();
    assertThat(reloaded.isAccepted()).isTrue();
    assertThat(reloaded.getAmount()).isEqualTo(new Amount(900, Currency.XPF));
    assertThat(reloaded.getCard().getNumber()).isEqualTo("4111-3");
  }
}
