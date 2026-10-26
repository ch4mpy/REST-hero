package com.c4soft.resthero.customer.domain;

import java.io.Serializable;
import org.hibernate.envers.Audited;
import org.jspecify.annotations.Nullable;
import com.c4soft.resthero.commons.domain.Iban;
import com.c4soft.resthero.commons.jpa.IbanStringAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * A customer's beneficiary. A customer can't register the same IBAN or label twice; two customers
 * can (the unique constraints are per customer). No setter: it changes through
 * {@link #update(Iban, String)}.
 */
@Audited
// LAB:3.1:REMOVE:START
@Entity
// LAB:3.1:REMOVE:END
@Table(name = "beneficiaries",
    uniqueConstraints = {
        @UniqueConstraint(name = "uc_beneficiaries_iban_customer_id",
            columnNames = {"iban", "customer_id"}),
        @UniqueConstraint(name = "uc_beneficiaries_label_customer_id",
            columnNames = {"label", "customer_id"})})
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@Builder(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Beneficiary implements Serializable {
  private static final long serialVersionUID = -3381960472216590834L;

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "beneficiarySeq")
  @SequenceGenerator(name = "beneficiarySeq", sequenceName = "beneficiaries_seq",
      allocationSize = 1)
  @EqualsAndHashCode.Include
  @ToString.Include
  private @Nullable Long id;

  /** Two concurrent updates of the same beneficiary: the second one fails, it never overwrites. */
  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "label", nullable = false)
  @ToString.Include
  private String label;

  @Column(name = "iban", nullable = false)
  @Convert(converter = IbanStringAttributeConverter.class)
  @ToString.Include
  private Iban iban;

  @Column(name = "customer_id", nullable = false)
  private String customerId;

  /** Its uniqueness among the customer's other beneficiaries is checked by the caller. */
  public void update(Iban iban, String label) {
    this.iban = iban;
    this.label = label;
  }

  public static Beneficiary of(String customerId, Iban iban, String label) {
    return Beneficiary.builder().label(label).iban(iban).customerId(customerId).build();
  }

}
