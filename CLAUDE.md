# REST-hero

Support de formation Spring Boot 4.1 / Java 25 : un cours (`README.md`, 6 chapitres), une API multi-modules (`api/`)
et, pour chaque entrée du sommaire, un énoncé de TP (`labs/<id>/lab.md`) dont la branche `lab/<id>` est générée en
retirant du code marqué. Le front React est un sous-module Git séparé (`frontend/`), hors de ce dépôt.

## Arborescence

- `README.md` : le cours, en français. Chaque section se termine par son bloc « T.P. ».
- `api/` : le build Maven (Spring Boot 4.1, Java 25 GraalVM, `sdk env install`).
  - `rest-hero-starter-common` : starter partagé (objets du domaine `Iban`, `Amount`, `Period`, contraintes de
    validation, convertisseurs, événements).
  - `rest-hero-problem` : starter du contrat d'erreur RFC 9457, partagé par les services et la gateway.
  - `currency-service`, `customer-service`, `account-service`, `card-service` : les API REST (serveurs de ressources).
  - `gateway` : Spring Cloud Gateway en BFF (`oauth2Login`, `TokenRelay=`).
  - `jar-content-check.xml` : la liste de ce qu'aucun jar ne doit contenir.
- `labs/` : les énoncés (`labs/<id>/lab.md`) et leurs scripts (`labs/scripts`), décrits dans `labs/README.md`.
- `certs/`, `secrets/`, `keycloak/`, `nginx-reverse-proxy/`, `observability/`, `compose-*.yml` : l'infra de dev Docker.
- `build-0-env.sh` écrit `.env` (dossier des certificats, mots de passe) ; `build-openapi.sh` construit tout et écrit les
  specs OpenAPI dans `frontend/openapi`.

## Vérifier son travail

Commande de vérification, depuis `api/` :

```bash
set -a && source ../.env && set +a && ./mvnw clean verify -Popenapi,h2
```

Le profil `openapi` démarre chaque service pour écrire sa spec dans `frontend/openapi` ; `account-service` et
`card-service` génèrent leurs clients depuis ces specs : sans elles, ils ne compilent pas. `verify` échoue aussi si un
jar contient une clé privée, un keystore ou un mot de passe (`jar-content-check.xml`).

Variante rapide, une fois les specs écrites par une première vérification complète :

```bash
./mvnw -Ph2 -pl <module> -am test -Dtest=<Classe> -Dsurefire.failIfNoSpecifiedTests=false
```

Les deux se lancent `.env` chargé (écrit par `./build-0-env.sh`) : il fournit `REST_HERO_CERTS_DIR`, où les
services lisent leur certificat, et `SPRING_DATASOURCE_PASSWORD`.

## TP et marqueurs

- Le code de `main` porte des marqueurs `LAB:<id>:REMOVE|TODO:START/END` (syntaxe dans `labs/README.md`). Ne jamais en
  supprimer, en déplacer ni en modifier un sans relire l'énoncé `labs/<id>/lab.md` qui en dépend : l'énoncé annonce
  ce que le stagiaire constate une fois le code retiré (un test qui échoue, un contexte qui ne démarre pas).
- Aperçu du retrait pour un TP : `./labs/scripts/strip_markers.py <id> <dossier>`, sur une copie du dépôt.
- Après un changement qui touche un fichier marqué, lancer la boucle de contrôle de `labs/README.md` sur les TP
  concernés : chaque branche doit compiler et échouer comme son énoncé le dit (`6.1` et `6.2` ne compilent pas, par
  conception).
- Un nouveau TP : `./labs/scripts/scaffold.py` crée l'énoncé manquant, le cours ajoute le bloc « T.P. »
  (`./start-lab.sh <id>`, `./exit-lab.sh --keep|--reset`). `lab.sh` n'existe plus : ne pas le citer.

## Contrat d'erreur

Toutes les erreurs sortent en `application/problem+json` (RFC 9457), écrites par `rest-hero-problem` :

- `ProblemType` est une énumération fermée ; son URN (`urn:rest-hero:problem:<code>`) est la clé de traduction du
  front. Un nouveau cas métier y ajoute une constante avec son statut et ses `parameters` documentés.
