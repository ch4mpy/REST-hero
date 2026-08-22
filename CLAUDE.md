# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repository is

REST-hero is a training course ("les TPs" / labs) teaching production-grade Spring Boot REST APIs, authored and maintained by Jérôme Wacongne (ch4mpy). It is a multi-service Java/Spring backend (`api/`) plus a React/TanStack frontend (`frontend/`, a git submodule), built as a fictitious simplified online bank. Most of `README.md` is course material in French, structured as numbered sections (e.g. `4.3`) each ending in a `##### T.P.` block that references a lab id.

### The labs mechanism

Each numbered section of `README.md` corresponds to a lab exercise. Source files can contain removal markers:

```java
// LAB:1.4.3:REMOVE:START
...code the trainee must reconstruct...
// LAB:1.4.3:REMOVE:END
```

or

```java
// LAB:1.4.3:TODO:START Optional hint message
...code...
// LAB:1.4.3:TODO:END
```

`./labs/scripts/build-lab.sh <id>` (invoked via `./lab.sh <id>`) creates/resets a `lab/<id>` branch from `main` with the marked blocks stripped (full removal) or replaced by a single `// TODO:` comment, and copies `labs/<id>/lab.md` to `lab.md` at the repo root as the exercise statement. `./labs/scripts/scaffold.py` generates skeleton `labs/<id>/lab.md` files for README entries that don't have one yet (never overwrites an existing one). See `labs/README.md` for full marker syntax and rules (same-id START/END pairing, markers don't nest for a given id, comment syntax adapts to file type: `//`, `<!-- -->`, `#`).

When editing course code on `main`, be aware that lines wrapped in `LAB:*` markers are intentionally load-bearing for the lab generation script — don't remove/reformat them as dead code.

## Environment setup and running the stack

Requires SDKMAN, nvm, Docker. JDK/Maven versions are pinned per `.sdkmanrc` (`java=25.0.2-graalce`, `maven=3.9.16`) — **run `sdk env install` / `sdk env` to pick up the right JDK/Maven instead of setting `JAVA_HOME` manually.**

```bash
bash ./deploy-dev.sh          # generates SSL certs, brings up Docker infra (Postgres, Keycloak, Mailpit, Grafana/Loki/Prometheus/Tempo, RabbitMQ, reverse proxy)
sdk env install
nvm install --lts && nvm use
cd api && mvn install -Popenapi,h2 && cd ..
git submodule init && git submodule update
cd frontend && npm i && npm run api && cd ..
```

In Keycloak admin console (`https://host.docker.internal/auth/admin/master/console/#/labs/realm-settings/email`), set the SMTP password to the value in `secrets/mail/password.txt`.

Key local URLs (self-signed cert via `host.docker.internal`):
- Frontend: `https://host.docker.internal/ui/` (`advisor`/`secret`)
- Keycloak admin: `https://host.docker.internal/auth/admin/master/console/#/labs` (`admin`/`secret`)
- Grafana: `https://host.docker.internal/grafana`
- Mailpit: `https://host.docker.internal/mailpit`

Running the frontend dev server: `cd frontend && npm run dev`.

Running an API service from an IDE: override `spring.datasource.password` with the value from `secrets/rest-api/postgres_password.txt` in the run configuration (see `.vscode/launch.json` for the five app entry points: `AccountServiceApplication`, `CardPaymentServiceApplication`, `CurrencyServiceApplication`, `CustomerServiceApplication`, `GatewayApplication`; env vars are loaded from `.env` for services that need it).

## Backend (`api/`) architecture

Maven reactor rooted at `api/pom.xml` (parent `spring-boot-starter-parent:4.1.0`, group `com.c4soft.resthero`, Java 25). Modules, matching directory layout:

- `rest-hero-starter-common` — shared Spring Boot auto-configuration (auto-config classes listed in `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`), including the shared domain-event contract and RabbitMQ config for messaging (see below).
- `currency-service` — reference data for supported currencies and FX rates (sourced from frankfurter.dev, ECB fixing).
- `customer-service` — customers (backed by Keycloak users, read/write via Keycloak Admin API) and their beneficiaries (only beneficiaries are persisted in its own DB).
- `account-service` — bank accounts and transfers between accounts; calls `customer-service` to validate customer existence and `currency-service` for FX operations.
- `card-service` — payment cards and card payments; calls `account-service` to validate account existence and to declare money movements from card payments.
- `gateway` — Spring Cloud Gateway (WebMvc-based) routing. `/gateway/bff/**` requests (frontend) are authorized via http-only session cookies + CSRF (`XSRF-TOKEN` cookie, `X-XSRF-TOKEN` header required on POST/PUT/PATCH/DELETE). `/gateway/m2m/**` requests (inter-service calls, Bruno/Postman) are authorized via OAuth2 `Bearer` tokens. Also relays RabbitMQ domain events to the frontend over Server-Sent Events (see Messaging below).

