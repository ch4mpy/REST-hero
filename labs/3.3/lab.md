# TP 3.3 — Relations

> Support de cours : [Relations](README.md#jpa-relations)

## Objectifs

À l'issue de ce TP, le stagiaire doit savoir annoter une relation objet-relationnelle entre deux entités, expliquer
le rôle de `@ManyToOne` et `@JoinColumn`, et reconnaître un objet valeur embarqué via `@Embedded` / `@Embeddable`.

## Consignes

Lancer `./start-lab.sh 3.3` pour créer la branche `lab/3.3`.

1. Lancer `mvn -pl card-service test -Dtest=CardPaymentRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false`.
	Constater l'échec au moment de persister un paiement de carte : la relation vers la carte n'est plus modélisée
	comme une association JPA.
2. Retrouver dans `api/card-service/src/main/java/com/c4soft/resthero/card/domain/CardPayment.java` le repère
	`LAB:3.3` sur la relation `card` et la reconstruire.
3. Relancer la même commande et vérifier que le repository fonctionne à nouveau. Expliquer pourquoi `@ManyToOne`
	est accompagné de `@JoinColumn` dans ce cas précis.
4. Ouvrir aussi `Amount.java` et `Card.java` pour repérer les annotations `@Embeddable` et `@Embedded`, puis expliquer
	en quoi un objet valeur embarqué se distingue d'une relation vers une autre table.
