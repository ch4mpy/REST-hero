package com.c4soft.resthero.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/**
 * Every route the gateway relays downstream is listed in {@code client.permit-all}: the
 * downstream service controls its access, and the list must say so. Read from {@code application.yml}
 * itself, as YAML can't share the paths between the routes and the list.
 */
class AnonymousAccessConfigurationTest {

  private static final String ROUTES = "spring.cloud.gateway.server.webmvc.routes";

  private static final String CLIENT_PERMIT_ALL =
      "com.c4-soft.springaddons.oidc.client.permit-all";

  private static final Pattern ROUTE_PREDICATE =
      Pattern.compile(Pattern.quote(ROUTES) + "\\[\\d+]\\.predicates\\[\\d+]");

  private static final Pattern PERMIT_ALL_ENTRY =
      Pattern.compile(Pattern.quote(CLIENT_PERMIT_ALL) + "\\[\\d+]");

  private static Properties applicationYml() {
    final var yaml = new YamlPropertiesFactoryBean();
    yaml.setResources(new ClassPathResource("application.yml"));
    return yaml.getObject();
  }

  /** The {@code Path=} patterns of the relayed routes. */
  static List<String> relayedPaths(Properties properties) {
    return properties.stringPropertyNames().stream()
        .filter(name -> ROUTE_PREDICATE.matcher(name).matches())
        .map(properties::getProperty)
        .filter(predicate -> predicate.startsWith("Path="))
        .flatMap(predicate -> Arrays.stream(predicate.substring("Path=".length()).split(",")))
        .map(String::trim)
        .toList();
  }

  static List<String> clientPermitAll(Properties properties) {
    return properties.stringPropertyNames().stream()
        .filter(name -> PERMIT_ALL_ENTRY.matcher(name).matches())
        .map(properties::getProperty)
        .toList();
  }

  static List<String> missingFromPermitAll(Properties properties) {
    final var permitAll = clientPermitAll(properties);
    return relayedPaths(properties).stream().filter(path -> !permitAll.contains(path)).toList();
  }

  @Test
  void givenTheGatewayConfiguration_whenListRelayedRoutes_thenAllAreInClientPermitAll() {
    final var properties = applicationYml();

    assertThat(relayedPaths(properties)).isNotEmpty().contains("/bff/accounts/**");
    assertThat(missingFromPermitAll(properties)).isEmpty();
  }

  /** The check can fail: a relayed route missing from the list is reported. */
  @Test
  void givenARelayedRouteMissingFromPermitAll_whenCheck_thenItIsReported() {
    final var properties = applicationYml();
    properties.setProperty(ROUTES + "[99].predicates[0]", "Path=/bff/other/**,/bff/accounts/**");

    assertThat(missingFromPermitAll(properties)).containsExactly("/bff/other/**");
  }
}
