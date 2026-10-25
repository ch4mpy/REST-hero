package com.c4soft.resthero.card.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithJwt;
import com.c4_soft.springaddons.security.oauth2.test.webmvc.AutoConfigureAddonsWebmvcResourceServerSecurity;
import com.c4_soft.springaddons.security.oauth2.test.webmvc.MockMvcSupport;
import com.c4soft.resthero.account.api.AccountsApi;
import com.c4soft.resthero.account.api.MoneyTransfersApi;
import com.c4soft.resthero.account.model.AccountResponse;
import com.c4soft.resthero.card.CardFixtures;
import com.c4soft.resthero.card.SecurityConfig;
import com.c4soft.resthero.card.WebConfiguration;
import com.c4soft.resthero.card.domain.Card;
import com.c4soft.resthero.card.domain.CardPayment;
import com.c4soft.resthero.card.jpa.CardPaymentRepository;
import com.c4soft.resthero.card.jpa.CardRepository;
import com.c4soft.resthero.commons.domain.Amount;
import com.c4soft.resthero.commons.domain.Currency;
import com.c4soft.resthero.commons.domain.Iban;
import com.c4soft.resthero.commons.domain.IbanStringMapper;
import com.c4soft.resthero.problem.ProblemType;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = CardController.class, properties = "server.ssl.enabled=false")
@Import({IbanStringMapper.class, CardMapperImpl.class, CardPaymentMapperImpl.class,
    WebConfiguration.class,
    SecurityConfig.class})
@AutoConfigureAddonsWebmvcResourceServerSecurity
class CardControllerTest {

  @MockitoBean
  CardRepository cardRepo;

  @MockitoBean
  CardPaymentRepository paymentRepo;

  @MockitoBean
  AccountsApi accountsApi;

  @MockitoBean
  MoneyTransfersApi transfersApi;

  @MockitoBean
  RabbitTemplate rabbitTemplate;

  @MockitoBean
  TopicExchange eventsExchange;

  @Autowired
  MockMvcSupport api;

  @Autowired
  ObjectMapper json;

  private static final String LIST_PATH = CardController.BASE_PATH + "?iban={iban}";

  private static final String PAYMENTS_IN_PERIOD_PATH =
      CardController.PAYMENT_LIST_PATH + "?from={from}&to={to}";

  /** The account service answers that the account of this IBAN belongs to this customer. */
  private void givenAccountOwnedBy(String customerId, String iban) {
    when(accountsApi.getAccount(iban))
        .thenReturn(
            ResponseEntity
                .ok(
                    new AccountResponse()
                        .iban(iban)
                        .customerId(customerId)
                        .currency("XPF")
                        .balance(0)));
  }

  private Card givenCustomersCard(Integer transactionCeiling, Integer rolling30Ceiling) {
    var card = CardFixtures.createCustomersCard(transactionCeiling, rolling30Ceiling);
    when(cardRepo.findByNumber(card.getNumber())).thenReturn(Optional.of(card));
    givenAccountOwnedBy(CardFixtures.CUSTOMER_SUBJECT, CardFixtures.CUSTOMER_IBAN);
    return card;
  }

  private Card givenSomeonesCard() {
    var card = CardFixtures.createSomeonesCard(1000, 5000);
    when(cardRepo.findByNumber(card.getNumber())).thenReturn(Optional.of(card));
    givenAccountOwnedBy(CardFixtures.SOMEONE_SUBJECT, CardFixtures.SOMEONE_IBAN);
    return card;
  }

  private static String cardPath(String path, Card card) {
    return path.replace("{%s}".formatted(CardController.CARD_NUMBER_PLACEHOLDER), card.getNumber());
  }

  // ===================== listCards =====================

  @Test
  @WithAnonymousUser
  void givenAnonymousUser_whenListCards_thenUnauthorized() throws Exception {
    api.get(LIST_PATH, CardFixtures.CUSTOMER_IBAN).andExpect(status().isUnauthorized());
  }

