package com.c4soft.resthero.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.ClientAuthorizationRequiredException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import com.c4soft.resthero.problem.ProblemType;

/**
 * Every error the SPA can get from the gateway is a problem of a {@link ProblemType}: no plain
 * text, no empty body, no login URL. The cases of a session cookie the gateway no longer knows are
 * in {@code InvalidSessionTest}, those of account-service unreachable in {@link ApiUnavailableTest}.
 *
 * <p>
 * Same infrastructure as {@code InvalidSessionTest}: Keycloak's discovery documents and
 * account-service are served by WireMock (account-service answers a request without a token with its
 * {@code unauthenticated} problem, and {@code /accounts/99} with a {@code concurrent-modification}
 * one), the RabbitMQ listeners don't start.
 * </p>
 */
@SpringBootTest(
    properties = {
      "issuer=${keycloak.base-url}/auth/realms/labs",
      "accounts-service=${account-service.base-url}",
      "cards-service=${account-service.base-url}",
      "customers-service=${account-service.base-url}",
      "currencies-service=${account-service.base-url}",
      "server.ssl.enabled=false",
      "spring.rabbitmq.listener.simple.auto-startup=false",
      "spring.rabbitmq.listener.direct.auto-startup=false"
    })
@ActiveProfiles("no-observability")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@EnableWireMock({
  @ConfigureWireMock(
      name = "keycloak",
      baseUrlProperties = "keycloak.base-url",
      filesUnderClasspath = "wiremock/keycloak"),
  @ConfigureWireMock(
      name = "account-service",
      baseUrlProperties = "account-service.base-url",
      filesUnderClasspath = "wiremock/account-service")
})
@Import(GatewayProblemsTest.FailingController.class)
class GatewayProblemsTest {
  private static final String CONTEXT_PATH = "/gateway";

  private static final String REGISTRATION_ID = "gateway";

  @Autowired MockMvc mockMvc;

  /** Keycloak's realm, on the port WireMock got when the context started. */
  @Value("${issuer}")
  String issuer;

  private static MockHttpServletRequestBuilder getGateway(String path) {
    return get(CONTEXT_PATH + path).contextPath(CONTEXT_PATH);
  }

