package com.c4soft.resthero.problem;

import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@link ProblemType#VALIDATION}: the request was well-formed but some values violate constraints.
 */
public class ValidationProblemDetail extends ApiProblemDetail {
  private static final long serialVersionUID = -757082078248225594L;

  private final Map<String, String> invalidFields;

  public ValidationProblemDetail(String detail, Map<String, String> invalidFields) {
    super(ProblemType.VALIDATION, detail, Map.of());
    this.invalidFields = Map.copyOf(invalidFields);
  }

  /** Violated property path (e.g. {@code answers[1].value}) to constraint message. */
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  public Map<String, String> getInvalidFields() {
    return invalidFields;
  }
}
