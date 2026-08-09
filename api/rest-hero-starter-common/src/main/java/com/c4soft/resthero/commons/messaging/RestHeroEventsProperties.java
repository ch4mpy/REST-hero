package com.c4soft.resthero.commons.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

/**
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@ConfigurationProperties(prefix = "rest-hero.events")
@Data
public class RestHeroEventsProperties {

  /**
   * Name of the topic exchange this service publishes its domain events to (consumers, such as
   * the gateway, subscribe to it by name).
   */
  private final String exchangeName;
}
