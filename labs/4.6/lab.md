# TP 4.6 — Appels de services REST externes

> Support de cours : [Appels de services REST externes](README.md#rest-controller-inter-service-communication)

## Objectifs

À l'issue de ce TP, le stagiaire doit savoir appeler un service REST distant depuis un contrôleur, comprendre le rôle
d'un client `@HttpExchange` exposé comme bean, et traduire les erreurs HTTP du service distant en problèmes de notre
contrat, par type d'exception.

## Consignes

Lancer `./start-lab.sh 4.6` pour créer la branche `lab/4.6`.

1. Lancer `mvn -pl account-service test -Dtest=AccountControllerTest -Dsurefire.failIfNoSpecifiedTests=false`.
	Constater qu'une création de compte pour un client inconnu ne se comporte plus comme prévu.
2. Retrouver dans `api/account-service/src/main/java/com/c4soft/resthero/account/web/AccountController.java` le repère
	`LAB:4.6` sur l'appel au `customer-service`, et reconstruire la ligne d'appel REST.
3. Relancer la même commande et vérifier que le contrôleur répond de nouveau `404` de type `customer-not-found` (avec le
	`customerId` en paramètre) pour un client inconnu. Expliquer pourquoi on intercepte
	`HttpClientErrorException.NotFound` puis `RestClientException`, dans cet ordre, ce que devient toute autre erreur du
	`customer-service` (`502`, `downstream-service-error`), et pourquoi la réponse du service aval ne doit jamais partir
	au client.
4. Repérer dans `CardController.java` et `MoneyTransferController.java` les autres appels REST inter-services du projet,
	et vérifier que chacun suit la même traduction.
