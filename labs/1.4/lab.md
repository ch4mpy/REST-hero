# TP 1.4 — Génération de spec OpenAPI à partir du code source

> Support de cours : [Génération de spec OpenAPI à partir du code source](README.md#maven-build-openapi-spec-generation)

## Objectifs

À l'issue de ce TP, le stagiaire doit savoir exécuter le profile `openapi` correctement, comprendre à quelle phase
la spec est récupérée, comprendre pourquoi il faut simuler le fournisseur OIDC pendant le build, et éviter le
réflexe de débutant qui consiste à corriger le fichier JSON généré plutôt que les annotations Swagger qui en sont à
l'origine.

## Consignes

Lancer `./start-lab.sh 1.4` pour créer la branche `lab/1.4`.

1. Lancer `mvn verify -pl account-service -am -Popenapi,h2 -DskipTests`. Constater un échec rapide (quelques
   secondes) avec une `ResourceAccessException` / `Connexion refusée` sur
   `http://localhost:<port>/auth/realms/labs/.well-known/openid-configuration`, `<port>` étant le port libre que le
   build a réservé pour WireMock (`keycloak-wiremock.port`). C'est l'application elle-même qui échoue à démarrer : au
   lancement, Spring tente de récupérer la configuration OpenID du realm Keycloak configuré en émetteur, et rien
   n'écoute sur ce port.
2. Retrouver dans `api/account-service/pom.xml` (repère `LAB:1.4`, dans le profile `openapi`) l'exécution de plugin
   manquante, et la reconstruire. Relancer `mvn verify -pl account-service -am -Popenapi,h2 -DskipTests` et
   vérifier que `frontend/openapi/account-service.openapi.json` est bien régénéré.
3. Expliquer le rôle de ce plugin : il démarre un faux serveur WireMock sur le port réservé par le
   `build-helper-maven-plugin`, servant les réponses de `src/test/resources/wiremock/keycloak`, afin que l'application démarrée par
   `spring-boot-maven-plugin` (goal `start`, phase `pre-integration-test`) trouve un fournisseur OIDC à interroger
   sans dépendre d'un vrai Keycloak. Repérer dans les logs l'ordre des étapes : démarrage de WireMock, démarrage de
   l'application, récupération de la spec sur `/v3/api-docs`, arrêt de l'application puis de WireMock.
4. Éditer directement `frontend/openapi/account-service.openapi.json` pour "corriger" la description d'une
   opération, puis relancer le build. Constater que la correction est écrasée. En tirer la règle : ce fichier est
   un artefact généré, jamais une source ; toute correction doit se faire sur les annotations Swagger dans le code
   Java du contrôleur concerné.
