# TP 2.7 — Starter Spring Boot

> Support de cours : [Starter Spring Boot](README.md#spring-boot-starter)

## Objectifs

À l'issue de ce TP, le stagiaire doit comprendre le rôle du fichier
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` dans un starter Spring Boot, et
savoir qu'une classe qui n'y est pas listée n'est jamais auto-configurée dans les applications qui dépendent du
starter, même si elle est parfaitement écrite et présente sur le classpath. Il doit aussi savoir qu'une classe
d'auto-configuration qui référence une dépendance optionnelle du starter doit se garder elle-même avec
`@ConditionalOnClass`, sous peine de faire échouer le démarrage de toute application qui dépend du starter mais n'a
pas ajouté cette dépendance optionnelle.

## Consignes

Lancer `./lab.sh 2.7` pour créer la branche `lab/2.7`.

1. Ouvrir
   `api/rest-hero-starter-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
   et observer son contenu : chaque ligne est le nom qualifié d'une classe auto-configurée dans tout module qui
   dépend de `rest-hero-starter-common`, sans qu'aucun `@Import` ni component scan explicite ne soit nécessaire côté
   consommateur.
2. Lancer `mvn -pl rest-hero-starter-common -am install -DskipTests` puis
   `mvn -pl account-service -am verify -Popenapi,h2 -DskipTests`. Constater l'échec au démarrage :
   `APPLICATION FAILED TO START` — `Parameter 0 of constructor in
   com.c4soft.resthero.commons.jpa.IbanStringAttributeConverter required a bean of type
   com.c4soft.resthero.commons.domain.IbanStringMapper that could not be found`.
3. Retrouver dans le fichier `AutoConfiguration.imports` (repère `LAB:2.7`) la ligne manquante, et la
   reconstruire. Relancer `mvn -pl rest-hero-starter-common -am install -DskipTests` puis
   `mvn -pl account-service -am verify -Popenapi,h2 -DskipTests` et vérifier que l'application démarre.
4. Expliquer pourquoi il a fallu réinstaller `rest-hero-starter-common` dans le dépôt local avant de retester
   `account-service` : ce fichier fait partie du jar du starter, tout changement dans ce module n'est visible des
   autres modules qu'après un nouvel `install` (voir le TP 1.1.2).
5. Expliquer la recommandation du support de cours pour la conception de starters : être peu intrusif et laisser
   la main à l'application consommatrice pour surcharger l'auto-configuration, à l'aide d'annotations telles que
   `@ConditionalOnMissingBean` ou `@ConditionalOnProperty`.
6. Ouvrir `api/rest-hero-starter-common/src/main/java/com/c4soft/resthero/commons/messaging/EventsRabbitConfiguration.java`.
   Cette classe est auto-configurée dans tout module qui dépend de `rest-hero-starter-common`, mais elle référence des
   types de `spring-boot-starter-amqp`, une dépendance optionnelle du starter (voir son `pom.xml`) : `customer-service`
   et `currency-service` en dépendent sans jamais ajouter cette dépendance optionnelle. Sans traitement particulier,
   ces deux services échoueraient au démarrage.
7. Lancer `mvn -pl rest-hero-starter-common install -DskipTests` puis
   `mvn -pl customer-service test -Dtest=CustomerServiceApplicationTests#contextLoads -Ph2`. Constater l'échec au
   démarrage : `IllegalStateException: Failed to introspect Class
   [com.c4soft.resthero.commons.messaging.EventsRabbitConfiguration] from ClassLoader [...]`, causé par un
   `NoClassDefFoundError: org/springframework/amqp/support/converter/MessageConverter` puis un
   `ClassNotFoundException: org.springframework.amqp.support.converter.MessageConverter` — Spring tente d'introspecter
   les méthodes `@Bean` de la classe et échoue car un type qu'elles utilisent n'est pas sur le classpath de
   `customer-service`.
8. Retrouver dans `EventsRabbitConfiguration` (repère `LAB:2.7`) l'annotation manquante, et la reconstruire.
   Relancer `mvn -pl rest-hero-starter-common install -DskipTests` puis
   `mvn -pl customer-service test -Dtest=CustomerServiceApplicationTests#contextLoads -Ph2` et vérifier que le
   contexte démarre. Expliquer pourquoi `@ConditionalOnClass(RabbitTemplate.class)` suffit à éviter l'échec : la
   condition est évaluée par lecture du bytecode de `EventsRabbitConfiguration`, avant que Spring ne tente de charger
   la classe elle-même, donc les types absents du classpath (ici `MessageConverter`) ne sont jamais résolus si la
   condition échoue.
