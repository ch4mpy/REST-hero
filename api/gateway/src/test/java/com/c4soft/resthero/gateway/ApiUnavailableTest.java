package com.c4soft.resthero.gateway;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import com.c4soft.resthero.problem.ProblemType;

/**
 * A downstream service unreachable: the gateway answers {@code 503} {@code service-unavailable},
 * without the service's address. Nothing listens on the port the four services are given here,
 * whatever runs on the developer's machine.
 */
@SpringBootTest(
    properties = {
      "issuer=${keycloak.base-url}/auth/realms/labs",
      "accounts-service=http://localhost:1",
      "cards-service=http://localhost:1",
      "customers-service=http://localhost:1",
      "currencies-service=http://localhost:1",
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
      filesUnderClasspath = "wiremock/keycloak")
})
class ApiUnavailableTest {
  private static final String CONTEXT_PATH = "/gateway";

  @Autowired MockMvc mockMvc;

  /** One request per relayed route (spring.cloud.gateway.server.webmvc.routes). */
  @ParameterizedTest
  @ValueSource(strings = {"/bff/accounts", "/bff/transfers", "/bff/cards", "/bff/customers", "/bff/currencies"})
  void givenTheServiceUnreachable_whenCallIt_thenServiceUnavailableProblem(String path)
      throws Exception {
    mockMvc
        .perform(get(CONTEXT_PATH + path).contextPath(CONTEXT_PATH))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(ProblemType.SERVICE_UNAVAILABLE.uri().toString()))
        .andExpect(content().string(not(containsString("localhost"))));
  }
}