- Une règle métier lève l'exception vérifiée `ApiProblemException` (avec `@Transactional(rollbackFor =
  ApiProblemException.class)`) ; un convertisseur ou une lambda lève sa forme non vérifiée, `.unchecked()`. Jamais de
  `ResponseStatusException` ni d'`ErrorResponseException`.
- Validation : `422` de type `validation` avec `invalidFields`. Rejets du framework (paramètre absent, corps illisible,
  variable de chemin non convertible) : `400` de type `invalid-request`.
- Un service appelé qui échoue : `502` de type `downstream-service-error` (paramètre `service`) ; Keycloak :
  `identity-provider-error`. Le détail d'un `5xx` est journalisé, jamais renvoyé au client.
- Les tests de contrôleur vérifient le statut exact, `$.type` et `$.parameters` ou `$.invalidFields`, un cas par test
  (cours, section 2.6).

## Sécurité et exploitation

- Aucune clé privée, aucun keystore ni mot de passe dans un jar : le certificat est lu dans `REST_HERO_CERTS_DIR`, les
  secrets viennent de variables d'environnement (`.env` en dev), sans valeur par défaut pour un mot de passe de base.
- Actuator n'expose que `health`, sans détails ni composants.
- Les services et la gateway écoutent sur `127.0.0.1` ; le profil `demo` les ouvre sur toutes les interfaces
  (Linux avec Docker Engine, voir « Déploiement de l'environnement de dev » dans le cours).
- Le schéma évolue par de nouveaux changesets Liquibase, jamais en modifiant un changeset appliqué (cours, section
  3.10). Les entités changent par des méthodes métier, sans setters.

## Écarts assumés par rapport aux skills

- `rest-hero-problem` est un starter auto-configuré (package `com.c4soft.resthero.problem`), partagé par les quatre
  services et la gateway, plutôt qu'une bibliothèque copiée dans chaque module.
- Les clients inter-services sont déclarés avec `RestClientHttpExchangeProxyFactoryBean` (exemples des TP 2.1 et 2.4),
  et les contrôleurs traduisent eux-mêmes les erreurs HTTP en `ApiProblemException`.
- L'infra Docker est celle de la formation (realm `labs`, rôles client, `/gateway/bff` et `/gateway/m2m`), pas celle
  de c4-infra.

## Spring JPA entity conventions

When adding a JPA `@Entity` (with its Liquibase changeset and schema-validating test), use the `create-spring-jpa-entity` skill (`/create-spring-jpa-entity`) rather than writing it by hand. It encodes this project's entity conventions (Lombok builder with non-public constructors, nullable generated id, explicit columns, Envers audit tables, DDL derived from Hibernate rather than guessed) and stays current as those conventions evolve; duplicating the detailed rules here would only let them drift out of sync with the skill.

## Spring DTO conventions

When adding a Request/Response DTO for a REST resource, use the `create-spring-rest-dto` skill (`/create-spring-rest-dto`) rather than writing it by hand. It encodes this project's DTO conventions (record shape, naming, flattening, validation) and stays current as those conventions evolve; duplicating the detailed rules here would only let them drift out of sync with the skill.

## Spring MapStruct mapper conventions

When adding or updating a MapStruct mapper between a domain object and its DTOs, use the `create-spring-mapper` skill (`/create-spring-mapper`) rather than writing it by hand. It encodes this project's mapper conventions (component model, interface-vs-abstract-class, ignored properties, `default`-method-first mapping) and stays current as those conventions evolve; duplicating the detailed rules here would only let them drift out of sync with the skill.

## Spring REST endpoint conventions

When adding an endpoint method (`@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping`) to a `@RestController`, use the `create-spring-rest-endpoint` skill (`/create-spring-rest-endpoint`) rather than writing it by hand. It encodes this project's endpoint conventions (request/response shape, status codes, transactions, logging, `@PreAuthorize`, business-rule errors as the checked `ApiProblemException` with a `ProblemType`) and stays current as those conventions evolve; duplicating the detailed rules here would only let them drift out of sync with the skill.

## Spring REST controller test conventions

When adding a unit-test suite for a `@RestController`, use the `create-spring-rest-controller-test` skill (`/create-spring-rest-controller-test`) rather than writing it by hand. It encodes this project's controller-test conventions (`@WebMvcTest` setup, mocking vs. importing real mappers, access-control coverage with `@WithAnonymousUser`/`@WithJwt`, validation and `Location` header checks) and stays current as those conventions evolve; duplicating the detailed rules here would only let them drift out of sync with the skill.

## Spring JPA specification conventions

When adding a list endpoint filtered by optional criteria, use the `create-spring-jpa-specification` skill (`/create-spring-jpa-specification`) rather than writing it by hand. It encodes this project's conventions (a criteria record, a `@ParameterObject` filter DTO with nullable validated fields, `JpaSpecificationExecutor` with a static `searchSpec` factory over the JPA static metamodel, a `PagedModel` response, one repository test per criterion) and stays current as those conventions evolve; duplicating the detailed rules here would only let them drift out of sync with the skill.
