package com.c4soft.resthero.problem;

import java.io.IOException;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * The safety net for the errors that reach {@code /error} (an exception thrown by a filter, a
 * {@code sendError} of the container or of a component writing no body): a problem typed by the
 * status (see {@link ProblemResponses#forStatus}), in place of Spring Boot's
 * {@code BasicErrorController}, which backs off when an {@link ErrorController} exists.
 *
 * <p>
 * Left out of the OpenAPI specs ({@link Hidden}): no client calls it. Open to everyone, as an error
 * dispatch is authorized like any request: the security configurations permit {@code /error}.
 * Called directly, without an error status, it answers {@code 404}.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Hidden
@RestController
@RequestMapping("${spring.web.error.path:${error.path:/error}}")
@Slf4j
public class ApiErrorController implements ErrorController {

  private final ProblemResponses problems;

  public ApiErrorController(ProblemResponses problems) {
    this.problems = problems;
  }

  @RequestMapping
  @PreAuthorize("permitAll()")
  public void error(HttpServletRequest request, HttpServletResponse response) throws IOException {
    // HttpStatusCode, not HttpStatus: a non-standard code (599...) keeps its class
    final HttpStatusCode status =
        request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code
            ? HttpStatusCode.valueOf(code)
            : HttpStatus.NOT_FOUND;
    final var uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) instanceof String errorUri
        ? errorUri
        : request.getRequestURI();
    if (status.is5xxServerError()) {
      log.error("{} {}: {}", request.getMethod(), uri, status,
          request.getAttribute(RequestDispatcher.ERROR_EXCEPTION));
    }
    problems.write(uri, response, ProblemResponses.forStatus(status));
  }
}
