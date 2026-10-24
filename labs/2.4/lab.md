# TP 2.4 — `@Configuration` et `@Bean`

> Support de cours : [`@Configuration` et `@Bean`](README.md#spring-configuration)

## Objectifs

À l'issue de ce TP, le stagiaire doit comprendre que `@Configuration` est ce qui fait détecter une classe par le
component scan, et que ses méthodes `@Bean` ne sont exécutées que si la classe elle-même est bien reconnue comme un
composant Spring.

## Consignes

Lancer `./start-lab.sh 2.4` pour créer la branche `lab/2.4`.

1. Lancer `mvn -pl account-service -am clean compile`. La compilation réussit : rien dans le code n'empêche
   `RestConfiguration` de compiler sans son annotation de classe.
2. Lancer `mvn -pl account-service -am verify -Popenapi,h2 -DskipTests`. Constater l'échec au démarrage :
   `APPLICATION FAILED TO START`, avec un message du type "Consider defining a bean of type
   `com.c4soft.resthero.api.CustomersApi` in your configuration" : c'est le premier bean consommateur de
   `CustomersApi` (typiquement `AccountController`, qui en a besoin dans son constructeur) qui échoue à
   s'instancier.
3. Retrouver dans `RestConfiguration.java` (`account-service`, repère `LAB:2.4`) l'annotation manquante sur la
   classe, et la reconstruire. Relancer `mvn -pl account-service -am verify -Popenapi,h2 -DskipTests` et vérifier
   que l'application démarre.
4. Expliquer pourquoi retirer uniquement `@Configuration` (en laissant les méthodes `@Bean` intactes) suffit à
   faire disparaître silencieusement `CustomersApi`, `CurrenciesApi` et `OAuth2AuthorizedClientManager` du
   contexte : `@Configuration` est ce qui fait que la classe est repérée par le component scan et que ses méthodes
   `@Bean` sont effectivement exécutées par Spring. Sans elle, la classe est un simple POJO ignoré, quel que soit
   le contenu de ses méthodes.
5. Citer, à partir du support de cours, au moins une situation où une classe ne peut pas être annotée
   `@Component` et nécessite une classe `@Configuration` séparée (classe d'une bibliothèque tierce, plusieurs
   instances d'un même type à configurer différemment, instanciation nécessitant une logique particulière).
