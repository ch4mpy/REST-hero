# TP 4.4 — Gestion des exceptions

> Support de cours : [Gestion des exceptions](README.md#rest-controller-exceptions)

## Objectifs

À l'issue de ce TP, le stagiaire doit savoir centraliser la traduction d'exceptions en réponses HTTP, reconnaître le
rôle d'un `@RestControllerAdvice`, et constater ce que deviennent les réponses d'erreur quand ce niveau de traitement
disparaît : le contrat d'erreur (`type`, statut, `parameters`, `invalidFields`) n'est plus tenu.

## Consignes

Lancer `./start-lab.sh 4.4` pour créer la branche `lab/4.4`.

1. Lancer `mvn -pl card-service test -Dtest=CardControllerTest -Dsurefire.failIfNoSpecifiedTests=false`.
	Constater que les cas d'erreur ne reviennent plus avec leur statut ni leur type : les problèmes métier (plafond de
	carte dépassé, compte inconnu du `account-service`) tombent en `500` de type `server-error`, une carte inconnue
	dans le chemin en `400` de type `invalid-request`, et les erreurs de validation (IBAN mal formé, plafond nul,
	période incomplète) en `400` de type `invalid-request` sans `invalidFields`, au lieu de `422` de type
	`validation`. C'est le filet générique qui a pris le relais : il ne connaît ni les `ApiProblemException`, ni
	les exceptions de validation.
2. Retrouver dans `api/rest-hero-problem/src/main/java/com/c4soft/resthero/problem/ApiExceptionHandler.java` le repère
	`LAB:4.4` sur l'annotation de classe, et la reconstruire.
3. Relancer la même commande et vérifier que les problèmes métier reviennent avec leur statut (`409`, `404`, `502`), leur
	type et leurs `parameters`, et les erreurs de validation en `422` avec leurs `invalidFields`. Expliquer pourquoi `@RestControllerAdvice` est ce qui rend
	les `@ExceptionHandler` visibles à tous les contrôleurs, et pourquoi ce handler est dans un starter partagé plutôt
	que dans chaque service.
4. Ouvrir `ProblemType` et repérer les types métier de `customer-service` et `account-service`. Pour chacun, retrouver
	dans le contrôleur l'`ApiProblemException` qui le lève et les `parameters` qu'elle transmet.