  @Test
  @WithJwt("advisor.json")
  void givenUserHasReadAnyAuthority_whenListCards_thenOk() throws Exception {
    var card = CardFixtures.createCustomersCard(1000, 5000);
    when(cardRepo.findByIban(card.getIban())).thenReturn(List.of(card));

    api
        .get(LIST_PATH, card.getIban().toMachineReadableString())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].number").value(card.getNumber()));
  }

  @Test
  @WithJwt("customer.json")
  void givenUserIsAccountOwner_whenListCards_thenOk() throws Exception {
    var card = givenCustomersCard(1000, 5000);
    when(cardRepo.findByIban(card.getIban())).thenReturn(List.of(card));

    api.get(LIST_PATH, CardFixtures.CUSTOMER_IBAN).andExpect(status().isOk());
  }

  @Test
  @WithJwt("customer.json")
  void givenUserIsNotAccountOwner_whenListCards_thenForbidden() throws Exception {
    givenSomeonesCard();

    api.get(LIST_PATH, CardFixtures.SOMEONE_IBAN).andExpect(status().isForbidden());
    verify(cardRepo, never()).findByIban(any());
  }

  /** A missing query parameter is rejected by the framework, before any validation. */
  @Test
  @WithJwt("advisor.json")
  void givenNoIban_whenListCards_thenInvalidRequest() throws Exception {
    api
        .get(CardController.BASE_PATH)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(ProblemType.INVALID_REQUEST.uri().toString()));
  }

  /** The query parameter is no IBAN: its conversion fails before the controller is reached. */
  @Test
  @WithJwt("advisor.json")
  void givenAMalformedIban_whenListCards_thenInvalidRequest() throws Exception {
    api
        .get(LIST_PATH, "not-an-iban")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(ProblemType.INVALID_REQUEST.uri().toString()));
  }

  // ===================== createCard =====================

  @Test
  @WithAnonymousUser
  void givenAnonymousUser_whenCreateCard_thenUnauthorized() throws Exception {
    api
        .post(new CardRequest(CardFixtures.CUSTOMER_IBAN, 1000, 5000), CardController.BASE_PATH)
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithJwt("customer.json")
  void givenUserDoesNotHaveCreateAuthority_whenCreateCard_thenForbidden() throws Exception {
    api
        .post(new CardRequest(CardFixtures.CUSTOMER_IBAN, 1000, 5000), CardController.BASE_PATH)
        .andExpect(status().isForbidden());
    verify(cardRepo, never()).save(any(Card.class));
  }

  @Test
  @WithJwt("advisor.json")
  void givenUserHasCreateAuthority_whenCreateCardWithKnownAccount_thenCreated() throws Exception {
    givenAccountOwnedBy(CardFixtures.CUSTOMER_SUBJECT, CardFixtures.CUSTOMER_IBAN);
    when(cardRepo.findByIban(any(Iban.class))).thenReturn(List.of());
    when(cardRepo.save(any(Card.class))).thenAnswer(i -> i.getArgument(0));
    var expectedNumber = CardFixtures.createCustomersCard(1000, 5000).getNumber();

    api
        .post(new CardRequest(CardFixtures.CUSTOMER_IBAN, 1000, 5000), CardController.BASE_PATH)
        .andExpect(status().isCreated())
        .andExpect(
            header().string(HttpHeaders.LOCATION, CardController.BASE_PATH + "/" + expectedNumber));
  }

  @Test
  @WithJwt("advisor.json")
  void givenTheAccountServiceDoesNotKnowTheAccount_whenCreateCard_thenAccountNotFound()
      throws Exception {
    when(accountsApi.getAccount(CardFixtures.CUSTOMER_IBAN))
        .thenThrow(
            HttpClientErrorException
                .create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null));

    api
        .post(new CardRequest(CardFixtures.CUSTOMER_IBAN, 1000, 5000), CardController.BASE_PATH)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(ProblemType.ACCOUNT_NOT_FOUND.uri().toString()))
        .andExpect(jsonPath("$.parameters.iban").value(CardFixtures.CUSTOMER_IBAN));
    verify(cardRepo, never()).save(any(Card.class));
  }

  @Test
  @WithJwt("advisor.json")
  void givenTheAccountServiceFails_whenCreateCard_thenDownstreamServiceError() throws Exception {
    when(accountsApi.getAccount(CardFixtures.CUSTOMER_IBAN))
        .thenThrow(
            HttpServerErrorException
                .create(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Internal Server Error",
                    HttpHeaders.EMPTY,
                    new byte[0],
                    StandardCharsets.UTF_8));

    api
        .post(new CardRequest(CardFixtures.CUSTOMER_IBAN, 1000, 5000), CardController.BASE_PATH)
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.type").value(ProblemType.DOWNSTREAM_SERVICE_ERROR.uri().toString()))
        .andExpect(jsonPath("$.parameters.service").value("account-service"));
    verify(cardRepo, never()).save(any(Card.class));
  }

  @Test
  @WithJwt("advisor.json")
  void givenNoIban_whenCreateCard_thenValidationProblem() throws Exception {
    api
        .post(new CardRequest(null, 1000, 5000), CardController.BASE_PATH)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.type").value(ProblemType.VALIDATION.uri().toString()))
        .andExpect(jsonPath("$.invalidFields.iban").isString());
  }

  @Test
  @WithJwt("advisor.json")
  void givenAMalformedIban_whenCreateCard_thenValidationProblem() throws Exception {
    api
        .post(new CardRequest("not-an-iban", 1000, 5000), CardController.BASE_PATH)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.invalidFields.iban").isString());
  }

  @Test
  @WithJwt("advisor.json")
  void givenANonPositiveTransactionCeiling_whenCreateCard_thenValidationProblem()
      throws Exception {
    api
        .post(new CardRequest(CardFixtures.CUSTOMER_IBAN, 0, 5000), CardController.BASE_PATH)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.invalidFields.transactionCeiling").isString());
  }

  // ===================== getCard =====================

  @Test
  @WithAnonymousUser
  void givenAnonymousUser_whenGetCard_thenUnauthorized() throws Exception {
    api.get(CardController.CARD_PATH, "some-card-number").andExpect(status().isUnauthorized());
  }

  @Test
  @WithJwt("advisor.json")
  void givenUserHasReadAnyAuthority_whenGetCard_thenOk() throws Exception {
    var card = CardFixtures.createCustomersCard(1000, 5000);
    when(cardRepo.findByNumber(card.getNumber())).thenReturn(Optional.of(card));

    var actual = json
        .readValue(
            api
                .get(CardController.CARD_PATH, card.getNumber())
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            CardResponse.class);

    assertThat(actual.number()).isEqualTo(card.getNumber());
  }

  @Test
  @WithJwt("customer.json")
  void givenUserIsCardOwner_whenGetCard_thenOk() throws Exception {
    var card = givenCustomersCard(1000, 5000);

    api.get(CardController.CARD_PATH, card.getNumber()).andExpect(status().isOk());
  }

  @Test
  @WithJwt("customer.json")
  void givenUserIsNotCardOwner_whenGetCard_thenForbidden() throws Exception {
    var card = givenSomeonesCard();

    api.get(CardController.CARD_PATH, card.getNumber()).andExpect(status().isForbidden());
  }

  @Test
  @WithJwt("advisor.json")
  void givenUnknownCardNumber_whenGetCard_thenCardNotFound() throws Exception {
    when(cardRepo.findByNumber("unknown-card-number")).thenReturn(Optional.empty());

    api
        .get(CardController.CARD_PATH, "unknown-card-number")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(ProblemType.CARD_NOT_FOUND.uri().toString()))
        .andExpect(jsonPath("$.parameters.cardNumber").value("unknown-card-number"));
  }

  // ===================== setCardStatus =====================

  @Test
  @WithAnonymousUser
  void givenAnonymousUser_whenSetCardStatus_thenUnauthorized() throws Exception {
    api
        .put(new CardStatusRequest(false), CardController.CARD_STATUS_PATH, "some-card-number")
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithJwt("customer.json")
  void givenUserDoesNotHaveCardStatusEditAuthority_whenSetCardStatus_thenForbidden()
      throws Exception {
    var card = givenCustomersCard(1000, 5000);

    api
        .put(new CardStatusRequest(false), CardController.CARD_STATUS_PATH, card.getNumber())
        .andExpect(status().isForbidden());
    verify(cardRepo, never()).save(any(Card.class));
  }

  @Test
  @WithJwt("advisor.json")
  void givenUserHasCardStatusEditAuthority_whenSetCardStatus_thenNoContent() throws Exception {
    var card = givenCustomersCard(1000, 5000);
    when(cardRepo.save(any(Card.class))).thenAnswer(i -> i.getArgument(0));

    api
        .put(new CardStatusRequest(false), CardController.CARD_STATUS_PATH, card.getNumber())
        .andExpect(status().isNoContent());

    assertThat(card.isActive()).isFalse();
    verify(cardRepo).save(card);
  }

  @Test
  @WithJwt("advisor.json")
  void givenNoStatus_whenSetCardStatus_thenValidationProblem() throws Exception {
    var card = givenCustomersCard(1000, 5000);

    api
        .put(new CardStatusRequest(null), CardController.CARD_STATUS_PATH, card.getNumber())
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.type").value(ProblemType.VALIDATION.uri().toString()))
        .andExpect(jsonPath("$.invalidFields.isActive").isString());
  }

  @Test
  @WithJwt("advisor.json")
  void givenUnknownCardNumber_whenSetCardStatus_thenCardNotFound() throws Exception {
    when(cardRepo.findByNumber("unknown-card-number")).thenReturn(Optional.empty());

    api
        .put(new CardStatusRequest(false), CardController.CARD_STATUS_PATH, "unknown-card-number")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(ProblemType.CARD_NOT_FOUND.uri().toString()))
        .andExpect(jsonPath("$.parameters.cardNumber").value("unknown-card-number"));
  }

  /** Another request changed the card in the meantime: the save loses the optimistic lock. */
  @Test
  @WithJwt("advisor.json")
  void givenAConcurrentUpdate_whenSetCardStatus_thenConcurrentModificationProblem() throws Exception {
    var card = givenCustomersCard(1000, 5000);
    when(cardRepo.save(any(Card.class)))
        .thenThrow(new ObjectOptimisticLockingFailureException(Card.class, card.getNumber()));

    api
        .put(
            new CardStatusRequest(!card.isActive()),
            CardController.CARD_STATUS_PATH,
            card.getNumber())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(ProblemType.CONCURRENT_MODIFICATION.uri().toString()))
        .andExpect(jsonPath("$.parameters.entity").value("Card"))
        .andExpect(jsonPath("$.parameters.id").value(card.getNumber()));
  }

  // ===================== setCardCeilings =====================

  @Test
  @WithAnonymousUser
  void givenAnonymousUser_whenSetCardCeilings_thenUnauthorized() throws Exception {
    api
        .put(new CardCeilingsRequest(1000, 5000), CardController.CARD_CEILINGS_PATH, "some-card-number")
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithJwt("customer.json")
  void givenUserDoesNotHaveCeilingsEditAuthority_whenSetCardCeilings_thenForbidden()
      throws Exception {
    var card = givenCustomersCard(1000, 5000);

    api
        .put(new CardCeilingsRequest(2000, 10000), CardController.CARD_CEILINGS_PATH, card.getNumber())
        .andExpect(status().isForbidden());
    verify(cardRepo, never()).save(any(Card.class));
  }

  @Test
  @WithJwt("advisor.json")
  void givenUserHasCeilingsEditAuthority_whenSetCardCeilings_thenNoContent() throws Exception {
    var card = givenCustomersCard(1000, 5000);
    when(cardRepo.save(any(Card.class))).thenAnswer(i -> i.getArgument(0));

    api
        .put(new CardCeilingsRequest(2000, 10000), CardController.CARD_CEILINGS_PATH, card.getNumber())
        .andExpect(status().isNoContent());

    assertThat(card.getCeilings().getTransaction()).isEqualTo(2000);
    assertThat(card.getCeilings().getRolling30()).isEqualTo(10000);
    verify(cardRepo).save(card);
  }

  @Test
  @WithJwt("advisor.json")
  void givenANonPositiveTransactionCeiling_whenSetCardCeilings_thenValidationProblem()
      throws Exception {
    var card = givenCustomersCard(1000, 5000);

    api
        .put(new CardCeilingsRequest(0, 5000), CardController.CARD_CEILINGS_PATH, card.getNumber())
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.type").value(ProblemType.VALIDATION.uri().toString()))
        .andExpect(jsonPath("$.invalidFields.transactionCeiling").isString());
  }

  @Test
  @WithJwt("advisor.json")
  void givenANonPositiveRolling30Ceiling_whenSetCardCeilings_thenValidationProblem()
      throws Exception {
    var card = givenCustomersCard(1000, 5000);

    api
        .put(new CardCeilingsRequest(1000, 0), CardController.CARD_CEILINGS_PATH, card.getNumber())
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.invalidFields.rolling30Ceiling").isString());
  }

  // ===================== listCardPayments =====================

  @Test
  @WithAnonymousUser
  void givenAnonymousUser_whenListCardPayments_thenUnauthorized() throws Exception {
    api
        .get(
            PAYMENTS_IN_PERIOD_PATH,
            "some-card-number",
            Instant.now().minus(1, ChronoUnit.DAYS),
            Instant.now())
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithJwt("advisor.json")
  void givenUserHasReadAnyAuthority_whenListCardPayments_thenOk() throws Exception {
    var card = CardFixtures.createCustomersCard(1000, 5000);
    var from = Instant.now().minus(1, ChronoUnit.DAYS);
    var to = Instant.now();
    when(cardRepo.findByNumber(card.getNumber())).thenReturn(Optional.of(card));
    when(paymentRepo.findByCardNumberAndTimestampBetween(card.getNumber(), from, to))
        .thenReturn(List.of());

    api.get(PAYMENTS_IN_PERIOD_PATH, card.getNumber(), from, to).andExpect(status().isOk());

    verify(paymentRepo).findByCardNumberAndTimestampBetween(card.getNumber(), from, to);
  }

  @Test
  @WithJwt("customer.json")
  void givenUserIsCardOwner_whenListCardPayments_thenOk() throws Exception {
    var card = givenCustomersCard(1000, 5000);
    var from = Instant.now().minus(1, ChronoUnit.DAYS);
    var to = Instant.now();
    when(paymentRepo.findByCardNumberAndTimestampBetween(card.getNumber(), from, to))
        .thenReturn(List.of());

    api.get(PAYMENTS_IN_PERIOD_PATH, card.getNumber(), from, to).andExpect(status().isOk());
  }

  @Test
  @WithJwt("customer.json")
  void givenUserIsNotCardOwner_whenListCardPayments_thenForbidden() throws Exception {
    var card = givenSomeonesCard();

    api
        .get(
            PAYMENTS_IN_PERIOD_PATH,
            card.getNumber(),
            Instant.now().minus(1, ChronoUnit.DAYS),
            Instant.now())
        .andExpect(status().isForbidden());
  }

  @Test
  @WithJwt("advisor.json")
  void givenNoPeriodEnd_whenListCardPayments_thenValidationProblem() throws Exception {
    var card = givenCustomersCard(1000, 5000);

    api
        .get(
            CardController.PAYMENT_LIST_PATH + "?from={from}",
            card.getNumber(),
            Instant.now().minus(100, ChronoUnit.DAYS))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.type").value(ProblemType.VALIDATION.uri().toString()))
        .andExpect(jsonPath("$.invalidFields.period").isString());
  }

  @Test
  @WithJwt("advisor.json")
  void givenNoPeriodStart_whenListCardPayments_thenValidationProblem() throws Exception {
    var card = givenCustomersCard(1000, 5000);

    api
        .get(CardController.PAYMENT_LIST_PATH + "?to={to}", card.getNumber(), Instant.now())
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.invalidFields.period").isString());
  }

  @Test
  @WithJwt("advisor.json")
  void givenNoPeriod_whenListCardPayments_thenValidationProblem() throws Exception {
    var card = givenCustomersCard(1000, 5000);

    api
        .get(CardController.PAYMENT_LIST_PATH, card.getNumber())
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.invalidFields.period").isString());
  }

  // ===================== createCardPayment =====================

  @Test
  @WithAnonymousUser
  void givenAnonymousUser_whenCreateCardPayment_thenUnauthorized() throws Exception {
    api
        .post(
            new CardPaymentCreationRequest("XPF", 100, "some-card-number", CardFixtures.SOMEONE_IBAN),
            CardController.PAYMENT_LIST_PATH,
            "some-card-number")
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithJwt("customer.json")
  void givenUserIsCardOwner_whenCreateCardPayment_thenCreated() throws Exception {
    var card = givenCustomersCard(1000, 5000);
    when(
        paymentRepo
            .findByCardNumberAndTimestampBetween(
                any(String.class),
                any(Instant.class),
                any(Instant.class)))
        .thenReturn(List.of());
    when(paymentRepo.save(any(CardPayment.class))).thenAnswer(i -> {
      CardPayment payment = i.getArgument(0, CardPayment.class);
      final var persistedPayment =
          Mockito.mockingDetails(payment).isMock() ? payment : Mockito.spy(payment);
      when(persistedPayment.getId()).thenReturn(1L);
      return persistedPayment;
    });
    when(transfersApi.transferMoneyBetweenAccounts(any()))
        .thenReturn(ResponseEntity.status(HttpStatus.CREATED).build());

    api
        .post(
            new CardPaymentCreationRequest("XPF", 100, card.getNumber(), CardFixtures.SOMEONE_IBAN),
            CardController.PAYMENT_LIST_PATH,
            card.getNumber())
        .andExpect(status().isCreated())
        .andExpect(
            header().string(HttpHeaders.LOCATION, cardPath(CardController.PAYMENT_LIST_PATH, card) + "/1"));
  }

  @Test
  @WithJwt("customer.json")
  void givenUserIsNotCardOwner_whenCreateCardPayment_thenForbidden() throws Exception {
    var card = givenSomeonesCard();

    api
        .post(
            new CardPaymentCreationRequest("XPF", 100, card.getNumber(), CardFixtures.SOMEONE_IBAN),
            CardController.PAYMENT_LIST_PATH,
            card.getNumber())
        .andExpect(status().isForbidden());
    verify(paymentRepo, never()).save(any(CardPayment.class));
  }

  /** Even its owner cannot pay with an inactive card: the access rule requires an active one. */
  @Test
  @WithJwt("customer.json")
  void givenTheCardIsInactive_whenCreateCardPayment_thenForbidden() throws Exception {
    var card = givenCustomersCard(1000, 5000);
    card.deactivate();

    api
        .post(
            new CardPaymentCreationRequest("XPF", 100, card.getNumber(), CardFixtures.SOMEONE_IBAN),
            CardController.PAYMENT_LIST_PATH,
            card.getNumber())
        .andExpect(status().isForbidden());
    verify(paymentRepo, never()).save(any(CardPayment.class));
  }

  @Test
  @WithJwt("customer.json")
  void givenAmountExceedsTransactionCeiling_whenCreateCardPayment_thenCeilingExceeded()
      throws Exception {
    var card = givenCustomersCard(100, 5000);

    api
        .post(
            new CardPaymentCreationRequest("XPF", 500, card.getNumber(), CardFixtures.SOMEONE_IBAN),
            CardController.PAYMENT_LIST_PATH,
            card.getNumber())
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type").value(ProblemType.CARD_TRANSACTION_CEILING_EXCEEDED.uri().toString()))
        .andExpect(jsonPath("$.parameters.ceiling").value(100))
        .andExpect(jsonPath("$.parameters.amount").value(500))
        .andExpect(jsonPath("$.parameters.currency").value("XPF"));
    verify(paymentRepo, never()).save(any(CardPayment.class));
  }

  @Test
  @WithJwt("customer.json")
  void givenAmountExceedsRolling30Ceiling_whenCreateCardPayment_thenCeilingExceeded()
      throws Exception {
    var card = givenCustomersCard(1000, 500);
    var existingPayment = CardPayment
        .builder()
        .id(1L)
        .amount(new Amount(400, Currency.XPF))
        .card(card)
        .destinationIban(Iban.of(CardFixtures.SOMEONE_IBAN))
        .accepted(true)
        .build();
    when(
        paymentRepo
            .findByCardNumberAndTimestampBetween(
                any(String.class),
                any(Instant.class),
                any(Instant.class)))
        .thenReturn(List.of(existingPayment));

    api
        .post(
            new CardPaymentCreationRequest("XPF", 500, card.getNumber(), CardFixtures.SOMEONE_IBAN),
            CardController.PAYMENT_LIST_PATH,
            card.getNumber())
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.type").value(ProblemType.CARD_ROLLING30_CEILING_EXCEEDED.uri().toString()))
        .andExpect(jsonPath("$.parameters.ceiling").value(500))
        .andExpect(jsonPath("$.parameters.amount").value(500))
        .andExpect(jsonPath("$.parameters.cumulatedAmount").value(400))
        .andExpect(jsonPath("$.parameters.currency").value("XPF"));
    verify(paymentRepo, never()).save(any(CardPayment.class));
  }

  @Test
  @WithJwt("customer.json")
  void givenTheTransferFails_whenCreateCardPayment_thenDownstreamServiceError() throws Exception {
    var card = givenCustomersCard(1000, 5000);
    when(
        paymentRepo
            .findByCardNumberAndTimestampBetween(
                any(String.class),
                any(Instant.class),
                any(Instant.class)))
        .thenReturn(List.of());
    when(paymentRepo.save(any(CardPayment.class))).thenAnswer(i -> i.getArgument(0));
    when(transfersApi.transferMoneyBetweenAccounts(any()))
        .thenThrow(
            HttpServerErrorException
                .create(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Service Unavailable",
                    HttpHeaders.EMPTY,
                    new byte[0],
                    StandardCharsets.UTF_8));

    api
        .post(
            new CardPaymentCreationRequest("XPF", 100, card.getNumber(), CardFixtures.SOMEONE_IBAN),
            CardController.PAYMENT_LIST_PATH,
            card.getNumber())
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.type").value(ProblemType.DOWNSTREAM_SERVICE_ERROR.uri().toString()))
        .andExpect(jsonPath("$.parameters.service").value("account-service"));
  }

  @Test
  @WithJwt("customer.json")
  void givenAMalformedDestinationIban_whenCreateCardPayment_thenValidationProblem()
      throws Exception {
    var card = givenCustomersCard(1000, 5000);

    api
        .post(
            new CardPaymentCreationRequest("XPF", 100, card.getNumber(), "not-an-iban"),
            CardController.PAYMENT_LIST_PATH,
            card.getNumber())
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.type").value(ProblemType.VALIDATION.uri().toString()))
        .andExpect(jsonPath("$.invalidFields.destinationIban").isString());
  }

  @Test
  @WithJwt("customer.json")
  void givenANegativeAmount_whenCreateCardPayment_thenValidationProblem() throws Exception {
    var card = givenCustomersCard(1000, 5000);

    api
        .post(
            new CardPaymentCreationRequest("XPF", -1, card.getNumber(), CardFixtures.SOMEONE_IBAN),
            CardController.PAYMENT_LIST_PATH,
            card.getNumber())
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.invalidFields.amount").isString());
  }

  @Test
  @WithJwt("customer.json")
  void givenAnUnsupportedCurrency_whenCreateCardPayment_thenValidationProblem() throws Exception {
    var card = givenCustomersCard(1000, 5000);

    api
        .post(
            new CardPaymentCreationRequest("XX", 100, card.getNumber(), CardFixtures.SOMEONE_IBAN),
            CardController.PAYMENT_LIST_PATH,
            card.getNumber())
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.invalidFields.currency").isString());
  }

}
