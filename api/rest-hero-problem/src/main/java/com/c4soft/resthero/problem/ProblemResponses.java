package com.c4soft.resthero.problem;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes a {@link ApiProblemDetail} where Spring MVC does not render the response: security
 * filters (authentication entry point, access denied, invalid session) and exception resolvers.
 *
 * <p>
 * The {@code detail} of the problems built here is a generic sentence for the logs and the curious:
 * never an exception message, and never displayed by the frontend, which translates the
 * {@code type}.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public class ProblemResponses {

  private final JsonMapper jsonMapper;

  public ProblemResponses(JsonMapper jsonMapper) {
    // the same rendering as Spring MVC's converters: no "properties" member
    this.jsonMapper =
        jsonMapper.rebuild().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class).build();
  }

  /** A problem of one of the parameterless types, with that type's status. */
  public static ApiProblemDetail of(ProblemType type) {
    return of(type, type.status());
  }

  /** A problem of one of the parameterless types, with an explicit status. */
  public static ApiProblemDetail of(ProblemType type, HttpStatusCode status) {
    return new ApiProblemDetail(type, status, genericDetail(type), Map.of());
  }

  public void write(HttpServletRequest request, HttpServletResponse response, ProblemType type)
      throws IOException {
    write(request, response, of(type));
  }

  /**
   * The problem for an error known only by its status (a framework rejection, what reaches
   * {@code /error}): {@code 401}, {@code 403}, {@code 404} and {@code 503} have their own type,
   * another {@code 4xx} is an {@link ProblemType#INVALID_REQUEST} with that status, anything else a
   * {@code 500} {@link ProblemType#SERVER_ERROR}.
   */
  public static ApiProblemDetail forStatus(HttpStatusCode status) {
    return switch (status.value()) {
      case 401 -> of(ProblemType.UNAUTHENTICATED);
      case 403 -> of(ProblemType.ACCESS_DENIED);
      case 404 -> of(ProblemType.NOT_FOUND);
      case 503 -> of(ProblemType.SERVICE_UNAVAILABLE);
      default -> status.is4xxClientError() ? of(ProblemType.INVALID_REQUEST, status)
          : of(ProblemType.SERVER_ERROR);
    };
  }

  public void write(HttpServletRequest request, HttpServletResponse response,
      ApiProblemDetail problem) throws IOException {
    write(request.getRequestURI(), response, problem);
  }

  /**
   * @param instance the URI the problem occurred on (on an error dispatch, the original request's,
   *        not {@code /error})
   */
  public void write(String instance, HttpServletResponse response, ApiProblemDetail problem)
      throws IOException {
    problem.setInstance(URI.create(instance));
    response.setStatus(problem.getStatus());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8);
    jsonMapper.writeValue(response.getOutputStream(), problem);
    response.flushBuffer();
  }

  /** The generic {@code detail} of a problem of that type: never an exception message. */
  public static String genericDetail(ProblemType type) {
    return switch (type) {
      case UNAUTHENTICATED -> "Authentication is required";
      case ACCESS_DENIED -> "Access is denied";
      case NOT_FOUND -> "No resource matches the request";
      case INVALID_REQUEST -> "The request was rejected";
      case SERVICE_UNAVAILABLE -> "A service the request depends on is temporarily unavailable";
      default -> "The request could not be completed because of a server-side failure";
    };
  }
}
