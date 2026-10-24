package com.c4soft.resthero.problem;

import static java.util.stream.Collectors.toMap;

import java.util.Map;
import java.util.Objects;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns {@link ApiProblemException} and validation failures into {@link ApiProblemDetail}
 * bodies. Every other exception is left to {@link ProblemHandlerExceptionResolver} (framework
 * rejections, unexpected failures) or to the security configuration ({@code 401}, {@code 403}),
 * which write the generic problem types.
 *
 * <p>
 * springdoc propagates the {@code @ApiResponse}s below to the operations
 * ({@code springdoc.override-with-generic-response}): those of a handler for an unchecked
 * exception to every operation, those of a handler for a checked exception only to the operations
 * declaring it in their {@code throws} clause. This is how {@link ApiProblemDetail},
 * {@link ValidationProblemDetail} and {@link ProblemType} get into the OpenAPI spec, and why the
 * {@code 422} is declared on the unchecked validation handlers too (a
 * {@code MethodArgumentNotValidException} is checked, and no endpoint declares it).
 * </p>
 *
 * <p>
 * Highest precedence so that it is consulted before any other advice for validation exceptions
 * and the type-mismatch exceptions in which Spring MVC wraps an {@link ApiProblemException}
 * thrown by a path-variable converter (otherwise generic {@code 400}s).
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ApiExceptionHandler {

  static final String VALIDATION_DETAIL = "Validation failed";
  static final String SERVER_ERROR_DETAIL = ProblemResponses.genericDetail(ProblemType.SERVER_ERROR);

  @ExceptionHandler(ApiProblemException.class)
  @ApiResponses({
      @ApiResponse(responseCode = "400", description = "Bad Request",
          content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = ApiProblemDetail.class))),
      @ApiResponse(responseCode = "404", description = "Not Found",
          content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = ApiProblemDetail.class))),
      @ApiResponse(responseCode = "409", description = "Conflict",
          content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = ApiProblemDetail.class))),
      @ApiResponse(responseCode = "500", description = "Internal Server Error",
          content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
              schema = @Schema(implementation = ApiProblemDetail.class)))})
  public ResponseEntity<ApiProblemDetail> handleProblem(ApiProblemException ex) {
    if (ex.getType().status().is5xxServerError()) {
      // A server-side failure's detail is written for the logs: with the Keycloak repositories,
      // it carries the response body of the admin API (realm and endpoint names, Keycloak's own
      // error messages). Same treatment as a data integrity violation: logged, not sent.
      log.error("{}: {}", ex.getType(), ex.getMessage());
      return problem(new ApiProblemDetail(ex.getType(), SERVER_ERROR_DETAIL,
          ex.getProblem().getParameters()));
    }
    log.debug("{}: {}", ex.getType(), ex.getMessage());
    return problem(ex.getProblem());
  }

  /** {@code @Valid} request bodies. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ApiResponse(responseCode = "422", description = "Unprocessable Content",
      content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
          schema = @Schema(implementation = ValidationProblemDetail.class)))
  public ResponseEntity<ValidationProblemDetail> handleBodyValidation(
      MethodArgumentNotValidException ex) {
    return validationProblem(ex.getBindingResult().getFieldErrors().stream()
        .collect(toMap(FieldError::getField, ApiExceptionHandler::messageOf,
            ApiExceptionHandler::join)));
  }

  /** Constraints on controller method parameters (Spring MVC's built-in method validation). */
  @ExceptionHandler(HandlerMethodValidationException.class)
  @ApiResponse(responseCode = "422", description = "Unprocessable Content",
      content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
          schema = @Schema(implementation = ValidationProblemDetail.class)))
  public ResponseEntity<ValidationProblemDetail> handleParameterValidation(
      HandlerMethodValidationException ex) {
    return validationProblem(ex.getParameterValidationResults().stream()
        .flatMap(result -> result.getResolvableErrors().stream()
            .map(error -> Map.entry(pathOf(result, error), messageOf(error))))
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, ApiExceptionHandler::join)));
  }

  /** {@code @Validated} beans below the controller. */
  @ExceptionHandler(ConstraintViolationException.class)
  @ApiResponse(responseCode = "422", description = "Unprocessable Content",
      content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
          schema = @Schema(implementation = ValidationProblemDetail.class)))
  public ResponseEntity<ValidationProblemDetail> handleConstraintViolation(
      ConstraintViolationException ex) {
    return validationProblem(ex.getConstraintViolations().stream()
        .collect(toMap(cv -> cv.getPropertyPath().toString(), ConstraintViolation::getMessage,
            ApiExceptionHandler::join)));
  }

  private static ResponseEntity<ApiProblemDetail> problem(ApiProblemDetail problem) {
    return ResponseEntity.status(problem.getStatus()).body(problem);
  }

  private static ResponseEntity<ValidationProblemDetail> validationProblem(
      Map<String, String> invalidFields) {
    final var problem = new ValidationProblemDetail(VALIDATION_DETAIL, invalidFields);
    return ResponseEntity.status(problem.getStatus()).body(problem);
  }

  private static String pathOf(ParameterValidationResult result, MessageSourceResolvable error) {
    if (error instanceof FieldError fieldError) {
      return fieldError.getField();
    }
    final var parameter = result.getMethodParameter();
    return Objects.requireNonNullElseGet(parameter.getParameterName(),
        () -> "arg" + parameter.getParameterIndex());
  }

  private static String messageOf(MessageSourceResolvable error) {
    return Objects.requireNonNullElse(error.getDefaultMessage(), "invalid");
  }

  /** A property violating several constraints keeps all of its messages. */
  private static String join(String first, String second) {
    return first + "; " + second;
  }
}
