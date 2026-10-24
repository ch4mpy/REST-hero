package com.c4soft.resthero.problem;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes the exceptions no {@code @ExceptionHandler} handled as problems: a framework rejection
 * ({@link ErrorResponse}: unknown route, unsupported method or media type..., or an unreadable body,
 * a parameter of the wrong type) by its status (see
 * {@link ProblemResponses#forStatus}), any other exception as a {@code 500}
 * {@link ProblemType#SERVER_ERROR} whose details are logged, never sent (SEC-6).
 *
 * <p>
 * A {@link HandlerExceptionResolver} rather than a {@code @ControllerAdvice}: those only apply to
 * annotated controllers, not to the BFF's functional routes (Spring Cloud Gateway) nor to the static
 * resources handler answering unknown routes. {@link ProblemWebMvcConfiguration} inserts it right
 * before Spring's {@code DefaultHandlerExceptionResolver}: after the {@code @ExceptionHandler}s
 * (the REST API's business problems keep their types), before the framework's {@code sendError}s.
 * </p>
 *
 * <p>
 * Left to the next resolvers, and to Spring Security's exception translation: authentication and
 * access-denied exceptions (whose {@code 401} or {@code 403} the security configuration writes),
 * clients gone away ({@link AsyncRequestNotUsableException}), responses already committed (an
 * event stream) and what a {@link ProblemResolutionExclusion} bean excludes.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Slf4j
public class ProblemHandlerExceptionResolver implements HandlerExceptionResolver {

  private final ProblemResponses problems;

  private final List<ProblemResolutionExclusion> exclusions;

  public ProblemHandlerExceptionResolver(ProblemResponses problems,
      List<ProblemResolutionExclusion> exclusions) {
    this.problems = problems;
    this.exclusions = List.copyOf(exclusions);
  }

  @Override
  public @Nullable ModelAndView resolveException(HttpServletRequest request,
      HttpServletResponse response, @Nullable Object handler, Exception ex) {
    if (response.isCommitted() || ex instanceof AccessDeniedException
        || ex instanceof AuthenticationException || ex instanceof AsyncRequestNotUsableException
        || exclusions.stream().anyMatch(exclusion -> exclusion.excludes(ex))) {
      return null;
    }
    final ApiProblemDetail problem;
    if ((ex instanceof HttpMessageNotReadableException || ex instanceof TypeMismatchException)
        && !(ex instanceof ConversionNotSupportedException)) {
      // client rejections Spring's DefaultHandlerExceptionResolver maps to a 400 although they are
      // no ErrorResponse (a missing converter is a server fault: a 500, as Spring answers it)
      problem = ProblemResponses.of(ProblemType.INVALID_REQUEST);
      log.debug("{} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
    } else if (ex instanceof ErrorResponse errorResponse) {
      problem = ProblemResponses.forStatus(errorResponse.getStatusCode());
      errorResponse.getHeaders().forEach((name, values) -> values
          .forEach(value -> response.addHeader(name, value)));
      // HttpStatusCode, not HttpStatus: a non-standard 4xx keeps its status (forStatus)
      if (HttpStatusCode.valueOf(problem.getStatus()).is5xxServerError()) {
        log.error("{} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
      } else {
        log.debug("{} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
      }
    } else {
      problem = ProblemResponses.of(ProblemType.SERVER_ERROR);
      log.error("{} {}: unhandled exception", request.getMethod(), request.getRequestURI(), ex);
    }
    try {
      problems.write(request, response, problem);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return new ModelAndView();
  }
}
