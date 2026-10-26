package com.c4soft.resthero.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithJwt;
import com.c4_soft.springaddons.security.oauth2.test.webmvc.AutoConfigureAddonsWebmvcResourceServerSecurity;
import com.c4_soft.springaddons.security.oauth2.test.webmvc.MockMvcSupport;
import com.c4soft.resthero.account.AccountFixtures;
import com.c4soft.resthero.account.MoneyTransferFixtures;
import com.c4soft.resthero.account.SecurityConfig;
import com.c4soft.resthero.account.WebConfiguration;
import com.c4soft.resthero.account.domain.Account;
import com.c4soft.resthero.account.domain.MoneyTransfer;
import com.c4soft.resthero.account.jpa.AccountRepository;
import com.c4soft.resthero.account.jpa.MoneyTransferRepository;
import com.c4soft.resthero.api.CurrenciesApi;
import com.c4soft.resthero.commons.domain.Amount;
import com.c4soft.resthero.commons.domain.Currency;
import com.c4soft.resthero.commons.domain.Iban;
import com.c4soft.resthero.commons.domain.IbanStringMapper;
import com.c4soft.resthero.commons.events.DomainEvent;
import com.c4soft.resthero.problem.ProblemType;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = MoneyTransferController.class, properties = "server.ssl.enabled=false")
@Import({IbanStringMapper.class, MoneyTransferMapperImpl.class,
    WebConfiguration.class, SecurityConfig.class})
@AutoConfigureAddonsWebmvcResourceServerSecurity
class MoneyTransferControllerTest {

  @MockitoBean
  AccountRepository accountRepo;

  @MockitoBean
  MoneyTransferRepository transferRepo;

  @MockitoBean
  CurrenciesApi currenciesApi;

  @MockitoBean
  RabbitTemplate rabbitTemplate;

  @MockitoBean
  TopicExchange eventsExchange;

  @Autowired
  MockMvcSupport api;

  @Autowired
  ObjectMapper json;

  /** An account this service does not manage: nobody owns it here. */
  private static final String ANOTHER_BANKS_IBAN = "FR762222333344445555";

  private final Account source = AccountFixtures.createCustomersXpfAccount(100000);

  private final Account destination = AccountFixtures.createSomeonesXpfAccount(50000);

  private MoneyTransferRequest transferRequest(String sourceIban, String destinationIban,
      Integer amount, String currency, String label) {
    return new MoneyTransferRequest(sourceIban, destinationIban, amount, currency, label);
  }

  private MoneyTransferRequest validTransferRequest() {
    return transferRequest(
        source.getIban().toMachineReadableString(),
        destination.getIban().toMachineReadableString(),
        1000,
        "XPF",
        "some label");
  }

  /** The repositories persist a transfer with the ID 42, the currency service changes nothing. */
  private void givenTransfersArePersisted() {
    when(transferRepo.save(any(MoneyTransfer.class))).thenAnswer(invocation -> {
      final var transfer = spy(invocation.getArgument(0, MoneyTransfer.class));
      when(transfer.getId()).thenReturn(42L);
      return transfer;
    });
    when(currenciesApi.change(anyInt(), anyString(), anyString()))
        .thenAnswer(i -> ResponseEntity.ok(i.getArgument(0, Integer.class)));
  }

  private void assertValidationProblemOn(MoneyTransferRequest dto, String field) throws Exception {
    api
        .post(dto, MoneyTransferController.BASE_PATH)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.type").value(ProblemType.VALIDATION.uri().toString()))
        .andExpect(jsonPath("$.invalidFields." + field).isString());
    verify(transferRepo, never()).save(any(MoneyTransfer.class));
  }

  // ===================== listMoneyTransfers =====================

  @Test
  @WithAnonymousUser
  void givenAnonymousUser_whenListTransfers_thenUnauthorized() throws Exception {
    api.get(MoneyTransferController.BASE_PATH).andExpect(status().isUnauthorized());
  }

  @Test
  @WithJwt("advisor.json")
  void givenUserIsAdvisor_whenListTransfers_thenOk() throws Exception {
    MoneyTransfer transfer = MoneyTransferFixtures.createMoneyTransfer(source, destination, 1000);
    when(transferRepo.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(transfer)));

