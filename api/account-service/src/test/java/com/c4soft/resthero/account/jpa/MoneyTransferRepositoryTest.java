package com.c4soft.resthero.account.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.c4soft.resthero.account.domain.MoneyTransfer;
import com.c4soft.resthero.account.domain.MoneyTransferFilteringCriteria;
import com.c4soft.resthero.account.jpa.MoneyTransferRepository;
import com.c4soft.resthero.commons.domain.Amount;
import com.c4soft.resthero.commons.domain.Currency;
import com.c4soft.resthero.commons.domain.Iban;
import com.c4soft.resthero.commons.domain.IbanStringMapperImpl;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@ActiveProfiles("h2")
@Import({IbanStringMapperImpl.class})
class MoneyTransferRepositoryTest {

  @Autowired
  MoneyTransferRepository moneyTransferJpaRepository;

  MoneyTransfer transfer1, transfer2, transfer3;

  @BeforeEach
  void setUp() {
    transfer1 = moneyTransferJpaRepository.save(
        MoneyTransfer.of(
            Iban.of("FR76 111222333"),
            Iban.of("FR76 444555666"),
            new Amount(1000, Currency.XPF),
            "Test transfer 1000 XPF",
            Instant.parse("2026-01-01T12:34:56Z")));

    transfer2 = moneyTransferJpaRepository.save(MoneyTransfer.of(
        Iban.of("FR76 123456789"),
        Iban.of("FR76 987654321"),
        new Amount(2000, Currency.EUR),
        "Test transfer 20 EUR",
        Instant.parse("2026-02-01T12:34:56Z")));

    transfer3 = moneyTransferJpaRepository.save(MoneyTransfer.of(
        Iban.of("FR76 123456789"),
        Iban.of("FR76 444555666"),
        new Amount(3000, Currency.AUD),
        "Test transfer 3 AUD",
        Instant.parse("2026-03-01T12:34:56Z")));
  }

