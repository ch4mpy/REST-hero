# TP 4.7 — Logs

> Support de cours : [Logs](README.md#rest-controller-logging)

## Objectifs

À l'issue de ce TP, le stagiaire doit savoir produire des logs d'audit cohérents sur les endpoints qui modifient
l'état de l'application, comprendre le rôle de `@Slf4j`, et savoir à quel endroit placer un log de synthèse pour une
opération REST `POST`, `PUT` ou `DELETE`.

## Consignes

Lancer `./start-lab.sh 4.7` pour créer la branche `lab/4.7`.

1. Lancer `mvn -pl customer-service test -Dtest=CustomerControllerTest -Dsurefire.failIfNoSpecifiedTests=false`.
	Observer la sortie console lors d'une création de client et constater qu'un log d'audit a disparu.
2. Retrouver dans `api/customer-service/src/main/java/com/c4soft/resthero/customer/web/CustomerController.java` le
	repère `LAB:4.7` sur le `log.info(...)` de création, et le reconstruire.
3. Relancer la même commande, ou déclencher manuellement une création de client, et vérifier que le résumé de l'action
	réapparaît au niveau `info`.
4. Expliquer pourquoi ces logs font partie du contrat fonctionnel du projet, au même titre que les réponses HTTP.
