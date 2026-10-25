package com.c4soft.resthero.customer.web;

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
import java.util.Map;
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
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithJwt;
import com.c4_soft.springaddons.security.oauth2.test.webmvc.AutoConfigureAddonsWebmvcResourceServerSecurity;
import com.c4_soft.springaddons.security.oauth2.test.webmvc.MockMvcSupport;
import com.c4soft.resthero.commons.domain.Iban;
import com.c4soft.resthero.commons.domain.IbanStringMapper;
import com.c4soft.resthero.customer.CustomerFixtures;
import com.c4soft.resthero.customer.SecurityConfig;
import com.c4soft.resthero.customer.WebConfiguration;
import com.c4soft.resthero.customer.domain.Beneficiary;
import com.c4soft.resthero.customer.domain.Customer;
import com.c4soft.resthero.customer.jpa.BeneficiaryRepository;
import com.c4soft.resthero.customer.keycloak.CustomerRepository;
import com.c4soft.resthero.problem.ApiProblemException;
import com.c4soft.resthero.problem.ProblemType;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = CustomerController.class, properties = "server.ssl.enabled=false")
@Import({IbanStringMapper.class, CustomerMapperImpl.class,
        WebConfiguration.class, SecurityConfig.class})
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
    MockMvcSupport api;

    @Autowired
    ObjectMapper json;

    private static Beneficiary beneficiary(Long id, String label, String iban, Customer customer) {
        final var beneficiary = Mockito.spy(Beneficiary.of(customer.getId(), Iban.of(iban), label));
        when(beneficiary.getId()).thenReturn(id);
        return beneficiary;
    }

    private Customer givenCustomer(Customer customer) {
        when(customerRepo.findById(customer.getId())).thenReturn(Optional.of(customer));
        return customer;
    }

    /** The customer's beneficiaries, each one also found by its ID. */
    private void givenBeneficiaries(Customer customer, Beneficiary... beneficiaries) {
        when(beneficiaryRepo.findByCustomerId(customer.getId())).thenReturn(List.of(beneficiaries));
        for (var beneficiary : beneficiaries) {
            when(beneficiaryRepo.findById(beneficiary.getId())).thenReturn(Optional.of(beneficiary));
        }
    }

    private void assertValidationProblemOn(CustomerCreationRequest dto, String field) throws Exception {
        api
                .post(dto, CustomerController.BASE_PATH)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(ProblemType.VALIDATION.uri().toString()))
                .andExpect(jsonPath("$.invalidFields." + field).isString());
        verify(customerRepo, never()).save(any());
    }

    // ===================== listCustomers =====================

    @Test
    @WithAnonymousUser
    void givenAnonymousUser_whenListCustomers_thenUnauthorized() throws Exception {
        api.get(CustomerController.BASE_PATH + "?search={search}", "e").andExpect(status().isUnauthorized());
    }

    @Test
    @WithJwt("john-deuf.json")
    void givenUserIsCustomer_whenListCustomers_thenForbidden() throws Exception {
        api.get(CustomerController.BASE_PATH + "?search={search}", "e").andExpect(status().isForbidden());
        verify(customerRepo, never()).listUsers(any(), any());
    }

    @Test
    @WithJwt("advisor.json")
    void givenUserIsGrantedWithReadAny_whenListCustomersWithNamePart_thenOk() throws Exception {
        var customer = CustomerFixtures.createJeanBonot();
        when(customerRepo.listUsers("bonot", PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(customer), PageRequest.of(0, 20), 1));

        api
                .get(CustomerController.BASE_PATH + "?search={search}", "bonot")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(customer.getId()));
    }

    // ===================== createCustomer =====================

    @Test
    @WithAnonymousUser
    void givenAnonymousUser_whenCreateCustomer_thenUnauthorized() throws Exception {
        api
                .post(new CustomerCreationRequest("Jean", "Bonot", "jean.bonot@test.pf"), CustomerController.BASE_PATH)
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithJwt("john-deuf.json")
    void givenUserIsCustomer_whenCreateCustomer_thenForbidden() throws Exception {
        api
                .post(new CustomerCreationRequest("Jean", "Bonot", "jean.bonot@test.pf"), CustomerController.BASE_PATH)
                .andExpect(status().isForbidden());
        verify(customerRepo, never()).save(any());
    }

    @Test
    @WithJwt("advisor.json")
    void givenNewCustomer_whenCreateCustomer_thenCreated() throws Exception {
        var newUserId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        when(customerRepo.save(any())).thenAnswer(i -> {
            final var customer = Mockito.spy(i.getArgument(0, Customer.class));
            when(customer.getId()).thenReturn(newUserId);
            return customer;
        });

        api
                .post(new CustomerCreationRequest("Jean", "Bonot", "jean.bonot@test.pf"), CustomerController.BASE_PATH)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, CustomerController.BASE_PATH + "/" + newUserId));
    }

    @Test
    @WithJwt("advisor.json")
    void givenExistingCustomer_whenCreateCustomer_thenCustomerAlreadyExists() throws Exception {
        // what CustomerRepository throws when Keycloak answers 409
        when(customerRepo.save(any()))
                .thenThrow(
                        new ApiProblemException(
                                ProblemType.CUSTOMER_ALREADY_EXISTS,
                                "A customer with email jean.bonot@test.pf already exists",
                                Map.of("email", "jean.bonot@test.pf")).unchecked());

        api
                .post(new CustomerCreationRequest("Jean", "Bonot", "jean.bonot@test.pf"), CustomerController.BASE_PATH)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(ProblemType.CUSTOMER_ALREADY_EXISTS.uri().toString()))
                .andExpect(jsonPath("$.parameters.email").value("jean.bonot@test.pf"));
    }

    /** What CustomerRepository throws when Keycloak answers anything else: its answer stays in the logs. */
    @Test
    @WithJwt("advisor.json")
    void givenTheIdentityProviderFails_whenCreateCustomer_thenIdentityProviderErrorWithoutItsAnswer()
            throws Exception {
        when(customerRepo.save(any()))
                .thenThrow(
                        new ApiProblemException(
                                ProblemType.IDENTITY_PROVIDER_ERROR,
                                "Keycloak answered 500 on realm labs: unknown_error",
                                Map.of()).unchecked());

        api
                .post(new CustomerCreationRequest("Jean", "Bonot", "jean.bonot@test.pf"), CustomerController.BASE_PATH)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value(ProblemType.IDENTITY_PROVIDER_ERROR.uri().toString()))
                .andExpect(content().string(not(containsString("realm labs"))));
    }

    @Test
    @WithJwt("advisor.json")
    void givenAnEmptyFirstName_whenCreateCustomer_thenValidationProblem() throws Exception {
        assertValidationProblemOn(new CustomerCreationRequest("", "Bonot", "bonot@test.pf"), "firstName");
    }

    @Test
    @WithJwt("advisor.json")
    void givenAnEmptyLastName_whenCreateCustomer_thenValidationProblem() throws Exception {
        assertValidationProblemOn(new CustomerCreationRequest("Jean", "", "jean@test.pf"), "lastName");
    }

    @Test
    @WithJwt("advisor.json")
    void givenAMalformedEmail_whenCreateCustomer_thenValidationProblem() throws Exception {
        assertValidationProblemOn(new CustomerCreationRequest("Jean", "Bonot", "not-an-email"), "email");
    }

    // ===================== getCustomer =====================

    @Test
    @WithAnonymousUser
    void givenAnonymousUser_whenGetCustomer_thenUnauthorized() throws Exception {
        api.get(CustomerController.CUSTOMER_PATH, "some-id").andExpect(status().isUnauthorized());
    }

    @Test
    @WithJwt("advisor.json")
    void givenUserIsGrantedWithReadAny_whenGetCustomerWithKnownCustomerId_thenOk() throws Exception {
        var customer = givenCustomer(CustomerFixtures.createJeanBonot());

        var actual = json
                .readValue(
                        api
                                .get(CustomerController.CUSTOMER_PATH, customer.getId())
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        CustomerResponse.class);

        assertThat(actual.id()).isEqualTo(customer.getId());
        assertThat(actual.firstName()).isEqualTo(customer.getFirstName());
    }

    @Test
    @WithJwt("john-deuf.json")
    void givenUserIsCustomer_whenGetCustomerWithCustomerId_thenOk() throws Exception {
        var customer = givenCustomer(CustomerFixtures.createJohnDeuf());

        api
                .get(CustomerController.CUSTOMER_PATH, customer.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customer.getId()));
    }

    @Test
    @WithJwt("john-deuf.json")
    void givenUserIsCustomer_whenGetCustomerWithSomeoneElseId_thenForbidden() throws Exception {
        var jefHini = givenCustomer(CustomerFixtures.createJefHini());

        api.get(CustomerController.CUSTOMER_PATH, jefHini.getId()).andExpect(status().isForbidden());
    }

    @Test
    @WithJwt("advisor.json")
    void givenUnknownCustomerId_whenGetCustomer_thenCustomerNotFound() throws Exception {
        when(customerRepo.findById("unknown-id")).thenReturn(Optional.empty());

        api
                .get(CustomerController.CUSTOMER_PATH, "unknown-id")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(ProblemType.CUSTOMER_NOT_FOUND.uri().toString()))
                .andExpect(jsonPath("$.parameters.customerId").value("unknown-id"));
    }

    // ===================== listBeneficiaries =====================

    @Test
    @WithJwt("advisor.json")
    void givenUserIsGrantedWithReadAny_whenListBeneficiaries_thenOk() throws Exception {
        var customer = CustomerFixtures.createJeanBonot();
        givenBeneficiaries(customer, beneficiary(1L, "Electricity", "FR761111222233334443", customer));

        api
                .get(CustomerController.BENEFICIARIES_PATH, customer.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("Electricity"))
                .andExpect(jsonPath("$[0].iban").value("FR761111222233334443"));
    }

    @Test
    @WithJwt("john-deuf.json")
    void givenUserIsCustomer_whenListOwnBeneficiaries_thenOk() throws Exception {
        var customer = CustomerFixtures.createJohnDeuf();
        givenBeneficiaries(customer, beneficiary(2L, "Landlord", "FR761111222233334441", customer));

        api.get(CustomerController.BENEFICIARIES_PATH, customer.getId()).andExpect(status().isOk());
    }

    @Test
    @WithJwt("john-deuf.json")
    void givenUserIsCustomer_whenListSomeoneElsesBeneficiaries_thenForbidden() throws Exception {
        var jefHini = CustomerFixtures.createJefHini();

        api.get(CustomerController.BENEFICIARIES_PATH, jefHini.getId()).andExpect(status().isForbidden());
        verify(beneficiaryRepo, never()).findByCustomerId(any());
    }

    // ===================== addBeneficiary =====================

    @Test
    @WithAnonymousUser
    void givenAnonymousUser_whenAddBeneficiary_thenUnauthorized() throws Exception {
        var customer = givenCustomer(CustomerFixtures.createJeanBonot());

        api
                .post(
                        new BeneficiaryRequest("FR761111222233334443", "Savings"),
                        CustomerController.BENEFICIARIES_PATH,
                        customer.getId())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithJwt("john-deuf.json")
    void givenUserIsCustomer_whenAddBeneficiaryToSomeoneElse_thenForbidden() throws Exception {
        var jefHini = givenCustomer(CustomerFixtures.createJefHini());

        api
                .post(
                        new BeneficiaryRequest("FR761111222233334443", "Savings"),
                        CustomerController.BENEFICIARIES_PATH,
                        jefHini.getId())
                .andExpect(status().isForbidden());
        verify(beneficiaryRepo, never()).save(any(Beneficiary.class));
    }

    @Test
    @WithJwt("john-deuf.json")
    void givenUserIsCustomer_whenAddBeneficiary_thenCreated() throws Exception {
        var customer = givenCustomer(CustomerFixtures.createJohnDeuf());
        when(beneficiaryRepo.save(any(Beneficiary.class))).thenAnswer(invocation -> {
            var saved = Mockito.spy(invocation.getArgument(0, Beneficiary.class));
            when(saved.getId()).thenReturn(7L);
            return saved;
        });

        api
                .post(
                        new BeneficiaryRequest("FR761111222233334443", "Savings"),
                        CustomerController.BENEFICIARIES_PATH,
                        customer.getId())
                .andExpect(status().isCreated())
                .andExpect(
                        header()
                                .string(
                                        HttpHeaders.LOCATION,
                                        "/customers/%s/beneficiaries/7".formatted(customer.getId())));
    }

    @Test
    @WithJwt("john-deuf.json")
    void givenDuplicateBeneficiaryIban_whenAddBeneficiary_thenIbanAlreadyRegistered() throws Exception {
        var customer = givenCustomer(CustomerFixtures.createJohnDeuf());
        givenBeneficiaries(customer, beneficiary(1L, "Existing", "FR761111222233334443", customer));

        api
                .post(
                        new BeneficiaryRequest("FR761111222233334443", "Other label"),
                        CustomerController.BENEFICIARIES_PATH,
                        customer.getId())
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.type").value(ProblemType.BENEFICIARY_IBAN_ALREADY_REGISTERED.uri().toString()))
                .andExpect(
                        jsonPath("$.parameters.iban")
                                .value(Iban.of("FR761111222233334443").toHumanReadableString()));
        verify(beneficiaryRepo, never()).save(any(Beneficiary.class));
    }

    /** Labels are compared without case: "savings" is the label "Savings" again. */
    @Test
    @WithJwt("john-deuf.json")
    void givenDuplicateBeneficiaryLabel_whenAddBeneficiary_thenLabelAlreadyRegistered() throws Exception {
        var customer = givenCustomer(CustomerFixtures.createJohnDeuf());
        givenBeneficiaries(customer, beneficiary(1L, "Savings", "FR761111222233334443", customer));

        api
                .post(
                        new BeneficiaryRequest("FR761111222233334441", "savings"),
                        CustomerController.BENEFICIARIES_PATH,
                        customer.getId())
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.type").value(ProblemType.BENEFICIARY_LABEL_ALREADY_REGISTERED.uri().toString()))
                .andExpect(jsonPath("$.parameters.label").value("savings"));
        verify(beneficiaryRepo, never()).save(any(Beneficiary.class));
    }

    @Test
    @WithJwt("john-deuf.json")
    void givenAMalformedIban_whenAddBeneficiary_thenValidationProblem() throws Exception {
        var customer = givenCustomer(CustomerFixtures.createJohnDeuf());

        api
                .post(new BeneficiaryRequest("not-an-iban", "Savings"), CustomerController.BENEFICIARIES_PATH, customer.getId())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(ProblemType.VALIDATION.uri().toString()))
                .andExpect(jsonPath("$.invalidFields.iban").isString());
    }

    @Test
    @WithJwt("john-deuf.json")
    void givenAnEmptyLabel_whenAddBeneficiary_thenValidationProblem() throws Exception {
        var customer = givenCustomer(CustomerFixtures.createJohnDeuf());

        api
                .post(new BeneficiaryRequest("FR761111222233334443", ""), CustomerController.BENEFICIARIES_PATH, customer.getId())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.invalidFields.label").isString());
    }

    // ===================== getBeneficiary =====================

    @Test
    @WithJwt("advisor.json")
    void givenKnownCustomerAndBeneficiary_whenGetBeneficiary_thenOk() throws Exception {
        var customer = givenCustomer(CustomerFixtures.createJeanBonot());
        var beneficiary = beneficiary(3L, "Internet", "FR761111222233334443", customer);
        givenBeneficiaries(customer, beneficiary);

        var actual = json
                .readValue(
                        api
                                .get(CustomerController.BENEFICIARY_PATH, customer.getId(), beneficiary.getId())
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        BeneficiaryResponse.class);

        assertThat(actual.id()).isEqualTo(beneficiary.getId());
        assertThat(actual.label()).isEqualTo(beneficiary.getLabel());
        assertThat(actual.iban()).isEqualTo("FR761111222233334443");
    }

    @Test
    @WithJwt("advisor.json")
    void givenUnknownBeneficiaryId_whenGetBeneficiary_thenBeneficiaryNotFound() throws Exception {
        var customer = givenCustomer(CustomerFixtures.createJeanBonot());
        when(beneficiaryRepo.findById(99L)).thenReturn(Optional.empty());

        api
                .get(CustomerController.BENEFICIARY_PATH, customer.getId(), 99L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(ProblemType.BENEFICIARY_NOT_FOUND.uri().toString()))
                .andExpect(jsonPath("$.detail").value("No beneficiary with ID 99"))
                .andExpect(jsonPath("$.parameters.length()").value(1))
                .andExpect(jsonPath("$.parameters.beneficiaryId").value("99"));
    }

    /** Reached through another customer's path, a beneficiary gets the answer of an unknown ID, word for word. */
    @Test
    @WithJwt("advisor.json")
    void givenTheBeneficiaryOfAnotherCustomer_whenGetBeneficiary_thenBeneficiaryNotFound() throws Exception {
        var customer = givenCustomer(CustomerFixtures.createJeanBonot());
        var jefHini = CustomerFixtures.createJefHini();
        var othersBeneficiary = beneficiary(3L, "Internet", "FR761111222233334443", jefHini);
        givenBeneficiaries(jefHini, othersBeneficiary);

        api
                .get(CustomerController.BENEFICIARY_PATH, customer.getId(), othersBeneficiary.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(ProblemType.BENEFICIARY_NOT_FOUND.uri().toString()))
                .andExpect(jsonPath("$.detail").value("No beneficiary with ID 3"))
                .andExpect(jsonPath("$.parameters.length()").value(1))
                .andExpect(jsonPath("$.parameters.beneficiaryId").value("3"));
    }

    // ===================== updateBeneficiary =====================

    @Test
    @WithJwt("john-deuf.json")
    void givenUserIsCustomer_whenUpdateBeneficiaryWithUniqueValues_thenNoContent() throws Exception {
        var customer = givenCustomer(CustomerFixtures.createJohnDeuf());
        var beneficiary = beneficiary(4L, "Rent", "FR761111222233334441", customer);
        givenBeneficiaries(customer, beneficiary, beneficiary(5L, "Power", "FR761111222233334443", customer));
        when(beneficiaryRepo.save(any(Beneficiary.class))).thenAnswer(invocation -> invocation.getArgument(0));

        api
                .put(
                        new BeneficiaryRequest("FR761111222233334444", "Taxes"),
                        CustomerController.BENEFICIARY_PATH,
                        customer.getId(),
                        beneficiary.getId())
                .andExpect(status().isNoContent());

        assertThat(beneficiary.getLabel()).isEqualTo("Taxes");
        assertThat(beneficiary.getIban().toMachineReadableString()).isEqualTo("FR761111222233334444");
        verify(beneficiaryRepo).save(beneficiary);
    }

    @Test
    @WithJwt("john-deuf.json")
    void givenDuplicateBeneficiaryLabel_whenUpdateBeneficiary_thenLabelAlreadyRegistered() throws Exception {
        var customer = givenCustomer(CustomerFixtures.createJohnDeuf());
        var beneficiary = beneficiary(4L, "Rent", "FR761111222233334441", customer);
        givenBeneficiaries(customer, beneficiary, beneficiary(5L, "Power", "FR761111222233334443", customer));

        api
                .put(
                        new BeneficiaryRequest("FR761111222233334444", "Power"),
                        CustomerController.BENEFICIARY_PATH,
                        customer.getId(),
                        beneficiary.getId())
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.type").value(ProblemType.BENEFICIARY_LABEL_ALREADY_REGISTERED.uri().toString()))
                .andExpect(jsonPath("$.parameters.label").value("Power"));
        verify(beneficiaryRepo, never()).save(any(Beneficiary.class));
    }

    // ===================== deleteBeneficiary =====================

    @Test
    @WithJwt("advisor.json")
    void givenKnownCustomerAndBeneficiary_whenDeleteBeneficiary_thenNoContent() throws Exception {
        var customer = givenCustomer(CustomerFixtures.createJeanBonot());
        var beneficiary = beneficiary(9L, "School", "FR761111222233334443", customer);
        givenBeneficiaries(customer, beneficiary);

        api
                .delete(CustomerController.BENEFICIARY_PATH, customer.getId(), beneficiary.getId())
                .andExpect(status().isNoContent());

        verify(beneficiaryRepo).delete(beneficiary);
    }

}
