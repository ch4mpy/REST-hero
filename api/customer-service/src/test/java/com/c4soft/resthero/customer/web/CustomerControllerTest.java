package com.c4soft.resthero.customer.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithJwt;
import com.c4_soft.springaddons.security.oauth2.test.webmvc.AutoConfigureAddonsWebmvcResourceServerSecurity;
import com.c4soft.resthero.commons.domain.Iban;
import com.c4soft.resthero.commons.domain.IbanStringMapper;
import com.c4soft.resthero.commons.exception.CommonExceptionsHandler;
import com.c4soft.resthero.customer.CustomerFixtures;
import com.c4soft.resthero.customer.SecurityConfig;
import com.c4soft.resthero.customer.SpringDataWebConvertersTestConfiguration;
import com.c4soft.resthero.customer.domain.Beneficiary;
import com.c4soft.resthero.customer.domain.Customer;
import com.c4soft.resthero.customer.jpa.BeneficiaryRepository;
import com.c4soft.resthero.customer.keycloak.CustomerRepository;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = CustomerController.class, properties = {})
@Import({IbanStringMapper.class, CustomerMapperImpl.class, CommonExceptionsHandler.class,
        SpringDataWebConvertersTestConfiguration.class, SecurityConfig.class})
@AutoConfigureAddonsWebmvcResourceServerSecurity
@SuppressWarnings("null")
class CustomerControllerTest {

    @MockitoBean
    CustomerRepository customerRepo;

    @MockitoBean
    BeneficiaryRepository beneficiaryRepo;

    @MockitoBean
    RabbitTemplate rabbitTemplate;

    @MockitoBean
    TopicExchange eventsExchange;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper json;

    private static Beneficiary beneficiary(
            Long id,
            String label,
            String iban,
            com.c4soft.resthero.customer.domain.Customer customer) {
        final var beneficiary = Mockito.spy(Beneficiary.of(customer.getId(), Iban.of(iban), label));
        when(beneficiary.getId()).thenReturn(id);
        return beneficiary;
    }

    // ===================== listCustomers =====================

