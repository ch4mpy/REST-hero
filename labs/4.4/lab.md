# TP 4.4 — Gestion des exceptions

> Support de cours : [Gestion des exceptions](README.md#rest-controller-exceptions)

## Objectifs

À l'issue de ce TP, le stagiaire doit savoir centraliser la traduction d'exceptions en réponses HTTP, reconnaître le
rôle d'un `@RestControllerAdvice`, et comprendre pourquoi supprimer ce niveau de traitement change la nature des
réponses renvoyées aux clients REST.

## Consignes

Lancer `./start-lab.sh 4.4` pour créer la branche `lab/4.4`.

1. Lancer `mvn -pl card-service test -Dtest=CardControllerTest -Dsurefire.failIfNoSpecifiedTests=false`.
	Constater que certains cas d'erreur ne reviennent plus avec le bon statut HTTP ni avec un `ProblemDetail`.
2. Retrouver dans `api/rest-hero-starter-common/src/main/java/com/c4soft/resthero/commons/exception/CommonExceptionsHandler.java`
	le repère `LAB:4.4` sur l'annotation de classe, et la reconstruire.
3. Relancer la même commande et vérifier que les exceptions métier et techniques sont de nouveau traduites de façon
	homogène. Expliquer pourquoi `@RestControllerAdvice` est ce qui rend les `@ExceptionHandler` visibles à tous les
	contrôleurs.
4. Ouvrir aussi les tests de `customer-service` et `account-service` pour repérer les cas où ces traductions
	d'exceptions sont indispensables.
