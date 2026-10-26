package com.c4soft.resthero.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.PathMappedEndpoints;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;

/**
 * The gateway's actuator, on its management port: {@code health} answers without a token, nothing
 * else is exposed. A real server: MockMvc only reaches the main port.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      "issuer=${keycloak.base-url}/auth/realms/labs",
      "accounts-service=${account-service.base-url}",
      "cards-service=${account-service.base-url}",
      "customers-service=${account-service.base-url}",
      "currencies-service=${account-service.base-url}",
      "server.ssl.enabled=false",
      "management.server.port=0",
      "management.server.ssl.enabled=false",
      // no broker in this test: its health would turn the aggregate DOWN (503)
      "management.health.rabbit.enabled=false",
      "spring.rabbitmq.listener.simple.auto-startup=false",
      "spring.rabbitmq.listener.direct.auto-startup=false"
    })
@ActiveProfiles("no-observability")
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
class ActuatorExposureTest {

  @LocalManagementPort int managementPort;

  @Autowired PathMappedEndpoints endpoints;

  private HttpStatusCode get(String path) {
    return RestClient.create("http://localhost:" + managementPort)
        .get()
        .uri(path)
        .exchange((request, response) -> response.getStatusCode());
  }

  @Test
  void givenNoToken_whenGetHealth_thenOk() {
    assertThat(get("/actuator/health").value()).isEqualTo(200);
  }

  /**
   * Checked on the exposed endpoints rather than on a status: without a token, an unlisted path gets
   * the resource server chain's {@code 401} whether {@code info} is exposed or not.
   */
  @Test
  void whenListExposedEndpoints_thenHealthOnly() {
    assertThat(endpoints.getAllRootPaths()).containsExactly("health");
  }
}
