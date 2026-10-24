package com.c4soft.resthero.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithJwt;
import com.c4soft.resthero.customer.keycloak.CustomerRepository;
import com.c4soft.resthero.customer.web.CustomerController;
import com.c4soft.resthero.customer.web.CustomerResponse;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "server.ssl.enabled=false")
@ActiveProfiles("h2")
@Import(MockedOAuth2ClientTestConfiguration.class)
@AutoConfigureMockMvc
class CustomerServiceApplicationTests {

  @Autowired
  MockMvc mockMvc;

  @Autowired
  ObjectMapper json;

  @MockitoBean
  CustomerRepository customerRepo;

  @Test
  void contextLoads() {}

  @Test
  @WithJwt("advisor.json")
  void givenUserIsGrantedWithReadAny_whenGetCustomerWithKnownCustomerId_thenOk() throws Exception {
    var customer = CustomerFixtures.createJeanBonot();
    when(customerRepo.findById(customer.getId())).thenReturn(Optional.of(customer));

    var mvcResult = mockMvc
        .perform(get("https://localhost" + CustomerController.CUSTOMER_PATH, customer.getId()))
        .andExpect(status().isOk())
        .andReturn();

    var actual =
        json.readValue(mvcResult.getResponse().getContentAsString(), CustomerResponse.class);
    assertThat(actual.id()).isEqualTo(customer.getId());
    assertThat(actual.firstName()).isEqualTo(customer.getFirstName());
  }

}
