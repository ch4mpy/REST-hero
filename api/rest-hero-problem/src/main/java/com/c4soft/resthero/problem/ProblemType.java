package com.c4soft.resthero.problem;

import java.net.URI;
import org.springframework.http.HttpStatus;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The closed set of problems this API reports, exposed in the OpenAPI spec as an enum of
 * {@code type} URIs so that the generated clients hold the translation keys at compile time.
 *
 * <p>
 * The parameters listed on each constant are the keys of
 * {@link ApiProblemDetail#getParameters()} the frontend can interpolate in its localized
 * message.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public enum ProblemType {
  /** Parameters: none. Invalid fields are in {@link ValidationProblemDetail#getInvalidFields()}. */
  VALIDATION(HttpStatus.UNPROCESSABLE_CONTENT, "validation"),

  /**
   * Parameters: {@code entity} (simple class name) and {@code id} when known. Another request
   * modified the same resource in the meantime: reload it before retrying.
   */
  CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "concurrent-modification"),

  /**
   * Parameters: {@code constraint} (the violated constraint's name) when the database reports it.
   * Safety net for a database constraint no business rule checked first (typically a race between
   * two requests).
   */
  DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT, "data-integrity-violation"),

  /**
   * Parameters: none. A REST call to the identity provider (Keycloak, where organizations, users,
   * groups and roles live) failed.
   */
  IDENTITY_PROVIDER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "identity-provider-error"),

  /** Parameters: {@code iban}. No account has this IBAN in the account service. */
  ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "account-not-found"),

  /** Parameters: {@code iban}. The account service already manages an account with this IBAN. */
  ACCOUNT_ALREADY_MANAGED(HttpStatus.CONFLICT, "account-already-managed"),

  /** Parameters: {@code transferId}. No money transfer has this identifier. */
  MONEY_TRANSFER_NOT_FOUND(HttpStatus.NOT_FOUND, "money-transfer-not-found"),

  /** Parameters: {@code customerId}. No customer has this identifier in the identity provider. */
  CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "customer-not-found"),

  /** Parameters: {@code email}. The identity provider already knows a user with this email. */
  CUSTOMER_ALREADY_EXISTS(HttpStatus.CONFLICT, "customer-already-exists"),

  /**
   * Parameters: {@code beneficiaryId}, and {@code customerId} when the path names a customer. The
   * customer has no beneficiary with this identifier.
   */
  BENEFICIARY_NOT_FOUND(HttpStatus.NOT_FOUND, "beneficiary-not-found"),

  /** Parameters: {@code iban}. The customer already has a beneficiary with this IBAN. */
  BENEFICIARY_IBAN_ALREADY_REGISTERED(HttpStatus.CONFLICT, "beneficiary-iban-already-registered"),

  /** Parameters: {@code label}. The customer already has a beneficiary with a similar label. */
  BENEFICIARY_LABEL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "beneficiary-label-already-registered"),

  /** Parameters: {@code cardNumber}. No card has this number. */
  CARD_NOT_FOUND(HttpStatus.NOT_FOUND, "card-not-found"),

  /**
   * Parameters: {@code ceiling}, {@code amount} (both in minor units) and {@code currency}. The
   * payment is above the card's per-transaction ceiling.
   */
  CARD_TRANSACTION_CEILING_EXCEEDED(HttpStatus.CONFLICT, "card-transaction-ceiling-exceeded"),

  /**
   * Parameters: {@code ceiling}, {@code amount}, {@code cumulatedAmount} (all in minor units) and
   * {@code currency}. The payment, added to the accepted payments of the last 30 days, is above the
   * card's rolling ceiling.
   */
  CARD_ROLLING30_CEILING_EXCEEDED(HttpStatus.CONFLICT, "card-rolling30-ceiling-exceeded"),

  /**
   * Parameters: {@code service} (the name of the downstream service). A call to another service of
   * this application failed: the cause is logged, never sent.
   */
  DOWNSTREAM_SERVICE_ERROR(HttpStatus.BAD_GATEWAY, "downstream-service-error"),

  /**
   * Parameters: none. No session (BFF), no or a rejected access token (REST API), or a session
   * cookie the BFF no longer knows: the user must log in (again).
   */
  UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "unauthenticated"),

  /** Parameters: none. An access rule, or the CSRF protection, refused the request. */
  ACCESS_DENIED(HttpStatus.FORBIDDEN, "access-denied"),

  /** Parameters: none. No route matches the request. */
  NOT_FOUND(HttpStatus.NOT_FOUND, "not-found"),

  /**
   * Parameters: none. The framework rejected the request (unreadable body, unsupported method or
   * media type...). The only type whose status varies: that of the rejection, {@code 400} by
   * default.
   */
  INVALID_REQUEST(HttpStatus.BAD_REQUEST, "invalid-request"),

  /** Parameters: none. An unexpected server-side failure, logged with its details. */
  SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "server-error"),

  /**
   * Parameters: none. The BFF could not reach the REST API (connection refused, timeout): try again
   * in a moment.
   */
  SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "service-unavailable");

  private static final String NAMESPACE = "urn:rest-hero:problem:";

  private final HttpStatus status;

  private final URI uri;

  ProblemType(HttpStatus status, String slug) {
    this.status = status;
    this.uri = URI.create(NAMESPACE + slug);
  }

  public HttpStatus status() {
    return status;
  }

  /** The problem {@code type}: what is serialized, and what the OpenAPI enum values are. */
  @JsonValue
  public URI uri() {
    return uri;
  }
}
