package com.c4soft.resthero.customer.keycloak;

import java.util.List;
import java.util.Optional;
import org.keycloak.admin.api.UsersApi;
import org.keycloak.admin.model.UserRepresentation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import com.c4soft.resthero.customer.domain.Customer;
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
    } catch (HttpClientErrorException e) {
      log
          .error(
              "Error while retrieving Keycloak's users page {} (size {}) with {}: {}",
              pageable.getPageNumber(),
              pageable.getPageSize(),
              search,
              e.getMessage());
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Error while counting Keycloak's users page %d (size %d) with %s: %s"
              .formatted(pageable.getPageNumber(), pageable.getPageSize(), search, e.getMessage()));
    }
  }

  public Customer save(Customer customer) {
    if (customer.getId() != null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "User update is not supported");
    }
    try {
      final var response = usersApi
          .adminRealmsRealmUsersPost(
              apiProperties.getRealmName(),
              Optional.of(mapper.map(customer)));
      var pathParts = response.getHeaders().getLocation().getPath().split("/");
      return new Customer(
          pathParts[pathParts.length - 1],
          customer.getFirstName(),
          customer.getLastName(),
          customer.getEmail());
    } catch (HttpClientErrorException e) {
      // FIXME: handle 409 conflict when user already exists
      log
          .error(
              "Error while creating Keycloak's user {} {} {}: {}",
              customer.getEmail(),
              customer.getFirstName(),
              customer.getLastName(),
              e.getMessage());
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Error while creating Keycloak's user %s %s %s: %s"
              .formatted(
                  customer.getEmail(),
                  customer.getFirstName(),
                  customer.getLastName(),
                  e.getMessage()));
    }
  }

  public Optional<Customer> findById(String id) {
    try {
      final var response = usersApi
          .adminRealmsRealmUsersUserIdGet(apiProperties.getRealmName(), id, Optional.empty());
      return Optional.ofNullable(response.getBody()).map(mapper::map);
    } catch (HttpClientErrorException e) {
      if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
        return Optional.empty();
      }
      log.error("Error while retrieving Keycloak's user with id {}: {}", id, e.getMessage());
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Error while retrieving Keycloak's user with id %s: %s".formatted(id, e.getMessage()));
    }
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
    } catch (HttpClientErrorException e) {
      log.error("Error while counting Keycloak's users with {}: {}", search, e.getMessage());
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Error while counting Keycloak's users with %s: %s".formatted(search, e.getMessage()));
    }
  }
}
