package com.c4soft.resthero.currency.frankfurter;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import com.c4soft.resthero.commons.domain.Amount;
import com.c4soft.resthero.commons.domain.Currency;
import com.c4soft.resthero.problem.ApiProblemException;
import com.c4soft.resthero.problem.ApiProblemRuntimeException;
import com.c4soft.resthero.problem.ProblemType;
import dev.frankfurter.api.RatesApi;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// LAB:2.2:REMOVE:START
@Service
// LAB:2.2:REMOVE:END
@RequiredArgsConstructor
@Slf4j
public class ForexService {
  public static final Currency PIVOT_CURR = Currency.EUR;

  private final CachingRatesRepository ratesRepo;

  // LAB:2.2:REMOVE:START
  @PostConstruct
  // LAB:2.2:REMOVE:END
  void warmUp() {
    try {
      for (final var curr : Currency.values()) {
        if (!curr.equals(PIVOT_CURR)) {
          ratesRepo.fetchRate(PIVOT_CURR, curr);
        }
      }
    } catch (ApiProblemRuntimeException e) {
      log.warn("The exchange rates were not warmed up: {}", e.getMessage());
    }
  }

  public Amount convert(Amount amount, Currency targetCurrency) {
    final var source =
        new BigDecimal(amount.getDigits()).scaleByPowerOfTen(-1 * amount.getCurrency().decimals);
    final var rate = getExchangeRate(amount.getCurrency(), targetCurrency);
    final var converted = source.multiply(rate);
    return new Amount(
        converted
            .scaleByPowerOfTen(targetCurrency.decimals)
            .round(MathContext.DECIMAL32)
            .intValue(),
        targetCurrency);
  }

  public BigDecimal getExchangeRate(Currency sourceCurrency, Currency targetCurrency) {
    if (sourceCurrency.equals(targetCurrency)) {
      return BigDecimal.ONE;
    }
    final var sourceToPivot = PIVOT_CURR.equals(sourceCurrency) ? BigDecimal.ONE
        : BigDecimal.ONE
            .divide(ratesRepo.fetchRate(PIVOT_CURR, sourceCurrency), 5, RoundingMode.HALF_UP);
    final var pivotToTarget = PIVOT_CURR.equals(targetCurrency) ? BigDecimal.ONE
        : ratesRepo.fetchRate(PIVOT_CURR, targetCurrency);
    return sourceToPivot.multiply(pivotToTarget);
  }

  @RequiredArgsConstructor
  @Repository
  @CacheConfig(cacheNames = CachingRatesRepository.FOREX_CACHE_NAME)
  static class CachingRatesRepository {
    public static final String FOREX_CACHE_NAME = "frankfurterForexCache";
    public static final String PROVIDERS = "ECB";

    private final RatesApi ratesApi;

    @Cacheable(cacheNames = FOREX_CACHE_NAME)
    public BigDecimal fetchRate(Currency fromCurrency, Currency toCurrency) {
      if (Currency.XPF.equals(toCurrency) && fromCurrency.equals(Currency.EUR)) {
        // Hardcoded rate for EUR to XPF as Frankfurter API does not support XPF
        return BigDecimal.valueOf(119.331742243);
      }
      if (fromCurrency.equals(toCurrency)) {
        return BigDecimal.ONE;
      }
      try {
        final var response = ratesApi
            .getRate(
                fromCurrency.name(),
                toCurrency.name(),
                Optional.empty(),
                Optional.of(PROVIDERS));
        return response.getBody().getRate();
      } catch (RestClientException e) {
        // the provider's answer is for the logs, never for the client
        log
            .error(
                "Failed to fetch exchange rate from %s to %s from Frankfurter API: %s"
                    .formatted(fromCurrency, toCurrency, e.getMessage()),
                e);
        throw new ApiProblemException(
            ProblemType.DOWNSTREAM_SERVICE_ERROR,
            "Failed to fetch exchange rate from %s to %s".formatted(fromCurrency, toCurrency),
            Map.of("service", "frankfurter")).unchecked();
      }
    }
  }
}
