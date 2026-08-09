package com.c4soft.resthero.card.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithJwt;
import com.c4_soft.springaddons.security.oauth2.test.webmvc.AutoConfigureAddonsWebmvcResourceServerSecurity;
import com.c4soft.resthero.account.api.AccountsApi;
import com.c4soft.resthero.account.api.MoneyTransfersApi;
import com.c4soft.resthero.account.model.AccountResponse;
import com.c4soft.resthero.card.CardFixtures;
import com.c4soft.resthero.card.SecurityConfig;
import com.c4soft.resthero.card.SpringDataWebConvertersTestConfiguration;
import com.c4soft.resthero.card.domain.Card;
import com.c4soft.resthero.card.domain.CardPayment;
import com.c4soft.resthero.card.jpa.CardPaymentRepository;
import com.c4soft.resthero.card.jpa.CardRepository;
import com.c4soft.resthero.commons.domain.Amount;
import com.c4soft.resthero.commons.domain.Currency;
import com.c4soft.resthero.commons.domain.Iban;
import com.c4soft.resthero.commons.domain.IbanStringMapper;
import com.c4soft.resthero.commons.exception.CommonExceptionsHandler;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = CardController.class, properties = {})
@Import({IbanStringMapper.class, CardMapperImpl.class, CardPaymentMapperImpl.class,
    CommonExceptionsHandler.class, SpringDataWebConvertersTestConfiguration.class,
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
  MockMvc mockMvc;

  @Autowired
  ObjectMapper json;

  private static AccountResponse accountOwnedBy(String customerId, String iban, String currency) {
    return new AccountResponse().iban(iban).customerId(customerId).currency(currency).balance(0);
  }

  // ===================== listCards =====================

  @Test
  @WithAnonymousUser
  void givenAnonymousUser_whenListCards_thenUnauthorized() throws Exception {
    mockMvc
        .perform(
            get("https://localhost" + CardController.BASE_PATH)
                .queryParam("iban", CardFixtures.CUSTOMER_IBAN))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithJwt("advisor.json")
  void givenUserHasReadAnyAuthority_whenListCards_thenOk() throws Exception {
    var card = CardFixtures.createCustomersCard(1000, 5000);
    when(cardRepo.findByIban(card.getIban())).thenReturn(List.of(card));

    var mvcResult = mockMvc
        .perform(
            get("https://localhost" + CardController.BASE_PATH)
                .queryParam("iban", card.getIban().toMachineReadableString()))
        .andExpect(status().isOk())
        .andReturn();

    assertThat(mvcResult.getResponse().getContentAsString()).contains(card.getNumber());
  }

  @Test
  @WithJwt("customer.json")
  void givenUserIsAccountOwner_whenListCards_thenOk() throws Exception {
    var card = CardFixtures.createCustomersCard(1000, 5000);
    when(cardRepo.findByIban(card.getIban())).thenReturn(List.of(card));
    when(accountsApi.getAccount(card.getIban().toMachineReadableString()))
        .thenReturn(
            ResponseEntity
                .ok(
                    accountOwnedBy(
                        CardFixtures.CUSTOMER_SUBJECT,
                        card.getIban().toMachineReadableString(),
                        "XPF")));

    mockMvc
        .perform(
            get("https://localhost" + CardController.BASE_PATH)
                .queryParam("iban", card.getIban().toMachineReadableString()))
        .andExpect(status().isOk());
  }

  @Test
  @WithJwt("customer.json")
  void givenUserIsNotAccountOwner_whenListCards_thenForbidden() throws Exception {
    var card = CardFixtures.createSomeonesCard(1000, 5000);
    when(accountsApi.getAccount(card.getIban().toMachineReadableString()))
        .thenReturn(
            ResponseEntity
                .ok(
                    accountOwnedBy(
                        CardFixtures.SOMEONE_SUBJECT,
                        card.getIban().toMachineReadableString(),
                        "XPF")));

    mockMvc
        .perform(
            get("https://localhost" + CardController.BASE_PATH)
                .queryParam("iban", card.getIban().toMachineReadableString()))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithJwt("advisor.json")
  void givenMissingOrInvalidIban_whenListCards_thenBadRequest() throws Exception {
    mockMvc
        .perform(get("https://localhost" + CardController.BASE_PATH))
        .andExpect(status().is4xxClientError());

    mockMvc
        .perform(
            get("https://localhost" + CardController.BASE_PATH).queryParam("iban", "not-an-iban"))
        .andExpect(status().is4xxClientError());
  }

  // ===================== createCard =====================

  @Test
  @WithAnonymousUser
  void givenAnonymousUser_whenCreateCard_thenUnauthorized() throws Exception {
    var dto = new CardRequest(CardFixtures.CUSTOMER_IBAN, 1000, 5000);

    mockMvc
        .perform(
            post("https://localhost" + CardController.BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dto)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithJwt("advisor.json")
  void givenUserHasCreateAuthority_whenCreateCardWithKnownAccount_thenCreated() throws Exception {
    var dto = new CardRequest(CardFixtures.CUSTOMER_IBAN, 1000, 5000);

    when(accountsApi.getAccount(CardFixtures.CUSTOMER_IBAN))
        .thenReturn(
            ResponseEntity
                .ok(
                    accountOwnedBy(
                        CardFixtures.CUSTOMER_SUBJECT,
                        CardFixtures.CUSTOMER_IBAN,
                        "XPF")));
    when(cardRepo.findByIban(any(Iban.class))).thenReturn(List.of());
    when(cardRepo.save(any(Card.class))).thenAnswer(i -> i.getArgument(0));

    var mvcResult = mockMvc
        .perform(
            post("https://localhost" + CardController.BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dto)))
        .andExpect(status().isCreated())
        .andReturn();

    assertThat(mvcResult.getResponse().getHeader("Location")).isNotNull();
  }

  @Test
  @WithJwt("customer.json")
  void givenUserDoesNotHaveCreateAuthority_whenCreateCard_thenForbidden() throws Exception {
    var dto = new CardRequest(CardFixtures.CUSTOMER_IBAN, 1000, 5000);

    mockMvc
        .perform(
            post("https://localhost" + CardController.BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dto)))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithJwt("advisor.json")
  void givenUnknownAccount_whenCreateCard_thenNotFound() throws Exception {
    var dto = new CardRequest(CardFixtures.CUSTOMER_IBAN, 1000, 5000);

    when(accountsApi.getAccount(CardFixtures.CUSTOMER_IBAN))
        .thenThrow(
            HttpClientErrorException
                .create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null));

    mockMvc
        .perform(
            post("https://localhost" + CardController.BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dto)))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithJwt("advisor.json")
  void givenInvalidPayload_whenCreateCard_thenBadRequest() throws Exception {
    // missing iban
    mockMvc
        .perform(
            post("https://localhost" + CardController.BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CardRequest(null, 1000, 5000))))
        .andExpect(status().is4xxClientError());

    // invalid iban
    mockMvc
        .perform(
            post("https://localhost" + CardController.BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CardRequest("not-an-iban", 1000, 5000))))
        .andExpect(status().is4xxClientError());

    // non positive ceilings
    mockMvc
        .perform(
            post("https://localhost" + CardController.BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(new CardRequest(CardFixtures.CUSTOMER_IBAN, 0, 5000))))
        .andExpect(status().is4xxClientError());
  }

  // ===================== getCard =====================

  @Test
  @WithAnonymousUser
  void givenAnonymousUser_whenGetCard_thenUnauthorized() throws Exception {
    mockMvc
        .perform(get("https://localhost" + CardController.CARD_PATH, "some-card-number"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithJwt("advisor.json")
  void givenUserHasReadAnyAuthority_whenGetCard_thenOk() throws Exception {
    var card = CardFixtures.createCustomersCard(1000, 5000);
    when(cardRepo.findByNumber(card.getNumber())).thenReturn(Optional.of(card));

    var actual = json
        .readValue(
            mockMvc
                .perform(get("https://localhost" + CardController.CARD_PATH, card.getNumber()))
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
    var card = CardFixtures.createCustomersCard(1000, 5000);
    when(cardRepo.findByNumber(card.getNumber())).thenReturn(Optional.of(card));
    when(accountsApi.getAccount(card.getIban().toMachineReadableString()))
        .thenReturn(
            ResponseEntity
                .ok(
                    accountOwnedBy(
                        CardFixtures.CUSTOMER_SUBJECT,
                        card.getIban().toMachineReadableString(),
                        "XPF")));

    mockMvc
        .perform(get("https://localhost" + CardController.CARD_PATH, card.getNumber()))
        .andExpect(status().isOk());
  }

  @Test
  @WithJwt("customer.json")
  void givenUserIsNotCardOwner_whenGetCard_thenForbidden() throws Exception {
    var card = CardFixtures.createSomeonesCard(1000, 5000);
    when(cardRepo.findByNumber(card.getNumber())).thenReturn(Optional.of(card));
    when(accountsApi.getAccount(card.getIban().toMachineReadableString()))
        .thenReturn(
            ResponseEntity
                .ok(
                    accountOwnedBy(
                        CardFixtures.SOMEONE_SUBJECT,
                        card.getIban().toMachineReadableString(),
                        "XPF")));

    mockMvc
        .perform(get("https://localhost" + CardController.CARD_PATH, card.getNumber()))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithJwt("advisor.json")
  void givenUnknownCardNumber_whenGetCard_thenNotFound() throws Exception {
    when(cardRepo.findByNumber("unknown-card-number")).thenReturn(Optional.empty());

    mockMvc
        .perform(get("https://localhost" + CardController.CARD_PATH, "unknown-card-number"))
        .andExpect(status().isNotFound());
  }

  // ===================== setCardStatus =====================

  @Test
  @WithAnonymousUser
  void givenAnonymousUser_whenSetCardStatus_thenUnauthorized() throws Exception {
    var dto = new CardStatusRequest(false);

    mockMvc
        .perform(
            put("https://localhost" + CardController.CARD_STATUS_PATH, "some-card-number")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dto)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithJwt("advisor.json")
  void givenUserHasCardStatusEditAuthority_whenSetCardStatus_thenAccepted() throws Exception {
    var card = CardFixtures.createCustomersCard(1000, 5000);
    var dto = new CardStatusRequest(false);
    when(cardRepo.findByNumber(card.getNumber())).thenReturn(Optional.of(card));
    when(cardRepo.save(any(Card.class))).thenAnswer(i -> i.getArgument(0));
    when(accountsApi.getAccount(card.getIban().toMachineReadableString()))
        .thenReturn(
            ResponseEntity
                .ok(
                    accountOwnedBy(
                        CardFixtures.CUSTOMER_SUBJECT,
                        card.getIban().toMachineReadableString(),
                        "XPF")));

    mockMvc
        .perform(
            put("https://localhost" + CardController.CARD_STATUS_PATH, card.getNumber())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dto)))
        .andExpect(status().isAccepted());
  }

  @Test
  @WithJwt("customer.json")
  void givenUserDoesNotHaveCardStatusEditAuthority_whenSetCardStatus_thenForbidden()
      throws Exception {
    var card = CardFixtures.createCustomersCard(1000, 5000);
    var dto = new CardStatusRequest(false);
    when(cardRepo.findByNumber(card.getNumber())).thenReturn(Optional.of(card));

    mockMvc
        .perform(
            put("https://localhost" + CardController.CARD_STATUS_PATH, card.getNumber())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dto)))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithJwt("advisor.json")
  void givenInvalidPayload_whenSetCardStatus_thenBadRequest() throws Exception {
    var card = CardFixtures.createCustomersCard(1000, 5000);
    when(cardRepo.findByNumber(card.getNumber())).thenReturn(Optional.of(card));

    mockMvc
        .perform(
            put("https://localhost" + CardController.CARD_STATUS_PATH, card.getNumber())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().is4xxClientError());
  }

  @Test
  @WithJwt("advisor.json")
  void givenUnknownCardNumber_whenSetCardStatus_thenNotFound() throws Exception {
    when(cardRepo.findByNumber("unknown-card-number")).thenReturn(Optional.empty());

    mockMvc
        .perform(
            put("https://localhost" + CardController.CARD_STATUS_PATH, "unknown-card-number")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CardStatusRequest(false))))
        .andExpect(status().isNotFound());
  }

  // ===================== setCardCeilings =====================

  @Test
  @WithAnonymousUser
  void givenAnonymousUser_whenSetCardCeilings_thenUnauthorized() throws Exception {
    var dto = new CardCeilingsRequest(1000, 5000);

    mockMvc
        .perform(
            put("https://localhost" + CardController.CARD_CEILINGS_PATH, "some-card-number")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dto)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithJwt("advisor.json")
  void givenUserHasCeilingsEditAuthority_whenSetCardCeilings_thenAccepted() throws Exception {
    var card = CardFixtures.createCustomersCard(1000, 5000);
    var dto = new CardCeilingsRequest(2000, 10000);
    when(cardRepo.findByNumber(card.getNumber())).thenReturn(Optional.of(card));
    when(cardRepo.save(any(Card.class))).thenAnswer(i -> i.getArgument(0));
    when(accountsApi.getAccount(card.getIban().toMachineReadableString()))
        .thenReturn(
            ResponseEntity
                .ok(
                    accountOwnedBy(
                        CardFixtures.CUSTOMER_SUBJECT,
                        card.getIban().toMachineReadableString(),
                        "XPF")));

    mockMvc
        .perform(
            put("https://localhost" + CardController.CARD_CEILINGS_PATH, card.getNumber())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dto)))
        .andExpect(status().isAccepted());
  }

  @Test
  @WithJwt("customer.json")
  void givenUserDoesNotHaveCeilingsEditAuthority_whenSetCardCeilings_thenForbidden()
      throws Exception {
    var card = CardFixtures.createCustomersCard(1000, 5000);
    var dto = new CardCeilingsRequest(2000, 10000);
    when(cardRepo.findByNumber(card.getNumber())).thenReturn(Optional.of(card));

    mockMvc
        .perform(
            put("https://localhost" + CardController.CARD_CEILINGS_PATH, card.getNumber())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dto)))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithJwt("advisor.json")
  void givenInvalidPayload_whenSetCardCeilings_thenBadRequest() throws Exception {
    var card = CardFixtures.createCustomersCard(1000, 5000);
    when(cardRepo.findByNumber(card.getNumber())).thenReturn(Optional.of(card));

    mockMvc
        .perform(
            put("https://localhost" + CardController.CARD_CEILINGS_PATH, card.getNumber())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CardCeilingsRequest(0, 5000))))
        .andExpect(status().is4xxClientError());
  }

  // ===================== listCardPayments =====================

  @Test
  @WithAnonymousUser
  void givenAnonymousUser_whenListCardPayments_thenUnauthorized() throws Exception {
    mockMvc
        .perform(
            get("https://localhost" + CardController.PAYMENT_LIST_PATH, "some-card-number")
                .queryParam("from", Instant.now().minus(1, ChronoUnit.DAYS).toString())
                .queryParam("to", Instant.now().toString()))
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

    mockMvc
        .perform(
            get("https://localhost" + CardController.PAYMENT_LIST_PATH, card.getNumber())
                .queryParam("from", from.toString())
                .queryParam("to", to.toString()))
        .andExpect(status().isOk());
  }

  @Test
  @WithJwt("customer.json")
  void givenUserIsCardOwner_whenListCardPayments_thenOk() throws Exception {
    var card = CardFixtures.createCustomersCard(1000, 5000);
    var from = Instant.now().minus(1, ChronoUnit.DAYS);
    var to = Instant.now();
    when(cardRepo.findByNumber(card.getNumber())).thenReturn(Optional.of(card));
    when(accountsApi.getAccount(card.getIban().toMachineReadableString()))
        .thenReturn(
            ResponseEntity
                .ok(
                    accountOwnedBy(
                        CardFixtures.CUSTOMER_SUBJECT,
                        card.getIban().toMachineReadableString(),
                        "XPF")));
    when(paymentRepo.findByCardNumberAndTimestampBetween(card.getNumber(), from, to))
        .thenReturn(List.of());

    mockMvc
        .perform(
            get("https://localhost" + CardController.PAYMENT_LIST_PATH, card.getNumber())
                .queryParam("from", from.toString())
                .queryParam("to", to.toString()))
        .andExpect(status().isOk());
  }

  @Test
  @WithJwt("customer.json")
  void givenUserIsNotCardOwner_whenListCardPayments_thenForbidden() throws Exception {
    var card = CardFixtures.createSomeonesCard(1000, 5000);
    when(cardRepo.findByNumber(card.getNumber())).thenReturn(Optional.of(card));
    when(accountsApi.getAccount(card.getIban().toMachineReadableString()))
        .thenReturn(
            ResponseEntity
                .ok(
                    accountOwnedBy(
                        CardFixtures.SOMEONE_SUBJECT,
                        card.getIban().toMachineReadableString(),
                        "XPF")));

    mockMvc
        .perform(
            get("https://localhost" + CardController.PAYMENT_LIST_PATH, card.getNumber())
                .queryParam("from", Instant.now().minus(1, ChronoUnit.DAYS).toString())
                .queryParam("to", Instant.now().toString()))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithJwt("advisor.json")
  void givenMissingPeriodEnd_whenListCardPayments_thenBadRequest() throws Exception {
    var card = CardFixtures.createCustomersCard(1000, 5000);
    when(cardRepo.findByNumber(card.getNumber())).thenReturn(Optional.of(card));

    mockMvc
        .perform(
            get("https://localhost" + CardController.PAYMENT_LIST_PATH, card.getNumber())
                .queryParam("from", Instant.now().minus(100, ChronoUnit.DAYS).toString()))
        .andExpect(status().is4xxClientError());
  }

  @Test
  @WithJwt("advisor.json")
  void givenMissingPeriodStart_whenListCardPayments_thenBadRequest() throws Exception {
    var card = CardFixtures.createCustomersCard(1000, 5000);
    when(cardRepo.findByNumber(card.getNumber())).thenReturn(Optional.of(card));

    mockMvc
        .perform(
            get("https://localhost" + CardController.PAYMENT_LIST_PATH, card.getNumber())
                .queryParam("to", Instant.now().toString()))
        .andExpect(status().is4xxClientError());
  }

  @Test
  @WithJwt("advisor.json")
  void givenMissingPeriod_whenListCardPayments_thenBadRequest() throws Exception {
    var card = CardFixtures.createCustomersCard(1000, 5000);
    when(cardRepo.findByNumber(card.getNumber())).thenReturn(Optional.of(card));

    mockMvc
        .perform(get("https://localhost" + CardController.PAYMENT_LIST_PATH, card.getNumber()))
        .andExpect(status().is4xxClientError());
  }

  // ===================== createCardPayment =====================

  @Test
  @WithAnonymousUser
  void givenAnonymousUser_whenCreateCardPayment_thenUnauthorized() throws Exception {
    var dto =
        new CardPaymentCreationRequest("XPF", 100, "some-card-number", CardFixtures.SOMEONE_IBAN);

    mockMvc
        .perform(
            post("https://localhost" + CardController.PAYMENT_LIST_PATH, "some-card-number")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dto)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithJwt("customer.json")
  void givenUserIsCardOwnerAndDestinationCurrencyDiffers_whenCreateCardPayment_thenCreated()
      throws Exception {
    var card = CardFixtures.createCustomersCard(1000, 5000);
    var dto =
        new CardPaymentCreationRequest("XPF", 100, card.getNumber(), CardFixtures.SOMEONE_IBAN);

    when(cardRepo.findByNumber(card.getNumber())).thenReturn(Optional.of(card));
    when(accountsApi.getAccount(card.getIban().toMachineReadableString()))
        .thenReturn(
            ResponseEntity
                .ok(
                    accountOwnedBy(
                        CardFixtures.CUSTOMER_SUBJECT,
                        card.getIban().toMachineReadableString(),
                        "XPF")));
    when(accountsApi.getAccount(CardFixtures.SOMEONE_IBAN))
        .thenReturn(
            ResponseEntity
                .ok(
                    accountOwnedBy(
                        CardFixtures.SOMEONE_SUBJECT,
                        CardFixtures.SOMEONE_IBAN,
                        "XPF")));
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
        .thenReturn(ResponseEntity.accepted().build());

    var mvcResult = mockMvc
        .perform(
            post("https://localhost" + CardController.PAYMENT_LIST_PATH, card.getNumber())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dto)))
        .andExpect(status().isCreated())
        .andReturn();

    assertThat(mvcResult.getResponse().getHeader("Location")).isNotNull();
  }

  @Test
  @WithJwt("customer.json")
  void givenUserIsNotCardOwner_whenCreateCardPayment_thenForbidden() throws Exception {
    var card = CardFixtures.createSomeonesCard(1000, 5000);
    var dto =
        new CardPaymentCreationRequest("XPF", 100, card.getNumber(), CardFixtures.SOMEONE_IBAN);
    when(cardRepo.findByNumber(card.getNumber())).thenReturn(Optional.of(card));
    when(accountsApi.getAccount(card.getIban().toMachineReadableString()))
        .thenReturn(
            ResponseEntity
                .ok(
                    accountOwnedBy(
                        CardFixtures.SOMEONE_SUBJECT,
                        card.getIban().toMachineReadableString(),
                        "XPF")));

    mockMvc
        .perform(
            post("https://localhost" + CardController.PAYMENT_LIST_PATH, card.getNumber())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dto)))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithJwt("customer.json")
  void givenAmountExceedsTransactionCeiling_whenCreateCardPayment_thenConflict() throws Exception {
    var card = CardFixtures.createCustomersCard(100, 5000);
    var dto =
        new CardPaymentCreationRequest("XPF", 500, card.getNumber(), CardFixtures.SOMEONE_IBAN);
    when(cardRepo.findByNumber(card.getNumber())).thenReturn(Optional.of(card));
    when(accountsApi.getAccount(card.getIban().toMachineReadableString()))
        .thenReturn(
            ResponseEntity
                .ok(
                    accountOwnedBy(
                        CardFixtures.CUSTOMER_SUBJECT,
                        card.getIban().toMachineReadableString(),
                        "XPF")));
    when(accountsApi.getAccount(CardFixtures.SOMEONE_IBAN))
        .thenReturn(
            ResponseEntity
                .ok(
                    accountOwnedBy(
                        CardFixtures.SOMEONE_SUBJECT,
                        CardFixtures.SOMEONE_IBAN,
                        "XPF")));

    mockMvc
        .perform(
            post("https://localhost" + CardController.PAYMENT_LIST_PATH, card.getNumber())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dto)))
        .andExpect(status().isConflict());
  }

  @Test
  @WithJwt("customer.json")
  void givenAmountExceedsRolling30Ceiling_whenCreateCardPayment_thenConflict() throws Exception {
    var card = CardFixtures.createCustomersCard(1000, 500);
    var dto =
        new CardPaymentCreationRequest("XPF", 500, card.getNumber(), CardFixtures.SOMEONE_IBAN);
    when(cardRepo.findByNumber(card.getNumber())).thenReturn(Optional.of(card));
    when(accountsApi.getAccount(card.getIban().toMachineReadableString()))
        .thenReturn(
            ResponseEntity
                .ok(
                    accountOwnedBy(
                        CardFixtures.CUSTOMER_SUBJECT,
                        card.getIban().toMachineReadableString(),
                        "XPF")));
    when(accountsApi.getAccount(CardFixtures.SOMEONE_IBAN))
        .thenReturn(
            ResponseEntity
                .ok(
                    accountOwnedBy(
                        CardFixtures.SOMEONE_SUBJECT,
                        CardFixtures.SOMEONE_IBAN,
                        "XPF")));

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

    mockMvc
        .perform(
            post("https://localhost" + CardController.PAYMENT_LIST_PATH, card.getNumber())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dto)))
        .andExpect(status().isConflict());
  }

  @Test
  @WithJwt("customer.json")
  void givenInvalidPayload_whenCreateCardPayment_thenBadRequest() throws Exception {
    var card = CardFixtures.createCustomersCard(1000, 5000);
    when(cardRepo.findByNumber(card.getNumber())).thenReturn(Optional.of(card));
    when(accountsApi.getAccount(card.getIban().toMachineReadableString()))
        .thenReturn(
            ResponseEntity
                .ok(
                    accountOwnedBy(
                        CardFixtures.CUSTOMER_SUBJECT,
                        card.getIban().toMachineReadableString(),
                        "XPF")));

    // invalid (but non-empty) destination IBAN
    mockMvc
        .perform(
            post("https://localhost" + CardController.PAYMENT_LIST_PATH, card.getNumber())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json
                        .writeValueAsString(
                            new CardPaymentCreationRequest(
                                "XPF",
                                100,
                                card.getNumber(),
                                "not-an-iban"))))
        .andExpect(status().is4xxClientError());

    // non positive amount
    mockMvc
        .perform(
            post("https://localhost" + CardController.PAYMENT_LIST_PATH, card.getNumber())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json
                        .writeValueAsString(
                            new CardPaymentCreationRequest(
                                "XPF",
                                -1,
                                card.getNumber(),
                                CardFixtures.SOMEONE_IBAN))))
        .andExpect(status().is4xxClientError());
  }

}
