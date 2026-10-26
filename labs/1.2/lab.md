# TP 1.2 — Dépendances

> Support de cours : [Dépendances](README.md#maven-build-dependencies)

## Objectifs

À l'issue de ce TP, le stagiaire doit savoir choisir un `scope` adapté à l'usage réel d'une dépendance, éviter de
figer une version déjà gérée par une BOM, et savoir diagnostiquer un conflit de versions avec
`dependency:tree`. Ce sont les trois erreurs les plus courantes des débutants sur ce sujet.

## Consignes

Lancer `./start-lab.sh 1.2` pour créer la branche `lab/1.2`.

1. Lancer `mvn -pl account-service dependency:tree -Ph2 -Dincludes=com.h2database`. Constater que `h2` n'apparaît
   qu'en scope `test`, alors que le profile `h2` est actif. Retrouver dans `api/account-service/pom.xml` (repère
   `LAB:1.2`, dans le profile `h2`) la dépendance manquante et la reconstruire avec le bon `scope`. Relancer la
   même commande et vérifier que `h2` apparaît cette fois aussi en scope `runtime`.
2. Expliquer pourquoi la dépendance `h2` du profile `h2` ne pouvait pas se contenter du scope `test` déjà présent
   plus haut dans le POM : ce dernier ne rend `h2` disponible que pour la compilation et l'exécution des tests, pas
   pour le classpath principal utilisé au démarrage de l'application (par exemple par
   `spring-boot-maven-plugin:start`, utilisé au TP 1.4).
3. Ajouter une balise `<version>` explicite et volontairement ancienne sur `spring-boot-starter-validation` dans
   `api/account-service/pom.xml`, alors que sa version est déjà gérée par le BOM `spring-boot-starter-parent`.
   Lancer `mvn dependency:tree -pl account-service -Dverbose` et observer le conflit de version signalé, puis
   annuler ce changement. En tirer la règle : ne jamais figer une version déjà gérée par le parent, sous peine de
   désynchronisation avec le reste de l'écosystème Spring Boot.
4. Essayer d'ajouter `spring-boot-starter-web` (nom historique, encore trouvé dans beaucoup de tutoriels) au lieu de
   `spring-boot-starter-webmvc` utilisé par ce projet (Spring Boot 4). Constater l'échec de résolution ou l'absence
   de gestion de version, et comprendre l'importance de vérifier le nom exact de l'artifact dans la version de
   Spring Boot réellement utilisée plutôt que de recopier un nom vu ailleurs.
