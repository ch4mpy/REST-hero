package com.c4soft.resthero.account.domain;

import java.io.Serializable;
import java.time.Instant;
import org.hibernate.envers.Audited;
import org.jspecify.annotations.Nullable;
import com.c4soft.resthero.commons.domain.Amount;
import com.c4soft.resthero.commons.domain.Iban;
import com.c4soft.resthero.commons.jpa.IbanStringAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** A transfer between two accounts: written once, never changed (no {@code @Version}). */
@Audited
@Entity
@Table(name = "transfers")
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@Builder(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class MoneyTransfer implements Serializable {
  private static final long serialVersionUID = -2914371587330456127L;

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "transferSeq")
  @SequenceGenerator(name = "transferSeq", sequenceName = "transfers_seq", allocationSize = 1)
  @EqualsAndHashCode.Include
  @ToString.Include
  private @Nullable Long id;

  @Column(name = "source_iban", nullable = false)
  @ToString.Include
  @Convert(converter = IbanStringAttributeConverter.class)
  private Iban sourceIban;

  @Column(name = "destination_iban", nullable = false)
  @ToString.Include
  @Convert(converter = IbanStringAttributeConverter.class)
  private Iban destinationIban;

  @Embedded
  private Amount amount;

  @Column(name = "timestamp", nullable = false)
  @Builder.Default
  private Instant timestamp = Instant.now();

  @Column(name = "label", nullable = false)
  private String label;

  public static MoneyTransfer of(
      Iban sourceIban,
      Iban destinationIban,
      Amount amount,
      String label) {
    return of(sourceIban, destinationIban, amount, label, Instant.now());
  }

  public static MoneyTransfer of(
      Iban sourceIban,
      Iban destinationIban,
      Amount amount,
      String label,
      Instant timestamp) {
    return MoneyTransfer
        .builder()
        .sourceIban(sourceIban)
        .destinationIban(destinationIban)
        .amount(amount)
        .label(label)
        .timestamp(timestamp)
        .build();
  }
}
