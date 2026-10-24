# TP 3.6 — JPA query methods

> Support de cours : [JPA query methods](README.md#jpa-query-dsl)

## Objectifs

À l'issue de ce TP, le stagiaire doit savoir reconnaître une méthode de requête dérivée par Spring Data, construire
son nom à partir des critères métier, et comprendre que l'implémentation est générée automatiquement à partir de la
signature.

## Consignes

Lancer `./start-lab.sh 3.6` pour créer la branche `lab/3.6`.

1. Lancer `mvn -pl card-service -am compile`. Constater l'échec de compilation dans `CardRepository.java` : la méthode
	de recherche par IBAN n'existe plus dans le repository JPA bas niveau.
2. Retrouver dans `api/card-service/src/main/java/com/c4soft/resthero/card/jpa/JpaCardRepository.java` le repère
	`LAB:3.6` sur la méthode de requête et la reconstruire.
3. Relancer la même commande et vérifier que `CardRepository` recompile. Expliquer pourquoi la seule signature
	`findByIban(Iban iban)` suffit à Spring Data pour générer l'implémentation.
4. Refaire le même raisonnement avec `findByCustomerId` et `findByCardNumberAndTimestampBetween` dans les autres
	repositories du projet.
