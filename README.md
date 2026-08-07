# REST-hero : APIs REST avec Spring Boot

Ce support de TPs répond à des exigences de production avancées. Les services REST sont notamment :
- **Audités** :
    * Hibernate Envers garde la trace de chaque version des entités en base données avec l'instant et l'auteur de la modification
    * Chaque endpoint qui modifie l'état de l'application (`POST`, `PUT` ou `DELETE`) log au niveau `info` un résumé de ce qui a été modifié et par qui
- **Observables** et observée de manière centralisée : émet des logs collectées dans Loki, des métriques dans Mimir et des traces dans Tempo, le tout visualisé dans [Grafana](https://host.docker.internal/grafana).
- **Documentés** avec OpenAPI : permet aux clients de générer le code pour les consommer et aux développeurs de la visualiser dans une Swagger-UI.
- **Communicants** : Appels REST inter-service:
  * le `customer-service` utilise Keycloak pour accéder aux utilisateurs
  * l'`account-service` vérifie auprès du `customer-service` qu'un client existe avant de créer un compte à son nom. Il sollicite aussi le `currency-service` lorsqu'un virement nécessite des opérations de change.
  * le `card-service` vérifie auprès de l'`account-service` qu'un compte existe avant de lui attacher une carte et lui déclare un transfert d'argent lors d'un paiement par carte.
- **Sécurisés** : 
  * chaque endpoint d'API vérifie l'identité attachée à la requête et ses relations éventuelles avec les ressources qu'elle cherche à manipuler avant d'autoriser l'accès.
  * ne sont utilisés que des clients OAuth2 confidentiels (avec mot de passe). Les requêtes du front React sont autorisées avec le pattern [_OAuth2 BFF_](https://www.baeldung.com/spring-cloud-gateway-bff-oauth2)
- **Persistants** : les objets métier sont sauvegardés dans PostgreSQL avec JPA. Les requêtes les plus complexes (filtres sur les paiements par carte et les mouvements entre comptes) sont construites avec des spécifications JPA.
- **Performants** : utilisation de caches pour limiter les accès à la base de données et les appels REST inter-services lorsque c'est pertinent.

- [Introduction](#intro)
- [Déploiement de l'environnement de dev](#dev-deployment)
- [1. Build avec Maven](#maven-build)
  * [1.1. Introduction](#maven-build-intro)
    - [1.1.1. Structure](#maven-build-structure)
    - [1.1.2. Phases](#maven-build-phases)
  * [1.2. Dépendances](#maven-build-dependencies)
  * [1.3. Processeurs d’annotations à la compilation](#maven-build-annotations-preprocessing)
  * [1.4. Génération de spec OpenAPI à partir du code source](#maven-build-openapi-spec-generation)
  * [1.5. Génération de code client à partir de spec OpenAPI](#maven-build-openapi-client-code-generation)
  * [1.6. Manipulation des ressources](#maven-build-resources-handling)
  * [1.7. Profiles Maven](#maven-profiles)
- [2. Fondamentaux Spring](#spring)
  * [2.1. Injection de dépendance](#spring-di)
  * [2.2. `@Component` et variantes](#spring-components)
  * [2.3. Configuration externe](#spring-properties)
  * [2.4. `@Configuration` et `@Bean`](#spring-configuration)
  * [2.5. Proxies générés](#spring-proxies)
  * [2.6. Tests](#spring-testing)
  * [2.7. Starter Spring Boot](#spring-boot-starter)
- [3. Modèles objet-relationnel et accès aux données](#jpa)
  * [3.1. `@Entity`](#jpa-entity)
  * [3.2. Identifiants générés](#jpa-generated-ids)
  * [3.3. Relations](#jpa-relations)
  * [3.4. Conversion de types](#jpa-type-converter)
  * [3.5. `@Repository` Spring Data JPA](#jpa-repositories)
  * [3.6. JPA query methods](#jpa-query-dsl)
  * [3.7. Spécifications JPA](#jpa-specifications)
  * [3.8. Transactions](#jpa-transactions)
  * [3.9. Hibernate Envers](#jpa-envers)
- [4. Services REST WebMvc avec Spring Boot](#rest-controller)
  * [4.1. `@RequestMapping`](#rest-controller-request-mapping)
  * [4.2. Convertisseurs automatiques de Spring](#rest-controller-converters)
  * [4.3. Validation des entrées](#rest-controller-validation)
  * [4.4. Gestion des exceptions](#rest-controller-exceptions)
  * [4.5. Génération de la documentation OpenAPI](#rest-controller-openapi)
  * [4.6. Appels de services REST externes](#rest-controller-inter-service-communication)
  * [4.7. Logs](#rest-controller-logging)
- [5. Mise en cache](#caching)
- [6. Messagerie asynchrone et notifications temps réel](#messaging)
  * [6.1. Publication d'un événement métier](#messaging-publish)
  * [6.2. Relais par la gateway : RabbitMQ vers Server-Sent Events](#messaging-gateway-sse)
  * [6.3. Abonnement du frontend](#messaging-frontend)

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
- `currency-service` fournit un référentiel des devise supportées et du change sur le fixing veille de la BCE (via [https://frankfurter.dev](https://frankfurter.dev/))
- `customer-service` responsable des clients et de leurs bénéficiaires. Ce service ne stocke que les bénéficiaires dans sa base de données. Les clients sont des utilisateurs de Keycloak (lecture / écriture via l'API Keycloak).
- `account-service` responsable des comptes bancaires et des transferts entre comptes.
- `card-service`responsable des cartes et des paiements par carte.

## <a name="dev-deployment"/>Déploiement de l'environnement de dev

Pré-requis :
- [Git](https://git-scm.com/install/). Sur Windows, Git bash avec Mingw. Toujours sous Windows, installer [7-zip](https://www.7-zip.fr/download.html) et créer une copie de`7z.exe` nommée `zip.exe`.
- [nvm](https://www.nvmnode.com/fr/guide/download.html)
- [SDKMan](https://sdkman.io/install/) 
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
cd api && mvn install -Popenapi,h2 && cd ..
git submodule init && git submodule update
cd frontend && npm i && npm run api && cd ..
```

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

Pour démarrer les services de l'API depuis un IDE, surcharger le propriété `spring.datasource.password` avec la valeur du fichier `/secrets/rest-api/postgres_password.txt` dans une run config.

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

`properties` est un ensemble de clef-valeur libres qui peuvent être référencées n'importe où dans le module où elles
sont définies, ou dans les modules enfant. Spring Boot définit de très nombreuses version de librairies de cette
manière. Maven fournie quelques properties contextuelles telles que `project.basedir`, `project.groupId`,
`project.artifactId` et , `project.version`.

Les `modules` enfants à inclure lors de l'exécution des phases d'un module parent doivent être déclarés.

Le `dependencyManagement` permet de définir des versions par défaut pour un module et ses enfants. On peut y importer un
`dependencyManagement` d'un autre POM avec une dépendnance de `type` `pom` et un `scope` de type `import` (
`spring-cloud-dependencies` par exemple).

`dependencies`, à la racine du `project`, déclare les dépendances effectives d'un module. Le `scope` d'une dépendance
indique comment elle est fournie et quand elle est utilisée:

- `compile` : valeur par défaut, la dépendnace est toujours inclue
- `provided` : fournie à l'exécution, généralement par le conteneur (par exemple la `servlet-api` est déjà dans
  Tomcat) => présent à la compilation et dans les test mais pas dans le jar
- `runtime` : absent lors de la compilation mais présents lors des tests et dans le jar
- `test` : présent uniquement lors de la compilation des tests et de leur exécution
- `import` : pour référencer un _artifact_ de type `pom`

La section `build` permet de contrôler l'assemblage du projet, notamment via ses sections `plugins` (et
`pluginManagement`) et `resources`.

La section `profiles` permet de surcharger toute partie du build pour certaines exécutions. Dans les TPs, nous utilisons
le profile `openapi` pour ajouter des dépendances à SpringDoc-OpenAPI, lancer l'application avant les test d'
intégration, récupérer la spec OpenAPI sur la swagger-ui; puis arrêter l'application après les tests d'intégration.

##### T.P.
Initialisation :
```bash
./lab.sh 1.1.1
```
Retour à la branche principale après T.P.
```bash
git switch main
```

#### 1.1.2. <a name="maven-build-phases"/>Phases

- **_validate_** : intégrité des POMs
- _initialize_ :
- _generate-sources_ :
- _process-sources_ :
- _generate-resources_ :
- _process-resources_ :
- **_compile_** :
- _process-classes_ :
- _generate-test-sources_ :
- _process-test-sources_ :
- _generate-test-resources_ :
- _process-test-resources_ :
- _test-compile_ :
- _process-test-classes_ :
- **_test_** : exécution des tests unitaires
- _prepare-package_ :
- **_package_** : assemblage du jar / war
- _pre-integration-test_ :
- _integration-test_ :
- _post-integration-test_ :
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
./lab.sh 1.1.2
```
Retour à la branche principale après T.P.
```bash
git switch main
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
- `spring-boot-starter-restclient`, `spring-boot-starter-oauth2-client` et `spring-addons-starter-rest` : consomation
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
./lab.sh 1.2
```
Retour à la branche principale après T.P.
```bash
git switch main
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
./lab.sh 1.3
```
Retour à la branche principale après T.P.
```bash
git switch main
```

### 1.4. <a name="maven-build-openapi-spec-generation"/>Génération de spec OpenAPI à partir du code source

Swagger peut générer des specs OpenAPI à partir de code Java. Il expose cette spec au runtime. Par défaut, la spec
elle-même est disponible sur `/v3/api-docs`.

Spring ayant de nombreuses conventions qui lui sont propres, il faut ajouter des métadonnées. Une partie est générée
automatiquement par `springdoc-openapi`, mais il faut souvent completer avec des annotations Swagger, notamment pour les
request parameters convertis automatiquement par Spring Web.

Pour éviter tou impact au runtime, la dépendances à `springdoc-openapi-starter-webmvc-api` et l'exécution du
`springdoc-openapi-maven-plugin` sont isolées dans un `profile` Maven.

Le `springdoc-openapi-maven-plugin` s'exécute pendant la phase `verify` en récupérant la spec OpenAPI sur`/v3/api-docs`.
Il faut donc préalablement démarrer l'application (utilisation du `spring-boot-maven-plugin`). Les clients OIDC ayant
besoin de récupérer la configuration OpenID du provider, nous utiliserons le `wiremock-maven-plugin`pour en exposer une.

```xml
<profile>
  <id>openapi</id>
  <properties>
    <integration-tests.port>8080</integration-tests.port>
  </properties>
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
        <groupId>uk.co.automatictester</groupId>
        <artifactId>wiremock-maven-plugin</artifactId>
        <executions>
          <execution>
            <id>start-wiremock</id>
            <phase>pre-integration-test</phase>
            <goals>
              <goal>run</goal>
            </goals>
            <configuration>
              <dir>src/test/resources/wiremock</dir>
              <params>--port=8089</params>
            </configuration>
          </execution>
          <execution>
            <id>stop-wiremock</id>
            <phase>post-integration-test</phase>
            <goals>
              <goal>stop</goal>
            </goals>
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
            <profiles>h2</profiles>
          </configuration>
          <executions>
            <execution>
              <id>pre-integration-test</id>
              <goals>
                <goal>start</goal>
              </goals>
              <configuration>
                <arguments>
                  <argument>--issuer=http://localhost:8089/auth/realms/labs</argument>
                </arguments>
                <environmentVariables>
                  <!--SERVER_SSL_ENABLED>false</SERVER_SSL_ENABLED-->
                </environmentVariables>
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

##### T.P.
Initialisation :
```bash
./lab.sh 1.4
```
Retour à la branche principale après T.P.
```bash
git switch main
```

### 1.5. <a name="maven-build-openapi-client-code-generation"/>Génération de code client à partir de spec OpenAPI

Le `openapi-generator-maven-plugin` permet de générer beaucoup de code à partir d'un spec OpenAPI. Ici nous nous
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
        <!-- prevent a name colllision between the
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
./lab.sh 1.5
```
Retour à la branche principale après T.P.
```bash
git switch main
```

### 1.6. <a name="maven-build-resources-handling"/>Manipulation des ressources

Par défaut, Maven utilise les ressources de `src/main/resources` et `src/test/resources` telles quelles. Il est possible
de modifier ce comportement dans le `buils`. Par exemple :

```xml
<resources>
  <resource>
    <directory>src/main/resources</directory>
  </resource>
  <resource>
    <directory>../../certs</directory>
  </resource>
</resources>
```

##### T.P.
Initialisation :
```bash
./lab.sh 1.6
```
Retour à la branche principale après T.P.
```bash
git switch main
```

### 1.7. <a name="maven-profiles"/>Profiles Maven

Il est possible de définir un `profile` Maven pour lequel à peut près n'importe quoi peut être redéfini (properties, dependencies, plugin à appliquer, etc.). C'est ce qui est fait dans les modules pour :
- basculer entre les dépendances pour H2 et celles pour PostgreSQL
- activer la Swagger-UI (avec les dépendances springdoc-openapi), démarrer puis arrêter l'application autour des tests d'intégration Maven et enfin récupérer la spec OpenAPI exposée par Swagger au runtime pour l'écrire dans le système de fichier

Pour activer un ou plusieurs profiles, ajouter l'option `-P` (majuscule) immédiatement suivie des profiles séparés par des virgules
```bash
mvn clean install -Popenapi,h2
```

Un profile peut être activé par défaut. C'est le cas du profile `postgresql` dans les modules.

Attention, dès qu'au moins un profile est activé de manière explicite, il n'y a plus d'activation par défaut. Dans ce projet, on associera donc toujours le profile `openapi` soit au profile `h2` (comme ci-dessus) soit au profile `postgresql`.

##### T.P.
Initialisation :
```bash
./lab.sh 1.7
```
Retour à la branche principale après T.P.
```bash
git switch main
```

## 2. <a name="spring"/>Fondamentaux Spring

### 2.1. <a name="spring-di"/>Injection de dépendance

L'injection de dépendance est le fait de compter sur le conteneur d'application pour fournir à un objet ceux dont il dépend pour accomplir ses tâches. 

Je recommande de faire l'injection par le biais du constructeur. 

Par exemple, pour l'`AccountController` qui a besoin de collaborer avec les 
- `AccountRepository` pour manipuler les comptes en base de donnée
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

Lorsque plusieurs beans ont le même type, il sont résolus par un _qualifier_ qui est par défaut le nom de la méthode `@Bean` qui a instancié chacun d'eux. Un exemple tiré de la `RestConfiguration` de l'account-service dans lequel `customerServiceClient` et `currenciesServiceClient` sont deux instances de `RestClient` exposées en tant que bean par `spring-addons-starter-rest`:
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

Pour rendre une dépendance optionnelle (on s'attend à ce qu'il soit possible que la configuration de l'application puisse ne pas fournir un bean), typer cette dépendance avec `Optional<T>` :
```java
@TestConfiguration
public class SpringDataWebConvertersTestConfiguration {
  @Autowired(required = false)
  Optional<AccountRepository> accountRepo;

  @Bean
  WebMvcConfigurer configurer() {
    return new WebMvcConfigurer() {
      @Override
      public void addFormatters(FormatterRegistry registry) {
        registry
            .addConverter(
                String.class,
                Account.class,
                iban -> accountRepo
                    .flatMap(r -> iban == null ? Optional.empty() : r.findByIban(Iban.of(iban)))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
      }
    };
  }
}
```

Les composants fournis par le biais de l'injection de dépendances peuvent être facilement remplacés pendant les tests.

##### T.P.
Initialisation :
```bash
./lab.sh 2.1
```
Retour à la branche principale après T.P.
```bash
git switch main
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
./lab.sh 2.2
```
Retour à la branche principale après T.P.
```bash
git switch main
```

### 2.3. <a name="spring-properties"/>Configuration externe

La configuration Spring Boot s'appuie en grande partie sur les _properties_ avec comme source principale `application.properties` ou, comme c'est le cas dans ce projet, `application.yml`.
```yaml
spring:
  datasource:
    password: change-me
```

Les propriétés qui sont définies dans `application.yml` peuvent ête surchargées par 
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
./lab.sh 2.3
```
Retour à la branche principale après T.P.
```bash
git switch main
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
./lab.sh 2.4
```
Retour à la branche principale après T.P.
```bash
git switch main
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
./lab.sh 2.5
```
Retour à la branche principale après T.P.
```bash
git switch main
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
- importées explicitement avec `@Import({})` si elle ne font pas partie de la _tranche_ prévue par Spring

##### T.P.
Initialisation :
```bash
./lab.sh 2.6
```
Retour à la branche principale après T.P.
```bash
git switch main
```


### 2.7. <a name="spring-boot-starter"/>Starter Spring Boot

Un starter Spring Boot sert à partager de l'auto-configuration.

Les composants à configurer dans les applications qui l'utilisent sont définis dans `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
```
com.c4soft.resthero.commons.CommonWebConfiguration
com.c4soft.resthero.commons.domain.IbanStringMapper
com.c4soft.resthero.commons.exception.CommonExceptionsHandler
```

Lors de la création de starters, il est important d'être peu intrusif et de laisser la main à l'application pour surcharger l'auto-configuration proposée. Les annotations `@ConditionalOn...` telles que `@ConditionalOnMissingBean` et `@ConditionalOnProperty` peuvent alors trouver tout leur intérêt.

##### T.P.
Initialisation :
```bash
./lab.sh 2.7
```
Retour à la branche principale après T.P.
```bash
git switch main
```

## 3. <a name="jpa"/>Modèles objet-relationnel et accès aux données

Les bases de données relationnelles sont modélisées avec des entités (tables) et des relations (clefs étrangères).

Le Modèle objet de Java ne peut être traduit directement en une représentation entité-relation (héritage, relations bi-directionnelles, ...)

La JPA (Java Persistence API) permet de faire le pont entre les deux représentations (classes VS entité-relation). Il
permet l'ORM (Object-Relational Mapping).

**La Javadoc JPA est excellente et contient de très nombreux exemples.** Il faut la consulter sans retenue.

### 3.1. <a name="jpa-entity"/>`@Entity`

Une entité est une classe mappée sur une table en base de donnée.

Son `@Id` correspond à la clef primaire de la table.

Elle doit avoir un constructeur par défaut (sans paramètre) dont la visibilité peut être restreinte.

Il est possible (et souvent recommandé) de limiter les méthodes `equals` et `hasCode` à la (ou aux) propriété (s) `@Id`.

```java
@Entity
@Table(name = "cards")
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@Builder(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Card {

  @Id
  @EqualsAndHashCode.Include
  @ToString.Include
  private String number;

  @Column(nullable = false)
  @ToString.Include
  private String accountNumber;

  // ...
}
```

##### T.P.
Initialisation :
```bash
./lab.sh 3.1
```
Retour à la branche principale après T.P.
```bash
git switch main
```

### 3.2. <a name="jpa-generated-ids"/>Identifiants générés

H2 et PostgreSQL utilisent les séquences pour les identifiants numériques auto-générés (pas de PK auto-incrémentée come
MySQL par exemple).

`@GenratedValue` indique qu'une valeur est fournie par la BDD lors du 1er enregistrement d'une entité. Elle est associée
à `@Id` et doit référencer un générateur (dans le cas H2 ou PostgreSQL, une séquence).

Les séquences sont décrites avec `@Generator`.

```java
@Id
@GeneratedValue(generator = "cardPaymentSeq")
@SequenceGenerator(name = "cardPaymentSeq", sequenceName = "payment_seq", allocationSize = 1)
private Long id;
```

##### T.P.
Initialisation :
```bash
./lab.sh 3.2
```
Retour à la branche principale après T.P.
```bash
git switch main
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

Une propriété ayant pour type une collection d'entités doit être décorées avec `@OneToMany` ou `@ManyToMany`.

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
./lab.sh 3.3
```
Retour à la branche principale après T.P.
```bash
git switch main
```

### 3.4. <a name="jpa-type-converter"/>Conversion de types

Lorsqu'un objet est mappé sur un type simple en base, il possible définir un `@Converter(autoApply = true)` qui
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
  ./lab.sh 3.4
  ```
  Retour à la branche principale après T.P.
  ```bash
  git switch main
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
./lab.sh 3.5
```
Retour à la branche principale après T.P.
```bash
git switch main
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
./lab.sh 3.6
```
Retour à la branche principale après T.P.
```bash
git switch main
```

### 3.7. <a name="jpa-specifications"/>Spécifications JPA

Lorsque la logique de filtrage devient trop complexe (notamment lors de l'application de critères optionnels), les Spécifications JPA sont souvent plus adaptées que les _"query methods"_.

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
./lab.sh 3.7
```
Retour à la branche principale après T.P.
```bash
git switch main
```

### 3.8. <a name="jpa-transactions"/>Transactions

Les opérations d'accès aux données en base se font à l'intérieur d'une transaction.

On utilise `@Transactionnal` pour déclarer qu'une méthode doit être exécutée à l'intérieur d'une transaction.

Les relations (`@OneToMany`, `@OneToOne`, etc.) étant _lazy_ par défaut, il faut parcourir le graph d'objet à
l'intérieur de la transaction dans laquelle la racine a été récupérée.

Le plus simple est généralement de décorer les méthodes de `@Controller` avec `@Transactionnal`, mais la logique métier
demande parfois plus de finesse (différentes méthode exécutées dans des transactions différentes pour que certaines
soient `commit` alors que d'autre sont `rollback`).
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
./lab.sh 3.8
```
Retour à la branche principale après T.P.
```bash
git switch main
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

Pour donner accès aux différents états dans lesquels une entité a été sauvegardée un `@Repository` doit implémenter `RevisionRepository<E, ID, R>`:
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
./lab.sh 3.9
```
Retour à la branche principale après T.P.
```bash
git switch main
```

## 4. <a name="rest-controller"/>Services REST WebMvc avec Spring Boot

Nous nous intéressons ici aux `@RestController` qui forment la façade visible d'une API REST.

### 4.1. <a name="rest-controller-request-mapping"/>`@RequestMapping`

Avec les signatures et types de retour de méthode, `@RequestMapping` participe à la définition d'un endpoint. On utilise
préférentiellement `@RequestMapping` au niveau de la classe pour les définitions communes à toutes les méthodes et ses
spécialisation `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping` et `@PatchMapping` sur chaque méthode.

La signature et le type de retour de la méthode donne les informations sur les entrées / sortie et le `@RequestMapping`
le reste, notamment :

- le verbe HTTP à employer
- le path du endpoint (attention, si le `@RequestMapping` au niveau de classe et un spécialisation au niveau de la
  méthode portent tous deux un path, c'est la concaténation qui est appliquée)
- les `MediaType` acceptés en entrée et ceux supportés en retour

Le bon emploi des verbes HTTP est important pour la sécurité Web : `GET` ne doit être employé que pour des opérations
qui ne changent pas l'état du système.

Pour rappel :

- `GET` est attendu pour les opérations en lecture et ne doit pas porter de _body_. Par exemple, les critères de filtre
  devraient être portés par des _request parameters_. Il est attendu que la réponse à un `GET` soit en status `200 Ok`et
  ait un _body_.
- `POST` est attendu pour une création de ressource et devrait porter un _body_ avec les informations sur la ressource à
  créer. La réponse devrait être en status `201 Created`, ne pas avoir de _body_ et porter un header `Location` pointant
  vers la ressource créée.
- `PUT` est attendu pour une modification de ressource existante et devrait porter un _body_ avec les informations de
  mise à jour. Il est attendu une réponse dans le champ `2xx` sans _body_ (par exemple `202 Accepted` ou `204 No-content`).
- `DELETE` est attendu pour supprimer une ressource et ne doit pas porter de _body_ (l'URL doit suffire à identifier la
  ressource à supprimer). Il est attendu une réponse dans le champ `2xx` sans _body_ (par exemple `202 Accepted` ou
  `204 No-content`).
- `PATCH` est attendu pour la mise à jour partielle d'un ressource (le propriétés manquantes du _body_ sont ignorées).
  Elle est peu usitée et parfois mal supportée par les clients, donc à éviter.

Une différence notable entre `POST` et `PUT` est que lors de la répétition d'une requête `PUT` sur la même resource et
avec le même _body_, seule la première requête devrait avoir un effet, alors que la répétition d'un `POST` doit créer
autant de ressources (ou tenter de le faire).

##### T.P.
Initialisation :
```bash
./lab.sh 4.1
```
Retour à la branche principale après T.P.
```bash
git switch main
```

### 4.2. <a name="rest-controller-converters"/>Convertisseurs automatiques de Spring

Au niveau du protocole HTTP, toutes les valeurs sont des `String`.

Spring Web comporte de nombreux mécanismes de (dé)sérialisation, notamment :

- JSON (avec Jackson) : convertie les `@RequestBody` et `@ResponseBody`
- le `FormatterRegistry` : enregistre des convertisseurs à appliquer sur les `@PathVariable` et `@RequestParam`.

Les `JpaRepository<E, ID>` pour les `@Entity` ayant `Integer`, `Long` ou `String` comme type sont enregistrés dans le
`FormatterRegistry`.

En considérant que `MyEntity` a un `@Id Long id` et qu'il existe un `JpaRepository<MyEntity, Long>`, alors un requête
`GET /resources/42` est routée sur la fonction suivante où `entity` est l'entité avec l'identifiant `42` :

```java
@GetMapping("/resources/{resourceId}")
Resource getResource(@PathVariable("resourceId") MyEntity entity);
```

Il est possible d'ajouter des converters dans le registry en fournissant une `@Configuration` qui implémente
`WebMvcConfigurer`. Par exemple, pour ajouter une conversion automatique d'un compte depuis son `Iban`
(`AccountRepository extends JpaRepository<Account, Iban>`):

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
      return iban == null ? null : accountRepo.findByIban(iban).orElse(null);
    }
  }
}
```

##### T.P.
Initialisation :
```bash
./lab.sh 4.2
```
Retour à la branche principale après T.P.
```bash
git switch main
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

##### T.P.
Initialisation :
```bash
./lab.sh 4.3
```
Retour à la branche principale après T.P.
```bash
git switch main
```

### 4.4. <a name="rest-controller-exceptions"/>Gestion des exceptions

Lorsqu'une erreur est détectée (technique ou métier), une exception est levée.

Les exceptions sont interceptées dans deux cas principaux :

- on souhaite la contourner (il y a un _plan B_ pour continuer le traitement)
- on souhaite la mapper vers un autre type d'exception (exception technique traduite en exception métier)

Les `@ExceptionHandler` déclarés dans un `@RestControllerAdvice` servent intercepter des exceptions et à définir la
réponse à envoyer :

```java
@RestControllerAdvice
public class CommonExceptionsHandler {

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
    final var detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    return ResponseEntity.status(detail.getStatus()).body(detail);
  }
}
```

Lorsque créer et intercepter une exception métier n'a pas d'intérêt intrinsèque, un raccourci est de soulever une
`ErrorResponseException` pour laquelle Spring fournit déjà un `@ExceptionHandler`.

##### T.P.
Initialisation :
```bash
./lab.sh 4.4
```
Retour à la branche principale après T.P.
```bash
git switch main
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
  ./lab.sh 4.5
  ```
  Retour à la branche principale après T.P.
  ```bash
  git switch main
  ```

### 4.6. <a name="rest-controller-inter-service-communication"/>Appels de services REST externes

Le client REST actuellement recommandé pour les servlets est `RestClient`. `RestTemplate` est en mode maintenance et
`WebClient` est plus adapté aux application réactives.

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

Lors d'une erreur pendant l'appel REST, `RestClient` soulève une `HttpClientErrorException`. Il est fortement recommandé
d'intercepter de telles erreurs et de les translater vers quelque chose ajoutant des logs et du contexte métier.

```java
try{
  customersApi.getCustomer(dto.customerId());
} catch (HttpClientErrorException e){
  if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
    log.warn("Rejecting account {} creation for unknown customer {}",iban, dto.customerId());
    throw new ResourceNotFoundException("Customer %s is not known by the customer-service".formatted(dto.iban()));
  }
  log.error(
      "Unexpected error while checking customer {} existence in customer-service",
      dto.customerId(),
      e);
  throw new ResponseStatusException(
      HttpStatus.INTERNAL_SERVER_ERROR,
      "Unexpected error while checking customer existence",
      e);
}
```

    ##### T.P.
    Initialisation :
    ```bash
    ./lab.sh 4.6
    ```
    Retour à la branche principale après T.P.
    ```bash
    git switch main
    ```

### 4.7. <a name="rest-controller-logging"/>Logs

Les logs sont la première source d'audit de l'application. Il est important de loguer suffisamment et au bon niveau:
`error`, `warn`, `info`, `debug` ou `trace`.

`@Slf4j` de Lombok permet de disposer d'un logger nommé `log`.

Avec Spring Boot 4, `spring-boot-starter-opentelemetry` et `opentelemetry-logback-appender-1.0` permettent de pousser
les logs vers Loki avec relativement peut de conf :

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
./lab.sh 4.7
```
Retour à la branche principale après T.P.
```bash
git switch main
```

## 5. <a name="caching"/>Mise en cache

L'enjeu principal de la mise en cache est l'obsolescence des données. Il faut donc remplacer ou supprimer des données en
cache lors d'accès en écriture, ce qui implique :

- avoir la maîtrise totale de ces écritures
- utiliser un cache distribué (Redis ?) en environnement distribué

Je conseille de créer un cache par type de donnée et par index. Par exemple, si on souhaite accéder à des instances de
`Bidule` soit par la valeur de leur propriété `truc`, soit par celle de lor propriété `machin`, on créera deux caches:
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

##### T.P.
Initialisation :
```bash
./lab.sh 5
```
Retour à la branche principale après T.P.
```bash
git switch main
```
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

Pour indiquer que la valeur de retour peut ête mise en cache :
```java
@Cacheable(cacheNames = "bidulesParTruc")
```

Pour indiquer qu'une opération en écriture nécessite des opérations de mise à jour du cache :
```java
@Caching(put = @CachePut(cacheNames = "bidulesParTruc", key = "#bidule.truc"),
    evict = @CacheEvict(cacheNames = "bidulesParMachin", key = "#bidule.machin"))
```

Lorsqu'une classe expose une interface publique plus importante que nécessaire cela peut grandement compliquer la
gestion des caches. Je recommande dans ce cas de faire un proxy n'exposant que le strict nécessaire et gérer les caches
à ce niveau.

## 6. <a name="messaging"/>Messagerie asynchrone et notifications temps réel

Nous étudions ici le méchanisme par lequel le serveur notifie le frontend qu'un évènement concernant l'utilisateur connecté vient de se produire (par exemple un virement reçu), sans  rafraîchissement de page ni polling.

L'architecture retenue a trois maillons :
- un service métier (`account-service`) publie un évènement applicatif sur RabbitMQ dès qu'une action métier réussit ;
- la `gateway` consomme ces évènements et les relaie au navigateur par Server-Sent Events (SSE), un flux HTTP à sens
  unique, plus simple qu'un WebSocket puisqu'on n'a jamais besoin d'écrire du navigateur vers le serveur sur ce canal ;
- le frontend garde des payloads d'évènement volontairement minces (identifiants seulement) et refait un appel REST
  classique dès qu'il est notifié, plutôt que de faire transiter la donnée métier complète par le bus d'évènements.

Le choix du SSE (plutôt que WebSocket/STOMP) tient aussi à la sécurité déjà en place : le flux SSE est exposé sous
`/bff/**`, donc protégé par les mêmes cookies de session que le reste du BFF (voir l'introduction), sans complication
supplémentaire de handshake ni de CSRF puisqu'un abonnement SSE est un simple `GET`.

Aucune des trois briques n'a besoin de connaître le token `Bearer` de l'utilisateur ni son cookie de session : la
publication d'un évènement est un aller RabbitMQ interne au backend (authentifié par des identifiants applicatifs,
pas par l'utilisateur). Le vrai sujet d'autorisation est ailleurs : garantir qu'un évènement n'est diffusé qu'aux
utilisateurs légitimes pour le voir, ni plus (fuite d'information), ni moins (un conseiller qui a le droit de
consulter n'importe quel compte doit aussi être notifié des évènements sur les comptes qu'il consulte, pas seulement
le client propriétaire). D'où un évènement qui porte, en plus de son type et de son identifiant de ressource, le
`subject` du propriétaire de la ressource *et* la liste des rôles qui y donnent également accès, à l'image des
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
tout en réutilisant la même classe de configuration partagée puisque seul le *nom* change d'un service à l'autre.

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
./lab.sh 6.1
```
Retour à la branche principale après T.P. :
```bash
git switch main
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

La gateway n'a besoin d'aucune connaissance métier pour ça : elle ne que comparer
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

L'annotation `@ApiResponse` est nécessaire pour documenter ce flux dans la spec OpenAPI : `SseEmitter` n'est un type générique Java (`ServerSentEvent<DomainEvent>`).

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
./lab.sh 6.2
```
Retour à la branche principale après T.P. :
```bash
git switch main
```

### 6.3. <a name="messaging-frontend"/>Abonnement du frontend

Le front du TP étant une application React utilisant [TANSTACK Query](https://tanstack.com/query/latest), un hook ouvre le flux SSE tant que l'utilisateur est authentifié, et invalide les entrées de cache concernées par le type de ressource reçu plutôt (le type d'évènement n'est pasutilisé) :

```ts
function invalidateForEvent(queryClient: QueryClient, event: DomainEvent): void {
  if (!event.resourceId) return;
  switch (event.resourceType) {
    case "account":
      queryClient.invalidateQueries({ queryKey: ["account", event.resourceId] });
      queryClient.invalidateQueries({ queryKey: ["transfers", event.resourceId] });
      queryClient.invalidateQueries({ queryKey: ["transfers-in", event.resourceId] });
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
    void gatewayApi.subscribeToServerStateChangedEventsRequestOpts().then((requestOpts) => {
      if (cancelled) return;
      eventSource = new EventSource(`${GATEWAY_BASE_URL}${requestOpts.path}`, {
        withCredentials: true,
      });
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
./lab.sh 6.3
```
Retour à la branche principale après T.P. :
```bash
git switch main
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