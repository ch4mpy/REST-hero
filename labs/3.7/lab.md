# TP 3.7 — Spécifications JPA

> Support de cours : [Spécifications JPA](README.md#jpa-specifications)

## Objectifs

À l'issue de ce TP, le stagiaire doit savoir brancher un repository sur `JpaSpecificationExecutor`, utiliser une
factory de spécification pour encapsuler des critères optionnels, et comprendre pourquoi les filtres complexes ne
sont pas toujours bien servis par les query methods.

## Consignes

Lancer `./start-lab.sh 3.7` pour créer la branche `lab/3.7`.

1. Lancer `mvn -pl account-service -am compile`. Constater l'échec de compilation dans `MoneyTransferController.java`
	: la méthode `findAll(specification, pageable)` n'est plus disponible.
2. Retrouver dans `api/account-service/src/main/java/com/c4soft/resthero/account/jpa/MoneyTransferRepository.java` le
	repère `LAB:3.7` sur l'héritage de l'interface et le reconstruire.
3. Relancer la même commande et vérifier que le contrôleur recompile. Expliquer le rôle de la factory
	`searchSpec(MoneyTransferFilteringCriteria)` et la manière dont elle assemble les critères optionnels.
4. Ouvrir aussi `MoneyTransferRepositoryTest` et repérer comment la spécification est validée champ par champ.
