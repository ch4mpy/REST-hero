package com.c4soft.resthero.problem;

import java.util.Map;
import lombok.Getter;

/**
 * Thrown by business rules; carries the exact {@link ApiProblemDetail} the client gets (see
 * {@link ApiExceptionHandler}).
 *
 * <p>
 * Checked, so that endpoint signatures list the problems they can actually report (and springdoc
 * documents them on exactly those endpoints). Two consequences: {@code @Transactional} methods that
 * can throw it and write must declare {@code rollbackFor = ApiProblemException.class} (Spring only
 * rolls back on unchecked exceptions by default), and code that can't declare it (Spring
 * {@code Converter}s, lambdas) throws it wrapped with {@link #unchecked()}.
 * </p>
 *
 * <p>
 * Deliberately not an {@link org.springframework.web.ErrorResponseException}: the problem module's
 * {@code ProblemHandlerExceptionResolver} would write it as a generic problem, losing its type.
 * </p>
 */
@Getter
public class ApiProblemException extends Exception {
  private static final long serialVersionUID = 2596330081420287571L;

  private final ProblemType type;

  private final transient ApiProblemDetail problem;

  /**
   * @param type the problem type
   * @param detail a human-readable, non-localized explanation (for logs and non-browser clients)
   * @param parameters the values listed on the {@link ProblemType} constant
   */
  public ApiProblemException(ProblemType type, String detail, Map<String, Object> parameters) {
    super(detail);
    this.type = type;
    this.problem = new ApiProblemDetail(type, detail, parameters);
  }

  /** For the places that can't declare a checked exception. */
  public ApiProblemRuntimeException unchecked() {
    return new ApiProblemRuntimeException(this);
  }
}
