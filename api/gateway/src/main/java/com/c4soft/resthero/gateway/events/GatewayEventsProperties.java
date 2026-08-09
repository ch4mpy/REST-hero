package com.c4soft.resthero.gateway.events;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

/**
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@ConfigurationProperties(prefix = "rest-hero.events")
@Data
public class GatewayEventsProperties {

  /**
   * Names of the business services' topic exchanges the gateway relays events from (mirrors the
   * per-service coupling the gateway already has for its routes).
   */
  private final List<String> subscribedExchanges;
}
