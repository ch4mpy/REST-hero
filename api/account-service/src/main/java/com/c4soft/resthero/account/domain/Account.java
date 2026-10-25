package com.c4soft.resthero.account.domain;

import java.io.Serializable;
import org.hibernate.envers.Audited;
import org.jspecify.annotations.Nullable;
import com.c4soft.resthero.commons.domain.Amount;
import com.c4soft.resthero.commons.domain.Currency;
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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * A bank account. No setter: its balance changes through {@link #credit(Amount)} and
 * {@link #debit(Amount)}, which keep its invariant (one currency per account).
 */
@Audited
@Entity
@Table(name = "accounts")
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Account implements Serializable {
  private static final long serialVersionUID = 6312459872190334512L;

  @Id
  // LAB:3.2:REMOVE:START
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "accountSeq")
  @SequenceGenerator(name = "accountSeq", sequenceName = "accounts_seq", allocationSize = 1)
  // LAB:3.2:REMOVE:END
  @EqualsAndHashCode.Include
  @ToString.Include
  private @Nullable Long id;

  /** Two concurrent updates of the same account: the second one fails, it never overwrites. */
  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "iban", unique = true, nullable = false)
  @Convert(converter = IbanStringAttributeConverter.class)
  @EqualsAndHashCode.Include
  @ToString.Include
  private Iban iban;

  @Column(name = "customer_id", nullable = false)
  @ToString.Include
  private String customerId;

  @Embedded
  private Amount balance;

  /**
   * @param amount
   * @throws IllegalArgumentException if the amount currency is different from the account currency
   */
  public void credit(Amount amount) throws IllegalArgumentException {
    if (!balance.getCurrency().equals(amount.getCurrency())) {
      throw new IllegalArgumentException("Cannot credit an amount with a different currency");
    }
    balance = new Amount(balance.getDigits() + amount.getDigits(), balance.getCurrency());
  }

  /**
   * @param amount
   * @throws IllegalArgumentException if the amount currency is different from the account currency
   */
  public void debit(Amount amount) throws IllegalArgumentException {
    if (!balance.getCurrency().equals(amount.getCurrency())) {
      throw new IllegalArgumentException("Cannot debit an amount with a different currency");
    }
    balance = new Amount(balance.getDigits() - amount.getDigits(), balance.getCurrency());
  }

  public static Account create(Iban iban, String customerId, Currency currency) {
    return Account.builder().iban(iban).customerId(customerId).balance(Amount.zero(currency)).build();
  }
}
