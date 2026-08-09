package com.c4soft.resthero.account.web;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
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
import org.springframework.web.bind.annotation.RestController;
import com.c4soft.resthero.account.domain.MoneyTransfer;
import com.c4soft.resthero.account.jpa.AccountRepository;
import com.c4soft.resthero.account.jpa.MoneyTransferRepository;
import com.c4soft.resthero.api.CurrenciesApi;
import com.c4soft.resthero.commons.domain.Iban;
import com.c4soft.resthero.commons.events.DomainEvent;
import com.c4soft.resthero.commons.events.ResourceType;
import io.micrometer.observation.annotation.Observed;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Tag(name = "MoneyTransfers")
@RestController
@RequestMapping(
    produces = {MediaType.APPLICATION_PROBLEM_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
@RequiredArgsConstructor
@Observed
@Slf4j
public class MoneyTransferController {
  public static final String BASE_PATH = "/transfers";
  public static final String TRANSFER_ID_PLACEHOLDER = "transferId";
  public static final String TRANSFER_PATH = BASE_PATH + "/{" + TRANSFER_ID_PLACEHOLDER + "}";

  private final AccountRepository accountRepo;

  private final MoneyTransferRepository transferRepo;
  private final MoneyTransferMapper transferMapper;

  private final CurrenciesApi currenciesApi;

  private final RabbitTemplate rabbitTemplate;
  private final TopicExchange eventsExchange;

  /**
   * Requires the `account.read_any` authority or that the authenticated user is the owner of the
   * source or destination account.
   *
   * @param dto the filter criteria for money transfers
   * @param pageable the pagination information
   * @return a paginated list of money transfers matching the filter criteria
   */
  @Transactional(readOnly = true)
  @GetMapping(BASE_PATH)
  @PreAuthorize("hasAuthority('account.read_any') or @ac.ownsAccount(#dto.sourceIban) or @ac.ownsAccount(#dto.destinationIban)")
  public PagedModel<MoneyTransferResponse> listMoneyTransfers(
      @Nullable @Valid @ParameterObject MoneyTransferFilterRequest dto,
      @ParameterObject Pageable pageable) {
    var criteria = transferMapper.map(dto == null ? MoneyTransferFilterRequest.ALL : dto);
    var transferPage = transferRepo.findAll(MoneyTransferRepository.searchSpec(criteria), pageable);
    var content = transferPage.getContent().stream().map(transferMapper::map).toList();
    return new PagedModel<>(
        new PageImpl<>(content, transferPage.getPageable(), transferPage.getTotalElements()));
  }

  /**
   * Requires the `account.transfer` authority.
   *
   * This labs implementation ignores other banks. If the source or destination account isn't
   * managed by this service (another bank?), the withdraw / credit is ignored and a transfer is
   * saved anyway.
   *
   * @param dto the money transfer request
   * @param auth the authentication object representing the current user
   * @return A response with a Location header pointing to the created transfer resource
   */
  @Transactional
  @PostMapping(BASE_PATH)
  // Off course, in a real world being the owner of the destination account would be enough to
  // transfer money to it...
  @PreAuthorize("hasAuthority('account.transfer') or @ac.ownsAccount(#dto.sourceIban) or @ac.ownsAccount(#dto.destinationIban)")
  public ResponseEntity<Void> transferMoneyBetweenAccounts(
      @RequestBody
      // LAB:4.3:TODO:START activer la validation du body JSON
      @Valid
      // LAB:4.3:TODO:END
      MoneyTransferRequest dto,
      Authentication auth) {
    final var sourceAccount = accountRepo.findByIban(Iban.of(dto.sourceIban()));
    final var destinationAccount = accountRepo.findByIban(Iban.of(dto.destinationIban()));

    log
        .info(
            "Transfering {} {} from {} to {}",
            dto.amount(),
            dto.currency(),
            dto.sourceIban(),
            dto.destinationIban());

    sourceAccount.ifPresent(a -> {
      final var substractedAmount =
          Objects.equals(a.getBalance().getCurrency(), dto.currency()) ? dto.amount()
              : currenciesApi
                  .change(dto.amount(), dto.currency(), a.getBalance().getCurrency().name())
                  .getBody();
      a.getBalance().setDigits(a.getBalance().getDigits() - substractedAmount);
      accountRepo.save(a);
      log
          .info(
              "{} withdrew {}{} ({}{}) from {}",
              auth.getName(),
              dto.amount(),
              dto.currency(),
              substractedAmount,
              a.getBalance().getCurrency(),
              dto.sourceIban());

      // LAB:6.1:TODO:START publier un DomainEvent d'évolution du compte source sur l'exchange du service
      rabbitTemplate
          .convertAndSend(
              eventsExchange.getName(),
              "account.updated",
              new DomainEvent(
                  ResourceType.ACCOUNT_TRANSFERS,
                  a.getIban().toMachineReadableString(),
                  a.getCustomerId(),
                  List.of("account.read_any"),
                  DomainEvent.EventType.UPDATE,
                  Instant.now()));
      // LAB:6.1:TODO:END
    });

    destinationAccount.ifPresent(a -> {
      final var addedAmount =
          Objects.equals(a.getBalance().getCurrency(), dto.currency()) ? dto.amount()
              : currenciesApi
                  .change(dto.amount(), dto.currency(), a.getBalance().getCurrency().name())
                  .getBody();
      a.getBalance().setDigits(a.getBalance().getDigits() + addedAmount);
      accountRepo.save(a);
      log
          .info(
              "{} credited {}{} ({}{}) to {}",
              auth.getName(),
              dto.amount(),
              dto.currency(),
              addedAmount,
              a.getBalance().getCurrency(),
              dto.destinationIban());

      // LAB:6.1:TODO:START publier un DomainEvent d'évolution du compte destinataire sur l'exchange du service
      rabbitTemplate
          .convertAndSend(
              eventsExchange.getName(),
              "account.updated",
              new DomainEvent(
                  ResourceType.ACCOUNT_TRANSFERS,
                  a.getIban().toMachineReadableString(),
                  a.getCustomerId(),
                  List.of("account.read_any"),
                  DomainEvent.EventType.UPDATE,
                  Instant.now()));
      // LAB:6.1:TODO:END
    });

    var transfer = transferRepo.save(transferMapper.map(dto));

    log
        .info(
            "{} transfered {}{} from {} to {}",
            auth.getName(),
            dto.amount(),
            dto.currency(),
            dto.sourceIban(),
            dto.destinationIban());

    return ResponseEntity
        .created(
            URI
                .create(
                    TRANSFER_PATH
                        .replace(
                            "{%s}".formatted(TRANSFER_ID_PLACEHOLDER),
                            transfer.getId().toString())))
        .build();
  }

  /**
   * Requires the `account.read_any` authority or that the authenticated user is the owner of the
   * source or destination account.
   *
   * @param transfer
   * @return the money transfer with the given ID
   */
  @Transactional(readOnly = true)
  @GetMapping(TRANSFER_PATH)
  @PreAuthorize("hasAuthority('account.read_any') or @ac.ownsAccount(#transfer.sourceIban) or @ac.ownsAccount(#transfer.destinationIban)")
  public MoneyTransferResponse getMoneyTransfer(
            // LAB:4.5:TODO:START documenter l'identifiant de virement pour OpenAPI
            @Parameter(schema = @Schema(type = "integer"),
          description = "The ID of the money transfer to retrieve")
            // LAB:4.5:TODO:END
      @PathVariable(name = TRANSFER_ID_PLACEHOLDER) MoneyTransfer transfer) {
    return transferMapper.map(transfer);
  }

}
