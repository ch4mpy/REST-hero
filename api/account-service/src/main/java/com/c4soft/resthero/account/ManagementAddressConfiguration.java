package com.c4soft.resthero.account;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextType;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementServerProperties;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * A management port, when the application opens a separate one, listens on the application's own
 * address ({@code server.address}, the loopback in dev), unless {@code management.server.address}
 * is set: Spring Boot would bind it to every interface.
 *
 * <ul>
 *   <li>Why not {@code management.server.address} in {@code application.yml}: Spring Boot refuses
 *       to start when it is set without a separate management port, the default ("Management-specific
 *       server address cannot be configured as the management server is not listening on a separate
 *       port").
 *   <li>A configuration of the management child context, which exists only with a separate port,
 *       registered by {@code
 *       META-INF/spring/org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration.imports}.
 *       Its customizer runs after Spring Boot's ({@code ManagementWebServerFactoryCustomizer}, order
 *       {@code 0}), which sets {@code management.server.address} on the management server: {@code
 *       null}, every interface, by default.
 *   <li>Why its properties are optional: in the application's package, the main context's component
 *       scan loads it too (the scan's filter leaves out auto-configurations only, not management
 *       context configurations), and there {@code ManagementServerProperties} exists only with a
 *       separate management port, so the customizer does nothing where they are absent (and, where
 *       they are, sets on the main server the {@code server.address} it already has), rather than
 *       sitting outside the scan root, which a one-segment group id hasn't got.
 * </ul>
 */
@ManagementContextConfiguration(value = ManagementContextType.CHILD, proxyBeanMethods = false)
class ManagementAddressConfiguration {

  @Bean
  ManagementAddressCustomizer managementAddressCustomizer(
      ObjectProvider<ManagementServerProperties> managementServerProperties,
      ObjectProvider<ServerProperties> serverProperties) {
    return new ManagementAddressCustomizer(managementServerProperties, serverProperties);
  }

  /**
   * Copies {@code server.address} to the management server when {@code management.server.address}
   * is unset; an explicit management address is left as Spring Boot set it. Without either
   * properties bean (the main context without a separate management port), it does nothing.
   */
  static final class ManagementAddressCustomizer
      implements WebServerFactoryCustomizer<ConfigurableWebServerFactory>, Ordered {

    private final ObjectProvider<ManagementServerProperties> managementServerProperties;

    private final ObjectProvider<ServerProperties> serverProperties;

    ManagementAddressCustomizer(
        ObjectProvider<ManagementServerProperties> managementServerProperties,
        ObjectProvider<ServerProperties> serverProperties) {
      this.managementServerProperties = managementServerProperties;
      this.serverProperties = serverProperties;
    }

    @Override
    public void customize(ConfigurableWebServerFactory factory) {
      final var management = managementServerProperties.getIfAvailable();
      final var server = serverProperties.getIfAvailable();
      if (management != null && server != null && management.getAddress() == null) {
        factory.setAddress(server.getAddress());
      }
    }

    /** After Spring Boot's {@code ManagementWebServerFactoryCustomizer} (order {@code 0}). */
    @Override
    public int getOrder() {
      return Ordered.LOWEST_PRECEDENCE;
    }
  }
}