  private static void pauseBetweenTransfers() {
    try {
      Thread.sleep(10);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  @Test
  void whenCriteriaOnFromAccount_thenTransfersFiltered() {
    var actual = moneyTransferJpaRepository
        .findAll(
            MoneyTransferRepository
                .searchSpec(
                    new MoneyTransferFilteringCriteria(
                        Iban.of("FR76 123456789"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)));
    assertThat(actual).hasSize(2);
    assertThat(
        actual.stream().allMatch(t -> Objects.equals(Iban.of("FR76 123456789"), t.getSourceIban())))
        .isTrue();
    assertThat(
        actual
            .stream()
            .anyMatch(t -> Objects.equals(Iban.of("FR76 987654321"), t.getDestinationIban())))
        .isTrue();
    assertThat(
        actual
            .stream()
            .anyMatch(t -> Objects.equals(Iban.of("FR76 444555666"), t.getDestinationIban())))
        .isTrue();
  }

  @Test
  void whenCriteriaOnToAccount_thenTransfersFiltered() {
    var actual = moneyTransferJpaRepository
        .findAll(
            MoneyTransferRepository
                .searchSpec(
                    new MoneyTransferFilteringCriteria(
                        null,
                        Iban.of("FR76 444555666"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)));
    assertThat(actual).hasSize(2);
    assertThat(
        actual
            .stream()
            .allMatch(t -> Objects.equals(Iban.of("FR76 444555666"), t.getDestinationIban())))
        .isTrue();
    assertThat(
        actual.stream().anyMatch(t -> Objects.equals(Iban.of("FR76 111222333"), t.getSourceIban())))
        .isTrue();
    assertThat(
        actual.stream().anyMatch(t -> Objects.equals(Iban.of("FR76 123456789"), t.getSourceIban())))
        .isTrue();
  }

  @Test
  void whenCriteriaOnMinAmount_thenTransfersFiltered() {
    var actual = moneyTransferJpaRepository
        .findAll(
            MoneyTransferRepository
                .searchSpec(
                    new MoneyTransferFilteringCriteria(
                        null,
                        null,
                        2000,
                        null,
                        null,
                        null,
                        null,
                        null)));
    assertThat(actual).hasSize(2);
    assertThat(actual.stream().anyMatch(t -> Objects.equals(2000, t.getAmount().getDigits())))
        .isTrue();
    assertThat(actual.stream().anyMatch(t -> Objects.equals(3000, t.getAmount().getDigits())))
        .isTrue();
  }

  @Test
  void whenCriteriaOnMaxAmount_thenTransfersFiltered() {
    var actual = moneyTransferJpaRepository
        .findAll(
            MoneyTransferRepository
                .searchSpec(
                    new MoneyTransferFilteringCriteria(
                        null,
                        null,
                        null,
                        2000,
                        null,
                        null,
                        null,
                        null)));
    assertThat(actual).hasSize(2);
    assertThat(actual.stream().anyMatch(t -> Objects.equals(1000, t.getAmount().getDigits())))
        .isTrue();
    assertThat(actual.stream().anyMatch(t -> Objects.equals(2000, t.getAmount().getDigits())))
        .isTrue();
  }

  @Test
  void whenCriteriaOnCurrency_thenTransfersFiltered() {
    var actual = moneyTransferJpaRepository
        .findAll(
            MoneyTransferRepository
                .searchSpec(
                    new MoneyTransferFilteringCriteria(
                        null,
                        null,
                        null,
                        null,
                        Currency.XPF,
                        null,
                        null,
                        null)));
    assertThat(actual).hasSize(1);
    assertThat(
        actual.stream().allMatch(t -> Objects.equals(Currency.XPF, t.getAmount().getCurrency())))
        .isTrue();
  }

  @Test
  void whenCriteriaOnMinTimestamp_thenTransfersFiltered() {
    final var from = transfer2.getTimestamp();
    var actual = moneyTransferJpaRepository
        .findAll(
            MoneyTransferRepository
                .searchSpec(
                    new MoneyTransferFilteringCriteria(
                        null,
                        null,
                        null,
                        null,
                        null,
                        from,
                        null,
                        null)));
    assertThat(actual).hasSize(2);
    assertThat(actual.stream().allMatch(t -> !t.getTimestamp().isBefore(from))).isTrue();
  }

  @Test
  void whenCriteriaOnMaxTimestamp_thenTransfersFiltered() {
    final var to = transfer2.getTimestamp();
    var actual = moneyTransferJpaRepository
        .findAll(
            MoneyTransferRepository
                .searchSpec(
                    new MoneyTransferFilteringCriteria(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        to,
                        null)));
    assertThat(actual).hasSize(2);
    assertThat(actual.stream().allMatch(t -> !t.getTimestamp().isAfter(to))).isTrue();
  }

  @Test
  void whenCriteriaOnLabel_thenTransfersFiltered() {
    var actual = moneyTransferJpaRepository
        .findAll(
            MoneyTransferRepository
                .searchSpec(
                    new MoneyTransferFilteringCriteria(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "3")));
    assertThat(actual).hasSize(1);
    assertThat(actual.stream().allMatch(t -> t.getLabel().contains("3"))).isTrue();
  }

  @Test
  void whenAllCriteriaSet_thenTransfersFiltered() {
    var instant = transfer1.getTimestamp();
    var actual = moneyTransferJpaRepository
        .findAll(
            MoneyTransferRepository
                .searchSpec(
                    new MoneyTransferFilteringCriteria(
                        transfer1.getSourceIban(),
                        transfer1.getDestinationIban(),
                        transfer1.getAmount().getDigits(),
                        transfer1.getAmount().getDigits(),
                        transfer1.getAmount().getCurrency(),
                        transfer1.getTimestamp(),
                        transfer1.getTimestamp(),
                        "%d".formatted(transfer1.getAmount().getDigits()))));
    assertThat(actual).hasSize(1);
  }

}
