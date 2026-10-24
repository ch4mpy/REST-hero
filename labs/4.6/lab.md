# TP 4.6 — Appels de services REST externes

> Support de cours : [Appels de services REST externes](README.md#rest-controller-inter-service-communication)

## Objectifs

À l'issue de ce TP, le stagiaire doit savoir appeler un service REST distant depuis un contrôleur, comprendre le rôle
d'un client `@HttpExchange` exposé comme bean, et traduire proprement les erreurs HTTP du service distant en erreur
métier locale.

## Consignes

Lancer `./start-lab.sh 4.6` pour créer la branche `lab/4.6`.

1. Lancer `mvn -pl account-service test -Dtest=AccountControllerTest -Dsurefire.failIfNoSpecifiedTests=false`.
	Constater qu'une création de compte pour un client inconnu ne se comporte plus comme prévu.
2. Retrouver dans `api/account-service/src/main/java/com/c4soft/resthero/account/web/AccountController.java` le repère
	`LAB:4.6` sur l'appel au `customer-service`, et reconstruire la ligne d'appel REST.
3. Relancer la même commande et vérifier que le contrôleur rejette à nouveau proprement les erreurs clients inconnues.
	Expliquer pourquoi la traduction de `HttpClientErrorException` est importante quand on parle à un autre service.
4. Repérer dans `CardController.java` et `MoneyTransferController.java` les autres appels REST inter-services du projet.
