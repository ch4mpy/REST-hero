package com.c4soft.resthero.gateway.events;

import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.c4soft.resthero.commons.events.DomainEvent;

import io.micrometer.observation.annotation.Observed;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Tag(name = "Gateway")
@RestController
@RequiredArgsConstructor
@Observed
@Slf4j
public class SseController {
  public static final String BASE_PATH = "/bff/events";

  private final SseEmitterRegistry registry;

  /**
   * Subscribes the current user to the notification stream: a {@link DomainEvent} is pushed
   * whenever something relevant to them happens on a business service. The frontend is expected
   * to refetch the actual resource over REST once notified.
   */
  @PreAuthorize("isAuthenticated()")
  @GetMapping(path = BASE_PATH, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  // LAB:6.2:TODO:START documenter le flux SSE dans la spec OpenAPI
  @ApiResponse(responseCode = "200",
      content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
          schema = @Schema(implementation = DomainEvent.class)))
  // LAB:6.2:TODO:END
  public SseEmitter subscribeToServerStateChangedEvents(Authentication auth) {
    final var emitter = new SseEmitter(0L);
    // LAB:6.2:TODO:START enregistrer l'émetteur, le sujet et les rôles de l'utilisateur courant
    final var roles =
        auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    registry.register(auth.getName(), roles, emitter);
    // LAB:6.2:TODO:END
    return emitter;
  }

}
