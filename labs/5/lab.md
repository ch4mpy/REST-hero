# TP 5 — Mise en cache

> Support de cours : [Mise en cache](README.md#caching)

## Objectifs

À l'issue de ce TP, le stagiaire doit savoir activer la mise en cache dans un service Spring Boot, comprendre la
différence entre un cache de lecture et une invalidation à l'écriture, et identifier les effets d'un cache absent ou
mal synchronisé sur des tests de repository.

## Consignes

Lancer `./start-lab.sh 5` pour créer la branche `lab/5`.

1. Lancer `mvn -pl account-service test -Dtest=AccountRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false` puis
	`mvn -pl card-service test -Dtest=CardRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false`. Constater que les
	scénarios de cache ne se comportent plus correctement.
2. Retrouver dans `api/account-service/src/main/java/com/c4soft/resthero/account/CacheConfiguration.java` et dans
	`api/card-service/src/main/java/com/c4soft/resthero/card/CacheConfiguration.java` les repères `LAB:5` sur
	`@EnableCaching`, et les reconstruire.
3. Relancer les deux commandes précédentes et constater que les caches sont de nouveau pris en compte. Expliquer
	pourquoi l'annotation de configuration est indispensable même si les méthodes du repository sont déjà décorées avec
	`@Cacheable` / `@CachePut` / `@CacheEvict`.
4. Lancer ensuite `mvn -pl account-service test -Dtest=AccountRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false`
	et constater qu'un cas de cache reste faux lors d'une écriture.
5. Retrouver dans `api/account-service/src/main/java/com/c4soft/resthero/account/jpa/AccountRepository.java` le repère
	`LAB:5` sur l'annotation de la méthode `save(...)`, et la reconstruire.
6. Relancer le test et vérifier que la lecture renvoie bien la donnée mise à jour après écriture. Expliquer la
	différence entre un cache de lecture (`@Cacheable`) et une mise à jour / éviction de cache (`@CachePut`,
	`@CacheEvict`, `@Caching`).
7. En complément, ouvrir `api/currency-service/src/main/java/com/c4soft/resthero/currency/frankfurter/ForexService.java`
	pour repérer un autre cache de lecture du projet et expliquer pourquoi la méthode `warmUp()` existe en plus.
