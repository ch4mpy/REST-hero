package com.c4soft.resthero.problem;

/**
 * Wraps an {@link ApiProblemException} where a checked exception can't be declared (Spring
 * {@code Converter}s, lambdas). {@link ApiExceptionHandler} resolves handlers through the cause
 * chain, so the wrapped problem is reported as if thrown directly.
 */
public class ApiProblemRuntimeException extends RuntimeException {
  private static final long serialVersionUID = -1234860742883560713L;

  private final ApiProblemException problem;

  public ApiProblemRuntimeException(ApiProblemException cause) {
    super(cause.getMessage(), cause);
    this.problem = cause;
  }

  @Override
  public synchronized ApiProblemException getCause() {
    return problem;
  }
}
