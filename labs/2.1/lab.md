# TP 2.1 — Injection de dépendance

> Support de cours : [Injection de dépendance](README.md#spring-di)

## Objectifs

À l'issue de ce TP, le stagiaire doit savoir pratiquer l'injection par constructeur (recommandée plutôt que
l'injection par champ ou par setter), comprendre pourquoi un bean déclaré avec des champs `final` ne peut pas se
passer d'un constructeur qui les initialise tous, et savoir que `@MockitoBean` est ce qui remplace effectivement
une dépendance injectée par un mock dans un test, un simple champ ne suffisant pas.

## Consignes

Lancer `./start-lab.sh 2.1` pour créer la branche `lab/2.1`.

1. Lancer `mvn -pl account-service -am clean compile` depuis `api/`. Constater l'échec de compilation dans
   `AccountController.java` : `variable accountRepo not initialized in the default constructor` (et de même pour
   `accountMapper`, `customersApi`, `rabbitTemplate` et `eventsExchange`). Ces champs sont déclarés `final`, ce qui
   impose qu'un constructeur les initialise tous.
2. Retrouver dans `AccountController.java` (repère `LAB:2.1`) l'annotation manquante, et la reconstruire.
   Expliquer ce qu'elle apporte : `@RequiredArgsConstructor` (Lombok) génère un constructeur prenant en paramètre
   tous les champs `final`, ce qui revient exactement à l'exemple du support de cours écrivant ce constructeur à la
   main.
3. Relancer `mvn -pl account-service -am clean compile` et vérifier que tout compile. Expliquer pourquoi Spring
   n'a besoin d'aucune annotation particulière sur ce constructeur unique pour l'utiliser automatiquement lors de
   l'instanciation du bean `AccountController` (une classe avec un seul constructeur n'a pas besoin de
   `@Autowired` dessus).
4. Lancer `mvn -pl account-service test -Dtest=AccountControllerTest -Dsurefire.failIfNoSpecifiedTests=false`.
   Constater l'échec au chargement du contexte de test : `NoSuchBeanDefinitionException: No qualifying bean of
   type com.c4soft.resthero.api.CustomersApi available`. Retrouver dans `AccountControllerTest.java` (repère
   `LAB:2.1`) l'annotation manquante sur le champ `customersApi`, et la reconstruire. Relancer la même commande et
   vérifier que tous les tests passent.
5. Expliquer pourquoi un simple champ `CustomersApi customersApi;` sans `@MockitoBean` ne suffit pas : dans une
   classe de test Spring, un champ n'est jamais alimenté automatiquement, quelle que soit son type, à moins d'être
   annoté (`@Autowired`, `@MockitoBean`, etc.). `@MockitoBean` a un rôle double ici : il crée un mock Mockito ET
   l'enregistre comme bean dans le contexte de test, ce qui permet à `AccountController` (qui a besoin d'un bean
   `CustomersApi` dans son constructeur, reconstruit à l'étape 2) de s'instancier normalement dans le contexte
   réduit du `@WebMvcTest`, sans jamais appeler le vrai `customer-service`.
6. Ouvrir `RestConfiguration.java` (`account-service`) et repérer les deux méthodes `@Bean` `customersApi` et
   `currenciesApi`, toutes deux de type `RestClient` en paramètre. Expliquer comment Spring les distingue sans
   ambiguïté : par défaut via le nom du paramètre (`customerServiceClient`, `currenciesServiceClient`), qui doit
   correspondre au nom du bean `RestClient` correspondant (déclaré via la configuration
   `spring-addons-starter-rest`, voir `application.yml`). Expliquer aussi comment `@Qualifier` permettrait de
   surcharger cette résolution par nom si les noms ne correspondaient pas.
