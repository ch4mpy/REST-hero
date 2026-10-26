# TP 4.3 — Validation des entrées

> Support de cours : [Validation des entrées](README.md#rest-controller-validation)

## Objectifs

À l'issue de ce TP, le stagiaire doit savoir déclencher la validation Jakarta sur un body JSON, comprendre que les
contraintes sur les champs ne sont prises en compte qu'à condition d'activer la validation côté contrôleur, et savoir
où placer `@Valid` sur une signature Spring WebMvc.

## Consignes

Lancer `./start-lab.sh 4.3` pour créer la branche `lab/4.3`.

1. Lancer `mvn -pl account-service test -Dtest=MoneyTransferControllerTest -Dsurefire.failIfNoSpecifiedTests=false`.
	Constater que les requêtes contenant des données invalides ne sont plus rejetées comme attendu.
2. Retrouver dans `api/account-service/src/main/java/com/c4soft/resthero/account/web/MoneyTransferController.java` le
	repère `LAB:4.3` sur le paramètre de la méthode `transferMoneyBetweenAccounts(...)`, et reconstruire l'annotation
	`@Valid`.
3. Relancer la même commande et vérifier que les bodies invalides redonnent bien une erreur de validation.
	Expliquer pourquoi `@RequestBody` seul ne suffit pas à déclencher l'exécution des contraintes du record.
4. Ouvrir `MoneyTransferRequest.java` pour retrouver les contraintes elles-mêmes, puis distinguer les annotations de
	contrainte et l'annotation qui active leur vérification.
