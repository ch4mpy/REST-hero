# REST-hero : APIs REST avec Spring Boot

Ce support de TPs répond à des exigences de production avancées. Les services REST sont notamment :

- **Audités** :
  - Hibernate Envers garde la trace de chaque version des entités en base de données avec l'instant et l'auteur de la modification
  - Chaque endpoint qui modifie l'état de l'application (`POST`, `PUT` ou `DELETE`) log au niveau `info` un résumé de ce qui a été modifié et par qui
- **Observables** et observées de manière centralisée : émet des logs collectés dans Loki, des métriques dans Mimir et des traces dans Tempo, le tout visualisé dans [Grafana](https://host.docker.internal/grafana).
- **Documentés** avec OpenAPI : permet aux clients de générer le code pour les consommer et aux développeurs de les visualiser dans une Swagger UI. Cette documentation est générée à partir des sources (commentaires JavaDoc compris)
- **Communicants** : Appels REST inter-service:
  - Le `customer-service` utilise Keycloak pour accéder aux utilisateurs
  - L'`account-service` vérifie auprès du `customer-service` qu'un client existe avant de créer un compte à son nom. Il sollicite également le `currency-service` lorsqu'un virement nécessite des opérations de change.
  - Le `card-service` vérifie auprès de l'`account-service` qu'un compte existe avant de lui attacher une carte et lui déclare un transfert d'argent lors d'un paiement par carte.
- **Bidirectionnels** : Le front manipule les ressources du serveur via des appels REST et le serveur pousse des évènements sur les ressources aux utilisateurs connectés via SSE (Server Sent Events). Les services publient des évènements de mise à jour de ressources sur RabbitMQ. Ces évènements sont relayés au frontend par SSE.
- **Sécurisés** :
  - Chaque endpoint d'API vérifie l'identité attachée à la requête et ses relations éventuelles avec les ressources qu'elle cherche à manipuler avant d'autoriser l'accès.
  - Ne sont utilisés que des clients OAuth2 confidentiels (avec mot de passe). Les requêtes du front React sont autorisées avec le pattern [_OAuth2 BFF_](https://www.baeldung.com/spring-cloud-gateway-bff-oauth2)
  - Les évènements SSE ne sont adressé qu'aux utilisateurs connectés qui ont un accès en lecture aux ressources modifiées
- **Persistants** : les objets métier sont sauvegardés dans PostgreSQL avec JPA. Les requêtes les plus complexes (filtres sur les paiements par carte et les mouvements entre comptes) sont construites avec des spécifications JPA.
- **Performants** : utilisation de caches pour limiter les accès à la base de données et les appels REST inter-services lorsque c'est pertinent.

- [Introduction](#intro)
- [Déploiement de l'environnement de dev](#dev-deployment)
- [1. Build avec Maven](#maven-build)
  - [1.1. Introduction](#maven-build-intro)
    - [1.1.1. Structure](#maven-build-structure)
    - [1.1.2. Phases](#maven-build-phases)
  - [1.2. Dépendances](#maven-build-dependencies)
  - [1.3. Processeurs d’annotations à la compilation](#maven-build-annotations-preprocessing)
  - [1.4. Génération de spec OpenAPI à partir du code source](#maven-build-openapi-spec-generation)
  - [1.5. Génération de code client à partir de spec OpenAPI](#maven-build-openapi-client-code-generation)
  - [1.6. Manipulation des ressources](#maven-build-resources-handling)
  - [1.7. Profiles Maven](#maven-profiles)
- [2. Fondamentaux Spring](#spring)
  - [2.1. Injection de dépendance](#spring-di)
  - [2.2. `@Component` et variantes](#spring-components)
  - [2.3. Configuration externe](#spring-properties)
  - [2.4. `@Configuration` et `@Bean`](#spring-configuration)
  - [2.5. Proxies générés](#spring-proxies)
  - [2.6. Tests](#spring-testing)
  - [2.7. Starter Spring Boot](#spring-boot-starter)
- [3. Modèles objet-relationnel et accès aux données](#jpa)
  - [3.1. `@Entity`](#jpa-entity)
  - [3.2. Identifiants générés](#jpa-generated-ids)
  - [3.3. Relations](#jpa-relations)
  - [3.4. Conversion de types](#jpa-type-converter)
  - [3.5. `@Repository` Spring Data JPA](#jpa-repositories)
  - [3.6. JPA query methods](#jpa-query-dsl)
  - [3.7. Spécifications JPA](#jpa-specifications)
  - [3.8. Transactions](#jpa-transactions)
  - [3.9. Hibernate Envers](#jpa-envers)
  - [3.10. Migrations de schéma avec Liquibase](#jpa-liquibase)
- [4. Services REST WebMvc avec Spring Boot](#rest-controller)
  - [4.1. `@RequestMapping`](#rest-controller-request-mapping)
  - [4.2. Convertisseurs automatiques de Spring](#rest-controller-converters)
  - [4.3. Validation des entrées](#rest-controller-validation)
  - [4.4. Gestion des exceptions](#rest-controller-exceptions)
  - [4.5. Génération de la documentation OpenAPI](#rest-controller-openapi)
  - [4.6. Appels de services REST externes](#rest-controller-inter-service-communication)
  - [4.7. Logs](#rest-controller-logging)
- [5. Mise en cache](#caching)
- [6. Messagerie asynchrone et notifications temps réel](#messaging)
  - [6.1. Publication d'un événement métier](#messaging-publish)
  - [6.2. Relais par la gateway : RabbitMQ vers Server-Sent Events](#messaging-gateway-sse)
  - [6.3. Abonnement du frontend](#messaging-frontend)

## <a name="intro"/>Introduction

Le cas d'utilisation est une banque en ligne simplifiée avec :

- Opérations de change basées sur le fixing veille de la Banque Centrale Européenne.
- Gestion des bénéficiaires d'un client.
- Virements entre comptes. Il n'y a pas de connexion à d'autres banques. Les opérations de crédit / débit des comptes qui ne sont pas gérés en interne sont simplement ignorées.
- Gestion des cartes de paiement pour chaque compte: création & (dés)activation.
- Paiements par carte.

La solution est composée d'une interface graphique React interrogeant une API REST composée des modules suivants :

- une `gateway`. Les requêtes (du frontend) préfixées avec `/gateway/bff` sont autorisées avec des cookies de session (`http-only=true`) et protégées contre le CSRF (cookie `XSRF-TOKEN` avec `http-only=false` et header `X-XSRF-TOKEN` requis pour pour les requêtes `POST`, `PUT` `PATCH` et `DELETE`). Les requêtes de clients OAuth2 (appels inter-services, Bruno, Postman, ...) préfixées avec `/gateway/m2m` sont autorisées avec un `Bearer` token dans le header `Authorization`.
- `rest-hero-starter-common` est un starter Spring Boot contenant des classes et de l'auto-configuration partagée.
- `rest-hero-problem` est un starter Spring Boot qui porte le contrat d'erreur de toutes les applications : les réponses
  d'erreur en _problem details_ (RFC 9457) dont le `type` est une valeur de l'énumération `ProblemType`.
- `currency-service` fournit un référentiel des devises supportées et du change sur le fixing veille de la BCE (via [https://frankfurter.dev](https://frankfurter.dev/))
- `customer-service` responsable des clients et de leurs bénéficiaires. Ce service ne stocke que les bénéficiaires dans sa base de données. Les clients sont des utilisateurs de Keycloak (lecture / écriture via l'API Keycloak).
- `account-service` responsable des comptes bancaires et des transferts entre comptes.
- `card-service`responsable des cartes et des paiements par carte.

## <a name="dev-deployment"/>Déploiement de l'environnement de dev

Pré-requis :

- [Git](https://git-scm.com/install/). Sur Windows, Git Bash avec Mingw. Toujours sous Windows, installer [7-zip](https://www.7-zip.fr/download.html) et créer une copie de`7z.exe` nommée `zip.exe`.
- [nvm](https://www.nvmnode.com/fr/guide/download.html)
- [SDKMan](https://sdkman.io/install/)
- [Python 3](https://www.python.org/downloads/) (`python3` dans le `PATH`), pour les scripts de génération des TPs (`labs/scripts`)
- Docker ou [Docker Desktop](https://docs.docker.com/desktop/)
- une entrée `127.0.0.1 host.docker.internal` dans `/etc/hosts` (`C:\windows\system32\drivers\etc\hosts` sous Windows)
- un IDE : [Eclipse STS](https://spring.io/tools#eclipse) et [Visual Studio Code](https://code.visualstudio.com/download) (avec des plugins pour React) ou IntelliJ Ultimate

Le script `deploy-dev.sh` :

- crée des certificats SSL auto-signés s'il n'y en a pas déjà dans `~/.ssh`
- monte l'infra dans Docker (bases PostgreSQL, Keycloak, Mailpit, Grafana, Loki, Prometheus, Tempo)
- fait un build Maven générant les specs OpenAPI du back
- initialise le sous-module Git contenant le code du front React
- installe les dépendances du front et génère le code client pour consommer l'API

```bash
bash ./deploy-dev.sh

sdk env install
nvm install --lts
nvm use
./build-openapi.sh
git submodule init && git submodule update
cd frontend && npm i && npm run api && cd ..
```

`build-openapi.sh` charge le fichier `.env` (écrit par `build-0-env.sh`) avant de lancer Maven : les services lisent leur certificat dans le dossier désigné par `REST_HERO_CERTS_DIR`, pas dans leur jar (voir [1.6](#maven-build-resources-handling)).

Dans [Keycloak](https://host.docker.internal/auth/admin/master/console/#/labs/realm-settings/email), éditer le mot de passe SMTP avec la valeur de `secrets/mail/password.txt`.

Les services Docker :

- https://host.docker.internal/ui/ le frontend React (`advisor`/`secret`)
- https://host.docker.internal/auth/admin/master/console/#/labs Keycloak (`admin`/`secret`)
- https://host.docker.internal/grafana
- https://host.docker.internal/mailpit

Pour démarrer le front depuis le répertoire `frontend`:

```bash
npm run dev
```

Pour démarrer les services de l'API depuis un IDE, charger le fichier `.env` dans la run config : il fournit `REST_HERO_CERTS_DIR` (le dossier du certificat) et `SPRING_DATASOURCE_PASSWORD` (le mot de passe des bases).

Les services et la gateway n'écoutent que sur la boucle locale (`server.address: 127.0.0.1`) : aucune autre machine du réseau ne les atteint. Keycloak et le reverse proxy, dans leurs conteneurs, les joignent par `host.docker.internal`, qui pointe sur la boucle locale (Docker Desktop, et l'entrée de `/etc/hosts` ci-dessus). Sous Linux avec Docker Engine, `host.docker.internal` est dans les conteneurs l'adresse du pont Docker, pas la boucle locale : démarrer alors la gateway (et les services que les conteneurs appellent) avec le profil `demo`, qui écoute sur toutes les interfaces (`server.address: 0.0.0.0`).

## 1. <a name="maven-build"/>Build avec Maven

### 1.1. <a name="maven-build-intro"/>Introduction

Par convention, bien que déclarés dans le module parent, les modules d'un projet Maven suivent l'arborescence de
répertoires.

```
api/
|─ account-service
|─ card-service
|─ currency-service
|─ customer-service
|─ gateway
|─ rest-hero-starter-common
```

Ce qui est défini dans le pom parent sert de valeur par défaut pour les modules (group-id, version, dépendances, etc.).

#### 1.1.1. <a name="maven-build-structure"/>Structure

Un _"artifact"_ (livrable) est identifié par ses `groupId`, `artifactId` et `version`.

Les valeurs de `packaging` généralement utilisées sont `pom` et `jar` (avant Boot, on utilisait aussi `war` ou `ear` en
fonction du serveur de déploiement).

Les `licenses`, `developers` et `scm` sont essentiellement informatives (bien que le dernier puisse être utilisé par des
plugins tels que `release`).

`properties` est un ensemble de clefs-valeurs libres qui peuvent être référencées n'importe où dans le module où elles
sont définies, ou dans les modules enfant. Spring Boot définit de très nombreuses versions de librairies de cette
manière. Maven fournie quelques properties contextuelles telles que `project.basedir`, `project.groupId`,
`project.artifactId` et , `project.version`.

Les `modules` enfants à inclure lors de l'exécution des phases d'un module parent doivent être déclarés.

Le `dependencyManagement` permet de définir des versions par défaut pour un module et ses enfants. On peut y importer un
`dependencyManagement` d'un autre POM avec une dépendance de `type` `pom` et un `scope` de type `import` (
`spring-cloud-dependencies` par exemple).

`dependencies`, à la racine du `project`, déclare les dépendances effectives d'un module. Le `scope` d'une dépendance
indique comment elle est fournie et quand elle est utilisée:

- `compile` : valeur par défaut, la dépendance est toujours incluse
- `provided` : fournie à l'exécution, généralement par le conteneur (par exemple la `servlet-api` est déjà dans
  Tomcat) => présent à la compilation et dans les tests mais pas dans le jar
- `runtime` : absent lors de la compilation mais présents lors des tests et dans le jar
- `test` : présent uniquement lors de la compilation des tests et de leur exécution
- `import` : pour référencer un _artifact_ de type `pom`

La section `build` permet de contrôler l'assemblage du projet, notamment via ses sections `plugins` (et
`pluginManagement`) et `resources`.

La section `profiles` permet de surcharger toute partie du build pour certaines exécutions. Dans les TPs, nous utilisons
le profile `openapi` pour ajouter des dépendances à SpringDoc-OpenAPI, lancer l'application avant les tests d'
intégration, récupérer la spec OpenAPI sur la Swagger UI; puis arrêter l'application après les tests d'intégration.

##### T.P.

Initialisation :

```bash
./start-lab.sh 1.1.1
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

#### 1.1.2. <a name="maven-build-phases"/>Phases

- **_validate_** : intégrité des POMs
- _initialize_ : initialisation de l'état du build (propriétés, répertoires de travail)
- _generate-sources_ : génération de code source (par exemple les interfaces `@HttpExchange` de `openapi-generator-maven-plugin`)
- _process-sources_ : traitement des sources avant compilation
- _generate-resources_ : génération de ressources
- _process-resources_ : copie (et filtrage éventuel) des ressources dans `target/classes`
- **_compile_** : compilation de `src/main/java`, processeurs d'annotations compris (Lombok, MapStruct, etc.)
- _process-classes_ : post-traitement des classes compilées
- _generate-test-sources_ : génération de code source pour les tests
- _process-test-sources_ : traitement des sources de test avant compilation
- _generate-test-resources_ : génération de ressources de test
- _process-test-resources_ : copie des ressources de test dans `target/test-classes`
- _test-compile_ : compilation de `src/test/java`
- _process-test-classes_ : post-traitement des classes de test compilées
- **_test_** : exécution des tests unitaires
- _prepare-package_ : dernières préparations avant l'assemblage
- **_package_** : assemblage du jar / war
- _pre-integration-test_ : préparation de l'environnement des tests d'intégration (le profil `openapi` y démarre WireMock et l'application)
- _integration-test_ : exécution de ce qui a besoin de l'application démarrée (le profil `openapi` y télécharge la spec OpenAPI)
- _post-integration-test_ : nettoyage de l'environnement des tests d'intégration (arrêt de l'application)
- **_verify_** : assertions sur l'état de sortie de l'environnement de build (peu utilisé)
- **_install_** : copie des packages dans le repo local
- **_deploy_** : export des packages sur le repo distant

L'exécution d'une phase implique celle de toutes les phases précédentes. `mvn install` et
`mvn validate compile test package verify install` reviennent donc au même.

Il est possible de "sauter" les tests avec l'option `-DskipTests`: `mvn install -DskipTests`.

L'exécution d'une phase sur un module provoque son exécution sur l'ensemble de ses modules enfants. Pour exécuter un
module spécifique, préciser son nom avec l'option `-pl` mais attention, pour que les dépendances soient aussi
assemblées, il faut ajouter `-am`. Par exemple (`mvn install -pl account-service -am`)

##### T.P.

Initialisation :

```bash
./start-lab.sh 1.1.2
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

### 1.2. <a name="maven-build-dependencies"/>Dépendances

Spring Boot gère la compatibilité d'un très grand nombre de dépendances. Pour initier un projet,
utiliser https://start.spring.io ou un plugin équivalent de l'IDE.

Dépendances utilisées durant les TPs:

- `spring-boot-starter-webmvc` : appli web
- `spring-boot-starter-validation` : validation des entrées
- `spring-boot-starter-data-jpa` : ORM et accès à la BDD
- `spring-data-envers` : versioning automatique des entités
- `spring-boot-starter-oauth2-resource-server` : autorisation d'accès aux ressources REST
- `spring-boot-starter-cache` avec `caffeine` : mise en cache pour limiter les accès en BDD et les appels inter-services
- `spring-boot-starter-actuator` : liveness et readiness probes
- `spring-boot-starter-opentelemetry`, `opentelemetry-logback-appender-1.0` et `spring-boot-starter-aspectj` :
  observabilité avec logging et `@Observed`
- `spring-boot-starter-restclient`, `spring-boot-starter-oauth2-client` et `spring-addons-starter-rest` : consommation
  d'un service REST distant autorisé avec OAuth2 (utilisateurs dans Keycloak via son Admin API)
- `spring-cloud-dependencies`et `spring-cloud-starter-gateway-server-webmvc` : Gateway pour routage avec `TokenRelay`
- `swagger-annotations` : documentation OpenAPI
- `spring-boot-devtools` : redémarre l'app après édition de code
- `spring-boot-configuration-processor` : génère les méta-données des `application.properties` à partir des
  `@ApplicationProperties`
- `lombok` : réduction de la verbosité Java
- `spring-addons-starter-oidc` : auto-configuration OIDC supplémentaire
- `mapstruct` : mapping automatique
- `jspecify` : null safety

##### T.P.

Initialisation :

```bash
./start-lab.sh 1.2
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

### 1.3. <a name="maven-build-annotations-preprocessing"/>Processeurs d’annotations à la compilation

`lombok`, `mapstruct`, `hibernate-jpamodelgen`, `spring-boot-configuration-processor` et `therapi-runtime-javadoc-scribe` génèrent du code à
partir d'annotations. Il faut indiquer au `maven-compiler-plugin` l'ordre dans lequel les appliquer (par exemple
Mapstruct utilise les accesseurs générés par Lombok).

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <annotationProcessorPaths combine.children="append">
      <path>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
      </path>
      <path>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct-processor</artifactId>
        <version>${mapstruct.version}</version>
      </path>
      <path>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok-mapstruct-binding</artifactId>
        <version>${lombok-mapstruct-binding.version}</version>
      </path>
      <path>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-configuration-processor</artifactId>
      </path>
      <path>
        <groupId>com.github.therapi</groupId>
        <artifactId>therapi-runtime-javadoc-scribe</artifactId>
      </path>
      <path>
        <groupId>org.hibernate</groupId>
        <artifactId>hibernate-jpamodelgen</artifactId>
        <version>${hibernate.version}</version>
      </path>
    </annotationProcessorPaths>
  </configuration>
</plugin>
```

##### T.P.

Initialisation :

```bash
./start-lab.sh 1.3
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

### 1.4. <a name="maven-build-openapi-spec-generation"/>Génération de spec OpenAPI à partir du code source

Swagger peut générer des specs OpenAPI à partir de code Java. Il expose cette spec au runtime. Par défaut, la spec
elle-même est disponible sur `/v3/api-docs`.

Spring ayant de nombreuses conventions qui lui sont propres, il faut ajouter des métadonnées. Une partie est générée
automatiquement par `springdoc-openapi`, mais il faut souvent compléter avec des annotations Swagger, notamment pour les
_request parameters_ convertis automatiquement par Spring Web.

Pour éviter tout impact au runtime, la dépendance à `springdoc-openapi-starter-webmvc-api` et l'exécution du
`springdoc-openapi-maven-plugin` sont isolées dans un `profile` Maven.

Le `springdoc-openapi-maven-plugin` s'exécute pendant la phase `verify` en récupérant la spec OpenAPI sur`/v3/api-docs`.
Il faut donc préalablement démarrer l'application (utilisation du `spring-boot-maven-plugin`). Les clients OIDC ayant
besoin de récupérer la configuration OpenID du provider, nous utiliserons le `wiremock-maven-plugin`pour en exposer une.

```xml
<profile>
  <id>openapi</id>
  <dependencies>
    <dependency>
      <groupId>com.github.therapi</groupId>
      <artifactId>therapi-runtime-javadoc</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springdoc</groupId>
      <artifactId>springdoc-openapi-starter-webmvc-api</artifactId>
    </dependency>
    <dependency>
      <groupId>com.c4-soft.springaddons</groupId>
      <artifactId>spring-addons-starter-openapi</artifactId>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <!-- des ports libres, réservés au moment du build (jamais dans <properties>, figées avant) -->
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>build-helper-maven-plugin</artifactId>
        <executions>
          <execution>
            <id>reserve-network-ports</id>
            <phase>pre-integration-test</phase>
            <goals>
              <goal>reserve-network-port</goal>
            </goals>
            <configuration>
              <portNames>
                <portName>keycloak-wiremock.port</portName>
                <portName>integration-tests.port</portName>
                <portName>spring-boot-jmx.port</portName>
              </portNames>
            </configuration>
          </execution>
        </executions>
      </plugin>
      <plugin>
        <groupId>uk.co.automatictester</groupId>
        <artifactId>wiremock-maven-plugin</artifactId>
        <executions>
          <execution>
            <id>keycloak-wiremock</id>
            <goals>
              <goal>run</goal>
              <goal>stop</goal>
            </goals>
            <configuration>
              <dir>src/test/resources/wiremock/keycloak</dir>
              <params>--port=${keycloak-wiremock.port}</params>
            </configuration>
          </execution>
        </executions>
      </plugin>
      <plugin>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-maven-plugin</artifactId>
        <configuration>
          <apiDocsUrl>${integration-tests.scheme}://${integration-tests.hostname}:${integration-tests.port}/v3/api-docs</apiDocsUrl>
        </configuration>
        <executions>
          <execution>
            <id>integration-test</id>
            <goals>
              <goal>generate</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
        <configuration>
          <profiles>h2,no-observability</profiles>
          <!-- commun à start et stop -->
          <jmxPort>${spring-boot-jmx.port}</jmxPort>
        </configuration>
        <executions>
          <execution>
            <id>pre-integration-test</id>
            <goals>
              <goal>start</goal>
            </goals>
            <configuration>
              <arguments>
                <argument>--issuer=http://localhost:${keycloak-wiremock.port}/auth/realms/labs</argument>
                <argument>--server.port=${integration-tests.port}</argument>
              </arguments>
            </configuration>
          </execution>
          <execution>
            <id>post-integration-test</id>
            <goals>
              <goal>stop</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</profile>
```

Aucun port n'est figé : le `build-helper-maven-plugin` réserve trois ports libres au moment du build (celui de WireMock,
celui de l'application, celui de JMX par lequel le goal `stop` arrête l'application), et chaque plugin les lit. Un
port fixe (8081, 8089...) échoue dès qu'une autre application l'occupe : le service lancé depuis l'IDE, un second
build en parallèle, un autre projet. Les ports réservés ne sont jamais déclarés dans les `<properties>` du pom : Maven
les y figerait avant que la réservation ne les fixe. Le plugin est déclaré en premier : dans la phase
`pre-integration-test`, il s'exécute avant WireMock et le démarrage de l'application.

Comme le port de WireMock change à chaque build, sa réponse de découverte OpenID ne peut pas écrire l'URL de l'émetteur
en dur : elle la compose avec le _response templating_ de WireMock, à partir de l'URL de la requête.

```json
{
  "request": { "method": "GET", "url": "/auth/realms/labs/.well-known/openid-configuration" },
  "response": {
    "status": 200,
    "transformers": ["response-template"],
    "headers": { "Content-Type": "application/json" },
    "jsonBody": {
      "issuer": "{{{request.baseUrl}}}/auth/realms/labs",
      "jwks_uri": "{{{request.baseUrl}}}/auth/realms/labs/protocol/openid-connect/certs"
    }
  }
}
```

##### T.P.

Initialisation :

```bash
./start-lab.sh 1.4
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

### 1.5. <a name="maven-build-openapi-client-code-generation"/>Génération de code client à partir de spec OpenAPI

Le `openapi-generator-maven-plugin` permet de générer beaucoup de code à partir d'une spec OpenAPI. Ici nous nous
intéressons aux interfaces `@HttpExchange` dont Spring sait générer des implémentations.

Voici son management dans le POM parent :

```xml
<plugin>
  <groupId>org.openapitools</groupId>
  <artifactId>openapi-generator-maven-plugin</artifactId>
  <version>${openapi-generator-maven-plugin.version}</version>
  <configuration>
    <generatorName>spring</generatorName>
    <cleanupOutput>false</cleanupOutput>
    <skipIfSpecIsUnchanged>true</skipIfSpecIsUnchanged>
    <generateApiTests>false</generateApiTests>
    <generateModelDocumentation>false</generateModelDocumentation>
    <generateModelTests>false</generateModelTests>
    <generateSupportingFiles>false</generateSupportingFiles>
    <configOptions>
      <documentationProvider>none</documentationProvider>
      <annotationLibrary>none</annotationLibrary>
      <ensureUniqueParams>true</ensureUniqueParams>
      <generateBuilders>false</generateBuilders>
      <interfaceOnly>true</interfaceOnly>
      <library>spring-http-interface</library>
      <openApiNullable>false</openApiNullable>
      <serializableModel>true</serializableModel>
      <skipDefaultInterface>true</skipDefaultInterface>
      <useJakartaEe>true</useJakartaEe>
      <useOptional>true</useOptional>
    </configOptions>
  </configuration>
</plugin>
```

Il faut ensuite l'exécuter dans chaque module avec :

```xml
<plugin>
  <groupId>org.openapitools</groupId>
  <artifactId>openapi-generator-maven-plugin</artifactId>
  <executions>
    <execution>
      <goals>
        <goal>generate</goal>
      </goals>
      <configuration>
        <!-- Keycloak's API V1 spec is invalid -->
        <skipValidateSpec>true</skipValidateSpec>
        <inputSpec>${project.basedir}/../keycloak-admin-api.openapi.json</inputSpec>
        <apiPackage>org.keycloak.admin.api</apiPackage>
        <modelPackage>org.keycloak.admin.model</modelPackage>
        <!-- prevent a name collision between the
        deprecated authTime (Integer) and the new auth_time
        (Long) in IDToken -->
        <nameMappings>auth_time=authTimeLong</nameMappings>
      </configuration>
    </execution>
  </executions>
</plugin>
```

La spec exposée par Keycloak étant imparfaite et le code généré comprenant des imports inutilisés qui posent des
problèmes de compilation, on applique le `fmt-maven-plugin`:

```xml
<plugin>
    <groupId>com.spotify.fmt</groupId>
    <artifactId>fmt-maven-plugin</artifactId>
    <version>2.29</version>
    <configuration>
      <sourceDirectory>target/generated-sources</sourceDirectory>
      <verbose>true</verbose>
      <filesNamePattern>.*\.java</filesNamePattern>
      <skip>false</skip>
      <skipSourceDirectory>false</skipSourceDirectory>
      <skipTestSourceDirectory>true</skipTestSourceDirectory>
      <skipSortingImports>false</skipSortingImports>
      <style>google</style>
    </configuration>
    <executions>
      <execution>
        <goals>
          <goal>format</goal>
        </goals>
      </execution>
    </executions>
</plugin>
```

##### T.P.

Initialisation :

```bash
./start-lab.sh 1.5
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

### 1.6. <a name="maven-build-resources-handling"/>Manipulation des ressources

Par défaut, Maven utilise les ressources de `src/main/resources` et `src/test/resources` telles quelles. Il est possible
de modifier ce comportement dans le `build`. Dès qu'un bloc `<resources>` est déclaré, il **remplace** les répertoires
par défaut au lieu de les compléter : il faut alors y lister aussi `src/main/resources`.

Un répertoire de ressources peut aussi être _filtré_ : Maven y remplace des expressions par la valeur de ses propriétés
(`project.version`, `project.artifactId`, etc.). Le parent de Spring Boot change le délimiteur en `@...@` pour que ces
expressions ne se confondent pas avec les placeholders `${...}` de Spring.

```xml
<resources>
  <resource>
    <directory>src/main/resources</directory>
  </resource>
  <resource>
    <directory>src/main/resources-filtered</directory>
    <filtering>true</filtering>
  </resource>
</resources>
```

```properties
# src/main/resources-filtered/META-INF/rest-hero-build.properties
build.artifact=@project.artifactId@
build.version=@project.version@
```

#### Jamais de clé privée dans un jar

Ce que contient un jar se copie, se publie et s'embarque dans des images : un jar ne doit contenir **ni clé privée, ni
keystore, ni mot de passe de keystore**. Une version précédente de ce support déclarait le dossier `certs/` comme
répertoire de ressources pour lire le certificat dans le classpath : la clé privée de développement se retrouvait alors
dans chaque jar. Ce n'est plus le cas, de deux manières :

- Les services lisent leur certificat et sa clé dans un dossier extérieur au jar, désigné par la variable
  d'environnement `REST_HERO_CERTS_DIR` (`build-0-env.sh` l'écrit dans `.env`) :

```yml
dev-certs-dir: ${REST_HERO_CERTS_DIR}
spring:
  ssl:
    bundle:
      pem:
        self-signed:
          keystore:
            certificate: file:${dev-certs-dir}/${cn}.crt
            private-key: file:${dev-certs-dir}/${cn}.key
```

- Le build le vérifie. Le pom parent déclare, pour tous ses modules, une exécution de `maven-antrun-plugin` en phase
  `verify` qui lance `api/jar-content-check.xml` sur le jar produit, et échoue en nommant chaque entrée interdite
  (`*.key`, `*.jks`, `*.p12`, `password.txt`, ou un fichier PEM contenant une clé privée) :

```
jar-content-check: .../currency-service-0.0.1-SNAPSHOT.jar contains a private key, a keystore or a keystore password, which must never be packaged:
BOOT-INF/classes/leaked.key
```

  On retire alors le fichier du module, jamais la vérification. Les images construites par Spring Boot ne reçoivent que
  le certificat (dossier `certs/ca-certificates`), sans clé.

##### T.P.

Initialisation :

```bash
./start-lab.sh 1.6
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

### 1.7. <a name="maven-profiles"/>Profiles Maven

Il est possible de définir un `profile` Maven pour lequel à peu près n'importe quoi peut être redéfini (properties, dependencies, plugin à appliquer, etc.). C'est ce qui est fait dans les modules pour :

- basculer entre les dépendances pour H2 et celles pour PostgreSQL
- activer la Swagger-UI (avec les dépendances springdoc-openapi), démarrer puis arrêter l'application autour des tests d'intégration Maven et enfin récupérer la spec OpenAPI exposée par Swagger au runtime pour l'écrire dans le système de fichier

Pour activer un ou plusieurs profils, ajouter l'option `-P` (majuscule) immédiatement suivie des profils séparés par des virgules

```bash
mvn clean install -Popenapi,h2
```

Un profil peut être activé par défaut. C'est le cas du profil `postgresql` dans les modules.

Attention, dès qu'au moins un profil est activé de manière explicite, il n'y a plus d'activation par défaut. Dans ce projet, on associera donc toujours le profil `openapi` soit au profil `h2` (comme ci-dessus) soit au profil `postgresql`.

##### T.P.

Initialisation :

```bash
./start-lab.sh 1.7
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

## 2. <a name="spring"/>Fondamentaux Spring

### 2.1. <a name="spring-di"/>Injection de dépendance

L'injection de dépendances est le fait de compter sur le conteneur d'application pour fournir à un objet ceux dont il dépend pour accomplir ses tâches.

Je recommande de faire l'injection par le biais du constructeur.

Par exemple, pour l'`AccountController` qui a besoin de collaborer avec les

- `AccountRepository` pour manipuler les comptes en base de données
- `AccountMapper` pour faire des conversions entre DTOs et objets métier
- `CustomersApi` pour dialoguer avec le `customer-service`

```java
@RestController
public class AccountController {
  private final AccountRepository accountRepo;
  private final AccountMapper accountMapper;
  private final CustomersApi customersApi;

  // Peut être remplacé par @RequiredArgsContructor de Lombok
  public AccountController(AccountRepository accountRepo, AccountMapper accountMapper, CustomersApi customersApi) {
    this.accountRepo = accountRepo;
    this.accountMapper = accountMapper;
    this.customersApi = customersApi;
  }
}
```

Spring s'occupe d'instancier les classes dans le bon ordre.

Lorsque plusieurs beans ont le même type, ils sont résolus par un _qualifier_ qui est par défaut le nom de la méthode `@Bean` qui a instancié chacun d'eux. Un exemple tiré de la `RestConfiguration` de l'account-service dans lequel `customerServiceClient` et `currenciesServiceClient` sont deux instances de `RestClient` exposées en tant que bean par `spring-addons-starter-rest`:

```yaml
com:
  c4-soft:
    springaddons:
      rest:
        client:
          customer-service-client:
            base-url: https://localhost:8081
          currencies-service-client:
            base-url: https://localhost:8084
```

```java
@Configuration
public class RestConfiguration {
  @Bean
  CustomersApi customersApi(RestClient customerServiceClient) throws Exception {
    return new RestClientHttpExchangeProxyFactoryBean<>(CustomersApi.class, customerServiceClient)
        .getObject();
  }

  @Bean
  CurrenciesApi currenciesApi(RestClient currenciesServiceClient) throws Exception {
    return new RestClientHttpExchangeProxyFactoryBean<>(
        CurrenciesApi.class,
        currenciesServiceClient).getObject();
  }
```

Il est possible de surcharger le _qualifier_ par défaut lors de la définition d'un bean comme lors de son injection avec `@Qualifier`.

Pour rendre une dépendance optionnelle (on s'attend à ce qu'il soit possible que la configuration de l'application puisse ne pas fournir un bean), typer cette dépendance avec `Optional<T>`, ou avec `ObjectProvider<T>` quand il peut y en avoir zéro, un ou plusieurs. Le module `rest-hero-problem` laisse ainsi chaque application déclarer, ou non, des exclusions à son traitement des exceptions :

```java
@Configuration(proxyBeanMethods = false)
public class ProblemWebMvcConfiguration implements WebMvcConfigurer {
  private final List<ProblemResolutionExclusion> exclusions;

  public ProblemWebMvcConfiguration(ObjectProvider<ProblemResolutionExclusion> exclusions) {
    // aucune, une ou plusieurs, dans l'ordre de leur @Order
    this.exclusions = exclusions.orderedStream().toList();
  }
}
```

Les composants fournis par le biais de l'injection de dépendances peuvent être facilement remplacés pendant les tests.

##### T.P.

Initialisation :

```bash
./start-lab.sh 2.1
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

### 2.2. <a name="spring-components"/>`@Component` et variantes

Les objets instanciés par Spring sont appelés des beans. Ils sont généralement détectés automatiquement lors du component scan grâce à l'annotation `@Component` ou à l'une de ses spécialisations, notamment :

- `@RestController` : expose des endpoints HTTP REST.
- `@Service` : porte de la logique métier.
- `@Repository` : accès aux données.
- `@Configuration` : déclare des beans à l'aide de méthodes @Bean.
- `@Component` : composant générique lorsqu'aucune autre annotation n'est pertinente (convertisseurs, mappers, validateurs, etc.).

```java
@Component
static class SecurityAwareRevisionListener implements RevisionListener {

  @Override
  public void newRevision(@Nullable Object revisionEntity) {
    if (SecurityContextHolder.getContext().getAuthentication() instanceof Authentication auth
        && revisionEntity instanceof Revinfo rev) {
      rev.setUsername(auth.getName());
    }
  }
}
```

Les `@Component` peuvent comporter des méthodes décorées avec `@PostConstruct` et `@PreDestroy`, ce qui permet de faire exécuter au conteneur d'application du code d'initialisation ou de nettoyage. C'est utile lorsqu'on initialise un cache, valide une configuration, ouvre une connexion, etc.

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class FrankfurterForexService implements ForexService {
  public static final Currency PIVOT_CURR = Currency.EUR;

  private final CachingRatesRepository ratesRepo;

  @PostConstruct
  void warmUp() {
    for (final var curr : Currency.values()) {
      if (!curr.equals(PIVOT_CURR)) {
        ratesRepo.fetchRate(PIVOT_CURR, curr);
      }
    }
  }
}
```

##### T.P.

Initialisation :

```bash
./start-lab.sh 2.2
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

### 2.3. <a name="spring-properties"/>Configuration externe

La configuration Spring Boot s'appuie en grande partie sur les _properties_ avec comme source principale `application.properties` ou, comme c'est le cas dans ce projet, `application.yml`.

```yaml
spring:
  datasource:
    password: ${SPRING_DATASOURCE_PASSWORD}
```

Une valeur peut référencer une variable d'environnement avec un _placeholder_ : `${NOM}` est remplacé par la valeur de la
variable, et `${NOM:défaut}` par `défaut` quand elle n'est pas définie. Pour un secret, pas de valeur par défaut :
l'application refuse de démarrer plutôt que d'utiliser un mot de passe prévisible. `build-0-env.sh` écrit les valeurs
de développement dans le fichier `.env` (ignoré par Git), que `build-openapi.sh` charge et qu'une run config d'IDE peut
charger.

Les propriétés qui sont définies dans `application.yml` peuvent aussi être surchargées par

- des variables d'environnement

```bash
SPRING_DATASOURCE_PASSWORD=secret
java -jar account-service.jar
```

- des arguments en ligne de commande

```bash
java -jar account-service.jar --spring.datasource.password=secret
# Ou
java -jar -Dspring.datasource.password=secret account-service.jar
```

Il est possible d'accéder à n'importe quelle _property_ en utilisant `@Value` lors de l'injection de dépendances

```java
@Component
public class MyConfigurableComponent {
  public MyConfigurableComponent(@Value("${spring.datasource.password}") String datassourcePassword) {
    // ...
  }
}
```

Pour définir ses propres propriétés de configuration, le plus commode est d'employer `@ConfigurationProperties`

```java
@SpringBootApplication
@ConfigurationPropertiesScan
public class CustomerServiceApplication {
}
```

```java
@ConfigurationProperties(prefix = "keycloak-admin-api")
@Data
public class KeycloakAdminApiProperties {

  private final URI baseUri;

  private final String realmName;
}
```

##### T.P.

Initialisation :

```bash
./start-lab.sh 2.3
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

### 2.4. <a name="spring-configuration"/>`@Configuration` et `@Bean`

Toutes les classes utiles à l'application ne peuvent pas être annotées avec `@Component`. C'est notamment le cas :

- des classes provenant de bibliothèques tierces ;
- lorsque plusieurs instances d'un même type doivent être configurées différemment ;
- lorsque l'instanciation nécessite une logique particulière.

Une classe `@Configuration` permet alors de déclarer explicitement les beans avec des méthodes `@Bean`.

```java
@Configuration
public class RestConfiguration {

  @Bean
  CustomersApi customersApi(RestClient customerServiceClient) throws Exception {
    return new RestClientHttpExchangeProxyFactoryBean<>(CustomersApi.class, customerServiceClient).getObject();
  }
}
```

Les paramètres d'une méthode `@Bean` sont injectés par Spring.

Je recommande de regrouper les beans ayant une responsabilité commune dans une même classe de configuration (`RestConfiguration`, `CacheConfiguration`, `WebConfiguration`, etc.) plutôt que de créer une configuration unique pour toute l'application.

##### T.P.

Initialisation :

```bash
./start-lab.sh 2.4
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

### 2.5. <a name="spring-proxies"/>Proxies générés

Spring génère fréquemment des proxies autour des beans afin d'ajouter des comportements transverses (cross-cutting concerns) sans modifier le code métier.

C'est notamment le cas pour :

- `@Transactional`
- `@Cacheable`, `@CachePut` et `@CacheEvict`
- `@Observed`
- `@PreAuthorize`

Le proxy intercepte les appels à une méthode avant de déléguer au bean original.

Une conséquence importante est qu'un appel d'une méthode d'un bean vers une autre méthode du même bean ne passe pas par le proxy.

Dans l'exemple suivant, `@Transactional` est ignoré car `saveAccount()` est dans le même bean :

```java
@Service
public class AccountService {

  public void createAccount() {
    // N'active PAS @Transactional
    saveAccount();
  }

  @Transactional
  public void saveAccount() {
    // ...
  }
}
```

Une solution :

```java
@Service
@RequiredArgsConstructor
public class AccountService {
  // Le bean injecté est un proxy autour du TransactionalAccountService
  // ajoutant la gestion de transaction
  private final TransactionalAccountService delegate;

  public void createAccount() {
    delegate.saveAccount();
  }

  @Repository
  static class TransactionalAccountService {
    @Transactional
    public void saveAccount() {
      // ...
    }
  }
}
```

##### T.P.

Initialisation :

```bash
./start-lab.sh 2.5
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

### 2.6. <a name="spring-testing"/>Tests

Spring Boot fournit plusieurs niveaux de tests. Plus le contexte Spring chargé est réduit, plus les tests sont rapides.

Il faut :

- privilégier les tests unitaires lorsque le comportement peut être vérifié sans démarrer Spring
- utiliser les tests de tranche pour tester les `@RestController` avec `@WebMvcTest` et le `@Repository` avec `@DataJpaTest`
- limiter au strict nécessaire les `@SpringBootTest`

Pour les tests d'accès aux données, `@DataJpaTest` effectue chaque test dans une transaction et effectue un rollback.

Les dépendances injectées par Spring peuvent être :

- remplacées par des `@MockitoBean` (ou des implémentations spécifiques aux tests)
- importées explicitement avec `@Import({})` si elles ne font pas partie de la _tranche_ prévue par Spring

##### T.P.

Initialisation :

```bash
./start-lab.sh 2.6
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

### 2.7. <a name="spring-boot-starter"/>Starter Spring Boot

Un starter Spring Boot sert à partager de l'auto-configuration.

Les composants à configurer dans les applications qui l'utilisent sont définis dans `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

```
com.c4soft.resthero.commons.CommonWebConfiguration
com.c4soft.resthero.commons.domain.IbanStringMapper
com.c4soft.resthero.commons.messaging.EventsRabbitConfiguration
```

Lors de la création de starters, il est important d'être peu intrusif et de laisser la main à l'application pour surcharger l'auto-configuration proposée. Les annotations `@ConditionalOn...` telles que `@ConditionalOnMissingBean` et `@ConditionalOnProperty` peuvent alors trouver tout leur intérêt.

Le projet a un second starter, `rest-hero-problem`, qui porte le contrat d'erreur de toutes les applications
([4.4](#rest-controller-exceptions)). Ses composants ne sont pas dans le _component scan_ des services (ils sont dans le
package `com.c4soft.resthero.problem`) : c'est le fichier d'imports qui les apporte. Un second fichier,
`META-INF/spring/org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc.imports`, les apporte aussi aux
tranches de test `@WebMvcTest`, qui n'appliquent pas l'auto-configuration générale : sans lui, un test de contrôleur
passerait avec des réponses d'erreur différentes de celles de l'application.

##### T.P.

Initialisation :

```bash
./start-lab.sh 2.7
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

## 3. <a name="jpa"/>Modèles objet-relationnel et accès aux données

Les bases de données relationnelles sont modélisées avec des entités (tables) et des relations (clefs étrangères).

Le Modèle objet de Java ne peut être traduit directement en une représentation entité-relation (héritage, relations bi-directionnelles, ...)

La JPA (Java Persistence API) permet de faire le pont entre les deux représentations (classes VS entité-relation). Il
permet l'ORM (Object-Relational Mapping).

**La Javadoc JPA est excellente et contient de très nombreux exemples.** Il faut la consulter sans retenue.

### 3.1. <a name="jpa-entity"/>`@Entity`

Une entité est une classe mappée sur une table en base de données.

Son `@Id` correspond à la clef primaire de la table.

Elle doit avoir un constructeur par défaut (sans paramètre) dont la visibilité peut être restreinte.

Il est possible (et souvent recommandé) de limiter les méthodes `equals` et `hashCode` à la (ou aux) propriété(s) `@Id`.

Une entité expose ses propriétés en lecture (`@Getter`), pas en écriture : pas de `@Data` ni de `@Setter`. Elle change
par des **méthodes métier** nommées comme ce qui arrive dans le domaine (`activate()`, `changeCeilings(...)`,
`credit(...)`), qui gardent ses invariants : un setter laisse n'importe quel appelant la mettre dans un état interdit
(un compte crédité dans une autre devise). Hibernate (accès par les champs) et MapStruct n'ont pas besoin de setters.
Une entité modifiée après sa création porte un `@Version` : deux mises à jour concurrentes ne s'écrasent pas, la seconde
échoue (`409 concurrent-modification`, voir [4.4](#rest-controller-exceptions)).

```java
@Entity
@Table(name = "cards")
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@Builder(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Card implements Serializable {

  @Id
  @Column(name = "number")
  @EqualsAndHashCode.Include
  @ToString.Include
  private String number;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "active", nullable = false)
  @Builder.Default
  private boolean active = false;

  public void activate() {
    this.active = true;
  }

  // ...
}
```

##### T.P.

Initialisation :

```bash
./start-lab.sh 3.1
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

### 3.2. <a name="jpa-generated-ids"/>Identifiants générés

H2 et PostgreSQL utilisent les séquences pour les identifiants numériques auto-générés (pas de PK auto-incrémentée comme
MySQL par exemple).

`@GeneratedValue` indique qu'une valeur est fournie par la BDD lors du 1er enregistrement d'une entité. Elle est associée
à `@Id` et doit référencer un générateur (dans le cas de H2 ou de PostgreSQL, une séquence).

Les séquences sont décrites avec `@SequenceGenerator`.

```java
@Id
@GeneratedValue(generator = "cardPaymentSeq")
@SequenceGenerator(name = "cardPaymentSeq", sequenceName = "payment_seq", allocationSize = 1)
private Long id;
```

##### T.P.

Initialisation :

```bash
./start-lab.sh 3.2
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

### 3.3. <a name="jpa-relations"/>Relations

Une propriété ayant pour type une autre entité doit porter `@OneToOne` ou `@ManyToOne`.

```java
@Entity
public class CardPayment {

  @Id
  private Long id;

  @ManyToOne
  @JoinColumn(name = "card_number", nullable = false, updatable = false)
  private Card card;
}
```

Une propriété ayant pour type une collection d'entités doit être décorée avec `@OneToMany` ou `@ManyToMany`.

En cas de relation bidirectionnelle, il faut indiquer un `mappedBy` du côté _"faible"_ (`@OneToMany` ou un des deux
`@OneToOne`).

`@Embeddable` indique qu'une classe n'est pas mappée sur une table. Ses propriétés sont ajoutées aux colonnes de la
table des entités dans lesquelles elle est `@Embedded`.

```java
@Entity
public class Card {

  @Id
  private String number;

  @Embedded
  private Ceilings ceilings;


  @Embeddable
  public static class Ceilings {

    @Column(name = "transaction_ceiling", nullable = false)
    private Integer transaction;

    @Column(name = "rolling30_ceiling", nullable = false)
    private Integer rolling30;
  }
}
```

##### T.P.

Initialisation :

```bash
./start-lab.sh 3.3
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

### 3.4. <a name="jpa-type-converter"/>Conversion de types

Lorsqu'un objet est mappé sur un type simple en base, il est possible de définir un `@Converter(autoApply = true)` qui
implémente `AttributeConverter<E, C>`.

```java
@Converter(autoApply = true)
public class InstantStringAttributeConverter implements AttributeConverter<Instant, String> {

  @Override
  public String convertToDatabaseColumn(Instant attribute) {
    if (attribute == null) {
      return null;
    }
    return attribute.toString();
  }

  @Override
  public Instant convertToEntityAttribute(String dbData) throws DateTimeParseException {
    if (dbData == null) {
      return null;
    }
    return Instant.parse(dbData);
  }
}
```

Si un `@Converter` n'est pas `autoApply = true`, il faut l'appliquer sur les propriétés à convertir :

```java
@Entity
public class Card {

  @Id
  private String number;

  @Convert(converter = IbanStringAttributeConverter.class)
  private Iban iban;
}
```

##### T.P.

Initialisation :

```bash
./start-lab.sh 3.4
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

### 3.5. <a name="jpa-repositories"/>`@Repository` Spring Data JPA

Leur rôle est de manipuler les données en base. Ce sont généralement des singletons générés par Spring à partir d'une
interface.

Il y a une arborescence d'interfaces qui apportent diverses fonctionnalités nous nous intéressons à
`JpaRepository<E, ID>` (CRUD, éventuellement paginé) et `JpaSpecificationExecutor<E>` (filtres avec spécifications).

```java
// Card entities have an @Id of type String (their number)
interface JpaCardRepository extends JpaRepository<Card, String> {
}
```

##### T.P.

Initialisation :

```bash
./start-lab.sh 3.5
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

### 3.6. <a name="jpa-query-dsl"/>JPA query methods

Spring Data expose
un [DSL pour les opérations simples sur les entités](https://docs.spring.io/spring-data/jpa/reference/repositories/query-keywords-reference.html).

```java
public interface CardPaymentJpaRepository extends JpaRepository<CardPayment, String> {

  Page<CardPayment> findByCardNumber(String cardNumber, Pageable pageable);

  List<CardPayment> findByCardNumberAndTimestampBetween(String cardNumber, Instant from, Instant to);
}
```

##### T.P.

Initialisation :

```bash
./start-lab.sh 3.6
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

### 3.7. <a name="jpa-specifications"/>Spécifications JPA

Lorsque la logique de filtrage devient trop complexe (notamment lors de l'application de critères optionnels), les spécifications JPA sont souvent plus adaptées que les _"query methods"_.

Le `@Repository` qui les utilise doit implémenter `JpaSpecificationExecutor<E>`.

Je recommande d'exposer des factories sur le repo pour convertir les critères de filtre en spécification :

```java
public interface MoneyTransferRepository
    extends JpaRepository<MoneyTransfer, Long>, JpaSpecificationExecutor<MoneyTransfer> {

  static Specification<MoneyTransfer> searchSpec(MoneyTransferFilteringCriteria criteria) {
    var spec = Specification.<MoneyTransfer>unrestricted();

    if (criteria.sourceIban() != null) {
      spec = spec.and(sourceAccountNumberLike(criteria.sourceIban()));
    }
    if (criteria.minAmount() != null) {
      spec = spec.and(amountGe(criteria.minAmount()));
    }

    return orderBytimestampDesc(spec);
  }

  private static Specification<MoneyTransfer> sourceAccountNumberLike(Iban iban) {
    return (root, query, cb) -> cb.equal(root.get(MoneyTransfer_.sourceIban), iban);
  }

  private static Specification<MoneyTransfer> amountGe(Integer digits) {
    return (root, query, cb) -> cb.ge(root.get(MoneyTransfer_.amount).get(Amount_.digits), digits);
  }

  private static Specification<MoneyTransfer> orderBytimestampDesc(
      Specification<MoneyTransfer> spec) {
    return (root, query, cb) -> {
      query.orderBy(cb.desc(root.get(MoneyTransfer_.timestamp)));
      return spec.toPredicate(root, query, cb);
    };
  }
}
```

##### T.P.

Initialisation :

```bash
./start-lab.sh 3.7
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

### 3.8. <a name="jpa-transactions"/>Transactions

Les opérations d'accès aux données en base se font à l'intérieur d'une transaction.

On utilise `@Transactional` pour déclarer qu'une méthode doit être exécutée à l'intérieur d'une transaction.

Les relations (`@OneToMany`, `@OneToOne`, etc.) étant _lazy_ par défaut, il faut parcourir le graphe d'objet à
l'intérieur de la transaction dans laquelle la racine a été récupérée.

Le plus simple est généralement de décorer les méthodes de `@Controller` avec `@Transactional`, mais la logique métier
demande parfois plus de finesse (différentes méthodes exécutées dans des transactions différentes pour que certaines
soient `commit` alors que d'autres sont `rollback`).

```java
@Transactional(readOnly = true)
@GetMapping(BASE_PATH)
public List<AccountResponse> listAccounts(@RequestParam String customerId) {
  final var accounts = accountRepo.findByCustomerId(customerId);
  return accounts.stream().map(accountMapper::map).toList();
}
```

##### T.P.

Initialisation :

```bash
./start-lab.sh 3.8
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

### 3.9. <a name="jpa-envers"/>Hibernate Envers

Hibernate Envers permet de conserver chaque version des entités auditées dans des tables dédiées avec des informations à notre main.

```xml
<dependency>
  <groupId>org.springframework.data</groupId>
  <artifactId>spring-data-envers</artifactId>
</dependency>
```

```java
@Configuration
@EnableEnversRepositories
public class PersistenceConfiguration {
}
```

```java
@Audited
@Entity
public class Account {
}
```

Pour donner accès aux différents états dans lesquels une entité a été sauvegardée, un `@Repository` doit implémenter `RevisionRepository<E, ID, R>`:

```java
interface JpaAccountRepository extends JpaRepository<Account, Long>, RevisionRepository<Account, Long, Long> {}
```

Pour ajouter des données aux révisions, il faut remplacer l'implémentation par défaut de `@RevisionEntity` et fournir une implémentation de `RevisionListener` :

```java
@Component
static class SecurityAwareRevisionListener implements RevisionListener {

  @Override
  public void newRevision(@Nullable Object revisionEntity) {
    if (SecurityContextHolder.getContext().getAuthentication() instanceof Authentication auth
        && revisionEntity instanceof Revinfo rev) {
      rev.setUsername(auth.getName());
    }
  }
}
```

```java
@Entity
@Table(name = "REVINFO")
@RevisionEntity(value = SecurityAwareRevisionListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
static class Revinfo implements Serializable {
  private static final long serialVersionUID = -5382427152828146876L;

  @Id
  @Column(name = "REV")
  @RevisionNumber
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "REVINFO_SEQ")
  @SequenceGenerator(name = "REVINFO_SEQ", sequenceName = "REVINFO_SEQ", allocationSize = 1)
  @EqualsAndHashCode.Include
  @ToString.Include
  private @Nullable Long id;

  @RevisionTimestamp
  @Column(name = "REVTSTMP")
  @Builder.Default
  private long timestamp = new Date().getTime();

  @Column(name = "USERNAME")
  private @Nullable String username;
}
```

##### T.P.

Initialisation :

```bash
./start-lab.sh 3.9
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

### 3.10. <a name="jpa-liquibase"/>Migrations de schéma avec Liquibase

Les tables ne sont jamais créées par Hibernate (`spring.jpa.hibernate.ddl-auto: none`) : le schéma est décrit par des
**changesets** Liquibase, que l'application applique au démarrage (`spring.liquibase.change-log`). Liquibase note dans la
table `databasechangelog` chaque changeset appliqué, avec une somme de contrôle : il n'applique un changeset qu'une fois,
et refuse de démarrer si un changeset déjà appliqué a été modifié.

D'où la règle : **un changeset appliqué ne se modifie jamais**. Pour changer le schéma, on ajoute un changeset, dans un
nouveau fichier inclus après les autres :

```xml
<databaseChangeLog ...>
    <!-- les changesets déjà appliqués, inchangés -->
    <changeSet id="1785605112050-1" author="ch4mp">...</changeSet>

    <include file="changes/1792864800000-1-beneficiaries-renamed-unique-per-customer.xml"
             relativeToChangelogFile="true"/>
</databaseChangeLog>
```

Exemple : la table des bénéficiaires s'appelait `benficiaries`, avec une contrainte unique sur le libellé seul et une
autre sur l'IBAN seul, si bien que deux clients ne pouvaient pas avoir chacun un bénéficiaire « Maman ». Le changelog
de `customer-service` qui corrige cela renomme les tables, remplace les contraintes par des contraintes **par client**
et ajoute la colonne de `@Version` :

```xml
<changeSet id="1792864800000-3" author="ch4mp">
    <renameTable oldTableName="benficiaries" newTableName="beneficiaries"/>
    <renameTable oldTableName="benficiaries_aud" newTableName="beneficiaries_aud"/>
</changeSet>
<changeSet id="1792864800000-6" author="ch4mp">
    <dropUniqueConstraint tableName="beneficiaries" constraintName="uc_43f2eae19d0eabc0cf5df4d4d"/>
    <dropUniqueConstraint tableName="beneficiaries" constraintName="uc_a5c37d5691418578ecd3de5fc"/>
    <addUniqueConstraint tableName="beneficiaries" columnNames="iban, customer_id"
                         constraintName="uc_beneficiaries_iban_customer_id"/>
    <addUniqueConstraint tableName="beneficiaries" columnNames="label, customer_id"
                         constraintName="uc_beneficiaries_label_customer_id"/>
</changeSet>
<changeSet id="1792864800000-7" author="ch4mp">
    <addColumn tableName="beneficiaries">
        <column name="version" type="BIGINT" defaultValueNumeric="0">
            <constraints nullable="false"/>
        </column>
    </addColumn>
</changeSet>
```

Quelques points d'attention :

- **Une même migration pour plusieurs bases.** Les tests et le profil `h2` utilisent H2, la production PostgreSQL. Tous
  les changements Liquibase ne sont pas supportés partout (H2 refuse `renameSequence` et l'`incrementBy` de
  `alterSequence`) : l'attribut `dbms` d'un changeset le réserve à une base, et un changeset frère dit la même chose
  à l'autre.
- **Les tables d'audit d'Envers.** Une révision de suppression (`revtype = 2`) n'enregistre que l'identifiant : les
  colonnes métier d'une table `_aud` doivent accepter `null`, sinon la suppression elle-même échoue.
- **Les séquences.** Une séquence qui avance de 50 alors que l'entité réserve ses identifiants un par un
  (`allocationSize = 1`) donne des trous, et la validation du schéma la refuse.

Le schéma est vérifié par les tests : `@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")` démarre
Liquibase sur une base H2 vide, puis Hibernate compare chaque entité au schéma obtenu (tables, colonnes, types,
séquences) et refuse de démarrer à la moindre différence. Les contraintes que la validation ne voit pas (unicité,
nullabilité des tables d'audit) ont leurs propres tests (`BeneficiaryPersistenceTest`, `BeneficiaryRepositoryTest`).

##### T.P.

Initialisation :

```bash
./start-lab.sh 3.10
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

## 4. <a name="rest-controller"/>Services REST WebMvc avec Spring Boot

Nous nous intéressons ici aux `@RestController` qui forment la façade visible d'une API REST.

### 4.1. <a name="rest-controller-request-mapping"/>`@RequestMapping`

Avec les signatures et types de retour de méthode, `@RequestMapping` participe à la définition d'un endpoint. On utilise
préférentiellement `@RequestMapping` au niveau de la classe pour les définitions communes à toutes les méthodes et ses
spécialisations `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping` et `@PatchMapping` sur chaque méthode.

La signature et le type de retour de la méthode donne les informations sur les entrées / sortie et le `@RequestMapping`
le reste, notamment :

- le verbe HTTP à employer
- le path du endpoint (attention, si le `@RequestMapping` au niveau de classe et une spécialisation au niveau de la
  méthode portent tous deux un path, c'est la concaténation qui est appliquée)
- les `MediaType` acceptés en entrée et ceux supportés en retour

Le bon emploi des verbes HTTP est important pour la sécurité Web : `GET` ne doit être employé que pour des opérations
qui ne changent pas l'état du système.

Pour rappel :

- `GET` est attendu pour les opérations en lecture et ne doit pas porter de _body_. Par exemple, les critères de filtre
  devraient être portés par des _request parameters_. Il est attendu que la réponse à un `GET` soit en statut `200 Ok`et
  ait un _body_.
- `POST` est attendu pour une création de ressource et devrait porter un _body_ avec les informations sur la ressource à
  créer. La réponse devrait être en statut `201 Created`, ne pas avoir de _body_ et porter un header `Location` pointant
  vers la ressource créée.
- `PUT` est attendu pour une modification de ressource existante et devrait porter un _body_ avec les informations de
  mise à jour. La réponse est `204 No Content` : la modification est faite quand la réponse part.
- `DELETE` est attendu pour supprimer une ressource et ne doit pas porter de _body_ (l'URL doit suffire à identifier la
  ressource à supprimer). La réponse est `204 No Content`, pour la même raison.
- `202 Accepted` est réservé aux traitements **asynchrones** : la requête est prise en compte, mais son effet n'est pas
  encore visible quand la réponse part (un traitement par lot, une file de messages). Le renvoyer sur un `PUT` synchrone
  ment au client, qui ne sait plus s'il doit relire la ressource ou attendre.
- `PATCH` est attendu pour la mise à jour partielle d'une ressource (les propriétés manquantes du _body_ sont ignorées).
  Elle est peu usitée et parfois mal supportée par les clients, donc à éviter.

Une différence notable entre `POST` et `PUT` est que lors de la répétition d'une requête `PUT` sur la même ressource et
avec le même _body_, seule la première requête devrait avoir un effet, alors que la répétition d'un `POST` doit créer
autant de ressources (ou tenter de le faire).

##### T.P.

Initialisation :

```bash
./start-lab.sh 4.1
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

### 4.2. <a name="rest-controller-converters"/>Convertisseurs automatiques de Spring

Au niveau du protocole HTTP, toutes les valeurs sont des `String`.

Spring Web comporte de nombreux mécanismes de (dé)sérialisation, notamment :

- JSON (avec Jackson) : convertit les `@RequestBody` et `@ResponseBody`
- le `FormatterRegistry` : enregistre des convertisseurs à appliquer sur les `@PathVariable` et `@RequestParam`.

Spring Data enregistre dans le `FormatterRegistry` un `DomainClassConverter` pour les `@Entity` dont un
`JpaRepository<E, ID>` existe (avec `Integer`, `Long` ou `String` comme type d'identifiant). En considérant que
`MyEntity` a un `@Id Long id` et qu'il existe un `JpaRepository<MyEntity, Long>`, une requête `GET /resources/42` est
routée sur la fonction suivante où `entity` est l'entité avec l'identifiant `42` :

```java
@GetMapping("/resources/{resourceId}")
Resource getResource(@PathVariable("resourceId") MyEntity entity);
```

Mais quand l'identifiant n'existe pas, ce convertisseur renvoie `null`, et la méthode est appelée avec un argument
`null` (ou Spring répond `500`). Dans ce projet, **chaque variable de chemin a son convertisseur explicite**, déclaré
dans une `@Configuration` qui implémente `WebMvcConfigurer`, et qui lève le problème typé de la ressource
([4.4](#rest-controller-exceptions)) quand elle n'existe pas : le client reçoit un `404` dont le `type` dit **quoi** est
introuvable. Un `Converter` ne peut pas déclarer d'exception checkée : le problème est levé `.unchecked()`.

```java
@Configuration
@RequiredArgsConstructor
public class WebConfiguration implements WebMvcConfigurer {
  private final IbanStringMapper ibanStringMapper;
  private final AccountRepository accountRepo;

  @Override
  public void addFormatters(FormatterRegistry registry) {
    registry.addConverter(new StringAccountConverter(ibanStringMapper, accountRepo));
  }

  @RequiredArgsConstructor
  static class StringAccountConverter implements Converter<String, Account> {
    private final IbanStringMapper ibanStringMapper;
    private final AccountRepository accountRepo;

    @Override
    public @Nullable Account convert(@Nullable String source) {
      final var iban = ibanStringMapper.map(source);
      if (iban == null) {
        return null;
      }
      return accountRepo.findByIban(iban).orElseThrow(() -> new ApiProblemException(
          ProblemType.ACCOUNT_NOT_FOUND,
          "No account with IBAN %s".formatted(iban.toHumanReadableString()),
          Map.of("iban", iban.toHumanReadableString())).unchecked());
    }
  }
}
```

##### T.P.

Initialisation :

```bash
./start-lab.sh 4.2
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

### 4.3. <a name="rest-controller-validation"/>Validation des entrées

L'utilisation de `jakarta.validation` permet de détecter au plus tôt les requêtes avec des données invalides. Lorsqu'un
paramètre ou attribut ne respecte pas les contraintes de validation, une `MethodArgumentNotValidException` ou
`ConstraintViolationException` est levée.

```java
public record MoneyTransferRequest(
    @NotNull @IbanString String sourceIban,
    @NotNull @IbanString String destinationIban,
    @NotNull @Min(1) Long amount,
    @NotNull @CurrencyIso3 String currency,
    @NotEmpty @Size(min = 3, max = 256) String label) {
}
```

```java
@PostMapping(BASE_PATH)
public ResponseEntity<Void> transferMoneyBetweenAccounts(@RequestBody @Valid MoneyTransferRequest dto);
```

Il est possible de créer ses propres annotations de validation. De telles annotations doivent faire référence à un
`ConstraintValidator` :

```java
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CurrencyIso3.IbanConstraintValidator.class)
public @interface CurrencyIso3 {
  String message() default "Doesn't look like an ISO 4217 currency code";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  public static class IbanConstraintValidator implements ConstraintValidator<CurrencyIso3, String> {
    @SuppressWarnings("null")
    private static final Pattern ISO_4217 = Pattern.compile("^[A-Z]{3}$");

    @Override
    public boolean isValid(@Nullable String value, @Nullable ConstraintValidatorContext context) {
      if (value == null) {
        return true;
      }
      return ISO_4217.matcher(value).matches();
    }

  }
}
```

Je recommande de laisser passer `null` et de combiner les annotations avec `@NotNull` pour rendre un paramètre /
attribut obligatoire.

Une violation est une erreur du **client**, pas du serveur : la réponse est `422 Unprocessable Content`, en
`application/problem+json`, de type `validation`, avec une propriété `invalidFields` qui associe à chaque champ fautif
le code de la contrainte violée (c'est ce que le frontend affiche sous le champ). Trois exceptions portent ces
violations, selon ce qui est validé : `MethodArgumentNotValidException` (un `@RequestBody @Valid`),
`HandlerMethodValidationException` (un `@RequestParam` ou une `@PathVariable` annotés) et
`ConstraintViolationException` (une validation hors contrôleur). Le module `rest-hero-problem` les traduit toutes les
trois ([4.4](#rest-controller-exceptions)) ; le test de contrôleur vérifie le `422`, le `type` et le champ nommé dans
`invalidFields`, pas un vague `4xx`.

##### T.P.

Initialisation :

```bash
./start-lab.sh 4.3
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

### 4.4. <a name="rest-controller-exceptions"/>Gestion des exceptions

Lorsqu'une erreur est détectée (technique ou métier), une exception est levée.

Les exceptions sont interceptées dans deux cas principaux :

- on souhaite la contourner (il y a un _plan B_ pour continuer le traitement)
- on souhaite la mapper vers un autre type d'exception (exception technique traduite en exception métier)

Ce qui sort d'une API REST en cas d'erreur est un **contrat** au même titre que les réponses nominales : le client doit
pouvoir distinguer les cas sans lire un message en anglais. Ce projet suit la [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)
(_problem details_) avec un module partagé, `rest-hero-problem` (un starter, voir [2.7](#spring-boot-starter)) :

- `ProblemType` est une énumération **fermée** des types de problèmes de l'application : les génériques
  (`unauthenticated`, `access-denied`, `not-found`, `invalid-request`, `validation`, `server-error`...) et les types
  métier (`account-not-found`, `beneficiary-iban-already-registered`, `card-transaction-ceiling-exceeded`...). Chaque
  type a un statut HTTP et une URN (`urn:rest-hero:problem:account-not-found`) qui sert de **clef de traduction** au
  frontend ;
- `ApiProblemDetail` porte le `type`, le statut, un `detail` technique (en anglais, pour les logs et le développeur) et
  des `parameters` **typés** (l'IBAN refusé, le plafond dépassé, le montant...) avec lesquels le frontend compose le
  message localisé ;
- `ApiProblemException` est une exception **checkée** : une méthode de contrôleur qui la lève le déclare dans sa clause
  `throws`, et son `@Transactional` porte `rollbackFor = ApiProblemException.class` (par défaut, Spring ne fait le
  rollback que sur les `RuntimeException`). Là où une checkée ne peut pas être déclarée (un `Converter`, une lambda),
  `.unchecked()` l'enveloppe dans une `ApiProblemRuntimeException` que le handler déballe ;
- `ApiExceptionHandler`, un `@RestControllerAdvice`, écrit les `ApiProblemDetail` des `ApiProblemException` et des
  trois exceptions de validation (en `422`, avec les `invalidFields`, voir [4.3](#rest-controller-validation)) ;
  `DataAccessProblemHandler` traduit les conflits JPA (`409`) ; un `HandlerExceptionResolver` et le contrôleur `/error`
  servent de filet pour tout le reste (rejets du framework en `400 invalid-request`, erreurs inattendues en
  `500 server-error`, dont le détail reste dans les logs).

```java
@Transactional(rollbackFor = ApiProblemException.class)
@PostMapping(BASE_PATH)
public ResponseEntity<Void> createAccount(@RequestBody @Valid AccountCreationRequest dto) throws ApiProblemException {
  final var iban = Iban.of(dto.iban());
  if (accountRepo.existsByIban(iban)) {
    throw new ApiProblemException(
        ProblemType.ACCOUNT_ALREADY_MANAGED,
        "The account-service already manages account %s".formatted(iban.toHumanReadableString()),
        Map.of("iban", iban.toHumanReadableString()));
  }
  // ...
}
```

Les `@ExceptionHandler` déclarés dans un `@RestControllerAdvice` interceptent les exceptions et définissent la réponse à
envoyer. C'est l'annotation `@RestControllerAdvice` qui les rend visibles à **tous** les contrôleurs :

```java
@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(ApiProblemException.class)
  public ResponseEntity<ApiProblemDetail> handleApiProblem(ApiProblemException ex) {
    return ResponseEntity.status(ex.getType().status())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(ex.toProblemDetail());
  }
}
```

À ne **pas** faire : lever une `ResponseStatusException` ou une `ErrorResponseException` avec un message libre. Le
statut est alors choisi au cas par cas, le `type` est vide, et le message (souvent celui d'une exception technique, SQL
ou réponse d'un autre service) part tel quel au client. Un type de problème coûte une ligne dans `ProblemType`.

##### T.P.

Initialisation :

```bash
./start-lab.sh 4.4
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

### 4.5. <a name="rest-controller-openapi"/>Génération de la documentation OpenAPI

OpenAPI est un standard largement adopté issu de Swagger. Il permet notamment :

- aux développeurs de visualiser les caractéristiques d'une API REST
- aux clients de générer du code pour consommer l'API

`springdoc-openapi` permet de générer le plus gros de la spec à partir du code source en se basant sur les
`@RequestMapping` et les signatures de méthodes. Il a quelques limitations auxquelles il faut pallier avec les
annotation Swagger:

- il ne prend pas en compte le `FormatterRegistry`. Lorsqu'une `@PathVariable` ou un `@RequestParam` utilise une
  conversion implicite. Il faut utiliser `@Parameter` pour indiquer le format attendu dans la requête HTTP.

```java
@GetMapping(TRANSFER_PATH)
public MoneyTransferResponse getMoneyTransfer(
    @Parameter(schema = @Schema(type = "integer"), description = "The ID of the money transfer to retrieve")
    @PathVariable(name = TRANSFER_ID_PLACEHOLDER) MoneyTransfer transfer);
```

- il ne prend pas en compte nativement les `@RequestParam` implicites (plusieurs request-params encapsulés dans un
  pseudo DTO). Il faut utiliser `@ParameterObject`.

```java
@GetMapping(BASE_PATH)
public PagedModel<MoneyTransferResponse> listMoneyTransfers(
    // les propriétés de MoneyTransferFilterRequest et de Pageable sont attendues en tant que request-param individuels dans l'URL
    // GET /money-transfers?sourceIban=FR7612341111&currency=XPF&page=1&size=42
    @Nullable @Valid @ParameterObject MoneyTransferFilterRequest dto,
    @ParameterObject Pageable pageable);
```

##### T.P.

Initialisation :

```bash
./start-lab.sh 4.5
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

### 4.6. <a name="rest-controller-inter-service-communication"/>Appels de services REST externes

Le client REST actuellement recommandé pour les servlets est `RestClient`. `RestTemplate` est en mode maintenance et
`WebClient` est plus adapté aux applications réactives.

Il est possible de l'utiliser directement, mais Spring sait générer un client à partir d'une interface `@HttpExchange`et
d'un `RestClient`. Spring Cloud proposait `@FeignClient` dans le même esprit, mais le projet est passé en mode
maintenance depuis l'apparition des proxy d'`HttpExchange` générés.

Dans ce projet, nous générons les interfaces `@HttpExchange` à partir de specs OpenAPI (en utilisant l'
`openapi-generator-maven-plugin`). Il est toutefois possible de déclarer ces interfaces manuellement, ce qui peut être
utile :

- lorsque l'API à consommer n'expose pas de spec OpenAPI
- lorsqu'on préfère déclarer des dépendances explicites (Maven) entre modules plutôt que de reposer sur les specs OpenAPI

Nous utilisons aussi [
`spring-addons-starter-rest`](https://github.com/ch4mpy/spring-addons/tree/master/spring-addons-starter-rest) pour
auto-configurer des instances de `RestClient` avec l'`application.yml`.

```java
@Configuration
public class RestConfiguration {

  @Bean
  // CustomersApi est une interface `@HttpExchange` générée à partir de la spec OpenAPI du customer-service
  // customerServiceClient est instancié par spring-addons à partir des propriétés customer-service-client ci-dessous
  CustomersApi customersApi(RestClient customerServiceClient) throws Exception {
    return new RestClientHttpExchangeProxyFactoryBean<>(CustomersApi.class, customerServiceClient).getObject();
  }
}
```

```yml
com:
  c4-soft:
    springaddons:
      rest:
        client:
          customer-service-client:
            base-url: ${reverse-proxy-uri}/gateway/m2m
            ssl-bundle: self-signed
            headers:
              Accept:
                - application/json
                - application/problem+json
            authorization:
              oauth2:
                oauth2-registration-id: account-service
```

Lors d'une erreur pendant l'appel REST, `RestClient` soulève une `RestClientException` : une
`HttpClientErrorException` ou `HttpServerErrorException` quand le service répond (chacune a une sous-classe par statut,
`HttpClientErrorException.NotFound` par exemple), une `ResourceAccessException` quand il ne répond pas. Il faut les
intercepter **par type** et les traduire en problème de notre contrat ([4.4](#rest-controller-exceptions)) : le cas
attendu (ici, un client inconnu) devient le type métier qui le décrit ; tout le reste devient
`DOWNSTREAM_SERVICE_ERROR` (`502`), dont le détail reste dans **nos** logs. La réponse du service aval, son statut et son
message ne partent jamais au client : ils ne font pas partie de notre contrat.

```java
try {
  customersApi.getCustomer(dto.customerId());
} catch (HttpClientErrorException.NotFound e) {
  log.warn("Rejecting account {} creation for unknown customer {}", iban, dto.customerId());
  throw new ApiProblemException(
      ProblemType.CUSTOMER_NOT_FOUND,
      "Customer %s is not known by the customer-service".formatted(dto.customerId()),
      Map.of("customerId", dto.customerId()));
} catch (RestClientException e) {
  log.error("Unexpected error while checking customer {} existence in customer-service", dto.customerId(), e);
  throw new ApiProblemException(
      ProblemType.DOWNSTREAM_SERVICE_ERROR,
      "The customer-service could not confirm that the customer exists",
      Map.of("service", "customer-service"));
}
```

##### T.P.

Initialisation :

```bash
./start-lab.sh 4.6
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

### 4.7. <a name="rest-controller-logging"/>Logs

Les logs sont la première source d'audit de l'application. Il est important de loguer suffisamment et au bon niveau:
`error`, `warn`, `info`, `debug` ou `trace`.

`@Slf4j` de Lombok permet de disposer d'un logger nommé `log`.

Avec Spring Boot 4, `spring-boot-starter-opentelemetry` et `opentelemetry-logback-appender-1.0` permettent de pousser
les logs vers Loki avec relativement peu de conf :

- `/src/main/resources/logback-spring.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/base.xml"/>

    <appender name="OTEL" class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="OTEL"/>
    </root>
</configuration>
```

- `/src/main/resources/application.yml`

```yml
management:
  opentelemetry:
    logging:
      export:
        otlp:
          endpoint: http://host.docker.internal:4318/v1/logs
```

##### T.P.

Initialisation :

```bash
./start-lab.sh 4.7
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

## 5. <a name="caching"/>Mise en cache

L'enjeu principal de la mise en cache est l'obsolescence des données. Il faut donc remplacer ou supprimer des données en
cache lors d'accès en écriture, ce qui implique :

- avoir la maîtrise totale de ces écritures
- utiliser un cache distribué (Redis ?) en environnement distribué

Je conseille de créer un cache par type de données et par index. Par exemple, si on souhaite accéder à des instances de
`Bidule` soit par la valeur de leur propriété `truc`, soit par celle de leur propriété `machin`, on créera deux caches:
`bidulesParTruc` et `bidulesParMachin`.

Pour activer la mise en cache dans l'application :

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
<!-- Optionnel. Permet la configuration de politiques d'expirations. -->
<!-- Sans implémentation spécifique, Spring utilise un ConcurrentMapCacheManager -->
<dependency>
  <groupId>com.github.ben-manes.caffeine</groupId>
  <artifactId>caffeine</artifactId>
</dependency>
```

```java
@Configuration
@EnableCaching
public class CacheConfiguration {
  // Nécessaire seulement pour spring.cache.type=simple (Caffeine et autres caches managés par Boot fournissent leur CacheManager)
  @Bean
  CacheManager cacheManager() {
    return new ConcurrentMapCacheManager();
  }
}
```

```yml
spring:
  cache:
    type: simple
---
spring:
  cache:
    type: caffeine
    caffeine:
      spec: expireAfterWrite=60m
```

Pour déclarer un ou plusieurs caches, on décore généralement une classe :

```java
@CacheConfig(cacheNames = {"bidulesParTruc", "bidulesParMachin"})
```

Pour indiquer que la valeur de retour peut être mise en cache :

```java
@Cacheable(cacheNames = "bidulesParTruc")
```

Pour indiquer qu'une opération en écriture nécessite des opérations de mise à jour du cache :

```java
@Caching(put = @CachePut(cacheNames = "bidulesParTruc", key = "#bidule.truc"),
    evict = @CacheEvict(cacheNames = "bidulesParMachin", key = "#bidule.machin"))
```

Lorsqu'une classe expose une interface publique plus importante que nécessaire, cela peut grandement compliquer la
gestion des caches. Je recommande dans ce cas de faire un proxy n'exposant que le strict nécessaire et de gérer les caches
à ce niveau.

##### T.P.

Initialisation :

```bash
./start-lab.sh 5
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

## 6. <a name="messaging"/>Messagerie asynchrone et notifications temps réel

Nous étudions ici le mécanisme par lequel le serveur notifie le frontend qu'un évènement concernant l'utilisateur connecté vient de se produire (par exemple un virement reçu), sans rafraîchissement de page ni polling.

L'architecture retenue a trois maillons :

- un service métier (`account-service`) publie un évènement applicatif sur RabbitMQ dès qu'une action métier réussit ;
- la `gateway` consomme ces évènements et les relaie au navigateur par Server-Sent Events (SSE), un flux HTTP à sens
  unique, plus simple qu'un WebSocket puisqu'on n'a jamais besoin d'écrire du navigateur vers le serveur sur ce canal ;
- le frontend garde des payloads d'évènement volontairement minces (identifiants seulement) et refait un appel REST
  classique dès qu'il est notifié, plutôt que de faire transiter la donnée métier complète par le bus d'évènements.

Le choix du SSE (plutôt que WebSocket/STOMP) tient aussi à la sécurité déjà en place : le flux SSE est exposé sous
`/bff/**`, donc protégé par les mêmes cookies de session que le reste du BFF (voir l'introduction), sans complication
supplémentaire de handshake ni de CSRF puisqu'un abonnement SSE est un simple `GET`. La gateway le réserve aux
sessions authentifiées par deux verrous : son `permit-all` liste une à une les routes relayées aux services (dont le
contrôle d'accès appartient au service), jamais `/bff/**` en bloc, si bien que `/bff/events` n'y figure pas ; et le
contrôleur porte `@PreAuthorize("isAuthenticated()")`, qui tient même si la liste s'élargit un jour. Sans session,
l'abonnement reçoit un `401` de type `unauthenticated`, sans URL de connexion.

Aucune des trois briques n'a besoin de connaître le token `Bearer` de l'utilisateur ni son cookie de session : la
publication d'un évènement est un aller RabbitMQ interne au backend (authentifié par des identifiants applicatifs,
pas par l'utilisateur). Le vrai sujet d'autorisation est ailleurs : garantir qu'un évènement n'est diffusé qu'aux
utilisateurs légitimes pour le voir, ni plus (fuite d'information), ni moins (un conseiller qui a le droit de
consulter n'importe quel compte doit aussi être notifié des évènements sur les comptes qu'il consulte, pas seulement
le client propriétaire). D'où un évènement qui porte, en plus de son type et de son identifiant de ressource, le
`subject` du propriétaire de la ressource _et_ la liste des rôles qui y donnent également accès, à l'image des
expressions `@PreAuthorize` déjà utilisées sur les endpoints REST correspondants.

### 6.1. <a name="messaging-publish"/>Publication d'un événement métier

Chaque service métier publie sur son propre exchange RabbitMQ de type `topic`, plutôt que sur un exchange unique
partagé par toute l'application. Le nom de cet exchange est une propriété externalisée (voir
[2.3](#spring-properties)), dont la valeur est mécaniquement dérivée du nom d'application déjà déclaré
(`spring.application.name`) plutôt qu'une chaîne inventée au cas par cas :

```yaml
rest-hero:
  events:
    exchange-name: rest-hero.${spring.application.name}
```

Ce découpage par service (plutôt qu'un exchange unique `app.events`) évite qu'un service ne puisse publier sous le nom
d'un autre, et respecte la frontière de responsabilité entre services : chacun garde la maîtrise de son propre topic,
tout en réutilisant la même classe de configuration partagée puisque seul le _nom_ change d'un service à l'autre.

Le contrat d'évènement lui-même est partagé (dans `rest-hero-starter-common`), et suffisamment générique :

- `resourceType` est une simple chaîne de caractères à la main de chaque service. Il permet au frontend de savoir quel type de requête doit éventuellement être rejoué (composants affichant des collections de données), voir même quel objet doit être mis à jour (`resourceId`).
- `resourceOwner` et `audienceRoles` décrivent qui doit recevoir l'évènement : le propriétaire de la ressource, et les roles qui y donnent accès. La gateway n'a besoin de rien savoir de plus pour diffuser correctement (voir 6.2)
- `eventType` donne du contexte sur le type d'évènement sur le serveur (création / mise à jour / suppression) et permet au frontend d'afficher un notification, même si la ressource concernée n'est pas actuellement affichée (par exemple _"Vous avez reçu 200 000 XPF"_)

```java
public record DomainEvent(
    String resourceType,
    String resourceId,
    String resourceOwner,
    List<String> audienceRoles,
    EventType eventType,
    Instant occurredAt) {

  public enum EventType {
    CREATE, UPDATE, DELETE;
  }
}
```

La classe qui déclare l'exchange et le convertisseur JSON pour RabbitMQ (`EventsRabbitConfiguration`) est, elle aussi, entièrement partagée et ne dépend que d'une propriété pour connaître le nom de son exchange :

```java
@Configuration
@ConditionalOnClass(RabbitTemplate.class)
@EnableConfigurationProperties(RestHeroEventsProperties.class)
public class EventsRabbitConfiguration {

  @Bean
  TopicExchange eventsExchange(RestHeroEventsProperties properties) {
    return new TopicExchange(properties.getExchangeName());
  }

  @Bean
  MessageConverter eventsMessageConverter(JsonMapper jsonMapper) {
    return new JacksonJsonMessageConverter(jsonMapper);
  }
}
```

Grâce à `@ConditionalOnClass`, cette classe n'est auto-configurée (voir [2.7](#spring-boot-starter)) que dans les modules qui dépendent de à la fois de `rest-hero-starter-common` et de `spring-boot-starter-amqp` (une dépendance optionnelle du starter). Sans cette garde, les service qui ne dépendraient que de `rest-hero-starter-common` échoueraient au démarrage en tentant de charger des types absents de leur classpath.
L'exercice sur cette annotation précisément est traité dans le TP [2.7](#spring-boot-starter), pas ici.

Publier un évènement se résume alors à un appel, juste après l'écriture qui vient de réussir. Dans le cas d'un virement, qui touche potentiellement deux comptes de deux propriétaires différents, un évènement est publié par compte concerné :

```java
rabbitTemplate.convertAndSend(
    eventsExchange.getName(),
    "account.updated",
    new DomainEvent(
        "account",
        account.getIban().toMachineReadableString(),
        account.getCustomerId(),
        List.of("account.read_any"),
        DomainEvent.EventType.UPDATE,
        Instant.now()));
```

`account.read_any` reprend exactement l'autorité déjà utilisée dans les expressions `@PreAuthorize` de
`AccountController` et `MoneyTransferController` : quiconque peut lire n'importe quel compte via REST doit aussi
pouvoir être notifié de ses évolutions.

##### T.P.

Initialisation :

```bash
./start-lab.sh 6.1
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

### 6.2. <a name="messaging-gateway-sse"/>Relais par la gateway : RabbitMQ vers Server-Sent Events

La gateway connaît déjà la liste des services métier (c'est ce qui structure ses routes `/bff/accounts/**`,
`/bff/cards/**`, etc.), donc ça ne l'engage pas davantage de lister aussi, en configuration, les exchanges auxquels
elle s'abonne :

```yaml
rest-hero:
  events:
    subscribed-exchanges:
      - rest-hero.account-service
```

Elle déclare sa propre queue (anonyme, non durable : elle ne doit survivre ni à un redémarrage ni être partagée entre instances) et un binding par exchange écouté, avec une clé de routage `#` (reçoit tout) :

```java
@Bean
Queue gatewayEventsQueue() {
  return new AnonymousQueue();
}

@Bean
Declarables gatewayEventsBindings(Queue gatewayEventsQueue, GatewayEventsProperties properties) {
  final var declarables = new ArrayList<Declarable>();
  for (final var exchangeName : properties.getSubscribedExchanges()) {
    final var exchange = new TopicExchange(exchangeName);
    declarables.add(exchange);
    declarables.add(BindingBuilder.bind(gatewayEventsQueue).to(exchange).with("#"));
  }
  return new Declarables(declarables);
}
```

Un registre garde, en mémoire, la liste des abonnements ouverts : pour chaque `SseEmitter`, le `subject` et les rôles accordés à l'utilisateur au moment de la souscription (les `GrantedAuthority` présentes sur son `Authentication` à cet instant), avec nettoyage automatique à la fermeture, au timeout ou en erreur :

```java
private record Subscription(String subject, Set<String> roles, SseEmitter emitter) {}

public void register(String subject, Set<String> roles, SseEmitter emitter) {
  final var subscription = new Subscription(subject, roles, emitter);
  subscriptions.add(subscription);
  final Runnable cleanup = () -> subscriptions.remove(subscription);
  emitter.onCompletion(cleanup);
  emitter.onTimeout(cleanup);
  emitter.onError(t -> cleanup.run());
}

public void broadcast(DomainEvent event) {
  for (final var subscription : subscriptions) {
    final var isOwner = subscription.subject().equals(event.resourceOwner());
    final var hasAudienceRole = event.audienceRoles().stream().anyMatch(subscription.roles()::contains);
    if (isOwner || hasAudienceRole) {
      subscription.emitter().send(event);
    }
  }
}
```

La gateway n'a besoin d'aucune connaissance métier pour ça : elle ne fait que comparer
des chaînes de caractères (`subject`, rôles).

`@PreAuthorize("isAuthenticated()")` garantit un 401 plutôt qu'un
enregistrement pour un utilisateur anonyme :

```java
@PreAuthorize("isAuthenticated()")
@GetMapping(path = "/bff/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
@ApiResponse(responseCode = "200",
    content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
        schema = @Schema(implementation = DomainEvent.class)))
public SseEmitter subscribeToServerStateChangedEvents(Authentication auth) {
  final var emitter = new SseEmitter(0L);
  final var roles = auth.getAuthorities().stream()
      .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
  registry.register(auth.getName(), roles, emitter);
  return emitter;
}
```

L'annotation `@ApiResponse` est nécessaire pour documenter ce flux dans la spec OpenAPI : `SseEmitter` n'est pas un type générique Java (`ServerSentEvent<DomainEvent>`).

Enfin, le relais proprement dit : un `@RabbitListener` reçoit l'évènement désérialisé et le diffuse aux abonnés
concernés :

```java
@RabbitListener(queues = "#{gatewayEventsQueue.name}")
public void onDomainEvent(DomainEvent event) {
  registry.broadcast(event);
}
```

**Limite connue, non traitée dans ce TP** : le registre d'abonnements est en mémoire, local à l'instance de la gateway. Avec plusieurs instances, un évènement qui arrive sur l'instance B alors que la connexion SSE de l'utilisateur est ouverte sur l'instance A est silencieusement perdu pour cet utilisateur. Une solution passerait par un registre partagé (Redis par exemple) ou par un relais STOMP directement géré par RabbitMQ, mais ça sort du cadre de ce TP.

##### T.P.

Initialisation :

```bash
./start-lab.sh 6.2
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

### 6.3. <a name="messaging-frontend"/>Abonnement du frontend

Le front du TP étant une application React utilisant [TANSTACK Query](https://tanstack.com/query/latest), un hook ouvre le flux SSE tant que l'utilisateur est authentifié, et invalide les entrées de cache concernées par le type de ressource reçu (le type d'évènement n'est pas utilisé) :

```ts
function invalidateForEvent(
  queryClient: QueryClient,
  event: DomainEvent,
): void {
  if (!event.resourceId) return;
  switch (event.resourceType) {
    case "account":
      queryClient.invalidateQueries({
        queryKey: ["account", event.resourceId],
      });
      queryClient.invalidateQueries({
        queryKey: ["transfers", event.resourceId],
      });
      queryClient.invalidateQueries({
        queryKey: ["transfers-in", event.resourceId],
      });
      break;
  }
}

export function useDomainEventsSubscription(): void {
  const queryClient = useQueryClient();
  const { data: user } = useMe();
  const authenticated = isAuthenticated(user);

  useEffect(() => {
    if (!authenticated) return;

    let eventSource: EventSource | undefined;
    let cancelled = false;

    // EventSource ne peut pas être piloté par le client fetch généré (il fait du streaming, pas
    // fetch), mais réutiliser son chemin de requête garde l'URL synchronisée avec la spec OpenAPI
    // plutôt que de coder "/bff/events" en dur une seconde fois ici.
    void gatewayApi
      .subscribeToServerStateChangedEventsRequestOpts()
      .then((requestOpts) => {
        if (cancelled) return;
        eventSource = new EventSource(
          `${GATEWAY_BASE_URL}${requestOpts.path}`,
          {
            withCredentials: true,
          },
        );
        eventSource.onmessage = (message) => {
          const event = DomainEventFromJSON(JSON.parse(message.data));
          invalidateForEvent(queryClient, event);
        };
      });

    return () => {
      cancelled = true;
      eventSource?.close();
    };
  }, [authenticated, queryClient]);
}
```

Ce TP n'a pas d'exercice à trous. C'est un TP d'observation.

##### T.P.

Initialisation :

```bash
./start-lab.sh 6.3
```

Retour à la branche principale après T.P.

```bash
./exit-lab.sh --keep
# ou pour restaurer l'état initial du TP
./exit-lab.sh --reset
```

<p xmlns:cc="http://creativecommons.org/ns#" xmlns:dct="http://purl.org/dc/terms/">
  La formation
  <a property="dct:title" rel="cc:attributionURL" href="https://github.com/ch4mpy/REST-hero">REST-hero</a> 
  par
  <a rel="cc:attributionURL dct:creator" property="cc:attributionName" href="https://github.com/ch4mpy">Jérôme Wacongne</a>
  est sous licence
  <a href="https://creativecommons.org/licenses/by/4.0/?ref=chooser-v1" target="_blank" rel="license noopener noreferrer" style="display:inline-block;">
    CC BY 4.0
    <img style="height:22px!important;margin-left:3px;vertical-align:text-bottom;" src="https://mirrors.creativecommons.org/presskit/icons/cc.svg?ref=chooser-v1">
    <img style="height:22px!important;margin-left:3px;vertical-align:text-bottom;" src="https://mirrors.creativecommons.org/presskit/icons/by.svg?ref=chooser-v1">
  </a>
</p>
