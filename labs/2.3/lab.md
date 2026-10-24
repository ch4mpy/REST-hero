# TP 2.3 — Configuration externe

> Support de cours : [Configuration externe](README.md#spring-properties)

## Objectifs

À l'issue de ce TP, le stagiaire doit savoir surcharger une propriété d'`application.yml` par une variable
d'environnement ou un argument en ligne de commande, et savoir déclarer une classe `@ConfigurationProperties` pour
qu'elle soit effectivement prise en compte par Spring Boot.

## Consignes

Lancer `./start-lab.sh 2.3` pour créer la branche `lab/2.3`.

1. Ouvrir `api/account-service/src/main/resources/application.yml` et repérer, dans le document YAML activé par
   le profile `postgresql`, le repère `LAB:2.3` à la place du mot de passe applicatif. Le renseigner avec une
   valeur (`password: change-me` par exemple).
2. Expliquer pourquoi une valeur écrite en dur dans `application.yml`, comme celle que le stagiaire vient de
   saisir, n'est presque jamais celle réellement utilisée en environnement de développement ou de production.
   Retrouver dans `secrets/rest-api/postgres_password.txt` le mot de passe effectivement configuré pour la base
   PostgreSQL locale (généré aléatoirement par `build-0-env.sh`), et expliquer comment le fournir à l'application
   sans toucher au fichier `application.yml` :
   ```bash
   SPRING_DATASOURCE_PASSWORD=$(cat secrets/rest-api/postgres_password.txt) java -jar account-service.jar
   # ou
   java -jar account-service.jar --spring.datasource.password=$(cat secrets/rest-api/postgres_password.txt)
   ```
3. Lancer `mvn -pl customer-service -am verify -Popenapi,h2 -DskipTests`. Constater l'échec au démarrage :
   `APPLICATION FAILED TO START` — `Parameter 0 of constructor in CustomerRepository required a bean of type
   KeycloakAdminApiProperties that could not be found`.
4. Retrouver dans `CustomerServiceApplication.java` (repère `LAB:2.3`) l'annotation manquante, et la
   reconstruire. Relancer `mvn -pl customer-service -am verify -Popenapi,h2 -DskipTests` et vérifier que
   l'application démarre. Expliquer le rôle de `@ConfigurationPropertiesScan` : sans elle, une classe annotée
   `@ConfigurationProperties` (ici `KeycloakAdminApiProperties`) n'est jamais enregistrée comme bean, même si elle
   est correctement écrite et présente sur le classpath.
