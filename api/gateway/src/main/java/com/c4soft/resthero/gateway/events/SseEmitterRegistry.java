package com.c4soft.resthero.gateway.events;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.c4soft.resthero.commons.events.DomainEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps track of the {@link SseEmitter}s opened by each connected browser session, together with
 * the subject and granted authorities they were subscribed with, so that a {@link DomainEvent} can
 * be relayed to whoever owns the resource or holds one of its audience roles, without the gateway
 * needing to know anything about what the resource actually is.
 *
 * <p>
 * In-memory only, a flat list scanned on every event: with several gateway instances, an event
 * reaching an instance that doesn't hold a matching subscription is silently dropped for that
 * instance (see chapter 6 of the README).
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Component
@Slf4j
public class SseEmitterRegistry {

  private record Subscription(String subject, Set<String> roles, SseEmitter emitter) {
  }

  private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

  public void register(String subject, Set<String> roles, SseEmitter emitter) {
    final var subscription = new Subscription(subject, roles, emitter);
    subscriptions.add(subscription);
    final Runnable cleanup = () -> subscriptions.remove(subscription);
    emitter.onCompletion(cleanup);
    emitter.onTimeout(cleanup);
    emitter.onError(t -> cleanup.run());
  }

  public void broadcast(DomainEvent event) {
    for (final var subscription : subscriptions) {
      final var isOwner = subscription.subject().equals(event.resourceOwner());
      final var hasAudienceRole =
          event.audienceRoles().stream().anyMatch(subscription.roles()::contains);
      if (isOwner || hasAudienceRole) {
        send(subscription, event);
      }
    }
  }

  private void send(Subscription subscription, DomainEvent event) {
    try {
      subscription.emitter().send(event);
    } catch (IOException e) {
      log.debug("Failed to send {} to {}, dropping emitter", event, subscription.subject(), e);
      subscriptions.remove(subscription);
    }
  }
}
