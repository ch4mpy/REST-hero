package com.c4soft.resthero.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import com.c4soft.resthero.problem.ProblemType;

/**
 * How the whole security chain treats a session cookie the gateway no longer knows (browser
 * reopened, session expired, gateway restarted), never with a redirection:
 *
 * <ul>
 * <li>on {@code /me} only, spring-addons' {@code invalid-session.pass-through-patterns} opens a new
 * session and the request proceeds as without a cookie: the session probe answers anonymous
 *;</li>
 * <li>every other route gets the invalid-session strategy's {@code 401};</li>
 * <li>{@code /logout} proceeds, its filter running before the session management.</li>
 * </ul>
 *
 * <p>
 * Keycloak's discovery documents are served by WireMock, as for the {@code openapi} Maven profile,
 * and so is {@code account-service}, which answers {@code 401} to what the gateway relays without a
 * token; the RabbitMQ listeners (resource events) don't start, so no broker is needed.
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
class InvalidSessionTest {
  private static final String CONTEXT_PATH = "/gateway";

  private static final String UNKNOWN_SESSION_ID = "0123456789ABCDEF0123456789ABCDEF";

  @Autowired MockMvc mockMvc;

  private static RequestPostProcessor unknownSession() {
    return request -> {
      request.setRequestedSessionId(UNKNOWN_SESSION_ID);
      request.setRequestedSessionIdValid(false);
      request.setRequestedSessionIdFromCookie(true);
      return request;
    };
  }

  private static MockHttpServletRequestBuilder getWithUnknownSession(String path) {
    return get(CONTEXT_PATH + path).contextPath(CONTEXT_PATH).with(unknownSession());
  }

  @Test
  void givenNoSessionCookie_whenGetMe_thenOkAnonymous() throws Exception {
    mockMvc.perform(get(CONTEXT_PATH + "/me").contextPath(CONTEXT_PATH)).andExpect(status().isOk());
  }

  @Test
  void givenUnknownSessionCookie_whenGetMe_thenOkAnonymousWithANewSession() throws Exception {
    MvcResult result =
        mockMvc
            .perform(getWithUnknownSession("/me"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
            .andExpect(jsonPath("$.sub").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.roles").isEmpty())
            .andReturn();
    // the pass-through replaced the stale session: a new one, not the unknown ID
    assertThat(result.getRequest().getSession(false)).isNotNull();
    assertThat(result.getRequest().getSession(false).getId()).isNotEqualTo(UNKNOWN_SESSION_ID);
  }

  @Test
  void givenNoSessionCookie_whenGetBffRoute_thenUnauthorized() throws Exception {
    mockMvc
        .perform(get(CONTEXT_PATH + "/bff/accounts").contextPath(CONTEXT_PATH))
        .andExpect(status().isUnauthorized());
  }

  /**
   * Not passed through: the gateway answers an {@code unauthenticated} problem
   * and the request is not relayed (that it opens no session:
   * {@link ProblemSecurityConfigurationTest}).
   */
  @Test
  void givenUnknownSessionCookie_whenGetBffRoute_thenUnauthenticatedProblemFromTheGateway()
      throws Exception {
    mockMvc
        .perform(getWithUnknownSession("/bff/accounts"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
        // not relayed: account-service (WireMock) would have answered with a Bearer challenge
        .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(ProblemType.UNAUTHENTICATED.uri().toString()));
  }

  @Test
  void givenUnknownSessionCookie_whenSubscribeToEvents_thenUnauthenticatedProblemWithoutRedirection()
      throws Exception {
    mockMvc
        .perform(getWithUnknownSession("/bff/events").accept(MediaType.TEXT_EVENT_STREAM))
        .andExpect(status().isUnauthorized())
        .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
        .andExpect(jsonPath("$.type").value(ProblemType.UNAUTHENTICATED.uri().toString()));
  }

  /**
   * The logout filter runs before the session management: a logout with a stale session cookie
   * proceeds (RP-initiated logout, {@code 202} with the URI to follow), never a redirection.
   */
  @Test
  void givenUnknownSessionCookie_whenLogout_thenAcceptedWithoutRedirection() throws Exception {
    mockMvc
        .perform(
            post(CONTEXT_PATH + "/logout")
                .contextPath(CONTEXT_PATH)
                .with(csrf())
                .with(unknownSession()))
        .andExpect(status().isAccepted())
        // the SPA follows it: its own post-logout page, not a login page
        .andExpect(header().string(HttpHeaders.LOCATION, "https://host.docker.internal/ui/"));
  }
}
