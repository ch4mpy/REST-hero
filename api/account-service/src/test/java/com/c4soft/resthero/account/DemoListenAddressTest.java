package com.c4soft.resthero.account;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;

/**
 * With the {@code demo} profile, which a container deployment sets, the REST API listens on all its
 * interfaces: in a container, the gateway reaches it on its network address. A management port
 * follows the main one. Same startup as {@link ListenAddressTest}, whose address lookup it reuses.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      "issuer=${keycloak.base-url}/auth/realms/labs",
      "spring.profiles.active=h2,no-observability,demo",
      "server.ssl.enabled=false",
      "management.server.port=0",
      "management.server.ssl.enabled=false",
      "spring.rabbitmq.listener.simple.auto-startup=false",
      "spring.rabbitmq.listener.direct.auto-startup=false"
    })
@EnableWireMock({
  @ConfigureWireMock(
      name = "keycloak",
      baseUrlProperties = "keycloak.base-url",
      filesUnderClasspath = "wiremock/keycloak")
})
class DemoListenAddressTest {

  @LocalServerPort int port;

  @LocalManagementPort int managementPort;

  @Test
  void givenTheDemoProfile_whenConnectOnALanAddress_thenBothPortsAccept() throws Exception {
    final var lanAddress = ListenAddressTest.assumeALanAddress();
    assertThatCode(() -> ListenAddressTest.connect(lanAddress, port)).doesNotThrowAnyException();
    assertThatCode(() -> ListenAddressTest.connect(lanAddress, managementPort))
        .doesNotThrowAnyException();
  }
}
