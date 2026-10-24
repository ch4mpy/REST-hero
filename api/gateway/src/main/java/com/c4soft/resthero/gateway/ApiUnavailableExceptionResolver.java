package com.c4soft.resthero.gateway;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import com.c4soft.resthero.problem.ProblemResponses;
import com.c4soft.resthero.problem.ProblemType;

/**
 * The REST API unreachable (connection refused, timeout): the proxy of the {@code /bff/api/v1/**}
 * route ({@code RestClient}) throws a {@link ResourceAccessException}, answered with a {@code 503}
 * {@link ProblemType#SERVICE_UNAVAILABLE}, for which the frontend has a dedicated message.
 * Its details are logged, never sent.
 *
 * <p>
 * A {@link HandlerExceptionResolver}: {@code @ExceptionHandler}s don't apply to the gateway's
 * functional routes (see {@code com.c4soft.resthero.problem.ProblemHandlerExceptionResolver}).
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ApiUnavailableExceptionResolver implements HandlerExceptionResolver {

  private final ProblemResponses problems;

  public ApiUnavailableExceptionResolver(ProblemResponses problems) {
    this.problems = problems;
  }

  @Override
  public @Nullable ModelAndView resolveException(HttpServletRequest request,
      HttpServletResponse response, @Nullable Object handler, Exception ex) {
    if (!(ex instanceof ResourceAccessException) || response.isCommitted()) {
      return null;
    }
    log.error("{} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
    try {
      problems.write(request, response, ProblemType.SERVICE_UNAVAILABLE);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return new ModelAndView();
  }
}