    @Test
    @WithAnonymousUser
    void givenAnonymousUser_whenListCustomers_thenUnauthorized() throws Exception {
        mockMvc
                .perform(
                        get("https://localhost" + CustomerController.BASE_PATH)
                                .queryParam("search", "e"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithJwt("advisor.json")
    void givenUserIsGrantedWithReadAny_whenListCustomersWithNamePart_thenOk() throws Exception {
        var customer = CustomerFixtures.createJeanBonot();
        when(customerRepo.listUsers("bonot", PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(customer), PageRequest.of(0, 20), 1));

        var mvcResult = mockMvc
                .perform(
                        get("https://localhost" + CustomerController.BASE_PATH)
                                .queryParam("search", "bonot"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(mvcResult.getResponse().getContentAsString()).contains(customer.getId());
    }

    // ===================== createCustomer =====================

    @Test
    @WithAnonymousUser
    void givenAnonymousUser_whenCreateCustomer_thenUnauthorized() throws Exception {
        var dto = new CustomerCreationRequest("Jean", "Bonot", "jean.bonot@test.pf");

        mockMvc
                .perform(
                        post("https://localhost" + CustomerController.BASE_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithJwt("advisor.json")
    void givenNewCustomer_whenCreateCustomer_thenCreated() throws Exception {
        var dto = new CustomerCreationRequest("Jean", "Bonot", "jean.bonot@test.pf");

        var newUserId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

        when(customerRepo.save(any())).thenAnswer(i -> {
            final var customer = Mockito.spy(i.getArgument(0, Customer.class));
            when(customer.getId()).thenReturn(newUserId);
            return customer;
        });

        var mvcResult = mockMvc
                .perform(
                        post("https://localhost" + CustomerController.BASE_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(mvcResult.getResponse().getHeader("Location")).contains(newUserId);
    }

    @Test
    @WithJwt("advisor.json")
    void givenExistingCustomer_whenCreateCustomer_thenConflict() throws Exception {
        var dto = new CustomerCreationRequest("Jean", "Bonot", "jean.bonot@test.pf");

        when(customerRepo.save(any()))
                .thenThrow(
                        new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Customer already exists"));

        mockMvc
                .perform(
                        post("https://localhost" + CustomerController.BASE_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(dto)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithJwt("advisor.json")
    void givenInvalidPayload_whenCreateCustomer_thenBadRequest() throws Exception {
        // missing first name
        mockMvc
                .perform(
                        post("https://localhost" + CustomerController.BASE_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json
                                                .writeValueAsString(
                                                        new CustomerCreationRequest(
                                                                "",
                                                                "Bonot",
                                                                "bonot@test.pf"))))
                .andExpect(status().is4xxClientError());

        // missing last name
        mockMvc
                .perform(
                        post("https://localhost" + CustomerController.BASE_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json
                                                .writeValueAsString(
                                                        new CustomerCreationRequest(
                                                                "Jean",
                                                                "",
                                                                "jean@test.pf"))))
                .andExpect(status().is4xxClientError());

        // invalid email
        mockMvc
                .perform(
                        post("https://localhost" + CustomerController.BASE_PATH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json
                                                .writeValueAsString(
                                                        new CustomerCreationRequest(
                                                                "Jean",
                                                                "Bonot",
                                                                "not-an-email"))))
                .andExpect(status().is4xxClientError());
    }

    // ===================== getCustomer =====================

    @Test
    @WithAnonymousUser
    void givenAnonymousUser_whenGetCustomer_thenUnauthorized() throws Exception {
        mockMvc
                .perform(get("https://localhost" + CustomerController.CUSTOMER_PATH, "some-id"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithJwt("advisor.json")
    void givenUserIsGrantedWithReadAny_whenGetCustomerWithKnownCustomerId_thenOk()
            throws Exception {
        var customer = CustomerFixtures.createJeanBonot();
        when(customerRepo.findById(customer.getId())).thenReturn(Optional.of(customer));

        var mvcResult = mockMvc
                .perform(
                        get(
                                "https://localhost" + CustomerController.CUSTOMER_PATH,
                                customer.getId()))
                .andExpect(status().isOk())
                .andReturn();

        var actual = json
                .readValue(mvcResult.getResponse().getContentAsString(), CustomerResponse.class);
        assertThat(actual.id()).isEqualTo(customer.getId());
        assertThat(actual.firstName()).isEqualTo(customer.getFirstName());
    }

    @Test
    @WithJwt("john-deuf.json")
    void givenUserIsCustomer_whenGetCustomerWithCustomerId_thenOk() throws Exception {
        var customer = CustomerFixtures.createJohnDeuf();
        when(customerRepo.findById(customer.getId())).thenReturn(Optional.of(customer));

        var mvcResult = mockMvc
                .perform(
                        get(
                                "https://localhost" + CustomerController.CUSTOMER_PATH,
                                customer.getId()))
                .andExpect(status().isOk())
                .andReturn();

        var actual = json
                .readValue(mvcResult.getResponse().getContentAsString(), CustomerResponse.class);
        assertThat(actual.id()).isEqualTo(customer.getId());
        assertThat(actual.firstName()).isEqualTo(customer.getFirstName());
    }

    @Test
    @WithJwt("john-deuf.json")
    void givenUserIsCustomer_whenGetCustomerWithSomeoneElseId_thenForbiden() throws Exception {
        var jefHini = CustomerFixtures.createJefHini();
        when(customerRepo.findById(jefHini.getId())).thenReturn(Optional.of(jefHini));

        mockMvc
                .perform(
                        get(
                                "https://localhost" + CustomerController.CUSTOMER_PATH,
                                jefHini.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithJwt("advisor.json")
    void givenUnknownCustomerId_whenGetCustomer_thenNotFound() throws Exception {
        when(customerRepo.findById("unknown-id")).thenReturn(Optional.empty());

        mockMvc
                .perform(get("https://localhost" + CustomerController.CUSTOMER_PATH, "unknown-id"))
                .andExpect(status().isNotFound());
    }

    // ===================== beneficiaries =====================

    @Test
    @WithJwt("advisor.json")
    void givenUserIsGrantedWithReadAny_whenListBeneficiaries_thenOk() throws Exception {
        var customer = CustomerFixtures.createJeanBonot();
        var beneficiary = beneficiary(1L, "Electricity", "FR761111222233334443", customer);
        when(beneficiaryRepo.findByCustomerId(customer.getId())).thenReturn(List.of(beneficiary));

        var mvcResult = mockMvc
                .perform(
                        get(
                                "https://localhost" + CustomerController.BENEFICIARIES_PATH,
                                customer.getId()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(mvcResult.getResponse().getContentAsString()).contains("Electricity");
        assertThat(mvcResult.getResponse().getContentAsString()).contains("FR761111222233334443");
    }

    @Test
    @WithJwt("john-deuf.json")
    void givenUserIsCustomer_whenListOwnBeneficiaries_thenOk() throws Exception {
        var customer = CustomerFixtures.createJohnDeuf();
        var beneficiary = beneficiary(2L, "Landlord", "FR761111222233334441", customer);
        when(beneficiaryRepo.findByCustomerId(customer.getId())).thenReturn(List.of(beneficiary));

        mockMvc
                .perform(
                        get(
                                "https://localhost" + CustomerController.BENEFICIARIES_PATH,
                                customer.getId()))
                .andExpect(status().isOk());
    }

    @Test
    @WithAnonymousUser
    void givenAnonymousUser_whenAddBeneficiary_thenUnauthorized() throws Exception {
        var customer = CustomerFixtures.createJeanBonot();
        when(customerRepo.findById(customer.getId())).thenReturn(Optional.of(customer));

        mockMvc
                .perform(
                        post(
                                "https://localhost" + CustomerController.BENEFICIARIES_PATH,
                                customer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json
                                                .writeValueAsString(
                                                        new BeneficiaryRequest(
                                                                "FR761111222233334443",
                                                                "Savings"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithJwt("john-deuf.json")
    void givenUserIsCustomer_whenAddBeneficiary_thenCreated() throws Exception {
        var customer = CustomerFixtures.createJohnDeuf();
        when(customerRepo.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(beneficiaryRepo.save(any(Beneficiary.class))).thenAnswer(invocation -> {
            var saved = Mockito.spy(invocation.getArgument(0, Beneficiary.class));
            when(saved.getId()).thenReturn(7L);
            return saved;
        });

        var mvcResult = mockMvc
                .perform(
                        post(
                                "https://localhost" + CustomerController.BENEFICIARIES_PATH,
                                customer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json
                                                .writeValueAsString(
                                                        new BeneficiaryRequest(
                                                                "FR761111222233334443",
                                                                "Savings"))))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(mvcResult.getResponse().getHeader("Location"))
                .isEqualTo("/customers/%s/beneficiaries/7".formatted(customer.getId()));
    }

    @Test
    @WithJwt("john-deuf.json")
    void givenDuplicateBeneficiaryIban_whenAddBeneficiary_thenConflict() throws Exception {
        var customer = CustomerFixtures.createJohnDeuf();
        var beneficiary = beneficiary(1L, "Existing", "FR761111222233334443", customer);
        when(customerRepo.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(beneficiaryRepo.findByCustomerId(customer.getId())).thenReturn(List.of(beneficiary));

        mockMvc
                .perform(
                        post(
                                "https://localhost" + CustomerController.BENEFICIARIES_PATH,
                                customer.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json
                                                .writeValueAsString(
                                                        new BeneficiaryRequest(
                                                                "FR761111222233334443",
                                                                "Other label"))))
                .andExpect(status().isConflict());
    }

    @Test
    @WithJwt("advisor.json")
    void givenKnownCustomerAndBeneficiary_whenGetBeneficiary_thenOk() throws Exception {
        var customer = CustomerFixtures.createJeanBonot();
        var beneficiary = beneficiary(3L, "Internet", "FR761111222233334443", customer);
        when(customerRepo.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(beneficiaryRepo.findByCustomerId(customer.getId())).thenReturn(List.of(beneficiary));
        when(beneficiaryRepo.findById(beneficiary.getId())).thenReturn(Optional.of(beneficiary));

        var mvcResult = mockMvc
                .perform(
                        get(
                                "https://localhost" + CustomerController.BENEFICIARY_PATH,
                                customer.getId(),
                                beneficiary.getId()))
                .andExpect(status().isOk())
                .andReturn();

        var actual = json
                .readValue(mvcResult.getResponse().getContentAsString(), BeneficiaryResponse.class);
        assertThat(actual.id()).isEqualTo(beneficiary.getId());
        assertThat(actual.label()).isEqualTo(beneficiary.getLabel());
        assertThat(actual.iban()).isEqualTo("FR761111222233334443");
    }

    @Test
    @WithJwt("john-deuf.json")
    void givenUserIsCustomer_whenUpdateBeneficiaryWithUniqueValues_thenAccepted() throws Exception {
        var customer = CustomerFixtures.createJohnDeuf();
        var beneficiary = beneficiary(4L, "Rent", "FR761111222233334441", customer);
        var other = beneficiary(5L, "Power", "FR761111222233334443", customer);
        when(customerRepo.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(beneficiaryRepo.findByCustomerId(customer.getId()))
                .thenReturn(List.of(beneficiary, other));
        when(beneficiaryRepo.findById(beneficiary.getId())).thenReturn(Optional.of(beneficiary));
        when(beneficiaryRepo.save(any(Beneficiary.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc
                .perform(
                        put(
                                "https://localhost" + CustomerController.BENEFICIARY_PATH,
                                customer.getId(),
                                beneficiary.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json
                                                .writeValueAsString(
                                                        new BeneficiaryRequest(
                                                                "FR761111222233334444",
                                                                "Taxes"))))
                .andExpect(status().isAccepted());

        assertThat(beneficiary.getLabel()).isEqualTo("Taxes");
        assertThat(beneficiary.getIban().toMachineReadableString())
                .isEqualTo("FR761111222233334444");
    }

    @Test
    @WithJwt("john-deuf.json")
    void givenDuplicateBeneficiaryLabel_whenUpdateBeneficiary_thenConflict() throws Exception {
        var customer = CustomerFixtures.createJohnDeuf();
        var beneficiary = beneficiary(4L, "Rent", "FR761111222233334441", customer);
        var other = beneficiary(5L, "Power", "FR761111222233334443", customer);
        when(customerRepo.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(beneficiaryRepo.findByCustomerId(customer.getId()))
                .thenReturn(List.of(beneficiary, other));
        when(beneficiaryRepo.findById(beneficiary.getId())).thenReturn(Optional.of(beneficiary));

        mockMvc
                .perform(
                        put(
                                "https://localhost" + CustomerController.BENEFICIARY_PATH,
                                customer.getId(),
                                beneficiary.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json
                                                .writeValueAsString(
                                                        new BeneficiaryRequest(
                                                                "FR761111222233334444",
                                                                "Power"))))
                .andExpect(status().isConflict());
    }

    @Test
    @WithJwt("advisor.json")
    void givenKnownCustomerAndBeneficiary_whenDeleteBeneficiary_thenAccepted() throws Exception {
        var customer = CustomerFixtures.createJeanBonot();
        var beneficiary = beneficiary(9L, "School", "FR761111222233334443", customer);
        when(customerRepo.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(beneficiaryRepo.findByCustomerId(customer.getId())).thenReturn(List.of(beneficiary));
        when(beneficiaryRepo.findById(beneficiary.getId())).thenReturn(Optional.of(beneficiary));
        when(customerRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc
                .perform(
                        delete(
                                "https://localhost" + CustomerController.BENEFICIARY_PATH,
                                customer.getId(),
                                beneficiary.getId()))
                .andExpect(status().isAccepted());

        verify(beneficiaryRepo).delete(beneficiary);
    }

}
