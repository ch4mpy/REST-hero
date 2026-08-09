package com.c4soft.resthero.gateway.events;

import java.util.ArrayList;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the gateway's own queue and binds it, with a catch-all routing key, to every business
 * service exchange listed in {@code rest-hero.events.subscribed-exchanges}.
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Configuration
@EnableConfigurationProperties(GatewayEventsProperties.class)
public class GatewayEventsConfiguration {

  @Bean
  Queue gatewayEventsQueue() {
    return new AnonymousQueue();
  }

  @Bean
  Declarables gatewayEventsBindings(Queue gatewayEventsQueue, GatewayEventsProperties properties) {
    final var declarables = new ArrayList<Declarable>();
    for (final var exchangeName : properties.getSubscribedExchanges()) {
      final var exchange = new TopicExchange(exchangeName);
      declarables.add(exchange);
      declarables.add(BindingBuilder.bind(gatewayEventsQueue).to(exchange).with("#"));
    }
    return new Declarables(declarables);
  }
}
