package com.c4soft.resthero.commons.events;

import java.time.Instant;
import java.util.List;

/**
 * Contract shared by every business service publishing to RabbitMQ and by the gateway relaying
 * events to the frontend over Server-Sent Events. Payload stays minimal (identifiers only): the
 * frontend is expected to refetch the actual resource over REST once notified.
 *
 * <p>
 * {@code resourceType} comes from the shared {@link ResourceType} taxonomy, so every publishing
 * service and the frontend agree on the same resource area names. {@code resourceOwner} and
 * {@code audienceRoles} together describe who should receive the event: the subject that owns the
 * resource, plus whichever authorities (mirroring the service's own {@code @PreAuthorize}
 * expressions) are allowed to see it regardless of ownership. The gateway only needs to know, for
 * each connected user, their subject and granted authorities to compute the audience, it has no
 * notion of what an "account" or a "transfer" is.
 */
public record DomainEvent(
    ResourceType resourceType,
    String resourceId,
    String resourceOwner,
    List<String> audienceRoles,
    EventType eventType,
    Instant occurredAt) {

  public enum EventType {
    CREATE, UPDATE, DELETE;
  }
}
