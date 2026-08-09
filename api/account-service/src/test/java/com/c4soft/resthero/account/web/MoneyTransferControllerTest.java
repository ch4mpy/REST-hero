package com.c4soft.resthero.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithJwt;
import com.c4_soft.springaddons.security.oauth2.test.webmvc.AutoConfigureAddonsWebmvcResourceServerSecurity;
import com.c4soft.resthero.account.AccountFixtures;
import com.c4soft.resthero.account.MoneyTransferFixtures;
import com.c4soft.resthero.account.SecurityConfig;
import com.c4soft.resthero.account.SpringDataWebConvertersTestConfiguration;
import com.c4soft.resthero.account.domain.Account;
import com.c4soft.resthero.account.domain.MoneyTransfer;
import com.c4soft.resthero.account.jpa.AccountRepository;
import com.c4soft.resthero.account.jpa.MoneyTransferRepository;
import com.c4soft.resthero.api.CurrenciesApi;
import com.c4soft.resthero.commons.domain.Iban;
import com.c4soft.resthero.commons.domain.IbanStringMapper;
import com.c4soft.resthero.commons.events.DomainEvent;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = MoneyTransferController.class, properties = {})
@Import({IbanStringMapper.class, MoneyTransferMapperImpl.class,
    SpringDataWebConvertersTestConfiguration.class, SecurityConfig.class})
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
  MockMvc mockMvc;

  @Autowired
  ObjectMapper json;

  @Test
  @WithAnonymousUser
  void givenAnonymousUser_whenListTransfers_thenUnauthorized() throws Exception {
    mockMvc
        .perform(get("https://localhost" + MoneyTransferController.BASE_PATH))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithJwt("advisor.json")
  void givenUserIsAdvisor_whenListTransfers_thenOk() throws Exception {
    Account source = AccountFixtures.createCustomersXpfAccount(100000);
    Account destination = AccountFixtures.createSomeonesXpfAccount(200000);

    MoneyTransfer transfer = MoneyTransferFixtures.createMoneyTransfer(source, destination, 1000);

    when(transferRepo.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(transfer)));

    var mvcResult = mockMvc
        .perform(
            get("https://localhost" + MoneyTransferController.BASE_PATH)
                .param("sourceIban", source.getIban().toMachineReadableString())
                .param("destinationIban", destination.getIban().toMachineReadableString()))
        .andExpect(status().isOk())
        .andReturn();

    var content = mvcResult.getResponse().getContentAsString();
    // basic sanity checks on returned payload
    assertThat(content).contains(destination.getIban().toMachineReadableString());
  }

  @Test
  @WithJwt("advisor.json")
  void givenInvalidTransferFilter_whenListTransfers_thenBadRequest() throws Exception {
    mockMvc
        .perform(
            get("https://localhost" + MoneyTransferController.BASE_PATH)
                .param("labelContaining", "ab"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithAnonymousUser
  void givenAnonymousUser_whenTransferMoney_thenUnauthorized() throws Exception {
    Account s1 = AccountFixtures.createCustomersXpfAccount(100000);
    Account d1 = AccountFixtures.createSomeonesXpfAccount(50000);

    var dto = new MoneyTransferRequest(
        s1.getIban().toMachineReadableString(),
        d1.getIban().toMachineReadableString(),
        1000,
        s1.getBalance().getCurrency().name(),
        "label");

    mockMvc
        .perform(
            post("https://localhost" + MoneyTransferController.BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dto)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithJwt("card-service.json")
  void givenServiceHasTransferAuthority_whenTransferMoney_thenCreated() throws Exception {
    Account source = AccountFixtures.createCustomersXpfAccount(100000);
    Account destination = AccountFixtures.createSomeonesXpfAccount(50000);

    var dto = new MoneyTransferRequest(
        source.getIban().toMachineReadableString(),
        destination.getIban().toMachineReadableString(),
        1000,
        "XPF",
        "some label");

    when(accountRepo.findByIban(source.getIban())).thenReturn(Optional.of(source));
    when(accountRepo.findByIban(destination.getIban())).thenReturn(Optional.of(destination));
    when(transferRepo.save(any(MoneyTransfer.class))).thenAnswer(invocation -> {
      final var transfer = spy(invocation.getArgument(0, MoneyTransfer.class));
      when(transfer.getId()).thenReturn(42L);
      return transfer;
    });
    when(currenciesApi.change(anyInt(), anyString(), anyString()))
        .thenAnswer(i -> ResponseEntity.ok(i.getArgument(0, Integer.class)));

    mockMvc
        .perform(
            post("https://localhost" + MoneyTransferController.BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dto)))
        .andExpect(status().isCreated());

    verify(rabbitTemplate, times(2))
        .convertAndSend(any(), eq("account.updated"), any(DomainEvent.class));
  }

  @Test
  @WithJwt("card-service.json")
  void givenUnknownSource_whenTransferMoney_thenCreated() throws Exception {
    Account source = AccountFixtures.createCustomersXpfAccount(100000);
    Account destination = AccountFixtures.createSomeonesXpfAccount(50000);

    var dto = new MoneyTransferRequest(
        source.getIban().toMachineReadableString(),
        destination.getIban().toMachineReadableString(),
        1000,
        "XPF",
        "label");

    when(accountRepo.findByIban(source.getIban())).thenReturn(Optional.empty());
    when(accountRepo.findByIban(destination.getIban())).thenReturn(Optional.of(destination));
    when(transferRepo.save(any(MoneyTransfer.class))).thenAnswer(invocation -> {
      final var transfer = spy(invocation.getArgument(0, MoneyTransfer.class));
      when(transfer.getId()).thenReturn(42L);
      return transfer;
    });
    when(currenciesApi.change(anyInt(), anyString(), anyString()))
        .thenAnswer(i -> ResponseEntity.ok(i.getArgument(0, Integer.class)));

    mockMvc
        .perform(
            post("https://localhost" + MoneyTransferController.BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dto)))
        .andExpect(status().isCreated());
  }

  @Test
  @WithJwt("card-service.json")
  void givenUnknownDestination_whenTransferMoney_thenCreated() throws Exception {
    Account source = AccountFixtures.createCustomersXpfAccount(100000);
    Iban destinationIban = AccountFixtures.createSomeonesXpfAccount(50000).getIban();

    var dto = new MoneyTransferRequest(
        source.getIban().toMachineReadableString(),
        destinationIban.toMachineReadableString(),
        1000,
        "XPF",
        "label");

    when(accountRepo.findByIban(source.getIban())).thenReturn(Optional.of(source));
    when(accountRepo.findByIban(destinationIban)).thenReturn(Optional.empty());
    when(transferRepo.save(any(MoneyTransfer.class))).thenAnswer(invocation -> {
      final var transfer = spy(invocation.getArgument(0, MoneyTransfer.class));
      when(transfer.getId()).thenReturn(42L);
      return transfer;
    });
    when(currenciesApi.change(anyInt(), anyString(), anyString()))
        .thenAnswer(i -> ResponseEntity.ok(i.getArgument(0, Integer.class)));

    mockMvc
        .perform(
            post("https://localhost" + MoneyTransferController.BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dto)))
        .andExpect(status().isCreated());
  }

  @SuppressWarnings("null")
  @Test
  @WithJwt("card-service.json")
  void givenInvalidTransferRequest_whenTransferMoney_thenBadRequest() throws Exception {
    Account source = AccountFixtures.createCustomersXpfAccount(100000);
    Account destination = AccountFixtures.createCustomersXpfAccount(50000);

    when(accountRepo.findByIban(source.getIban())).thenReturn(Optional.of(source));
    when(accountRepo.findByIban(destination.getIban())).thenReturn(Optional.of(destination));

    mockMvc
        .perform(
            post("https://localhost" + MoneyTransferController.BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json
                        .writeValueAsString(
                            new MoneyTransferRequest(
                                "not-an-iban",
                                destination.getIban().toMachineReadableString(),
                                1000,
                                "XPF",
                                "label"))))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("https://localhost" + MoneyTransferController.BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json
                        .writeValueAsString(
                            new MoneyTransferRequest(
                                source.getIban().toMachineReadableString(),
                                "not-an-iban",
                                1000,
                                "XPF",
                                "label"))))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("https://localhost" + MoneyTransferController.BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json
                        .writeValueAsString(
                            new MoneyTransferRequest(
                                source.getIban().toMachineReadableString(),
                                destination.getIban().toMachineReadableString(),
                                -1,
                                "XPF",
                                "label"))))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("https://localhost" + MoneyTransferController.BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json
                        .writeValueAsString(
                            new MoneyTransferRequest(
                                source.getIban().toMachineReadableString(),
                                destination.getIban().toMachineReadableString(),
                                1000,
                                "XPFX",
                                "label"))))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("https://localhost" + MoneyTransferController.BASE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json
                        .writeValueAsString(
                            new MoneyTransferRequest(
                                source.getIban().toMachineReadableString(),
                                destination.getIban().toMachineReadableString(),
                                1000,
                                "XPF",
                                "la"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithJwt("advisor.json")
  void givenUserIsAdvisor_whenGetTransfer_thenOk() throws Exception {
    Account source3 = AccountFixtures.createCustomersXpfAccount(100000);
    Account destination3 = AccountFixtures.createSomeonesXpfAccount(50000);

    MoneyTransfer transfer2 =
        MoneyTransferFixtures.createMoneyTransfer(source3, destination3, 1000);

    when(transferRepo.findById(1L)).thenReturn(Optional.of(transfer2));

    var actual = json
        .readValue(
            mockMvc
                .perform(get("https://localhost" + MoneyTransferController.TRANSFER_PATH, "1"))
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
    var moneyTransferId = 2L;

    when(transferRepo.findById(moneyTransferId)).thenReturn(Optional.of(mock(MoneyTransfer.class)));

    mockMvc
        .perform(get("https://localhost" + MoneyTransferController.TRANSFER_PATH, moneyTransferId))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithJwt("advisor.json")
  void givenUnknownTransferId_whenGetTransfer_thenNotFound() throws Exception {
    when(transferRepo.findById(999L)).thenReturn(Optional.empty());

    mockMvc
        .perform(get("https://localhost" + MoneyTransferController.TRANSFER_PATH, 999L))
        .andExpect(status().isNotFound());
  }

}
