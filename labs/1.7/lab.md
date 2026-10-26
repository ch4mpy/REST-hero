# TP 1.7 — Profiles Maven

> Support de cours : [Profiles Maven](README.md#maven-profiles)

## Objectifs

À l'issue de ce TP, le stagiaire doit savoir activer un ou plusieurs profiles Maven correctement, connaître le rôle
de `activeByDefault`, et ne plus confondre un profile Maven (mécanisme de build, actif via `-P`) avec un profile
Spring (mécanisme d'exécution, actif via `spring.profiles.active`), confusion très répandue tant les deux
partagent le même vocabulaire.

## Consignes

Lancer `./start-lab.sh 1.7` pour créer la branche `lab/1.7`.

1. Lancer `mvn help:active-profiles -pl customer-service` sans aucune option `-P`. Constater qu'aucun profile
   n'est actif, alors qu'auparavant `postgresql` l'était par défaut. Confirmer avec
   `mvn dependency:tree -pl customer-service -Dincludes=org.postgresql:*,com.h2database:*` qu'aucun driver JDBC
   n'est présent : ni PostgreSQL (plus activé par défaut), ni H2 (scope `test` uniquement).
2. Retrouver dans `api/customer-service/pom.xml` (repère `LAB:1.7`, dans le profile `postgresql`) le bloc
   `<activation>` manquant, et le reconstruire. Relancer `mvn help:active-profiles -pl customer-service` et
   vérifier que `postgresql` réapparaît dans la liste des profiles actifs sans qu'aucune option `-P` ne soit
   passée.
3. Relancer `mvn help:active-profiles -pl customer-service -Ph2`. Constater cette fois que `postgresql` n'apparaît
   plus dans la liste des profiles actifs. En tirer la règle : dès qu'un profile est activé explicitement, plus
   aucun profile `activeByDefault` ne s'active automatiquement, il faut alors tout demander soi-même.
4. Piège de syntaxe fréquent : lancer `mvn verify -pl account-service -am -Popenapi -Ph2` (deux options `-P`
   séparées) plutôt que `-Popenapi,h2`. Vérifier avec `help:active-profiles` lequel des deux profiles est
   réellement actif, et comprendre pourquoi une seule liste, séparée par des virgules, doit être utilisée pour
   cumuler plusieurs profiles.
5. Dans `application.yml` d'`account-service`, repérer le bloc `spring.profiles.active` qui liste `postgresql`.
   Expliquer que ce réglage active un profile Spring du même nom au runtime (sélection de configuration
   applicative), totalement indépendant du profile Maven `postgresql` qui, lui, contrôle uniquement quelle
   dépendance JDBC est embarquée à la compilation. Les deux portent le même nom par convention de ce projet, mais
   rien dans Maven ni Spring n'impose ni ne garantit cette cohérence : elle doit être maintenue à la main par
   l'équipe.
