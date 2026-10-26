package com.c4soft.resthero.card.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import com.c4soft.resthero.card.domain.Card;
import com.c4soft.resthero.card.domain.CardPayment;
import com.c4soft.resthero.commons.domain.Amount;
import com.c4soft.resthero.commons.domain.Currency;
import com.c4soft.resthero.commons.domain.Iban;
import com.c4soft.resthero.commons.domain.IbanStringMapperImpl;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@ActiveProfiles("h2")
@Import({IbanStringMapperImpl.class})
class CardPaymentRepositoryTest {

  @Autowired
  CardPaymentRepository paymentRepo;

  @Autowired
  JpaCardRepository cardRepo;

  Iban iban = Iban.of("FR761111222233334444");

  @SuppressWarnings("null")
  Card card1, card2;

  @SuppressWarnings("null")
  CardPayment payment1, payment2, payment3;

  @BeforeEach
  @SuppressWarnings("null")
  void setUp() {
    card1 = cardRepo
        .save(
            Card
                .create(
                    "1111-1111-1111-1111-1",
                    iban,
                    Card.Ceilings.builder().rolling30(300000).transaction(100000).build()));
    card2 = cardRepo
        .save(
            Card
                .create(
                    "2222-2222-2222-2222-2",
                    iban,
                    Card.Ceilings.builder().rolling30(500000).transaction(150000).build()));

    payment1 = paymentRepo
        .save(
            CardPayment
                .builder()
                .card(card1)
                .amount(new Amount(120000, Currency.XPF))
                .destinationIban(Iban.of("FR7622222222222222222"))
                .accepted(false)
                .timestamp(Instant.parse("2026-01-01T00:01:10Z"))
                .build());
    payment2 = paymentRepo
        .save(
            CardPayment
                .builder()
                .card(card1)
                .amount(new Amount(120000, Currency.XPF))
                .destinationIban(Iban.of("FR7622222222222222222"))
                .accepted(false)
                .timestamp(Instant.parse("2026-01-01T00:01:30Z"))
                .build());
    payment3 = paymentRepo
        .save(
            CardPayment
                .builder()
                .card(card2)
                .amount(new Amount(120000, Currency.XPF))
                .destinationIban(Iban.of("FR7622222222222222222"))
                .timestamp(Instant.parse("2026-01-01T00:02:30Z"))
                .accepted(true)
                .build());
  }

  @Test
  void whenFindByCardNumber_thenFiltered() {
    final var actual = paymentRepo.findByCardNumber("1111-1111-1111-1111-1", PageRequest.of(0, 10));
    assertThat(actual.getContent()).containsExactlyInAnyOrder(payment1, payment2);
  }

  @Test
  @SuppressWarnings("null")
  void whenFindByCardNumberAndTimestampBetween_thenFiltered() {
    final var actual = paymentRepo
        .findByCardNumberAndTimestampBetween(
            "1111-1111-1111-1111-1",
            Instant.parse("2026-01-01T00:01:00Z"),
            Instant.parse("2026-01-01T00:01:15Z"));
    assertThat(actual).containsExactlyInAnyOrder(payment1);

  }
}
