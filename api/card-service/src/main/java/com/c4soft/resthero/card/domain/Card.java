package com.c4soft.resthero.card.domain;

import java.io.Serializable;
import org.hibernate.envers.Audited;
import com.c4soft.resthero.commons.domain.Iban;
import com.c4soft.resthero.commons.jpa.IbanStringAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
 * A payment card. No setter: it changes through {@link #activate()}, {@link #deactivate()} and
 * {@link #changeCeilings(Ceilings)}.
 */
@Audited
@Entity
@Table(name = "cards")
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@Builder(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Card implements Serializable {
  private static final long serialVersionUID = 4721839054617728391L;

  @Id
  @Column(name = "number")
  @EqualsAndHashCode.Include
  @ToString.Include
  private String number;

  /** Two concurrent updates of the same card: the second one fails, it never overwrites. */
  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "iban", nullable = false)
  @ToString.Include
  @Convert(converter = IbanStringAttributeConverter.class)
  private Iban iban;

  @Embedded
  private Ceilings ceilings;

  @Column(name = "active", nullable = false)
  @Builder.Default
  private boolean active = false;

  public void activate() {
    this.active = true;
  }

  public void deactivate() {
    this.active = false;
  }

  public void changeCeilings(Ceilings ceilings) {
    this.ceilings = ceilings;
  }

  public static Card create(String number, Iban iban, Ceilings ceilings) {
    return Card.builder().number(number).iban(iban).ceilings(ceilings).build();
  }

  /** A value object: replaced as a whole ({@link Card#changeCeilings(Ceilings)}), never edited. */
  @Embeddable
  @Getter
  @ToString
  @EqualsAndHashCode
  @Builder
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  @AllArgsConstructor(access = AccessLevel.PROTECTED)
  public static class Ceilings implements Serializable {
    private static final long serialVersionUID = -8102567734918264053L;

    @Column(name = "transaction_ceiling", nullable = false)
    private Integer transaction;

    @Column(name = "rolling30_ceiling", nullable = false)
    private Integer rolling30;
  }
}
