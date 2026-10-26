# TP 1.1.2 — Phases

> Support de cours : [Phases](README.md#maven-build-phases)

## Objectifs

À l'issue de ce TP, le stagiaire doit savoir situer les phases usuelles (`compile`, `test`, `package`, `verify`,
`install`) dans le cycle de vie, et surtout comprendre qu'une phase ne fait que ce que les plugins qui lui sont
rattachés font : `package` sur un module `jar` n'assemble par défaut qu'un jar "nu" (`maven-jar-plugin`), et rien ne
garantit qu'il soit exécutable sans le bon plugin configuré sur cette même phase.

## Consignes

Lancer `./start-lab.sh 1.1.2` pour créer la branche `lab/1.1.2`.

1. Lancer `mvn -pl account-service -am clean package -DskipTests`. Le build réussit, mais inspecter le jar produit
   (`unzip -l account-service/target/account-service-*.jar`, ou simplement sa taille) : il ne contient ni
   `BOOT-INF/`, ni `Main-Class` dans son manifeste. C'est un jar "nu", non exécutable.
2. Retrouver dans `api/account-service/pom.xml` (repère `LAB:1.1.2`) le plugin manquant dans `<build><plugins>`, et le
   reconstruire. Relancer `mvn -pl account-service -am clean package -DskipTests` et constater cette fois un jar bien
   plus volumineux, avec un `BOOT-INF/` et un `Main-Class` dans son manifeste.
3. Expliquer pourquoi le build de l'étape 1 était en `BUILD SUCCESS` malgré le jar non exécutable : la phase
   `package` s'est bien exécutée, mais aucun plugin ne lui était rattaché pour produire un jar Spring Boot ; c'est
   `spring-boot-maven-plugin` (goal `repackage`, lié à `package`) qui fait ce travail, pas la phase elle-même.
4. Lancer `mvn test -pl account-service -am`, puis vérifier l'absence de jar dans `account-service/target/`. Expliquer
   pourquoi `test` ne produit pas de livrable, contrairement à une idée reçue fréquente.
5. Sur `account-service`, comparer `mvn test -Popenapi,h2` et `mvn verify -Popenapi,h2` (voir le TP 1.4 pour le détail
   du profile `openapi`). Constater que la génération de la spec OpenAPI ne se déclenche pas avec `mvn test`
   seul : `test` n'exécute que les tests unitaires, jamais les étapes liées à `verify`, dont font partie les
   _integration-tests_ Maven (pendant lesquels les specs OpenAPI sont extraites).
