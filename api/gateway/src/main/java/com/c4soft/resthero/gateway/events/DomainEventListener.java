package com.c4soft.resthero.gateway.events;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import com.c4soft.resthero.commons.events.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DomainEventListener {

  private final SseEmitterRegistry registry;

  @RabbitListener(queues = "#{gatewayEventsQueue.name}")
  public void onDomainEvent(DomainEvent event) {
    // LAB:6.2:TODO:START relayer l'événement vers les abonnés concernés
    registry.broadcast(event);
    // LAB:6.2:TODO:END
  }
}
