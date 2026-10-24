package com.c4soft.resthero.customer.web;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.c4soft.resthero.commons.domain.Iban;
import com.c4soft.resthero.commons.events.DomainEvent;
import com.c4soft.resthero.commons.events.ResourceType;
import com.c4soft.resthero.customer.domain.Beneficiary;
import com.c4soft.resthero.customer.domain.Customer;
import com.c4soft.resthero.customer.jpa.BeneficiaryRepository;
import com.c4soft.resthero.customer.keycloak.CustomerRepository;
import com.c4soft.resthero.problem.ApiProblemException;
import com.c4soft.resthero.problem.ProblemType;
import io.micrometer.observation.annotation.Observed;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Tag(name = "Customers")
@RestController
@RequestMapping(
    produces = {MediaType.APPLICATION_PROBLEM_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
@RequiredArgsConstructor
@Observed
@Slf4j
public class CustomerController {
  public static final String BASE_PATH = "/customers";
  public static final String CUSTOMER_ID_PLACEHOLDER = "customerId";
  public static final String CUSTOMER_PATH = BASE_PATH + "/{" + CUSTOMER_ID_PLACEHOLDER + "}";
  public static final String BENEFICIARIES_PATH = CUSTOMER_PATH + "/beneficiaries";
  public static final String BENEFICIARY_ID_PLACEHOLDER = "beneficiaryId";
  public static final String BENEFICIARY_PATH =
      BENEFICIARIES_PATH + "/{" + BENEFICIARY_ID_PLACEHOLDER + "}";

  private final CustomerMapper customerMapper;

  private final BeneficiaryRepository beneficiaryRepo;

  private final CustomerRepository userRepo;

  private final RabbitTemplate rabbitTemplate;
  private final TopicExchange eventsExchange;

  /**
   * Requires the `customer.read_any` authority
   * 
   * @param search A String contained in username, first or last name, or email.
   * @param pageable
   * @return
   */
  @Transactional(readOnly = true)
  // LAB:4.1:TODO:START déclarer la route de la liste des clients
  @GetMapping(path = BASE_PATH)
  // LAB:4.1:TODO:END
  @PreAuthorize("hasAuthority('customer.read_any')")
  public PagedModel<CustomerResponse> listCustomers(
      @RequestParam(required = false) @Nullable String search,
      @ParameterObject Pageable pageable) {
    var usersPage = userRepo.listUsers(search, pageable);
    return new PagedModel(usersPage.map(customerMapper::map));
  }

  /**
   * Requires the `customer.edit` authority
   * 
   * @param dto
   * @return
   */
  @Transactional
  @PostMapping(path = BASE_PATH)
  @PreAuthorize("hasAuthority('customer.edit')")
  public ResponseEntity<Void> createCustomer(
      @RequestBody @Valid CustomerCreationRequest dto,
      Authentication auth) {
    var customer = userRepo.save(customerMapper.map(dto));
    // LAB:4.7:TODO:START journaliser la création d'un client
    log.info("{} created customer {}", auth.getName(), customer);
    // LAB:4.7:TODO:END
    return ResponseEntity
        .created(
            URI
                .create(
                    CUSTOMER_PATH
                        .replace("{%s}".formatted(CUSTOMER_ID_PLACEHOLDER), customer.getId())))
        .build();
  }

  /**
   * Requires the `customer.read_any` authority or the user to be the customer
   * 
   * @param customer
   * @return
   */
  @Transactional(readOnly = true)
  @GetMapping(path = CUSTOMER_PATH)
  @PreAuthorize("hasAuthority('customer.read_any') or #customer.id == authentication.name")
  public CustomerResponse getCustomer(
      @Parameter(schema = @Schema(type = "string"),
          description = "The ID of the customer to retrieve")
      @PathVariable(CUSTOMER_ID_PLACEHOLDER) Customer customer) {
    return customerMapper.map(customer);
  }

  /**
   * Requires the `customer.read_any` authority or the user to be the customer
   * 
   * @param customer
   * @return
   */
  @Transactional(readOnly = true)
  @GetMapping(path = BENEFICIARIES_PATH)
  @PreAuthorize("hasAuthority('customer.read_any') or #customerId == authentication.name")
  public List<BeneficiaryResponse> listBeneficiaries(
      @PathVariable(CUSTOMER_ID_PLACEHOLDER) String customerId) {
    return beneficiaryRepo.findByCustomerId(customerId).stream().map(customerMapper::map).toList();
  }

  /**
   * Requires the `customer.edit` authority or the user to be the customer
   * 
   * @param customer
   * @param dto Neither IBAN nor label can be used for this customer
   * @return a response with a Location header pointing to the created beneficiary
   * @throws ApiProblemException {@link ProblemType#BENEFICIARY_IBAN_ALREADY_REGISTERED} or
   *         {@link ProblemType#BENEFICIARY_LABEL_ALREADY_REGISTERED} when the customer already has
   *         a beneficiary with this IBAN or a similar label
   */
  @Transactional(rollbackFor = ApiProblemException.class)
  @PostMapping(path = BENEFICIARIES_PATH)
  @PreAuthorize("hasAuthority('customer.edit') or #customer.id == authentication.name")
  public ResponseEntity<Void> addBeneficiary(
      @Parameter(schema = @Schema(type = "string"),
          description = "The ID of the customer to retrieve")
      @PathVariable(CUSTOMER_ID_PLACEHOLDER) Customer customer,
      @RequestBody @Valid BeneficiaryRequest dto,
      Authentication auth) throws ApiProblemException {
    final var iban = Iban.of(dto.iban());
    final var label = dto.label().trim();
    final var existingBeneficiaries = beneficiaryRepo.findByCustomerId(customer.getId());
    assertIbanNotKnown(existingBeneficiaries.stream(), iban);
    assertLabelNotKnown(existingBeneficiaries.stream(), label);
    final var beneficiary = beneficiaryRepo.save(customerMapper.map(dto, customer.getId()));
    log.info("{} created beneficiary {} for customer {}", auth.getName(), beneficiary, customer);

    publishBeneficiaryEvent(beneficiary, DomainEvent.EventType.CREATE);

    return ResponseEntity
        .created(
            URI
                .create(
                    BENEFICIARY_PATH
                        .replace("{%s}".formatted(CUSTOMER_ID_PLACEHOLDER), customer.getId())
                        .replace(
                            "{%s}".formatted(BENEFICIARY_ID_PLACEHOLDER),
                            beneficiary.getId().toString())))
        .build();
  }

  @Transactional(readOnly = true)
  @GetMapping(path = BENEFICIARY_PATH)
  @PreAuthorize("hasAuthority('customer.read_any') or #customer.id == authentication.name")
  public BeneficiaryResponse getBeneficiary(
      @Parameter(schema = @Schema(type = "string"),
          description = "The ID of the customer to retrieve")
      @PathVariable(CUSTOMER_ID_PLACEHOLDER) Customer customer,
      @Parameter(schema = @Schema(type = "integer", format = "int64"),
          description = "The ID of the customer to retrieve")
      @PathVariable(BENEFICIARY_ID_PLACEHOLDER) Beneficiary beneficiary)
      throws ApiProblemException {
    assertCustomerBeneficiaryConsistency(customer, beneficiary);
    return customerMapper.map(beneficiary);
  }

  /** A synchronous update, done when the response leaves: {@code 204}, never {@code 202}. */
  @Transactional(rollbackFor = ApiProblemException.class)
  @PutMapping(path = BENEFICIARY_PATH)
  @PreAuthorize("hasAuthority('customer.edit') or #customer.id == authentication.name")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void updateBeneficiary(
      @Parameter(schema = @Schema(type = "string"),
          description = "The ID of the customer to retrieve")
      @PathVariable(CUSTOMER_ID_PLACEHOLDER) Customer customer,
      @Parameter(schema = @Schema(type = "integer", format = "int64"),
          description = "The ID of the customer to retrieve")
      @PathVariable(BENEFICIARY_ID_PLACEHOLDER) Beneficiary beneficiary,
      @RequestBody @Valid BeneficiaryRequest dto,
      Authentication auth) throws ApiProblemException {
    assertCustomerBeneficiaryConsistency(customer, beneficiary);
    var iban = Iban.of(dto.iban());
    var label = dto.label().trim();
    var otherBeneficiaries = beneficiaryRepo
        .findByCustomerId(customer.getId())
        .stream()
        .filter(b -> !Objects.equals(b.getId(), beneficiary.getId()))
        .toList();
    assertIbanNotKnown(otherBeneficiaries.stream(), iban);
    assertLabelNotKnown(otherBeneficiaries.stream(), label);
    beneficiary.setIban(iban);
    beneficiary.setLabel(label);
    beneficiaryRepo.save(beneficiary);
    log.info("{} updated beneficiary {} for customer {}", auth.getName(), beneficiary, customer);

    publishBeneficiaryEvent(beneficiary, DomainEvent.EventType.UPDATE);
  }

  /** A synchronous deletion, done when the response leaves: {@code 204}, never {@code 202}. */
  @Transactional(rollbackFor = ApiProblemException.class)
  @DeleteMapping(path = BENEFICIARY_PATH)
  @PreAuthorize("hasAuthority('customer.edit') or #customer.id == authentication.name")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteBeneficiary(
      @Parameter(schema = @Schema(type = "string"),
          description = "The ID of the customer to retrieve")
      @PathVariable(CUSTOMER_ID_PLACEHOLDER) Customer customer,
      @Parameter(schema = @Schema(type = "integer", format = "int64"),
          description = "The ID of the customer to retrieve")
      @PathVariable(BENEFICIARY_ID_PLACEHOLDER) Beneficiary beneficiary,
      Authentication auth) throws ApiProblemException {
    assertCustomerBeneficiaryConsistency(customer, beneficiary);
    beneficiaryRepo.delete(beneficiary);
    log.info("{} deleted beneficiary {} for customer {}", auth.getName(), beneficiary, customer);

    publishBeneficiaryEvent(beneficiary, DomainEvent.EventType.DELETE);
  }

  private void publishBeneficiaryEvent(Beneficiary beneficiary, DomainEvent.EventType eventType) {
    rabbitTemplate
        .convertAndSend(
            eventsExchange.getName(),
            "customer.beneficiaries.updated",
            new DomainEvent(
                ResourceType.CUSTOMER_BENEFICIARIES,
                beneficiary.getId().toString(),
                beneficiary.getCustomerId(),
                List.of("customer.read_any"),
                eventType,
                Instant.now()));
  }

  private void assertIbanNotKnown(Stream<Beneficiary> beneficiariesStream, Iban iban)
      throws ApiProblemException {
    if (beneficiariesStream.anyMatch(b -> Objects.equals(b.getIban(), iban))) {
      log.warn("The customer already has a beneficiary with IBAN {}", iban);
      throw new ApiProblemException(
          ProblemType.BENEFICIARY_IBAN_ALREADY_REGISTERED,
          "A beneficiary is already registered with IBAN %s".formatted(iban),
          Map.of("iban", iban.toHumanReadableString()));
    }
  }

  private void assertLabelNotKnown(Stream<Beneficiary> beneficiariesStream, String label)
      throws ApiProblemException {
    final var needle = label.trim().toLowerCase();
    if (beneficiariesStream.anyMatch(b -> Objects.equals(b.getLabel().toLowerCase(), needle))) {
      log.warn("The customer already has a beneficiary with {} as label", needle);
      throw new ApiProblemException(
          ProblemType.BENEFICIARY_LABEL_ALREADY_REGISTERED,
          "A beneficiary is already registered with a label similar to %s".formatted(label),
          Map.of("label", label));
    }
  }

  /**
   * A beneficiary reached through another customer's path is, for that customer, not found: the
   * same answer as for a beneficiary that does not exist, so that the path leaks nothing.
   */
  private void assertCustomerBeneficiaryConsistency(Customer customer, Beneficiary beneficiary)
      throws ApiProblemException {
    if (!Objects.equals(customer.getId(), beneficiary.getCustomerId())) {
      log.warn("Customer {} and beneficiary {} do not match", customer, beneficiary);
      throw new ApiProblemException(
          ProblemType.BENEFICIARY_NOT_FOUND,
          "No beneficiary with ID %d for customer %s"
              .formatted(beneficiary.getId(), customer.getId()),
          Map.of("customerId", customer.getId(), "beneficiaryId", beneficiary.getId()));
    }
  }
}
