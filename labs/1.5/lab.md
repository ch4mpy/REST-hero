# TP 1.5 — Génération de code client à partir de spec OpenAPI

> Support de cours : [Génération de code client à partir de spec OpenAPI](README.md#maven-build-openapi-client-code-generation)

## Objectifs

À l'issue de ce TP, le stagiaire doit comprendre que le code généré dans `target/generated-sources` n'est jamais
une source à modifier à la main, savoir situer la génération dans le cycle de vie, et comprendre pourquoi seules
des interfaces `@HttpExchange` sont générées ici, sans classe d'implémentation.

## Consignes

Lancer `./start-lab.sh 1.5` pour créer la branche `lab/1.5`.

1. Lancer `mvn -pl card-service -am clean compile`. Constater l'échec de compilation dans `CardController.java` et
   `RestConfiguration.java` : `cannot find symbol` pour `AccountsApi`, `MoneyTransfersApi` et
   `MoneyTransferRequest`. Ces classes doivent être générées à partir de la spec `account-service.openapi.json`.
2. Retrouver dans `api/card-service/pom.xml` (repère `LAB:1.5`) l'exécution manquante du
   `openapi-generator-maven-plugin`, et la reconstruire. Relancer `mvn -pl card-service -am clean compile` et
   vérifier que la compilation réussit. Ouvrir un des fichiers générés sous
   `card-service/target/generated-sources/...` pour voir à quoi correspondent `AccountsApi` et
   `MoneyTransfersApi`.
3. Modifier arbitrairement l'un de ces fichiers générés, puis relancer `mvn compile -pl card-service -am`.
   Constater que la modification a disparu : ce répertoire est entièrement régénéré à chaque exécution du plugin,
   jamais fusionné avec l'existant.
4. Repérer dans `api/card-service/src/main/java/com/c4soft/resthero/card/RestConfiguration.java` comment les
   interfaces générées sont instanciées, alors qu'aucune classe d'implémentation n'a été générée
   (`interfaceOnly=true`, `library=spring-http-interface` dans la configuration du plugin, `api/pom.xml`).
   Expliquer le rôle de `RestClientHttpExchangeProxyFactoryBean` et pourquoi chercher une classe `AccountsApiImpl`
   générée serait une fausse piste.
