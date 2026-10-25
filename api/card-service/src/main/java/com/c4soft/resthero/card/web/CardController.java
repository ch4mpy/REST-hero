package com.c4soft.resthero.card.web;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import com.c4soft.resthero.account.api.AccountsApi;
import com.c4soft.resthero.account.api.MoneyTransfersApi;
import com.c4soft.resthero.account.model.MoneyTransferRequest;
import com.c4soft.resthero.card.domain.Card;
import com.c4soft.resthero.card.domain.Card.Ceilings;
import com.c4soft.resthero.card.domain.CardPayment;
import com.c4soft.resthero.card.jpa.CardPaymentRepository;
import com.c4soft.resthero.card.jpa.CardRepository;
import com.c4soft.resthero.commons.domain.Amount;
import com.c4soft.resthero.commons.domain.Currency;
import com.c4soft.resthero.commons.domain.Iban;
import com.c4soft.resthero.commons.domain.Period;
import com.c4soft.resthero.commons.events.DomainEvent;
import com.c4soft.resthero.commons.events.ResourceType;
import com.c4soft.resthero.commons.validation.ValidPeriod;
import com.c4soft.resthero.problem.ApiProblemException;
import com.c4soft.resthero.problem.ProblemType;
import io.micrometer.observation.annotation.Observed;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Tag(name = "Cards")
@RestController
@RequestMapping(
    produces = {MediaType.APPLICATION_PROBLEM_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
@RequiredArgsConstructor
@Observed
@Slf4j
public class CardController {
  public static final String BASE_PATH = "/cards";
  public static final String CARD_NUMBER_PLACEHOLDER = "cardNumber";
  public static final String CARD_PATH = BASE_PATH + "/{" + CARD_NUMBER_PLACEHOLDER + "}";
  public static final String CARD_STATUS_PATH = CARD_PATH + "/status";
  public static final String CARD_CEILINGS_PATH = CARD_PATH + "/ceilings";
  public static final String PAYMENT_LIST_PATH = CARD_PATH + "/payments";
  public static final String PAYMENT_ID_PLACEHOLDER = "paymentId";
  public static final String PAYMENT_PATH = PAYMENT_LIST_PATH + "/{" + PAYMENT_ID_PLACEHOLDER + "}";

  private final CardRepository cardRepo;
  private final CardMapper cardMapper;
  private final CardPaymentRepository paymentRepo;
  private final CardPaymentMapper paymentMapper;
  private final AccountsApi accountsApi;
  private final TransactionalCardPaymentHelper transactionalCardPaymentHelper;

  private final RabbitTemplate rabbitTemplate;
  private final TopicExchange eventsExchange;

  /**
   * Requires the `card.read_any` authority or that the authenticated user is the owner of the
   * account.
   * 
   * @param iban
   * @return
   */
  @Transactional(readOnly = true)
  @GetMapping(path = BASE_PATH)
  @PreAuthorize("hasAuthority('card.read_any') or @ac.ownsAccount(#iban.toMachineReadableString())")
  public List<CardResponse> listCards(@RequestParam Iban iban) {
    var cards = cardRepo.findByIban(iban);
    return cards.stream().map(cardMapper::map).toList();
  }

  /**
   * Requires the `card.create` authority.
   * 
   * @param dto
   * @param auth
   * @return a response with a `Location` header pointing to the newly created card resource
   * @throws ApiProblemException {@link ProblemType#ACCOUNT_NOT_FOUND} when the account service
   *         does not know the account, {@link ProblemType#DOWNSTREAM_SERVICE_ERROR} when it
   *         answers anything else or cannot be reached
   */
  @Transactional(rollbackFor = ApiProblemException.class)
  @PostMapping(path = BASE_PATH)
  @PreAuthorize("hasAuthority('card.create')")
  public ResponseEntity<Void> createCard(@RequestBody @Valid CardRequest dto, Authentication auth)
      throws ApiProblemException {
    // Assert that the account is known by the account service
    com.c4soft.resthero.account.model.AccountResponse account;
    try {
      account = accountsApi.getAccount(dto.iban()).getBody();
    } catch (HttpClientErrorException.NotFound e) {
      log.warn("{} atempted to create a card for unknown account {}", auth.getName(), dto.iban());
      throw accountNotFound(dto.iban());
    } catch (RestClientException e) {
      log
          .error(
              "Error while checking account {} existence for user {}",
              dto.iban(),
              auth.getName(),
              e);
      throw accountServiceError("The account-service could not confirm that the account exists");
    }
    var iban = Iban.of(dto.iban());
    var existingCards = cardRepo.findByIban(iban);
    var cardNumber = "4%s%d".formatted(iban.getBban(), existingCards.size());

    var card = cardRepo
        .save(
            Card
                .create(
                    cardNumber,
                    iban,
                    Ceilings
                        .builder()
                        .rolling30(dto.rolling30Ceiling())
                        .transaction(dto.transactionCeiling())
                        .build()));
    log.info("{} created card {} for account {}", auth.getName(), card.getNumber(), dto.iban());

    rabbitTemplate
        .convertAndSend(
            eventsExchange.getName(),
            "account.cards.updated",
            new DomainEvent(
                ResourceType.ACCOUNT_CARDS,
                iban.toMachineReadableString(),
                account.getCustomerId(),
                List.of("card.read_any"),
                DomainEvent.EventType.CREATE,
                Instant.now()));

    return ResponseEntity
        .created(
            URI
                .create(
                    CARD_PATH.replace("{%s}".formatted(CARD_NUMBER_PLACEHOLDER), card.getNumber())))
        .build();
  }

  /**
   * Requires the `card.read_any` authority or that the authenticated user is the owner of the card
   * 
   * @param card
   * @return
   */
  @Transactional(readOnly = true)
  @GetMapping(path = CARD_PATH)
  @PreAuthorize("hasAuthority('card.read_any') or @ac.ownsAccount(#card.getIban().toMachineReadableString())")
  public CardResponse getCard(
      @Parameter(schema = @Schema(type = "string"),
          description = "The number of the card to change the status of")
      @PathVariable(name = CARD_NUMBER_PLACEHOLDER) Card card) {
    return cardMapper.map(card);
  }

  /**
   * Requires the `card.status_edit` authority
   * 
   * @param card
   * @param dto
   * @param auth
   */
  @Transactional
  @PutMapping(path = CARD_STATUS_PATH)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('card.status_edit')")
  public void setCardStatus(
      @Parameter(schema = @Schema(type = "string"),
          description = "The number of the card to change the status of")
      @PathVariable(name = CARD_NUMBER_PLACEHOLDER) Card card,
      @RequestBody @Valid CardStatusRequest dto,
      Authentication auth) {
    if (card.isActive() == dto.isActive()) {
      log
          .debug(
              "{} attempted to change card {} status to {} but it is already in that status",
              auth.getName(),
              card.getNumber(),
              dto.isActive());
      return;
    }

    if (dto.isActive()) {
      card.activate();
    } else {
      card.deactivate();
    }

    cardRepo.save(card);
    log.info("{} changed card {} status to {}", auth.getName(), card.getNumber(), dto.isActive());

    publishCardUpdatedEvent(card);
  }

  /**
   * Requires the `card.ceilings_edit` authority
   * 
   * @param card
   * @param dto
   * @param auth
   */
  @Transactional
  @PutMapping(path = CARD_CEILINGS_PATH)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('card.ceilings_edit')")
  public void setCardCeilings(
      @Parameter(schema = @Schema(type = "string"),
          description = "The number of the card to change the ceilings of")
      @PathVariable(name = CARD_NUMBER_PLACEHOLDER) Card card,
      @RequestBody @Valid CardCeilingsRequest dto,
      Authentication auth) {
    var newCeilings = Ceilings
        .builder()
        .rolling30(dto.rolling30Ceiling())
        .transaction(dto.transactionCeiling())
        .build();
    log
        .debug(
            "{} is changing card {} ceilings from {} to {}",
            auth.getName(),
            card.getNumber(),
            card.getCeilings(),
            newCeilings);
    card.changeCeilings(newCeilings);
    cardRepo.save(card);
    log.info("{} changed card {} ceilings to {}", auth.getName(), card.getNumber(), newCeilings);

    publishCardUpdatedEvent(card);
  }

  /**
   * Requires the `card.read_any` authority or that the authenticated user is the owner of the card
   * 
   * @param card
   * @param period from and to are required and must be a valid period (from < to)
   * @return
   */
  @Transactional(readOnly = true)
  @GetMapping(path = PAYMENT_LIST_PATH)
  @PreAuthorize("hasAuthority('card.read_any') or @ac.ownsAccount(#card.getIban().toMachineReadableString())")
  public List<CardPaymentResponse> listCardPayments(
      @Parameter(schema = @Schema(type = "string"),
          description = "The number of the card to retrieve the payments of")
      @PathVariable(name = CARD_NUMBER_PLACEHOLDER) Card card,
      @ParameterObject
      @NotNull
      @ValidPeriod(fromRequired = true, toRequired = true) Period period) {
    return paymentRepo
        .findByCardNumberAndTimestampBetween(card.getNumber(), period.from(), period.to())
        .stream()
        .map(paymentMapper::map)
        .toList();
  }

  /**
   * Requires the authenticated user is the owner of the card
   * 
   * @param card
   * @param dto
   * @return
   * @throws ApiProblemException {@link ProblemType#CARD_TRANSACTION_CEILING_EXCEEDED} or
   *         {@link ProblemType#CARD_ROLLING30_CEILING_EXCEEDED} when a ceiling refuses the payment,
   *         {@link ProblemType#ACCOUNT_NOT_FOUND} when the account service does not know the
   *         destination account, {@link ProblemType#DOWNSTREAM_SERVICE_ERROR} when it answers
   *         anything else or cannot be reached
   */
  @Transactional(rollbackFor = ApiProblemException.class)
  @PostMapping(path = PAYMENT_LIST_PATH)
  @PreAuthorize("#card.isActive() && @ac.ownsAccount(#card.getIban().toMachineReadableString())")
  public ResponseEntity<Void> createCardPayment(
      @Parameter(schema = @Schema(type = "string",
          description = "The number of the card to create a payment with"))
      @PathVariable(name = CARD_NUMBER_PLACEHOLDER) Card card,
      @RequestBody @Valid CardPaymentCreationRequest dto,
      Authentication auth) throws ApiProblemException {
    // Assert that the destination account is known by the account service
    try {
      log.debug("Retrieving account {} from the account service", dto.destinationIban());

      if (card.getCeilings().getTransaction().compareTo(dto.amount()) < 0) {
        log
            .warn(
                "Card payment with card {} to account {} rejected because the transaction ceiling of {} does not allow this payment of {}",
                card.getNumber(),
                dto.destinationIban(),
                card.getCeilings().getTransaction(),
                dto.amount());
        throw new ApiProblemException(
            ProblemType.CARD_TRANSACTION_CEILING_EXCEEDED,
            "Card transaction ceiling set at %d does not allow this payment of %d"
                .formatted(card.getCeilings().getTransaction(), dto.amount()),
            Map
                .of(
                    "ceiling",
                    card.getCeilings().getTransaction(),
                    "amount",
                    dto.amount(),
                    "currency",
                    dto.currency()));
      }

      final var cumulatedAmount =
          transactionalCardPaymentHelper.getAcceptedPaymentsCumulatedAmountOn30Days(card);
      if (card.getCeilings().getRolling30().compareTo(cumulatedAmount + dto.amount()) < 0) {
        log
            .warn(
                "Card payment with card {} to account {} rejected because the rolling30 ceiling of {} does not allow this payment of {} because the cumulated amount of accepted payments is {}",
                card.getNumber(),
                dto.destinationIban(),
                card.getCeilings().getRolling30(),
                dto.amount(),
                cumulatedAmount);
        throw new ApiProblemException(
            ProblemType.CARD_ROLLING30_CEILING_EXCEEDED,
            "Card rolling30 ceiling set at %d does not allow this payment of %d because the cumulated amount of accepted payments is %d"
                .formatted(card.getCeilings().getRolling30(), dto.amount(), cumulatedAmount),
            Map
                .of(
                    "ceiling",
                    card.getCeilings().getRolling30(),
                    "amount",
                    dto.amount(),
                    "cumulatedAmount",
                    cumulatedAmount,
                    "currency",
                    dto.currency()));
      }

      var payment = transactionalCardPaymentHelper.createPayemnt(card, dto);
      log
          .debug(
              "Created card payment {} with card {} to account {}",
              payment.getId(),
              card.getNumber(),
              dto.destinationIban());

      transactionalCardPaymentHelper.transferMoneyAndAccept(payment);
      log
          .info(
              "{} paid {}{} with card {} to account {}",
              auth.getName(),
              dto.amount(),
              dto.currency(),
              card.getNumber(),
              dto.destinationIban());

      rabbitTemplate
          .convertAndSend(
              eventsExchange.getName(),
              "card.payments.created",
              new DomainEvent(
                  ResourceType.CARD_PAYMENTS,
                  card.getNumber(),
                  resolveAccountOwner(card.getIban()),
                  List.of("card.read_any"),
                  DomainEvent.EventType.CREATE,
                  Instant.now()));

      return ResponseEntity
          .created(
              URI
                  .create(
                      PAYMENT_PATH
                          .replace("{%s}".formatted(CARD_NUMBER_PLACEHOLDER), card.getNumber())
                          .replace(
                              "{%s}".formatted(PAYMENT_ID_PLACEHOLDER),
                              payment.getId().toString())))
          .build();

    } catch (HttpClientErrorException.NotFound e) {
      log
          .warn(
              "Card payment with card {} to account {} rejected because the destination account is not known to the account service",
              card.getNumber(),
              dto.destinationIban());
      throw accountNotFound(dto.destinationIban());
    } catch (RestClientException e) {
      log
          .error(
              "Error while checking destination account {} existence for card payment with card {}",
              dto.destinationIban(),
              card.getNumber(),
              e);
      throw accountServiceError(
          "The account-service could not confirm that the destination account exists");
    }
  }

  private static ApiProblemException accountNotFound(String iban) {
    return new ApiProblemException(
        ProblemType.ACCOUNT_NOT_FOUND,
        "Account %s is not known to the account-service".formatted(iban),
        Map.of("iban", iban));
  }

  // the account service's answer is for the logs, never for the client: the detail is ours
  private static ApiProblemException accountServiceError(String detail) {
    return new ApiProblemException(
        ProblemType.DOWNSTREAM_SERVICE_ERROR,
        detail,
        Map.of("service", "account-service"));
  }

  private String resolveAccountOwner(Iban iban) {
    return accountsApi.getAccount(iban.toMachineReadableString()).getBody().getCustomerId();
  }

  private void publishCardUpdatedEvent(Card card) {
    rabbitTemplate
        .convertAndSend(
            eventsExchange.getName(),
            "card.updated",
            new DomainEvent(
                ResourceType.CARD,
                card.getNumber(),
                resolveAccountOwner(card.getIban()),
                List.of("card.read_any"),
                DomainEvent.EventType.UPDATE,
                Instant.now()));
  }

  // LAB:2.5:REMOVE:START
  @Service
  // LAB:2.5:REMOVE:END
  @RequiredArgsConstructor
  static class TransactionalCardPaymentHelper {
    private final CardPaymentRepository paymentRepo;
    private final MoneyTransfersApi transfersApi;

    @Transactional(readOnly = true)
    Integer getAcceptedPaymentsCumulatedAmountOn30Days(Card card) {
      final var now = Instant.now();
      final var last30DaysPayments = paymentRepo
          .findByCardNumberAndTimestampBetween(
              card.getNumber(),
              now.minus(30, ChronoUnit.DAYS),
              now);
      return last30DaysPayments
          .stream()
          .filter(CardPayment::isAccepted)
          .mapToInt(p -> p.getAmount().getDigits())
          .sum();

    }

    // LAB:3.8:REMOVE:START
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = false)
    // LAB:3.8:REMOVE:END
    CardPayment createPayemnt(Card card, CardPaymentCreationRequest dto) {
      return paymentRepo
          .save(
              CardPayment
                  .builder()
                  .amount(new Amount(dto.amount(), Currency.valueOf(dto.currency())))
                  .card(card)
                  .destinationIban(Iban.of(dto.destinationIban()))
                  .accepted(false)
                  .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = false)
    CardPayment transferMoneyAndAccept(CardPayment payment) {
      try {
        var body = new MoneyTransferRequest()
            .amount(payment.getAmount().getDigits())
            .currency(payment.getAmount().getCurrency().name())
            .sourceIban(payment.getCard().getIban().toMachineReadableString())
            .destinationIban(payment.getDestinationIban().toMachineReadableString())
            .label(
                "Payment with card %s to %s"
                    .formatted(
                        payment.getCard().getNumber(),
                        payment.getDestinationIban().toHumanReadableString()));
        transfersApi.transferMoneyBetweenAccounts(body);
      } catch (RestClientException e) {
        log
            .error(
                "Error while transferring money for card payment {} with card {} to account {}: {}",
                payment.getId(),
                payment.getCard().getNumber(),
                payment.getDestinationIban(),
                e.getMessage());
        throw accountServiceError("The account-service could not transfer the money").unchecked();
      }
      log
          .debug(
              "Sucessfully transferred money for card payment {} with card {} to account {}",
              payment.getId(),
              payment.getCard().getNumber(),
              payment.getDestinationIban());
      payment.accept();
      final var acceptedPayment = paymentRepo.save(payment);
      log.debug("Saved card payment {} as accepted", acceptedPayment.getId());
      return acceptedPayment;
    }
  }

}
