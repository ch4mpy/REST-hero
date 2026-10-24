# TP 1.6 — Manipulation des ressources

> Support de cours : [Manipulation des ressources](README.md#maven-build-resources-handling)

## Objectifs

À l'issue de ce TP, le stagiaire doit savoir où se trouvent réellement les fichiers de ressources d'un module, et
surtout comprendre que le bloc `<resources>` d'un POM n'est pas additif par rapport aux répertoires par défaut de
Maven : dès qu'on le déclare, on prend en charge la liste complète soi-même, y compris `src/main/resources` si on
veut continuer à le voir copié.

## Consignes

Lancer `./start-lab.sh 1.6` pour créer la branche `lab/1.6`.

1. Lancer `mvn -pl gateway -am clean package -DskipTests`. Le build réussit (`BUILD SUCCESS`), ce qui peut donner
   une fausse impression que tout va bien. Inspecter pourtant le jar produit
   (`unzip -l gateway/target/gateway-*.jar | grep -i application`) : `application.yml`, `banner.txt` et
   `logback-spring.xml` (normalement présents dans `src/main/resources`) sont absents du jar.
2. Retrouver dans `api/gateway/pom.xml` (repère `LAB:1.6`, dans `<build><resources>`) l'entrée manquante pour
   `src/main/resources`, et la reconstruire aux côtés de celle pour `../../certs`. Relancer le build et vérifier
   que `application.yml` est de nouveau présent dans le jar.
3. Expliquer pourquoi le retrait de cette seule entrée suffisait à faire disparaître tous les fichiers de
   `src/main/resources` du jar, sans jamais faire échouer le build : `<resources>` remplace entièrement les
   répertoires par défaut de Maven dès qu'il est déclaré, il ne les complète pas. Un module qui ne déclare aucun
   bloc `<resources>` (comme `rest-hero-starter-common`) continue au contraire à bénéficier du comportement par
   défaut.
4. Ouvrir `application.yml` et repérer des expressions comme `${cn}`, `${reverse-proxy-uri}` ou `${issuer}`.
   Expliquer qu'il s'agit ici de placeholders résolus par Spring au démarrage, et non par le filtering de
   ressources de Maven (non activé sur ce module). Discuter du risque si le filtering Maven était activé sur ce
   fichier : la syntaxe `${...}` étant strictement identique pour les deux mécanismes, Maven tenterait de
   substituer ces expressions avec ses propres propriétés dès `process-resources`, bien avant que Spring n'ait la
   moindre chance de les résoudre à son tour.