Cross-cutting conventions used throughout the services (documented at length, with rationale, in `README.md` — consult it before assuming a pattern):
- Entities: Lombok-heavy (`@Getter`, `@EqualsAndHashCode(onlyExplicitlyIncluded = true)`, `@ToString(onlyExplicitlyIncluded = true)`, protected builder/constructors), audited with Hibernate Envers (`@Audited`, `spring-data-envers`, a `SecurityAwareRevisionListener` stamps each revision with the authenticated username).
- Data access: Spring Data JPA repositories; JPA Specifications (`JpaSpecificationExecutor`) for complex/optional filters, with static factory methods on the repository interface.
- Inter-service REST clients: generated `@HttpExchange` interfaces from each service's OpenAPI spec via `openapi-generator-maven-plugin` (library `spring-http-interface`), wired up with `spring-addons-starter-rest`-configured `RestClient` beans; `HttpClientErrorException` from these calls should be caught and translated to a meaningful domain/HTTP response rather than left to propagate raw.
- Validation: `jakarta.validation` on request DTOs (records); custom constraint annotations let `null` pass through and are combined with `@NotNull` when required.
- Exceptions: `@RestControllerAdvice` (`CommonExceptionsHandler` in the shared starter) maps technical exceptions to `ProblemDetail`; prefer throwing `ErrorResponseException` directly over defining a bespoke exception + handler when there's no other reason to have a custom type.
- OpenAPI: generated at build time by `springdoc-openapi-maven-plugin` (behind the `openapi` Maven profile, output written to `frontend/openapi/`) with the app started against WireMock-stubbed OIDC discovery; `@Parameter`/`@ParameterObject`/`@ApiResponse` annotations fill gaps springdoc can't infer (implicit `FormatterRegistry` conversions, bundled request-param DTOs, non-generic response types like `SseEmitter`).
- Caching: `spring-boot-starter-cache` + Caffeine; one cache per data type *and* per lookup index; cache management should sit behind a narrow proxy interface when the underlying class exposes a broader public API than callers need.
- Security: every mutating endpoint (`POST`/`PUT`/`PATCH`/`DELETE`) is `@PreAuthorize`-guarded and logs (at `info`) a summary of what changed and by whom; only confidential OAuth2 clients are used server-to-server.
- Messaging (`rabbitmq`, added recently — see `README.md` §6): business services publish `DomainEvent` records (`resourceType`, `resourceId`, `resourceOwner`, `audienceRoles`, `eventType`, `occurredAt`) to their own per-service topic exchange (`rest-hero.<spring.application.name>`, never a shared exchange) right after a successful write. The gateway subscribes to the configured exchanges (`rest-hero.events.subscribed-exchanges`) via an anonymous non-durable queue bound with `#`, and broadcasts to open `/bff/events` SSE subscriptions whose subject/roles match `resourceOwner`/`audienceRoles`. `audienceRoles` on a published event must mirror the authority already used in that resource's `@PreAuthorize` expressions. Known limitation, left unaddressed: the gateway's subscription registry is in-memory and per-instance, so it doesn't work correctly with more than one gateway instance.

Maven build essentials:
- Profiles `h2` / `postgresql` (default) select the datasource; `openapi` adds springdoc + starts/stops the app around integration tests to (re)generate the OpenAPI spec. Because explicit profile activation disables Spring Boot's default-active profile, always pair `openapi` with either `h2` or `postgresql` explicitly: `mvn install -Popenapi,h2`.
- Full reactor build/tests: `cd api && mvn install -Popenapi,h2` (or `-Popenapi,postgresql` against the Dockerized DB).
- Single module (with its dependencies rebuilt too): `mvn install -pl account-service -am -Popenapi,h2`.
- Skip tests: append `-DskipTests`.
- Run a single test class/method with the standard Surefire selector, e.g. `mvn test -pl account-service -Dtest=AccountControllerTest#givenX_whenY_thenZ`.
- Generated code (OpenAPI clients, JPA metamodel, Lombok, MapStruct) lands under each module's `target/generated-sources`; annotation processor order matters (Lombok → MapStruct → Spring Boot config processor → therapi-javadoc → Hibernate JPA modelgen) and is fixed in the parent POM's `maven-compiler-plugin` config — don't reorder without understanding why (Mapstruct needs Lombok's generated accessors).

### Generating REST controller tests

`api/test-generation/README.md` is a spec, followed by an AI assistant, for generating `@WebMvcTest` controller tests from controller source (not a runnable tool). Key rules if asked to generate or extend such tests: derive request/response shape and expected status purely from the controller source (mappings, `@PreAuthorize`, DTO validation annotations); reuse existing JWT fixture files under `src/test/resources` via `@WithJwt` (mapping in `jwt-mapping.json` — this project uses Keycloak client roles under `resource_access`, not realm roles) rather than crafting new tokens; prefer instantiating and serializing real DTOs over hand-written JSON strings; pull test values from existing `*Fixtures` classes first, and stop to ask rather than inventing a fixture when one is missing for a required value.

## Frontend (`frontend/`)

React + TypeScript app on TanStack Start/Router/Query, Vite, Tailwind, shadcn/radix-ui components. It's a **git submodule** (`frontend` → `https://github.com/ch4mpy/rest-hero-frontend.git`) that also syncs with Lovable — avoid rewriting published history on its branches (no force-push / rebase / amend of already-pushed commits) since that desyncs Lovable's view of the project.

Commands (from `frontend/`):
```bash
npm run dev             # dev server
npm run build            # production build
npm run lint             # eslint
npm run format            # prettier --write
npm run api              # regenerate all typescript-fetch API clients from openapi/*.json (gateway, currency, customer, account, card)
```
Each `<service>-api:generate` script regenerates just that one client into `src/rest/<service>` from `openapi/<service>.openapi.json` — regenerate after the backend's OpenAPI spec changes (i.e. after `mvn install -Popenapi,...` in `api/`, which writes those specs).

SSE subscription to backend domain events lives in a hook that opens `EventSource` against the gateway's `/bff/events` path (URL derived from the generated gateway client, not hardcoded) and invalidates TanStack Query cache entries keyed by `resourceType`/`resourceId` from each received event — extend `invalidateForEvent`'s switch when a new event `resourceType` needs frontend cache invalidation.
