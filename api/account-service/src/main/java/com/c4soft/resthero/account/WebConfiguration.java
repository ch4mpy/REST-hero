package com.c4soft.resthero.account;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.c4soft.resthero.account.domain.Account;
import com.c4soft.resthero.account.domain.MoneyTransfer;
import com.c4soft.resthero.account.jpa.AccountRepository;
import com.c4soft.resthero.account.jpa.MoneyTransferRepository;
import com.c4soft.resthero.commons.domain.IbanStringMapper;
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
  private final IbanStringMapper ibanStringMapper;
  private final AccountRepository accountRepo;
  private final MoneyTransferRepository transferRepo;

  @Override
  public void addFormatters(FormatterRegistry registry) {
    registry.addConverter(new StringAccountConverter(ibanStringMapper, accountRepo));
    registry.addConverter(new StringMoneyTransferConverter(transferRepo));
  }

  @RequiredArgsConstructor
  static class StringAccountConverter implements Converter<String, Account> {
    private final IbanStringMapper ibanStringMapper;
    private final AccountRepository accountRepo;

    @Override
    public @Nullable Account convert(@Nullable String source) {
      final var iban = ibanStringMapper.map(source);
      if (iban == null) {
        return null;
      }
      return accountRepo
          .findByIban(iban)
          .orElseThrow(
              () -> new ApiProblemException(
                  ProblemType.ACCOUNT_NOT_FOUND,
                  "No account with IBAN %s".formatted(iban.toHumanReadableString()),
                  Map.of("iban", iban.toHumanReadableString())).unchecked());
    }
  }

  @RequiredArgsConstructor
  static class StringMoneyTransferConverter implements Converter<String, MoneyTransfer> {
    private final MoneyTransferRepository transferRepo;

    @Override
    public @Nullable MoneyTransfer convert(@Nullable String source) {
      if (source == null) {
        return null;
      }
      final Long id;
      try {
        id = Long.valueOf(source);
      } catch (NumberFormatException e) {
        throw notFound(source);
      }
      return transferRepo.findById(id).orElseThrow(() -> notFound(source));
    }

    private static RuntimeException notFound(String transferId) {
      return new ApiProblemException(
          ProblemType.MONEY_TRANSFER_NOT_FOUND,
          "No money transfer with ID %s".formatted(transferId),
          Map.of("transferId", transferId)).unchecked();
    }
  }
}
