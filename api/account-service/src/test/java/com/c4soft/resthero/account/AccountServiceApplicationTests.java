package com.c4soft.resthero.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithJwt;
import com.c4soft.resthero.account.jpa.AccountRepository;
import com.c4soft.resthero.account.web.AccountController;
import com.c4soft.resthero.account.web.AccountResponse;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "server.ssl.enabled=false")
@ActiveProfiles("h2")
@Import(MockedOAuth2ClientTestConfiguration.class)
@AutoConfigureMockMvc
class AccountServiceApplicationTests {

  @Autowired
  MockMvc mockMvc;

  @Autowired
  ObjectMapper json;

  @Autowired
  AccountRepository accountRepo;

  @Test
  void contextLoads() {}

  @Test
  @WithJwt("advisor.json")
  void givenUserIsAdvisor_whenGetAccountWithAKnownIban_thenOk() throws Exception {
    var account = accountRepo.save(AccountFixtures.createCustomersXpfAccount(100000));

    var actual = json
        .readValue(
            mockMvc
                .perform(
                    get(
                        "https://localhost" + AccountController.ACCOUNT_PATH,
                        account.getIban().toMachineReadableString()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            AccountResponse.class);

    assertThat(actual.customerId()).isEqualTo(AccountFixtures.CUSTOMER_SUBJECT);
  }

}
