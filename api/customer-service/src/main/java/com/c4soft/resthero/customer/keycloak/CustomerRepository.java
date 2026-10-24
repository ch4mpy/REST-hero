package com.c4soft.resthero.customer.keycloak;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.keycloak.admin.api.UsersApi;
import org.keycloak.admin.model.UserRepresentation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import com.c4soft.resthero.customer.domain.Customer;
import com.c4soft.resthero.problem.ApiProblemException;
import com.c4soft.resthero.problem.ProblemType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Repository
@RequiredArgsConstructor
@Slf4j
public class CustomerRepository {

  private final KeycloakAdminApiProperties apiProperties;

  private final UsersApi usersApi;

  private final UserRepresentationMapper mapper;

  public Page<Customer> listUsers(String search, Pageable pageable) {
    try {
      final var response = usersApi
          .adminRealmsRealmUsersGet(
              apiProperties.getRealmName(),
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.of(true),
              Optional.of(false),
              Optional.of(pageable.getPageSize() * pageable.getPageNumber()),
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.of(pageable.getPageSize()),
              Optional.empty(),
              Optional.ofNullable(search).map("*%s"::formatted),
              Optional.empty());

      final List<UserRepresentation> users =
          Optional.ofNullable(response.getBody()).orElse(List.of());

      return new PageImpl<>(
          users,
          pageable,
          users.size() < pageable.getPageSize() && pageable.getPageNumber() == 0 ? users.size()
              : countUsers(search))
          .map(mapper::map);
    } catch (RestClientException e) {
      // Keycloak's answer is for the logs, never for the client
      log
          .error(
              "Error while retrieving Keycloak's users page {} (size {}) with {}: {}",
              pageable.getPageNumber(),
              pageable.getPageSize(),
              search,
              e.getMessage());
      throw identityProviderError("The identity provider could not list the customers");
    }
  }

  public Customer save(Customer customer) {
    if (customer.getId() != null) {
      throw new UnsupportedOperationException("User update is not supported");
    }
    try {
      final var response = usersApi
          .adminRealmsRealmUsersPost(
              apiProperties.getRealmName(),
              Optional.of(mapper.map(customer)));
      final var location = response.getHeaders().getLocation();
      if (location == null) {
        log
            .error(
                "Keycloak created user {} without saying where (no Location header)",
                customer.getEmail());
        throw identityProviderError("The identity provider created the customer without its ID");
      }
      var pathParts = location.getPath().split("/");
      return new Customer(
          pathParts[pathParts.length - 1],
          customer.getFirstName(),
          customer.getLastName(),
          customer.getEmail());
    } catch (HttpClientErrorException.Conflict e) {
      log.warn("Keycloak already has a user with email {}", customer.getEmail());
      throw new ApiProblemException(
          ProblemType.CUSTOMER_ALREADY_EXISTS,
          "A customer with email %s already exists".formatted(customer.getEmail()),
          Map.of("email", customer.getEmail())).unchecked();
    } catch (RestClientException e) {
      log
          .error(
              "Error while creating Keycloak's user {} {} {}: {}",
              customer.getEmail(),
              customer.getFirstName(),
              customer.getLastName(),
              e.getMessage());
      throw identityProviderError("The identity provider could not create the customer");
    }
  }

  public Optional<Customer> findById(String id) {
    try {
      final var response = usersApi
          .adminRealmsRealmUsersUserIdGet(apiProperties.getRealmName(), id, Optional.empty());
      return Optional.ofNullable(response.getBody()).map(mapper::map);
    } catch (HttpClientErrorException.NotFound e) {
      return Optional.empty();
    } catch (RestClientException e) {
      log.error("Error while retrieving Keycloak's user with id {}: {}", id, e.getMessage());
      throw identityProviderError("The identity provider could not retrieve the customer");
    }
  }

  /**
   * The repository's methods are called from controllers' transactions and from converters, where a
   * checked exception cannot be declared: the problem goes up unchecked.
   */
  private static RuntimeException identityProviderError(String detail) {
    return new ApiProblemException(ProblemType.IDENTITY_PROVIDER_ERROR, detail, Map.of()).unchecked();
  }

  Integer countUsers(String search) {
    try {
      final var response = usersApi
          .adminRealmsRealmUsersCountGet(
              apiProperties.getRealmName(),
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.of(true),
              Optional.of(false),
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.ofNullable(search).map("*%s"::formatted),
              Optional.empty());

      return response.getBody() == null ? 0 : response.getBody();
    } catch (RestClientException e) {
      log.error("Error while counting Keycloak's users with {}: {}", search, e.getMessage());
      throw identityProviderError("The identity provider could not count the customers");
    }
  }
}
