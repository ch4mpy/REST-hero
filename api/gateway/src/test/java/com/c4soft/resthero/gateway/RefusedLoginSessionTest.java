package com.c4soft.resthero.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import com.c4_soft.springaddons.security.oidc.starter.properties.SpringAddonsOidcClientProperties;
import com.c4soft.resthero.problem.ProblemType;

/**
 * A refused login changes no session: spring-addons' resolver
 * saves an allowed success address in the session before it refuses the failure address, so the
 * gateway checks both before it ({@code SpringSecurityPatchConfiguration}).
 *
 * <p>
 * Each request reaches the login filter: {@code /oauth2/**} is open to anonymous requests, and the
 * sessions are the SPA's anonymous one, as a login finds it. The whole session is compared before
 * and after, every attribute.
 * </p>
 *
 * <p>
 * Hence a context of its own, which no other class shares (its last property makes its cache key
 * unique): spring-security-test's {@code csrf()}, which other classes use
 * ({@code GatewayProblemsTest}), replaces the CSRF token repository of the context it runs in (the
 * gateway's keeps the token in a cookie) by one that keeps it in the session. Sharing a context
 * would also hurt the other class: the test framework restarts a cached context before handing it
 * to another class, which starts the RabbitMQ listeners whatever their {@code auto-startup}.
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
      "spring.rabbitmq.listener.direct.auto-startup=false",
      "refused-login-session-test.own-context=true"
    })
@ActiveProfiles("no-observability")
@AutoConfigureMockMvc
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
class RefusedLoginSessionTest {
  private static final String CONTEXT_PATH = "/gateway";

  private static final String REGISTRATION_ID = "gateway";

  private static final String FOREIGN_URI = "https://evil.example/";

  /** The application's own origin: what the SPA sends (its current page). */
  private static final String APP_URI = "https://host.docker.internal/ui/";

  /** Another page of the application, where an earlier login, never completed, was to return. */
  private static final String EARLIER_APP_URI = "https://host.docker.internal/ui/earlier";

  private static final String SUCCESS_URI_ATTRIBUTE =
      SpringAddonsOidcClientProperties.POST_AUTHENTICATION_SUCCESS_URI_SESSION_ATTRIBUTE;

  @Autowired MockMvc mockMvc;

  private static MockHttpServletRequestBuilder startLogin() {
    return get(CONTEXT_PATH + "/oauth2/authorization/" + REGISTRATION_ID).contextPath(CONTEXT_PATH);
  }

  /** A copy of the session's attributes, to compare them before and after a request. */
  private static Map<String, Object> attributes(MockHttpSession session) {
    final var attributes = new HashMap<String, Object>();
    for (final var name : Collections.list(session.getAttributeNames())) {
      attributes.put(name, session.getAttribute(name));
    }
    return attributes;
  }

  /**
   * spring-addons checks the success address, and stores it in the session, before it checks the
   * failure address: a login refused for its failure address must leave the session as it was, the
   * allowed success address not stored either.
   */
  private void expectRefusedLoginLeavesTheSessionUnchanged(MockHttpSession session)
      throws Exception {
    final var before = attributes(session);
    mockMvc
        .perform(startLogin().session(session)
            .header("X-POST-LOGIN-SUCCESS-URI", APP_URI)
            .header("X-POST-LOGIN-FAILURE-URI", FOREIGN_URI))
        .andExpect(status().isBadRequest())
        .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(ProblemType.INVALID_REQUEST.uri().toString()));
    assertThat(attributes(session)).isEqualTo(before);
  }

  /** The SPA's anonymous session, where an earlier login left its success address. */
  @Test
  void givenASessionHoldingASuccessUri_whenStartLoginWithARefusedFailureUri_thenInvalidRequestProblemAndSessionUnchanged()
      throws Exception {
    final var session = new MockHttpSession();
    session.setAttribute(
        SpringAddonsOidcClientProperties.POST_AUTHENTICATION_SUCCESS_URI_SESSION_ATTRIBUTE,
        EARLIER_APP_URI);
    expectRefusedLoginLeavesTheSessionUnchanged(session);
  }

  /** The SPA's anonymous session, holding no return address yet. */
  @Test
  void givenASessionWithoutSuccessUri_whenStartLoginWithARefusedFailureUri_thenInvalidRequestProblemAndSessionUnchanged()
      throws Exception {
    expectRefusedLoginLeavesTheSessionUnchanged(new MockHttpSession());
  }

  /** {@code 400} {@code invalid-request}, without {@code Location}. */
  private MvcResult expectRefused(MockHttpServletRequestBuilder request) throws Exception {
    return mockMvc
        .perform(request)
        .andExpect(status().isBadRequest())
        .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(ProblemType.INVALID_REQUEST.uri().toString()))
        .andReturn();
  }

  /** The failure address as a parameter, which spring-addons reads when the header is absent. */
  @Test
  void givenARefusedFailureUriParameter_whenStartLogin_thenSessionUnchanged() throws Exception {
    final var session = new MockHttpSession();
    session.setAttribute(SUCCESS_URI_ATTRIBUTE, EARLIER_APP_URI);
    final var before = attributes(session);

    expectRefused(
        startLogin()
            .session(session)
            .header("X-POST-LOGIN-SUCCESS-URI", APP_URI)
            .param("post_login_failure_uri", FOREIGN_URI));

    assertThat(attributes(session)).isEqualTo(before);
  }

  /** Without a session, a refused login opens none (spring-addons' resolver opens one first). */
  @Test
  void givenNoSession_whenStartLoginWithARefusedFailureUri_thenNoSessionOpened() throws Exception {
    final var result =
        expectRefused(
            startLogin()
                .header("X-POST-LOGIN-SUCCESS-URI", APP_URI)
                .header("X-POST-LOGIN-FAILURE-URI", FOREIGN_URI));

    assertThat(result.getRequest().getSession(false)).isNull();
  }

  /**
   * The other side: both addresses allowed, spring-addons saves the success one, as before. The
   * comparisons above aren't vacuous: this request does change the session.
   */
  @Test
  void givenAllowedUris_whenStartLogin_thenTheSuccessUriIsSaved() throws Exception {
    final var session = new MockHttpSession();
    session.setAttribute(SUCCESS_URI_ATTRIBUTE, EARLIER_APP_URI);

    mockMvc
        .perform(
            startLogin()
                .session(session)
                .header("X-POST-LOGIN-SUCCESS-URI", APP_URI)
                .header("X-POST-LOGIN-FAILURE-URI", APP_URI))
        .andExpect(status().isOk())
        .andExpect(header().exists(HttpHeaders.LOCATION));

    assertThat(session.getAttribute(SUCCESS_URI_ATTRIBUTE)).isEqualTo(APP_URI);
  }
}
