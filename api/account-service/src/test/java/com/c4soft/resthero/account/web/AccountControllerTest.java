package com.c4soft.resthero.account.web;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithJwt;
import com.c4_soft.springaddons.security.oauth2.test.webmvc.AutoConfigureAddonsWebmvcResourceServerSecurity;
import com.c4_soft.springaddons.security.oauth2.test.webmvc.MockMvcSupport;
import com.c4soft.resthero.account.AccountFixtures;
import com.c4soft.resthero.account.SecurityConfig;
import com.c4soft.resthero.account.WebConfiguration;
import com.c4soft.resthero.account.domain.Account;
import com.c4soft.resthero.account.jpa.AccountRepository;
import com.c4soft.resthero.account.jpa.MoneyTransferRepository;
import com.c4soft.resthero.api.CustomersApi;
import com.c4soft.resthero.commons.domain.Iban;
import com.c4soft.resthero.commons.domain.IbanStringMapper;
import com.c4soft.resthero.problem.ProblemType;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = AccountController.class, properties = "server.ssl.enabled=false")
@Import({
    // LAB:2.6:REMOVE:START
    IbanStringMapper.class,
    // LAB:2.6:REMOVE:END
    AccountMapperImpl.class, MoneyTransferMapperImpl.class,
    WebConfiguration.class, SecurityConfig.class})
@AutoConfigureAddonsWebmvcResourceServerSecurity
class AccountControllerTest {

  @MockitoBean
  AccountRepository accountRepo;

  @MockitoBean
  MoneyTransferRepository transferRepo;

  // LAB:2.1:REMOVE:START
  @MockitoBean
  // LAB:2.1:REMOVE:END
  CustomersApi customersApi;

  @MockitoBean
  RabbitTemplate rabbitTemplate;

  @MockitoBean
  TopicExchange eventsExchange;

  @Autowired
  MockMvcSupport api;

  @Autowired
  ObjectMapper json;

  private static final String LIST_PATH = AccountController.BASE_PATH + "?customerId={customerId}";

  private static AccountCreationRequest creationRequest(String iban, String customerId,
      String currency) {
    return new AccountCreationRequest(iban, customerId, currency);
  }

  private static AccountCreationRequest validCreationRequest() {
    return creationRequest(
        AccountFixtures.createCustomersXpfAccount(100000).getIban().toMachineReadableString(),
        AccountFixtures.CUSTOMER_SUBJECT,
        "XPF");
  }

  // ===================== listAccounts =====================

  @Test
  @WithAnonymousUser
  void givenAnonymousUser_whenListAccounts_thenUnauthorized() throws Exception {
    api.get(LIST_PATH, "1234").andExpect(status().isUnauthorized());
  }

  @Test
  @WithJwt("advisor.json")
  void givenUserIsAdvisor_whenListAccountsForKnownCustomerId_thenMatchingAccountsReturned()
      throws Exception {
    List<Account> accountList = List
        .of(
            AccountFixtures.createCustomersXpfAccount(100000),
            AccountFixtures.createCustomersEurAccount(200000));
    when(accountRepo.findByCustomerId(AccountFixtures.CUSTOMER_SUBJECT)).thenReturn(accountList);

    List<AccountResponse> actual = json
        .readValue(
            api
                .get(LIST_PATH, AccountFixtures.CUSTOMER_SUBJECT)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            new TypeReference<>() {});

    assertThat(actual).hasSize(accountList.size());
    assertThat(actual).allMatch(dto -> AccountFixtures.CUSTOMER_SUBJECT.equals(dto.customerId()));
    verify(accountRepo).findByCustomerId(AccountFixtures.CUSTOMER_SUBJECT);
  }