  /**
   * An HTTP session holding the security context, as {@code oauth2Login} leaves it. In the session,
   * not set by the test support (which every filter chain would see): the resource server chain,
   * stateless, must ignore it.
   */
  private MockHttpSession loggedInSession() {
    final var now = Instant.now();
    final var idToken = new OidcIdToken("id-token", now, now.plusSeconds(300),
        Map.of("sub", "user-1", "iss", issuer,
            "preferred_username", "user"));
    final var user = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken,
        "preferred_username");
    final var session = new MockHttpSession();
    session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
        new SecurityContextImpl(
            new OAuth2AuthenticationToken(user, user.getAuthorities(), REGISTRATION_ID)));
    return session;
  }

  private static ResultActions expectProblem(ResultActions result, ProblemType type)
      throws Exception {
    return result
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(type.uri().toString()));
  }

  @Test
  void givenNoSession_whenSubscribeToEvents_thenUnauthenticatedProblemWithoutLoginUrl()
      throws Exception {
    final var result =
        mockMvc
            .perform(getGateway("/bff/events").accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isUnauthorized())
            .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
            .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
            .andExpect(content().string(not(containsString("login"))));
    expectProblem(result, ProblemType.UNAUTHENTICATED);
  }

  /** The other side of the default refusal: a logged-in user's session still opens the stream. */
  @Test
  void givenASession_whenSubscribeToEvents_thenTheStreamStarts() throws Exception {
    mockMvc
        .perform(getGateway("/bff/events").accept(MediaType.TEXT_EVENT_STREAM)
            .session(loggedInSession()))
        .andExpect(status().isOk())
        .andExpect(request().asyncStarted());
  }

  @Test
  void givenNoSession_whenGetUnknownRoutes_thenUnauthenticatedProblem() throws Exception {
    // refused by default, in both filter chains
    expectProblem(
        mockMvc.perform(getGateway("/unknown")).andExpect(status().isUnauthorized()),
        ProblemType.UNAUTHENTICATED);
    expectProblem(
        mockMvc.perform(getGateway("/bff/unknown")).andExpect(status().isUnauthorized()),
        ProblemType.UNAUTHENTICATED);
  }

  /** {@code 404} only under the client chain's prefixes: the resource server chain is stateless. */
  @Test
  void givenASession_whenGetUnknownRoutes_thenNotFoundUnderTheClientPrefixesOnly()
      throws Exception {
    expectProblem(
        mockMvc
            .perform(getGateway("/bff/unknown").session(loggedInSession()))
            .andExpect(status().isNotFound()),
        ProblemType.NOT_FOUND);
    expectProblem(
        mockMvc
            .perform(getGateway("/unknown").session(loggedInSession()))
            .andExpect(status().isUnauthorized()),
        ProblemType.UNAUTHENTICATED);
  }

  @Test
  void givenAnAccessRuleRefusing_whenCallIt_thenAccessDeniedProblem() throws Exception {
    expectProblem(
        mockMvc
            .perform(getGateway(FailingController.FORBIDDEN_PATH).session(loggedInSession()))
            .andExpect(status().isForbidden()),
        ProblemType.ACCESS_DENIED);
  }

  @Test
  void givenAnInvalidCsrfToken_whenLogout_thenAccessDeniedProblem() throws Exception {
    expectProblem(
        mockMvc
            .perform(post(CONTEXT_PATH + "/logout").contextPath(CONTEXT_PATH)
                .with(csrf().useInvalidToken()))
            .andExpect(status().isForbidden()),
        ProblemType.ACCESS_DENIED);
  }

  /**
   * Spring Security hands a missing CSRF token to the invalid-session strategy, as a session that
   * expired: {@code 401} {@code unauthenticated}, not {@code 403}.
   */
  @Test
  void givenNoCsrfToken_whenLogout_thenUnauthenticatedProblem() throws Exception {
    expectProblem(
        mockMvc
            .perform(post(CONTEXT_PATH + "/logout").contextPath(CONTEXT_PATH))
            .andExpect(status().isUnauthorized()),
        ProblemType.UNAUTHENTICATED);
  }

  /** An unexpected failure in a handler: its message stays in the logs. */
  @Test
  void givenAFailingHandler_whenCallIt_thenServerErrorProblemWithoutDetails() throws Exception {
    expectProblem(
        mockMvc
            .perform(getGateway(FailingController.PATH).session(loggedInSession()))
            .andExpect(status().isInternalServerError())
            .andExpect(content().string(not(containsString(FailingController.SECRET)))),
        ProblemType.SERVER_ERROR);
  }

  /**
   * What {@code TokenRelay=} throws, inside the handler, when no authorized client is left:
   * {@code RestfulOAuth2Filter} answers it {@code 401}, the shared exception resolver leaves it
   * alone.
   */
  @Test
  void givenTheClientNeedsAuthorizationAgain_whenRelayed_thenUnauthenticatedProblem()
      throws Exception {
    expectProblem(
        mockMvc
            // with a session: anonymous, the client chain would answer 401 before the handler
            .perform(getGateway(FailingController.CLIENT_AUTHORIZATION_REQUIRED_PATH)
                .session(loggedInSession()))
            .andExpect(status().isUnauthorized())
            .andExpect(header().doesNotExist(HttpHeaders.LOCATION)),
        ProblemType.UNAUTHENTICATED);
  }

  /**
   * A refresh the authorization server refuses ({@code invalid_grant}: SSO session or refresh token
   * expired) throws a plain {@code ClientAuthorizationException}: also a {@code 401}, not a
   * {@code 500}.
   */
  @Test
  void givenARefusedRefresh_whenRelayed_thenUnauthenticatedProblem() throws Exception {
    expectProblem(
        mockMvc
            // with a session: anonymous, the client chain would answer 401 before the handler
            .perform(getGateway(FailingController.REFRESH_REFUSED_PATH).session(loggedInSession()))
            .andExpect(status().isUnauthorized())
            .andExpect(header().doesNotExist(HttpHeaders.LOCATION)),
        ProblemType.UNAUTHENTICATED);
  }

  /** Handlers failing the ways the gateway's own ones can fail, under /bff/** (client chain). */
  @RestController
  static class FailingController {
    static final String PATH = "/bff/test/failing";

    static final String FORBIDDEN_PATH = "/bff/test/forbidden";

    static final String CLIENT_AUTHORIZATION_REQUIRED_PATH =
        "/bff/test/client-authorization-required";

    static final String REFRESH_REFUSED_PATH = "/bff/test/refresh-refused";

    static final String SECRET = "jdbc:postgresql://internal-host/secret";

    @GetMapping(PATH)
    @PreAuthorize("permitAll()")
    String fail() {
      throw new IllegalStateException(SECRET);
    }

    @GetMapping(FORBIDDEN_PATH)
    @PreAuthorize("denyAll()")
    String forbidden() {
      return "never";
    }

    @GetMapping(CLIENT_AUTHORIZATION_REQUIRED_PATH)
    @PreAuthorize("permitAll()")
    String requireClientAuthorization() {
      throw new ClientAuthorizationRequiredException(REGISTRATION_ID);
    }

    @GetMapping(REFRESH_REFUSED_PATH)
    @PreAuthorize("permitAll()")
    String refuseRefresh() {
      throw new ClientAuthorizationException(new OAuth2Error("invalid_grant"), REGISTRATION_ID);
    }
  }

  @Test
  void givenNoSession_whenCallTheApi_thenItsUnauthenticatedProblemIsRelayed() throws Exception {
    expectProblem(
        mockMvc
            .perform(getGateway("/bff/accounts"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer")),
        ProblemType.UNAUTHENTICATED);
  }

  @Test
  void givenAnApiProblem_whenRelayed_thenUnchanged() throws Exception {
    expectProblem(
        mockMvc
            .perform(getGateway("/bff/accounts/99"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.parameters.id").value(99))
            .andExpect(jsonPath("$.detail").value("Account 99 was modified in the meantime")),
        ProblemType.CONCURRENT_MODIFICATION);
  }

  /** On the main port, the actuator is just an unlisted path: refused by default. */
  @Test
  void givenNoToken_whenGetActuatorOnTheMainPort_thenUnauthenticatedProblem() throws Exception {
    expectProblem(
        mockMvc.perform(getGateway("/actuator/info")).andExpect(status().isUnauthorized()),
        ProblemType.UNAUTHENTICATED);
  }

  // ---------- what anonymous requests may reach ----------

  @Test
  void givenNoSession_whenStartLogin_thenOkWithTheAuthorizationUri() throws Exception {
    mockMvc
        .perform(getGateway("/oauth2/authorization/" + REGISTRATION_ID))
        .andExpect(status().isOk())
        .andExpect(header().exists(HttpHeaders.LOCATION));
  }

  @Test
  void givenNoSession_whenLogout_thenAcceptedWithoutEffect() throws Exception {
    mockMvc
        .perform(post(CONTEXT_PATH + "/logout").contextPath(CONTEXT_PATH).with(csrf()))
        .andExpect(status().isAccepted());
  }

  @Test
  void givenNoSession_whenGetError_thenItsProblem() throws Exception {
    // called directly, without an error status: 404 (an error dispatch carries its status)
    expectProblem(
        mockMvc.perform(getGateway("/error")).andExpect(status().isNotFound()),
        ProblemType.NOT_FOUND);
  }

  // ---------- refused return addresses ----------

  /** Not the application's origin, though one character away from it. */
  private static final String LOOKALIKE_URI = "https://host-docker.internal/ui/phish";

  private static final String FOREIGN_URI = "https://evil.example/";

  /** The application's own origin: what the SPA sends (its current page). */
  private static final String APP_URI = "https://host.docker.internal/ui/";

  /** Declared by the WireMock discovery document, as by Keycloak's. */
  @Value("${issuer}/protocol/openid-connect/logout")
  String endSessionEndpoint;

  private static final String AUTHORIZATION_REQUEST_ATTRIBUTE =
      HttpSessionOAuth2AuthorizationRequestRepository.class.getName() + ".AUTHORIZATION_REQUEST";

  private static MockHttpServletRequestBuilder startLogin() {
    return getGateway("/oauth2/authorization/" + REGISTRATION_ID);
  }

  private static MockHttpServletRequestBuilder logout() {
    return post(CONTEXT_PATH + "/logout").contextPath(CONTEXT_PATH).with(csrf());
  }

  /**
   * {@code 400} {@code invalid-request}, without {@code Location}, and no authorization request
   * saved. That a refused login leaves the rest of the session unchanged is in
   * {@code RefusedLoginSessionTest}.
   */
  private void expectRefusedLogin(MockHttpServletRequestBuilder request) throws Exception {
    final var session = loggedInSession();
    expectProblem(
        mockMvc
            .perform(request.session(session))
            .andExpect(status().isBadRequest())
            .andExpect(header().doesNotExist(HttpHeaders.LOCATION)),
        ProblemType.INVALID_REQUEST);
    assertThat(session.getAttribute(AUTHORIZATION_REQUEST_ATTRIBUTE)).isNull();
  }

  @Test
  void givenARefusedSuccessUri_whenStartLogin_thenInvalidRequestProblemWithoutLocation()
      throws Exception {
    expectRefusedLogin(startLogin().header("X-POST-LOGIN-SUCCESS-URI", LOOKALIKE_URI));
    expectRefusedLogin(startLogin().header("X-POST-LOGIN-SUCCESS-URI", FOREIGN_URI));
  }

  @Test
  void givenARefusedFailureUri_whenStartLogin_thenInvalidRequestProblemWithoutLocation()
      throws Exception {
    expectRefusedLogin(startLogin().header("X-POST-LOGIN-FAILURE-URI", FOREIGN_URI));
  }

  @Test
  void givenARefusedSuccessUriParameter_whenStartLogin_thenInvalidRequestProblemWithoutLocation()
      throws Exception {
    expectRefusedLogin(startLogin().queryParam("post_login_success_uri", FOREIGN_URI));
  }

  /**
   * Logged in: a refused logout must leave the session alone (anonymous, there would be nothing to
   * close and the test would pass for the wrong reason). No {@code Location}: no RP-Initiated
   * Logout, so Keycloak's session stays open too.
   */
  private void expectRefusedLogoutLeavesTheSessionsIntact(String postLogoutUri) throws Exception {
    final var session = loggedInSession();
    expectProblem(
        mockMvc
            .perform(logout().session(session).header("X-POST-LOGOUT-SUCCESS-URI", postLogoutUri))
            .andExpect(status().isBadRequest())
            .andExpect(header().doesNotExist(HttpHeaders.LOCATION)),
        ProblemType.INVALID_REQUEST);
    assertThat(session.isInvalid()).isFalse();
    mockMvc
        .perform(getGateway("/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("user"));
  }

  @Test
  void givenALoggedInUserAndARefusedUri_whenLogout_thenInvalidRequestProblemAndSessionsIntact()
      throws Exception {
    expectRefusedLogoutLeavesTheSessionsIntact(FOREIGN_URI);
  }

  /** spring-addons refuses a blank header at logout (it ignores one at login). */
  @Test
  void givenALoggedInUserAndABlankUri_whenLogout_thenInvalidRequestProblemAndSessionsIntact()
      throws Exception {
    expectRefusedLogoutLeavesTheSessionsIntact("");
  }

  /**
   * Neither refusal is logged as an error. A guard: MockMvc doesn't dispatch an error to
   * {@code /error} as the server does, whose log the E2E run checks (AC-3).
   */
  @Test
  void givenRefusedUris_whenLoginAndLogout_thenNoErrorLogged(CapturedOutput output)
      throws Exception {
    // only what this test logs: the class-wide capture also holds the other tests' output
    final var start = output.length();
    mockMvc
        .perform(startLogin().session(loggedInSession())
            .header("X-POST-LOGIN-SUCCESS-URI", LOOKALIKE_URI))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(logout().session(loggedInSession())
            .header("X-POST-LOGOUT-SUCCESS-URI", FOREIGN_URI))
        .andExpect(status().isBadRequest());
    assertThat(output.toString().substring(start))
        .doesNotContain(" ERROR ")
        .doesNotContain("InvalidRedirectionUriException");
  }

  @Test
  void givenTheAppsOwnUri_whenStartLogin_thenOkWithTheAuthorizationUri() throws Exception {
    mockMvc
        .perform(
            startLogin().session(loggedInSession()).header("X-POST-LOGIN-SUCCESS-URI", APP_URI))
        .andExpect(status().isOk())
        .andExpect(header().exists(HttpHeaders.LOCATION));
  }

  /**
   * RP-Initiated Logout: the {@code Location} is Keycloak's {@code end_session_endpoint} (WireMock
   * discovery), which sends the browser back to the application's address afterwards.
   */
  @Test
  void givenALoggedInUserAndTheAppsOwnUri_whenLogout_thenAcceptedWithLocation() throws Exception {
    final var location =
        mockMvc
            .perform(
                logout().session(loggedInSession()).header("X-POST-LOGOUT-SUCCESS-URI", APP_URI))
            .andExpect(status().isAccepted())
            .andExpect(header().exists(HttpHeaders.LOCATION))
            .andReturn()
            .getResponse()
            .getHeader(HttpHeaders.LOCATION);
    assertThat(UriComponentsBuilder.fromUriString(location).replaceQuery(null).toUriString())
        .isEqualTo(endSessionEndpoint);
    assertThat(
            UriComponentsBuilder.fromUriString(location)
                .build()
                .getQueryParams()
                .get("post_logout_redirect_uri"))
        .singleElement()
        .extracting(value -> UriUtils.decode(value, StandardCharsets.UTF_8))
        .isEqualTo(APP_URI);
  }
}
