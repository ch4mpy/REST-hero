package com.c4soft.resthero.currency;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.endpoint.web.PathMappedEndpoints;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.client.RestClient;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;
import com.c4soft.resthero.problem.ProblemType;

/**
 * The REST API's actuator, on its main port: only {@code health} is exposed. Its probes answer
 * without a token; the index and the aggregate health need one and give nothing but the health's
 * links, status and groups; any other {@code /actuator/**} is {@code 401} without a token and
 * {@code 404} with one, even the token of a user who just self-registered: such a token can't turn
 * a logger to {@code TRACE} (whose logs are exported to Grafana) nor read the configuration's keys.
 * {@code heapdump}, which Spring Boot already closes by default, is checked as a regression guard.
 *
 * <p>A real server, called over HTTP, with real tokens: signed by a key of this test, which
 * WireMock serves as the realm's JWKS, and checked by the API's own decoder (issuer, signature,
 * expiry). A call without a token to an unexposed endpoint is {@code 401} before and after the
 * fix: only the calls with a token tell exposed from unexposed.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      "issuer=${keycloak.base-url}/auth/realms/labs",
      "spring.profiles.active=no-observability",
      "server.ssl.enabled=false",
      "com.c4-soft.springaddons.rest.client.frankfurter-client.base-url=http://localhost:1",
      // the no-observability profile turns the probes off: they are what this test checks
      "management.endpoint.health.probes.enabled=true",
      "management.health.livenessstate.enabled=true",
      "management.health.readinessstate.enabled=true",
      // no broker in this test: its health would turn the aggregate DOWN (503)
      "management.health.rabbit.enabled=false",
      // the ssl indicator reads the bundle's certificate, which this test doesn't depend on
      "management.health.ssl.enabled=false",
      "spring.rabbitmq.listener.simple.auto-startup=false",
      "spring.rabbitmq.listener.direct.auto-startup=false"
    })
@EnableWireMock({
  @ConfigureWireMock(
      name = "keycloak",
      baseUrlProperties = "keycloak.base-url",
      filesUnderClasspath = "wiremock/keycloak")
})
class ActuatorExposureTest {

  private static final String JWKS_PATH = "/auth/realms/labs/protocol/openid-connect/certs";

  /** The API's root logger: turned to {@code TRACE}, every log of the API's own code. */
  private static final String LOGGER = "com.c4soft.resthero.currency";

  /**
   * One key for the whole class: the API's decoder caches the JWKS, a key generated per method
   * would be unknown to it from the second method on.
   */
  private static final RSAKey KEY = generateKey();

  /** The realm roles of a user who just self-registered: no role of the application. */
  private static final List<String> SELF_REGISTERED_USER_ROLES =
      List.of("default-roles-labs", "offline_access", "uma_authorization");

  @LocalServerPort int port;

  @Value("${issuer}")
  String issuer;

  @Autowired PathMappedEndpoints endpoints;

  /** The API's own logging system: what {@code /actuator/loggers} would change. */
  @Autowired LoggingSystem loggingSystem;

  @InjectWireMock("keycloak")
  WireMockServer keycloak;

  private static RSAKey generateKey() {
    try {
      return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Ahead of the mapping {@code labs-jwks.json}, which serves an empty JWKS. */
  @BeforeEach
  void serveTheTestKey() {
    keycloak.stubFor(
        WireMock.get(urlPathEqualTo(JWKS_PATH))
            .atPriority(1)
            .willReturn(okJson(new JWKSet(KEY).toPublicJWKSet().toString())));
  }

  private String selfRegisteredUserToken() {
    final var now = Instant.now();
    final var claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(UUID.randomUUID().toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .claim("preferred_username", "just-registered")
            .claim("realm_access", Map.of("roles", SELF_REGISTERED_USER_ROLES))
            .build();
    final var header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY.getKeyID()).build();
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(KEY)))
        .encode(JwtEncoderParameters.from(header, claims))
        .getTokenValue();
  }

  private record Answer(HttpStatusCode status, @Nullable String body) {
    @Nullable Object json(String path) {
      return body == null ? null : JsonPath.read(body, path);
    }
  }

  private Answer get(String path, @Nullable String token) {
    return call(HttpMethod.GET, path, token, null);
  }

  /**
   * Reads the status and, when it is JSON (a problem, an actuator answer), the body: another body
   * (a heap dump, should {@code heapdump} ever be opened) is never read.
   */
  private Answer call(
      HttpMethod method, String path, @Nullable String token, @Nullable String jsonBody) {
    final var spec =
        RestClient.create("http://127.0.0.1:" + port)
            .method(method)
            .uri(path)
            .headers(
                headers -> {
                  if (token != null) {
                    headers.setBearerAuth(token);
                  }
                });
    if (jsonBody != null) {
      spec.contentType(MediaType.APPLICATION_JSON).body(jsonBody);
    }
    return spec.exchange(
        (request, response) -> {
          final MediaType contentType = response.getHeaders().getContentType();
          final String body =
              contentType != null && contentType.getSubtype().endsWith("json")
                  ? new String(response.getBody().readAllBytes(), UTF_8)
                  : null;
          return new Answer(response.getStatusCode(), body);
        });
  }

  @Test
  void givenNoToken_whenGetProbes_thenOk() {
    assertThat(get("/actuator/health/liveness", null).status().value()).isEqualTo(200);
    assertThat(get("/actuator/health/readiness", null).status().value()).isEqualTo(200);
  }

  @Test
  void givenNoToken_whenGetHeapdump_thenUnauthenticated() {
    final var answer = get("/actuator/heapdump", null);
    assertThat(answer.status().value()).isEqualTo(401);
    assertThat(answer.json("$.type")).isEqualTo(ProblemType.UNAUTHENTICATED.uri().toString());
  }

  @Test
  void givenNoToken_whenGetIndexOrHealth_thenUnauthenticated() {
    assertThat(get("/actuator", null).status().value()).isEqualTo(401);
    assertThat(get("/actuator/health", null).status().value()).isEqualTo(401);
  }

  /** Not exposed, and not among the paths permitted to anonymous requests. */
  @Test
  void givenNoToken_whenGetPrometheus_thenUnauthenticated() {
    assertThat(get("/actuator/prometheus", null).status().value()).isEqualTo(401);
  }

  /**
   * A logger turned to {@code TRACE} would send personal data or secrets to the logs exported to
   * Grafana. The level is read from the API's logging system, not from the endpoint (which no
   * longer answers), and put back as it was should the request go through.
   */
  @Test
  void givenASelfRegisteredUsersToken_whenSetLoggerToTrace_thenNotFoundAndLevelUnchanged() {
    final var before = loggingSystem.getLoggerConfiguration(LOGGER);
    assertThat(before).isNotNull();
    assertThat(before.getEffectiveLevel()).isNotEqualTo(LogLevel.TRACE);
    try {
      final var answer =
          call(
              HttpMethod.POST,
              "/actuator/loggers/" + LOGGER,
              selfRegisteredUserToken(),
              "{\"configuredLevel\":\"TRACE\"}");

      final var after = loggingSystem.getLoggerConfiguration(LOGGER);
      // soft: a failure shows both the answer and the level it left
      assertSoftly(
          softly -> {
            softly.assertThat(answer.status().value()).isEqualTo(404);
            softly
                .assertThat(answer.json("$.type"))
                .isEqualTo(ProblemType.NOT_FOUND.uri().toString());
            softly.assertThat(after).isNotNull();
            if (after != null) {
              softly
                  .assertThat(after.getConfiguredLevel())
                  .isEqualTo(before.getConfiguredLevel());
              softly.assertThat(after.getEffectiveLevel()).isEqualTo(before.getEffectiveLevel());
            }
          });
    } finally {
      loggingSystem.setLogLevel(LOGGER, before.getConfiguredLevel());
    }
  }

  @Test
  void givenASelfRegisteredUsersToken_whenGetEnv_thenNotFound() {
    final var answer = get("/actuator/env", selfRegisteredUserToken());
    assertThat(answer.status().value()).isEqualTo(404);
    assertThat(answer.json("$.type")).isEqualTo(ProblemType.NOT_FOUND.uri().toString());
  }

  /** A regression guard: Spring Boot already closes {@code heapdump} by default. */
  @Test
  void givenASelfRegisteredUsersToken_whenGetHeapdump_thenNotFound() {
    final var answer = get("/actuator/heapdump", selfRegisteredUserToken());
    assertThat(answer.status().value()).isEqualTo(404);
    assertThat(answer.json("$.type")).isEqualTo(ProblemType.NOT_FOUND.uri().toString());
  }

  @Test
  void givenAToken_whenGetEnvOrLoggers_thenNotFound() {
    final var token = selfRegisteredUserToken();
    final var env = get("/actuator/env", token);
    assertThat(env.status().value()).isEqualTo(404);
    assertThat(env.json("$.type")).isEqualTo(ProblemType.NOT_FOUND.uri().toString());
    final var loggers = get("/actuator/loggers", token);
    assertThat(loggers.status().value()).isEqualTo(404);
    assertThat(loggers.json("$.type")).isEqualTo(ProblemType.NOT_FOUND.uri().toString());
  }

  @Test
  void givenAToken_whenGetHealth_thenStatusAndGroupsOnly() {
    final var answer = get("/actuator/health", selfRegisteredUserToken());
    assertThat(answer.status().value()).isEqualTo(200);
    assertThat(answer.json("$")).asInstanceOf(MAP).containsOnlyKeys("status", "groups");
    assertThat(answer.json("$.status")).isNotNull();
    assertThat(answer.json("$.groups"))
        .asInstanceOf(LIST)
        .containsExactlyInAnyOrder("liveness", "readiness");
  }

  @Test
  void givenAToken_whenGetIndex_thenHealthLinksOnly() {
    final var answer = get("/actuator", selfRegisteredUserToken());
    assertThat(answer.status().value()).isEqualTo(200);
    assertThat(answer.json("$._links"))
        .asInstanceOf(MAP)
        .containsOnlyKeys("self", "health", "health-path");
  }

  @Test
  void whenListExposedEndpoints_thenHealthOnly() {
    assertThat(endpoints.getAllRootPaths()).containsExactly("health");
  }
}
