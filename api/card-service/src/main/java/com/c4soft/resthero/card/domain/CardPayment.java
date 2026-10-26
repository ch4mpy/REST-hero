package com.c4soft.resthero.card.domain;

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
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * A payment made with a card: created not accepted, then {@link #accept() accepted} once the money
 * is transferred. Equal by identifier: two payments of the same amount are two payments.
 */
@Audited
@Entity
@Table(name = "payments")
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CardPayment implements Serializable {
  private static final long serialVersionUID = 1958362047712290318L;

  @Id
  @GeneratedValue(generator = "cardPaymentSeq")
  @SequenceGenerator(name = "cardPaymentSeq", sequenceName = "payment_seq", allocationSize = 1)
  @EqualsAndHashCode.Include
  @ToString.Include
  private @Nullable Long id;

  @Column(name = "timestamp", nullable = false)
  @Builder.Default
  private Instant timestamp = Instant.now();

  @Embedded
  private Amount amount;

  // LAB:3.3:REMOVE:START
  @ManyToOne
  @JoinColumn(name = "card_number", nullable = false, updatable = false)
  // LAB:3.3:REMOVE:END
  private Card card;

  @Column(name = "destination_iban", nullable = false)
  @Convert(converter = IbanStringAttributeConverter.class)
  @ToString.Include
  private Iban destinationIban;

  @Column(name = "accepted", nullable = false)
  @Builder.Default
  @ToString.Include
  private boolean accepted = false;

  /** The money is transferred: the payment counts toward the card's rolling ceiling. */
  public void accept() {
    this.accepted = true;
  }
}
