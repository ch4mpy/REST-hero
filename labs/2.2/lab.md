# TP 2.2 — `@Component` et variantes

> Support de cours : [`@Component` et variantes](README.md#spring-components)

## Objectifs

À l'issue de ce TP, le stagiaire doit savoir déclarer un bean des deux façons vues en cours : par une annotation de
stéréotype (`@Component` ou une de ses spécialisations comme `@Service`, `@Repository`, `@RestController`) détectée
par le component scan, ou par une méthode `@Bean` d'une classe `@Configuration`. Il doit aussi savoir utiliser
`@PostConstruct` pour exécuter du code d'initialisation après la construction d'un bean, et comprendre que l'oubli
de cette annotation ne casse rien à la compilation ni au démarrage : la méthode devient simplement une méthode
normale, jamais appelée automatiquement.

## Consignes

Lancer `./start-lab.sh 2.2` pour créer la branche `lab/2.2`.

1. Lancer `mvn -pl currency-service -am clean compile`. La compilation réussit. Lancer ensuite
   `mvn -pl currency-service -am verify -Popenapi -DskipTests`. Constater l'échec au démarrage :
   `APPLICATION FAILED TO START` — `Parameter 0 of constructor in ...CachingRatesRepository required a bean of
   type dev.frankfurter.api.RatesApi that could not be found`.
2. Retrouver dans `RestConfiguration.java` (`currency-service`, repère `LAB:2.2`) la méthode `@Bean` manquante, et
   la reconstruire. Elle illustre la seconde façon de déclarer un bean : une méthode d'une classe `@Configuration`,
   utile ici car `RatesApi` est une interface générée (voir TP 1.5), sur laquelle on ne peut pas coller
   d'annotation de stéréotype.
3. Relancer `mvn -pl currency-service -am verify -Popenapi -DskipTests`. Constater un nouvel échec :
   `Parameter 1 of constructor in ...CurrencyController required a bean of type
   com.c4soft.resthero.currency.frankfurter.ForexService that could not be found`.
4. Retrouver dans `ForexService.java` (repère `LAB:2.2`) l'annotation de classe manquante, et la reconstruire.
   Elle illustre la première façon de déclarer un bean : une annotation de stéréotype directement sur la classe,
   détectée par le component scan sans configuration supplémentaire. Relancer
   `mvn -pl currency-service -am verify -Popenapi -DskipTests` et vérifier que l'application démarre.
5. Expliquer pourquoi ces deux mécanismes produisent le même résultat (un bean disponible pour l'injection) mais
   ne s'appliquent pas aux mêmes cas : `@Service` convient à une classe qu'on écrit soi-même, tandis que la méthode
   `@Bean` est nécessaire dès qu'on ne peut pas ou ne veut pas modifier la classe instanciée (interface générée,
   bibliothèque tierce, ou logique d'instanciation particulière comme ici avec `RestClientHttpExchangeProxyFactoryBean`).
6. Ouvrir de nouveau `ForexService.java` et repérer la méthode `warmUp()`, dont le rôle est de précharger le cache
   des taux de change au démarrage de l'application, en interrogeant l'API Frankfurter pour chaque devise.
   Retrouver le second repère `LAB:2.2` sur `warmUp()`, et reconstruire l'annotation manquante. Expliquer pourquoi
   son absence est un piège particulièrement sournois, bien différent des deux précédents : sans elle, `warmUp()`
   reste une méthode valide, jamais appelée par personne, sans la moindre erreur ni avertissement au démarrage.
   Le seul symptôme observable serait un premier appel de conversion plus lent que les suivants (cache vide au
   démarrage), ou l'absence de tout appel sortant vers Frankfurter dans les logs de démarrage.
7. Expliquer la différence entre `@PostConstruct` (exécuté une fois, juste après l'injection des dépendances du
   bean) et un constructeur : `@PostConstruct` permet d'exécuter du code qui a besoin que toutes les dépendances du
   bean soient déjà injectées, ce qu'un constructeur ne garantit pas toujours selon l'ordre d'instanciation.
8. Repérer dans `PersistenceConfiguration.java` (`account-service`) la classe imbriquée
   `SecurityAwareRevisionListener`, annotée `@Component` et implémentant `RevisionListener` d'Hibernate Envers.
   Chercher (Javadoc ou code source d'Hibernate Envers) comment un `RevisionListener` est réellement instancié via
   `@RevisionEntity(value = SecurityAwareRevisionListener.class)`, et en déduire si cette instanciation passe par
   le conteneur Spring ou par un simple appel réflexif à un constructeur sans argument. Expliquer ce que cela
   implique sur l'utilité réelle de l'annotation `@Component` à cet endroit précis.