    var content = api
        .get(
            MoneyTransferController.BASE_PATH + "?sourceIban={source}&destinationIban={destination}",
            source.getIban().toMachineReadableString(),
            destination.getIban().toMachineReadableString())
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();

    assertThat(content).contains(destination.getIban().toMachineReadableString());
  }

  @Test
  @WithJwt("customer.json")
  void givenUserIsCustomer_whenListTransfersOfSomeoneElsesAccount_thenForbidden()
      throws Exception {
    when(accountRepo.findByIban(destination.getIban())).thenReturn(Optional.of(destination));

    api
        .get(
            MoneyTransferController.BASE_PATH + "?sourceIban={source}",
            destination.getIban().toMachineReadableString())
        .andExpect(status().isForbidden());
  }

  @Test
  @WithJwt("advisor.json")
  void givenATooShortLabelFilter_whenListTransfers_thenValidationProblem() throws Exception {
    api
        .get(MoneyTransferController.BASE_PATH + "?labelContaining={label}", "ab")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.type").value(ProblemType.VALIDATION.uri().toString()))
        .andExpect(jsonPath("$.invalidFields.labelContaining").isString());
  }

  // ===================== transferMoneyBetweenAccounts =====================

  @Test
  @WithAnonymousUser
  void givenAnonymousUser_whenTransferMoney_thenUnauthorized() throws Exception {
    api.post(validTransferRequest(), MoneyTransferController.BASE_PATH)
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithJwt("customer.json")
  void givenUserOwnsNeitherAccount_whenTransferMoney_thenForbidden() throws Exception {
    when(accountRepo.findByIban(destination.getIban())).thenReturn(Optional.of(destination));

    api
        .post(
            transferRequest(
                destination.getIban().toMachineReadableString(),
                ANOTHER_BANKS_IBAN,
                1000,
                "XPF",
                "some label"),
            MoneyTransferController.BASE_PATH)
        .andExpect(status().isForbidden());
    verify(transferRepo, never()).save(any(MoneyTransfer.class));
  }

  @Test
  @WithJwt("card-service.json")
  void givenServiceHasTransferAuthority_whenTransferMoney_thenCreated() throws Exception {
    when(accountRepo.findByIban(source.getIban())).thenReturn(Optional.of(source));
    when(accountRepo.findByIban(destination.getIban())).thenReturn(Optional.of(destination));
    givenTransfersArePersisted();

    api
        .post(validTransferRequest(), MoneyTransferController.BASE_PATH)
        .andExpect(status().isCreated())
        .andExpect(header().string(HttpHeaders.LOCATION, MoneyTransferController.BASE_PATH + "/42"));

    verify(rabbitTemplate, times(2))
        .convertAndSend(any(), eq("account.updated"), any(DomainEvent.class));
  }

  /** This service ignores other banks: an account it does not manage is neither debited nor credited. */
  @Test
  @WithJwt("card-service.json")
  void givenUnknownSource_whenTransferMoney_thenCreated() throws Exception {
    when(accountRepo.findByIban(source.getIban())).thenReturn(Optional.empty());
    when(accountRepo.findByIban(destination.getIban())).thenReturn(Optional.of(destination));
    givenTransfersArePersisted();

    api.post(validTransferRequest(), MoneyTransferController.BASE_PATH)
        .andExpect(status().isCreated());

    verify(rabbitTemplate, times(1))
        .convertAndSend(any(), eq("account.updated"), any(DomainEvent.class));
  }

  @Test
  @WithJwt("card-service.json")
  void givenUnknownDestination_whenTransferMoney_thenCreated() throws Exception {
    when(accountRepo.findByIban(source.getIban())).thenReturn(Optional.of(source));
    when(accountRepo.findByIban(destination.getIban())).thenReturn(Optional.empty());
    givenTransfersArePersisted();

    api.post(validTransferRequest(), MoneyTransferController.BASE_PATH)
        .andExpect(status().isCreated());

    verify(rabbitTemplate, times(1))
        .convertAndSend(any(), eq("account.updated"), any(DomainEvent.class));
  }

  @Test
  @WithJwt("card-service.json")
  void givenAMalformedSourceIban_whenTransferMoney_thenValidationProblem() throws Exception {
    var valid = validTransferRequest();
    assertValidationProblemOn(
        transferRequest("not-an-iban", valid.destinationIban(), valid.amount(), valid.currency(),
            valid.label()),
        "sourceIban");
  }

  @Test
  @WithJwt("card-service.json")
  void givenAMalformedDestinationIban_whenTransferMoney_thenValidationProblem() throws Exception {
    var valid = validTransferRequest();
    assertValidationProblemOn(
        transferRequest(valid.sourceIban(), "not-an-iban", valid.amount(), valid.currency(),
            valid.label()),
        "destinationIban");
  }

  @Test
  @WithJwt("card-service.json")
  void givenANegativeAmount_whenTransferMoney_thenValidationProblem() throws Exception {
    var valid = validTransferRequest();
    assertValidationProblemOn(
        transferRequest(valid.sourceIban(), valid.destinationIban(), -1, valid.currency(),
            valid.label()),
        "amount");
  }

  @Test
  @WithJwt("card-service.json")
  void givenAnUnsupportedCurrency_whenTransferMoney_thenValidationProblem() throws Exception {
    var valid = validTransferRequest();
    assertValidationProblemOn(
        transferRequest(valid.sourceIban(), valid.destinationIban(), valid.amount(), "XPFX",
            valid.label()),
        "currency");
  }

  @Test
  @WithJwt("card-service.json")
  void givenATooShortLabel_whenTransferMoney_thenValidationProblem() throws Exception {
    var valid = validTransferRequest();
    assertValidationProblemOn(
        transferRequest(valid.sourceIban(), valid.destinationIban(), valid.amount(),
            valid.currency(), "la"),
        "label");
  }

  // ===================== getMoneyTransfer =====================

  @Test
  @WithJwt("advisor.json")
  void givenUserIsAdvisor_whenGetTransfer_thenOk() throws Exception {
    MoneyTransfer transfer = MoneyTransferFixtures.createMoneyTransfer(source, destination, 1000);
    when(transferRepo.findById(1L)).thenReturn(Optional.of(transfer));

    var actual = json
        .readValue(
            api
                .get(MoneyTransferController.TRANSFER_PATH, "1")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            MoneyTransferResponse.class);

    assertThat(actual.label()).contains("Test transfer of");
  }

  @Test
  @WithJwt("customer.json")
  void givenUserIsCustomer_whenGetSomeoneElsesTransfer_thenForbidden() throws Exception {
    when(transferRepo.findById(2L))
        .thenReturn(
            Optional
                .of(
                    MoneyTransfer
                        .of(
                            destination.getIban(),
                            Iban.of(ANOTHER_BANKS_IBAN),
                            new Amount(1000, Currency.XPF),
                            "rent")));
    when(accountRepo.findByIban(destination.getIban())).thenReturn(Optional.of(destination));

    api.get(MoneyTransferController.TRANSFER_PATH, 2L).andExpect(status().isForbidden());
  }

  @Test
  @WithJwt("advisor.json")
  void givenUnknownTransferId_whenGetTransfer_thenMoneyTransferNotFound() throws Exception {
    when(transferRepo.findById(999L)).thenReturn(Optional.empty());

    api
        .get(MoneyTransferController.TRANSFER_PATH, 999L)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(ProblemType.MONEY_TRANSFER_NOT_FOUND.uri().toString()))
        .andExpect(jsonPath("$.parameters.transferId").value("999"));
  }

  /** An ID that is no number names no transfer: the same answer as an unknown one. */
  @Test
  @WithJwt("advisor.json")
  void givenATransferIdThatIsNoNumber_whenGetTransfer_thenMoneyTransferNotFound()
      throws Exception {
    api
        .get(MoneyTransferController.TRANSFER_PATH, "abc")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(ProblemType.MONEY_TRANSFER_NOT_FOUND.uri().toString()))
        .andExpect(jsonPath("$.parameters.transferId").value("abc"));
    verify(transferRepo, never()).findById(any());
  }

}
