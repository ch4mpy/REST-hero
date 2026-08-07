package com.c4soft.resthero.account.web;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import com.c4soft.resthero.account.domain.Account;
import com.c4soft.resthero.account.jpa.AccountRepository;
import com.c4soft.resthero.api.CustomersApi;
import com.c4soft.resthero.commons.domain.Iban;
import com.c4soft.resthero.commons.events.DomainEvent;
import com.c4soft.resthero.commons.events.ResourceType;
import com.c4soft.resthero.commons.exception.ResourceNotFoundException;
import io.micrometer.observation.annotation.Observed;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Tag(name = "Accounts")
@RestController
@RequestMapping(
    produces = {MediaType.APPLICATION_PROBLEM_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
// LAB:2.1:REMOVE:START
@RequiredArgsConstructor
// LAB:2.1:REMOVE:END
@Observed
@Slf4j
public class AccountController {
  public static final String BASE_PATH = "/accounts";
  public static final String ACCOUNT_PLACEHOLDER = "iban";
  public static final String ACCOUNT_PATH = BASE_PATH + "/{" + ACCOUNT_PLACEHOLDER + "}";

  private final AccountRepository accountRepo;
  private final AccountMapper accountMapper;

  private final CustomersApi customersApi;

  private final RabbitTemplate rabbitTemplate;
  private final TopicExchange eventsExchange;

  /**
   * Requires the `account.read_any` authority or that the given customer ID matches the
   * authenticated user.
   *
   * @param customerId
   * @return all accounts with the given customer ID.
   */
  @Transactional(readOnly = true)
  @GetMapping(BASE_PATH)
  @PreAuthorize("hasAuthority('account.read_any') || #customerId == authentication.name")
  public List<AccountResponse> listAccounts(
      @RequestParam @Size(min = 1, max = 36) String customerId) {
    final var accounts = accountRepo.findByCustomerId(customerId);
    return accounts.stream().map(accountMapper::map).toList();
  }

  /**
   * Requires the `account.create` authority.
   *
   * @param dto
   * @return a 201 Created response with the Location header set to the newly created account's URL.
   * @throws ResourceNotFoundException if the customer ID in the request is not known by the
   *         customer service.
   */
  @Transactional
  @PostMapping(BASE_PATH)
  @PreAuthorize("hasAuthority('account.create')")
  public ResponseEntity<Void> createAccount(
      @RequestBody @Valid AccountCreationRequest dto,
      Authentication auth) throws ResourceNotFoundException {
    final var iban = Iban.of(dto.iban());

    // Assert that no account with this IBAN is managed already
    if (accountRepo.existsByIban(iban)) {
      log.warn("Rejecting duplicate account {} creation", iban);
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "The account-service already manages account %s".formatted(iban.toHumanReadableString()));
    }

    // Assert that the customer ID is known by the customer service
    try {
      // LAB:4.6:TODO:START appeler le customer-service pour vérifier l'existence du client
      customersApi.getCustomer(dto.customerId());
      // LAB:4.6:TODO:END
    } catch (HttpClientErrorException e) {
      if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
        log.warn("Rejecting account {} creation for unknown customer {}", iban, dto.customerId());
        throw new ResourceNotFoundException(
            "Customer %s is not known by the customer-service".formatted(dto.iban()));
      } else {
        log
            .error(
                "Unexpected error while checking customer {} existence in customer-service",
                dto.customerId(),
                e);
      }
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Unexpected error while checking customer existence",
          e);
    }

    // Create the new account
    final var account = accountRepo.save(accountMapper.createAccount(dto));
    log
        .info(
            "{} created account {} for customer {}",
            auth.getName(),
            account.getIban(),
            account.getCustomerId());

    rabbitTemplate
        .convertAndSend(
            eventsExchange.getName(),
            "account.created",
            new DomainEvent(
                ResourceType.ACCOUNT,
                account.getIban().toMachineReadableString(),
                account.getCustomerId(),
                List.of("account.read_any"),
                DomainEvent.EventType.CREATE,
                Instant.now()));

    return ResponseEntity
        .created(
            URI
                .create(
                    ACCOUNT_PATH
                        .replace(
                            "{%s}".formatted(ACCOUNT_PLACEHOLDER),
                            account.getIban().toMachineReadableString())))
        .build();
  }

  /**
   * Requires the `account.read_any` authority or that the given account's customer ID matches the
   * authenticated user.
   *
   * @param account
   * @return the account with the given IBAN.
   */
  @Transactional(readOnly = true)
  @GetMapping(ACCOUNT_PATH)
  @PreAuthorize("hasAuthority('account.read_any') || #account.customerId == authentication.name")
  public AccountResponse getAccount(
      @Parameter(schema = @Schema(type = "string"),
          description = "The IBAN of the account to retrieve")
      @PathVariable(name = ACCOUNT_PLACEHOLDER) Account account) {
    return accountMapper.map(account);
  }

}
