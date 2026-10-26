package com.c4soft.resthero.currency;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;

/**
 * With its default configuration, the REST API listens on the loopback only: its main port and a
 * management port, here a separate one opened without {@code management.server.address}, accept
 * a connection from {@code 127.0.0.1} and refuse one on an address of the machine that other
 * devices of the local network could reach. In dev, the gateway reaches it through
 * {@code host.docker.internal}, which resolves to the loopback.
 *
 * <p>
 * Same startup as {@code ActuatorExposureTest} (H2, Keycloak served by WireMock, the RabbitMQ
 * listeners don't start), with a real server: the test connects to its sockets.
 * </p>
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      "issuer=${keycloak.base-url}/auth/realms/labs",
      "spring.profiles.active=no-observability",
      "server.ssl.enabled=false",
      "com.c4-soft.springaddons.rest.client.frankfurter-client.base-url=http://localhost:1",
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
class ListenAddressTest {

  /**
   * Windows refuses a connection to its own non-loopback address after about 2 s (the SYN is sent
   * again after the RST). A refusal is a {@link ConnectException}; a connection that waits longer
   * ends in a {@code SocketTimeoutException}, which fails the refusal and the acceptance alike,
   * never passes either.
   */
  static final int CONNECT_TIMEOUT_MILLIS = 5_000;

  @LocalServerPort int port;

  @LocalManagementPort int managementPort;

  /**
   * The first IPv4 address of an active interface, neither loopback nor link-local: an address
   * other devices of the local network could connect to.
   */
  static Optional<InetAddress> lanAddress() throws SocketException {
    for (final var networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
      if (!networkInterface.isUp() || networkInterface.isLoopback()) {
        continue;
      }
      for (final var address : Collections.list(networkInterface.getInetAddresses())) {
        if (address instanceof Inet4Address
            && !address.isLoopbackAddress()
            && !address.isLinkLocalAddress()) {
          return Optional.of(address);
        }
      }
    }
    return Optional.empty();
  }

  /** Skips the test, saying so, on a machine without such an address. */
  static InetAddress assumeALanAddress() throws SocketException {
    final var address = lanAddress();
    Assumptions.assumeTrue(
        address.isPresent(), "no IPv4 address on a network interface: nothing to connect on");
    return address.get();
  }

  static void connect(InetAddress address, int port) throws IOException {
    try (var socket = new Socket()) {
      socket.connect(new InetSocketAddress(address, port), CONNECT_TIMEOUT_MILLIS);
    }
  }

  @Test
  void givenDefaultConfiguration_whenConnectFromLoopback_thenBothPortsAccept() throws Exception {
    final var loopback = InetAddress.getByName("127.0.0.1");
    assertThatCode(() -> connect(loopback, port)).doesNotThrowAnyException();
    assertThatCode(() -> connect(loopback, managementPort)).doesNotThrowAnyException();
  }

  @Test
  void givenDefaultConfiguration_whenConnectOnALanAddress_thenMainPortRefused() throws Exception {
    final var lanAddress = assumeALanAddress();
    assertThatThrownBy(() -> connect(lanAddress, port)).isInstanceOf(ConnectException.class);
  }

  @Test
  void givenDefaultConfiguration_whenConnectOnALanAddress_thenManagementPortRefused()
      throws Exception {
    final var lanAddress = assumeALanAddress();
    assertThatThrownBy(() -> connect(lanAddress, managementPort))
        .isInstanceOf(ConnectException.class);
  }
}
