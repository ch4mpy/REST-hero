package com.c4soft.resthero.customer;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.c4soft.resthero.customer.domain.Beneficiary;
import com.c4soft.resthero.customer.domain.Customer;
import com.c4soft.resthero.customer.jpa.BeneficiaryRepository;
import com.c4soft.resthero.customer.keycloak.CustomerRepository;
import com.c4soft.resthero.problem.ApiProblemException;
import com.c4soft.resthero.problem.ProblemType;
import lombok.RequiredArgsConstructor;

/**
 * The converters of the path variables to the domain objects they name. Each throws the typed
 * problem of its resource when there is none: Spring Data's {@code DomainClassConverter} would
 * give {@code null}, which the problem resolver can only report as a {@code 500}. A converter can
 * declare no checked exception, so the problem goes up unchecked.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfiguration implements WebMvcConfigurer {
  private final CustomerRepository customerRepo;
  private final BeneficiaryRepository beneficiaryRepo;

  @Override
  public void addFormatters(FormatterRegistry registry) {
    registry.addConverter(new StringCustomerConverter(customerRepo));
    registry.addConverter(new StringBeneficiaryConverter(beneficiaryRepo));
  }

  @RequiredArgsConstructor
  static class StringCustomerConverter implements Converter<String, Customer> {
    private final CustomerRepository repo;

    @Override
    public @Nullable Customer convert(@Nullable String source) {
      if (source == null) {
        return null;
      }
      return repo
          .findById(source)
          .orElseThrow(
              () -> new ApiProblemException(
                  ProblemType.CUSTOMER_NOT_FOUND,
                  "No customer with ID %s".formatted(source),
                  Map.of("customerId", source)).unchecked());
    }
  }

  @RequiredArgsConstructor
  static class StringBeneficiaryConverter implements Converter<String, Beneficiary> {
    private final BeneficiaryRepository repo;

    @Override
    public @Nullable Beneficiary convert(@Nullable String source) {
      if (source == null) {
        return null;
      }
      final Long id;
      try {
        id = Long.valueOf(source);
      } catch (NumberFormatException e) {
        throw notFound(source);
      }
      return repo.findById(id).orElseThrow(() -> notFound(source));
    }

    // the customer is not known here: the controller adds it when the beneficiary is another's
    private static RuntimeException notFound(String beneficiaryId) {
      return new ApiProblemException(
          ProblemType.BENEFICIARY_NOT_FOUND,
          "No beneficiary with ID %s".formatted(beneficiaryId),
          Map.of("beneficiaryId", beneficiaryId)).unchecked();
    }
  }
}
