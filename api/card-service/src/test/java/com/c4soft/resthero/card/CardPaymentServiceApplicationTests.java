package com.c4soft.resthero.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import com.c4soft.resthero.account.api.AccountsApi;
import com.c4soft.resthero.account.api.MoneyTransfersApi;
import com.c4soft.resthero.account.model.AccountResponse;
import com.c4soft.resthero.card.jpa.CardPaymentRepository;
import com.c4soft.resthero.card.jpa.CardRepository;
import com.c4soft.resthero.card.web.CardController;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "server.ssl.enabled=false")
@ActiveProfiles("h2")
@Import({MockedOAuth2ClientTestConfiguration.class})
@AutoConfigureMockMvc
class CardPaymentServiceApplicationTests {

  @MockitoBean
  AccountsApi accountsApi;

  @MockitoBean
  MoneyTransfersApi transfersApi;

  @Autowired
  MockMvc mockMvc;

  @Autowired
  CardRepository cardRepo;

  @Autowired
  CardPaymentRepository paymentRepo;

  private static AccountResponse accountOwnedBy(String customerId, String iban, String currency) {
    return new AccountResponse().iban(iban).customerId(customerId).currency(currency).balance(0);
  }

  @Test
  void contextLoads() {}

  @Test
  @WithMockUser(username = CardFixtures.CUSTOMER_SUBJECT)
  void givenMoneyTransferFails_whenCreateCardPayment_thenPaymentIsPersistedAsNotAccepted()
      throws Exception {
    var card = cardRepo.save(CardFixtures.createCustomersCard(1000, 5000));

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
    when(transfersApi.transferMoneyBetweenAccounts(any()))
        .thenThrow(
            HttpClientErrorException
                .create(HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY, new byte[0], null));

    mockMvc
        .perform(
            post(CardController.PAYMENT_LIST_PATH, card.getNumber())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      \"currency\": \"XPF\",
                      \"amount\": 100,
                      \"cardNumber\": \"%s\",
                      \"destinationIban\": \"%s\"
                    }
                    """.formatted(card.getNumber(), CardFixtures.SOMEONE_IBAN)))
        .andExpect(status().is5xxServerError());

    assertThat(paymentRepo.findAll()).singleElement().satisfies(payment -> {
      assertThat(payment.getCard().getNumber()).isEqualTo(card.getNumber());
      assertThat(payment.getDestinationIban().toMachineReadableString())
          .isEqualTo(CardFixtures.SOMEONE_IBAN);
      assertThat(payment.getAmount().getDigits()).isEqualTo(100L);
      assertThat(payment.isAccepted()).isFalse();
    });
  }

}
