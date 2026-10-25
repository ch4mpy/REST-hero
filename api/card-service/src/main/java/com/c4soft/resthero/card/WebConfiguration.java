package com.c4soft.resthero.card;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.c4soft.resthero.card.domain.Card;
import com.c4soft.resthero.card.jpa.CardRepository;
import com.c4soft.resthero.problem.ApiProblemException;
import com.c4soft.resthero.problem.ProblemType;
import lombok.RequiredArgsConstructor;

/**
 * The converter of the card number path variable to the card it names, as stored (not from the
 * cache: see {@link CardRepository#findStoredByNumber}). It throws the typed problem when there is
 * no such card: Spring Data's {@code DomainClassConverter} would give {@code null}, which the problem resolver can only report
 * as a {@code 500}. A converter can declare no checked exception, so the problem goes up unchecked.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfiguration implements WebMvcConfigurer {
  private final CardRepository cardRepo;

  @Override
  public void addFormatters(FormatterRegistry registry) {
    registry.addConverter(new StringCardConverter(cardRepo));
  }

  @RequiredArgsConstructor
  static class StringCardConverter implements Converter<String, Card> {
    private final CardRepository cardRepo;

    @Override
    public @Nullable Card convert(@Nullable String source) {
      if (source == null) {
        return null;
      }
      return cardRepo
          .findStoredByNumber(source)
          .orElseThrow(
              () -> new ApiProblemException(
                  ProblemType.CARD_NOT_FOUND,
                  "No card with number %s".formatted(source),
                  Map.of("cardNumber", source)).unchecked());
    }
  }
}
