package com.c4soft.resthero.problem;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * RFC 9457 problem details whose {@code type} is one of {@link ProblemType}, plus the named values
 * the frontend interpolates in the localized message for that type.
 *
 * <p>
 * Returned as-is from the REST API's exception handlers (Spring MVC sets the {@code instance} and
 * {@code application/problem+json} content type for any {@link ProblemDetail} body), or written by
 * {@link ProblemResponses} where Spring MVC does not render the response.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public class ApiProblemDetail extends ProblemDetail {
  private static final long serialVersionUID = -5170243660409538104L;

  /** The name of the {@link ProblemType} schema in the OpenAPI spec. */
  public static final String PROBLEM_TYPE_SCHEMA = "ProblemType";

  public static final String PROBLEM_TYPE_SCHEMA_REF = "#/components/schemas/" + PROBLEM_TYPE_SCHEMA;

  private final Map<String, Object> parameters;

  public ApiProblemDetail(ProblemType type, String detail, Map<String, Object> parameters) {
    this(type, type.status(), detail, parameters);
  }

  /**
   * For {@link ProblemType#INVALID_REQUEST}, whose status is that of the rejection: every other
   * type has its own.
   */
  public ApiProblemDetail(ProblemType type, HttpStatusCode status, String detail,
      Map<String, Object> parameters) {
    super(ProblemDetail.forStatusAndDetail(status, detail));
    setType(type.uri());
    this.parameters = Map.copyOf(parameters);
  }

  /** Same value as the parent's, overridden only to document it as a {@link ProblemType}. */
  @Override
  @Schema(ref = PROBLEM_TYPE_SCHEMA_REF)
  public URI getType() {
    // ProblemDetail#getType is declared @Nullable; both constructors set the type, and a null here
    // would be a bug to see at once, not a value to invent outside the ProblemType schema
    return Objects.requireNonNull(super.getType());
  }

  /**
   * Named values to interpolate in the localized message for the {@link #getType() type}. Always
   * serialized, even empty: Spring's {@code ProblemDetailJacksonMixin} would otherwise leave an
   * empty map out, against the schema, which requires it.
   */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  public Map<String, Object> getParameters() {
    return parameters;
  }
}