  @Test
  @WithJwt("customer.json")
  void givenUserIsACustomer_whenListAccountsForHimself_thenMatchingAccountsReturned()
      throws Exception {
    List<Account> accountList = List
        .of(
            AccountFixtures.createCustomersXpfAccount(100000),
            AccountFixtures.createCustomersEurAccount(200000));
    when(accountRepo.findByCustomerId(AccountFixtures.CUSTOMER_SUBJECT)).thenReturn(accountList);

    api
        .get(LIST_PATH, AccountFixtures.CUSTOMER_SUBJECT)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(accountList.size()));
  }

  @Test
  @WithJwt("customer.json")
  void givenUserIsACustomer_whenListSomeoneElseAccounts_thenForbidden() throws Exception {
    api.get(LIST_PATH, AccountFixtures.SOMEONE_SUBJECT).andExpect(status().isForbidden());
    verify(accountRepo, never()).findByCustomerId(any());
  }

  /** A missing query parameter is rejected by the framework, before any validation. */
  @Test
  @WithJwt("advisor.json")
  void givenNoCustomerId_whenListAccounts_thenInvalidRequest() throws Exception {
    api
        .get(AccountController.BASE_PATH)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(ProblemType.INVALID_REQUEST.uri().toString()));
  }

  @Test
  @WithJwt("advisor.json")
  void givenAnEmptyCustomerId_whenListAccounts_thenValidationProblem() throws Exception {
    api
        .get(LIST_PATH, "")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.type").value(ProblemType.VALIDATION.uri().toString()))
        .andExpect(jsonPath("$.invalidFields.customerId").isString());
  }

  // ===================== getAccount =====================

  @Test
  @WithJwt("advisor.json")
  void givenUserIsAdvisor_whenGetAccountWithAKnownIban_thenOk() throws Exception {
    var account = AccountFixtures.createCustomersXpfAccount(100000);
    when(accountRepo.findStoredByIban(account.getIban())).thenReturn(Optional.of(account));

    var actual = json
        .readValue(
            api
                .get(AccountController.ACCOUNT_PATH, account.getIban().toMachineReadableString())
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            AccountResponse.class);

    assertThat(actual.customerId()).isEqualTo(AccountFixtures.CUSTOMER_SUBJECT);
  }

  @Test
  @WithJwt("customer.json")
  void givenUserIsCustomer_whenGetAccountWithOneOfHisAccountsIban_thenOk() throws Exception {
    var account = AccountFixtures.createCustomersXpfAccount(100000);
    when(accountRepo.findStoredByIban(account.getIban())).thenReturn(Optional.of(account));

    var actual = json
        .readValue(
            api
                .get(AccountController.ACCOUNT_PATH, account.getIban().toMachineReadableString())
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            AccountResponse.class);

    assertThat(Iban.of(actual.iban())).isEqualTo(account.getIban());
  }

  @Test
  @WithJwt("customer.json")
  void givenUserIsCustomer_whenGetAccountWithSomeoneElsesAccountsIban_thenForbidden()
      throws Exception {
    var account = AccountFixtures.createSomeonesXpfAccount(100000);
    when(accountRepo.findStoredByIban(account.getIban())).thenReturn(Optional.of(account));

    api
        .get(AccountController.ACCOUNT_PATH, account.getIban().toMachineReadableString())
        .andExpect(status().isForbidden());
  }

  @Test
  @WithJwt("advisor.json")
  void givenAnExternalIban_whenGetAccount_thenAccountNotFound() throws Exception {
    var iban = AccountFixtures.createCustomersXpfAccount(100000).getIban();
    when(accountRepo.findStoredByIban(any(Iban.class))).thenReturn(Optional.empty());

    api
        .get(AccountController.ACCOUNT_PATH, iban.toMachineReadableString())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(ProblemType.ACCOUNT_NOT_FOUND.uri().toString()))
        .andExpect(jsonPath("$.parameters.iban").value(iban.toHumanReadableString()));
  }

  /** The path variable is no IBAN: its conversion fails before the controller is reached. */
  @Test
  @WithJwt("advisor.json")
  void givenAMalformedIban_whenGetAccount_thenInvalidRequest() throws Exception {
    api
        .get(AccountController.ACCOUNT_PATH, "not-an-iban")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(ProblemType.INVALID_REQUEST.uri().toString()));
    verify(accountRepo, never()).findStoredByIban(any());
  }

  // ===================== createAccount =====================

  @Test
  @WithAnonymousUser
  void givenAnonymousUser_whenCreateAccount_thenUnauthorized() throws Exception {
    api.post(validCreationRequest(), AccountController.BASE_PATH)
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithJwt("customer.json")
  void givenUserDoesNotHaveAccountCreateAuthority_whenCreateAccount_thenForbidden()
      throws Exception {
    api.post(validCreationRequest(), AccountController.BASE_PATH)
        .andExpect(status().isForbidden());
    verify(accountRepo, never()).save(any(Account.class));
  }

  @Test
  @WithJwt("advisor.json")
  void givenUserHasAccountCreateAuthority_whenCreateAccountWithValidBody_thenCreated()
      throws Exception {
    var dto = validCreationRequest();
    when(accountRepo.existsByIban(any(Iban.class))).thenReturn(false);
    when(customersApi.getCustomer(AccountFixtures.CUSTOMER_SUBJECT))
        .thenReturn(ResponseEntity.ok(null));
    when(accountRepo.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

    api
        .post(dto, AccountController.BASE_PATH)
        .andExpect(status().isCreated())
        .andExpect(
            header()
                .string(
                    HttpHeaders.LOCATION,
                    AccountController.ACCOUNT_PATH
                        .replace(
                            "{%s}".formatted(AccountController.ACCOUNT_PLACEHOLDER),
                            dto.iban())));
  }

  @Test
  @WithJwt("advisor.json")
  void givenAnAccountWithThisIbanExists_whenCreateAccount_thenAccountAlreadyManaged()
      throws Exception {
    var iban = AccountFixtures.createCustomersXpfAccount(100000).getIban();
    when(accountRepo.existsByIban(iban)).thenReturn(true);

    api
        .post(validCreationRequest(), AccountController.BASE_PATH)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(ProblemType.ACCOUNT_ALREADY_MANAGED.uri().toString()))
        .andExpect(jsonPath("$.parameters.iban").value(iban.toHumanReadableString()));
    verify(accountRepo, never()).save(any(Account.class));
  }

  @Test
  @WithJwt("advisor.json")
  void givenTheCustomerServiceDoesNotKnowTheCustomer_whenCreateAccount_thenCustomerNotFound()
      throws Exception {
    when(accountRepo.existsByIban(any(Iban.class))).thenReturn(false);
    when(customersApi.getCustomer(AccountFixtures.CUSTOMER_SUBJECT))
        .thenThrow(
            HttpClientErrorException
                .create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], UTF_8));

    api
        .post(validCreationRequest(), AccountController.BASE_PATH)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value(ProblemType.CUSTOMER_NOT_FOUND.uri().toString()))
        .andExpect(jsonPath("$.parameters.customerId").value(AccountFixtures.CUSTOMER_SUBJECT));
    verify(accountRepo, never()).save(any(Account.class));
  }

  @Test
  @WithJwt("advisor.json")
  void givenTheCustomerServiceFails_whenCreateAccount_thenDownstreamServiceErrorWithoutItsAnswer()
      throws Exception {
    when(accountRepo.existsByIban(any(Iban.class))).thenReturn(false);
    when(customersApi.getCustomer(AccountFixtures.CUSTOMER_SUBJECT))
        .thenThrow(
            HttpServerErrorException
                .create(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Internal Server Error",
                    HttpHeaders.EMPTY,
                    "the customer-service's own stack trace".getBytes(UTF_8),
                    UTF_8));

    api
        .post(validCreationRequest(), AccountController.BASE_PATH)
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.type").value(ProblemType.DOWNSTREAM_SERVICE_ERROR.uri().toString()))
        .andExpect(jsonPath("$.parameters.service").value("customer-service"))
        .andExpect(content().string(not(containsString("stack trace"))));
    verify(accountRepo, never()).save(any(Account.class));
  }

  @Test
  @WithJwt("advisor.json")
  void givenNoCustomerId_whenCreateAccount_thenValidationProblem() throws Exception {
    var valid = validCreationRequest();

    api
        .post(creationRequest(valid.iban(), null, valid.currency()), AccountController.BASE_PATH)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.type").value(ProblemType.VALIDATION.uri().toString()))
        .andExpect(jsonPath("$.invalidFields.customerId").isString());
  }

  @Test
  @WithJwt("advisor.json")
  void givenAnUnsupportedCurrency_whenCreateAccount_thenValidationProblem() throws Exception {
    var valid = validCreationRequest();

    api
        .post(creationRequest(valid.iban(), valid.customerId(), "XX"), AccountController.BASE_PATH)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.invalidFields.currency").isString());
  }

  @Test
  @WithJwt("advisor.json")
  void givenAMalformedIban_whenCreateAccount_thenValidationProblem() throws Exception {
    var valid = validCreationRequest();

    api
        .post(
            creationRequest("not-an-iban", valid.customerId(), valid.currency()),
            AccountController.BASE_PATH)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.invalidFields.iban").isString());
  }

  /** A body that is no JSON is rejected by the framework, before any validation. */
  @Test
  @WithJwt("advisor.json")
  void givenABodyThatIsNoJson_whenCreateAccount_thenInvalidRequest() throws Exception {
    api
        .perform(
            MockMvcRequestBuilders
                .post(AccountController.BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"iban\": "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(ProblemType.INVALID_REQUEST.uri().toString()));
  }

}
