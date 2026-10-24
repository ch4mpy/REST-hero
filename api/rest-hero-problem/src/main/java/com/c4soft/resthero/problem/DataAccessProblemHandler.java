package com.c4soft.resthero.problem;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * The safety nets of the applications that use JPA: another request modified the same row
 * ({@code @Version}), or a database constraint no business rule checked first. Split from
 * {@link ApiExceptionHandler} so that the applications without JPA (the gateway, the currency
 * service) do not need Hibernate on their classpath: this advice is not registered without it.
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnClass(name = "org.hibernate.exception.ConstraintViolationException")
@Slf4j
public class DataAccessProblemHandler {

  /**
   * Optimistic-locking failure on a {@code @Version}ed entity: another request committed a change to
   * the same row in the meantime.
   */
  @ExceptionHandler(OptimisticLockingFailureException.class)
  @ApiResponse(responseCode = "409", description = "Conflict",
      content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
          schema = @Schema(implementation = ApiProblemDetail.class)))
  public ResponseEntity<ApiProblemDetail> handleConcurrentModification(
      OptimisticLockingFailureException ex) {
    final var parameters = new HashMap<String, Object>();
    if (ex instanceof ObjectOptimisticLockingFailureException objectEx) {
      final var entity = objectEx.getPersistentClass();
      if (entity != null) {
        parameters.put("entity", entity.getSimpleName());
      }
      final var id = objectEx.getIdentifier();
      if (id != null) {
        parameters.put("id", id);
      }
    }
    log.debug("Concurrent modification: {}", ex.getMessage());
    return problem(new ApiProblemDetail(ProblemType.CONCURRENT_MODIFICATION,
        "The resource was modified by another request in the meantime", parameters));
  }

  /**
   * A database constraint no business rule checked first. The exception message (SQL, table and
   * constraint names) is logged, not sent: only the constraint name is, when the driver reports it.
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  @ApiResponse(responseCode = "409", description = "Conflict",
      content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
          schema = @Schema(implementation = ApiProblemDetail.class)))
  public ResponseEntity<ApiProblemDetail> handleDataIntegrityViolation(
      DataIntegrityViolationException ex) {
    log.warn("Data integrity violation: {}", ex.getMessage());
    final var parameters = constraintName(ex)
        .<Map<String, Object>>map(name -> Map.of("constraint", name)).orElse(Map.of());
    return problem(new ApiProblemDetail(ProblemType.DATA_INTEGRITY_VIOLATION,
        "The request conflicts with existing data", parameters));
  }

  /** Hibernate reports the violated constraint's name (as declared in the Liquibase changesets). */
  private static Optional<String> constraintName(Throwable ex) {
    for (var cause = ex; cause != null; cause = cause.getCause()) {
      if (cause instanceof org.hibernate.exception.ConstraintViolationException hibernateEx) {
        return Optional.ofNullable(hibernateEx.getConstraintName());
      }
    }
    return Optional.empty();
  }

  private static ResponseEntity<ApiProblemDetail> problem(ApiProblemDetail problem) {
    return ResponseEntity.status(problem.getStatus()).body(problem);
  }
}
