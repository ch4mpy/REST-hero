# TP 3.2 — Identifiants générés

> Support de cours : [Identifiants générés](README.md#jpa-generated-ids)

## Objectifs

À l'issue de ce TP, le stagiaire doit savoir expliquer le rôle d'une séquence JPA, distinguer un identifiant fourni
par l'application d'un identifiant généré par la base, et comprendre pourquoi `@GeneratedValue` doit être combiné avec
un générateur explicite sur les bases utilisées dans ce projet.

## Consignes

Lancer `./start-lab.sh 3.2` pour créer la branche `lab/3.2`.

1. Lancer `mvn -pl account-service test -Dtest=AccountServiceApplicationTests -Dsurefire.failIfNoSpecifiedTests=false`.
	Constater l'échec lors de la sauvegarde d'un compte : l'identifiant `id` n'est plus généré automatiquement.
2. Retrouver dans `api/account-service/src/main/java/com/c4soft/resthero/account/domain/Account.java` le repère
	`LAB:3.2` sur les annotations du champ `id` et les reconstruire.
3. Relancer la même commande et vérifier que l'enregistrement d'un compte fonctionne à nouveau. Expliquer le rôle de la
	séquence `accounts_seq`, du `@Id` et de `@GeneratedValue`.
4. Repérer les autres entités du projet qui utilisent la même idée avec des séquences (`Beneficiary`, `CardPayment`,
	`Revinfo`) et expliquer pourquoi H2 et PostgreSQL suivent la même stratégie ici.
