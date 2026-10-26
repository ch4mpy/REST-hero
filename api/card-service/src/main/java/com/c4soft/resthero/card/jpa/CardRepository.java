package com.c4soft.resthero.card.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import com.c4soft.resthero.card.domain.Card;
import com.c4soft.resthero.commons.domain.Iban;
import lombok.RequiredArgsConstructor;

@Service
@CacheConfig(cacheNames = {CardRepository.CARD_BY_NUMBER_CACHE, CardRepository.CARD_BY_IBAN_CACHE})
@RequiredArgsConstructor
public class CardRepository {
  static final String CARD_BY_NUMBER_CACHE = "cardByNumber";
  static final String CARD_BY_IBAN_CACHE = "cardByIban";

  private final JpaCardRepository cardRepo;

  /**
   * A card read through the cache, which the caching lab (5) works on. Requests resolve their card
   * past the cache ({@link #findStoredByNumber}).
   */
  @Cacheable(cacheNames = CARD_BY_NUMBER_CACHE)
  public Optional<Card> findByNumber(String number) {
    return cardRepo.findById(number);
  }

  /**
   * The card as stored, read past the cache: a request is authorized on, and changes, the database's
   * state (a cached instance keeps what a rolled-back request changed in it), and an unknown number
   * leaves no entry in the cache.
   */
  public Optional<Card> findStoredByNumber(String number) {
    return cardRepo.findById(number);
  }

  @Caching(put = @CachePut(cacheNames = CARD_BY_NUMBER_CACHE, key = "#card.number"),
      evict = @CacheEvict(cacheNames = CardRepository.CARD_BY_IBAN_CACHE, key = "#card.iban"))
  public Card save(Card card) {
    return cardRepo.save(card);
  }

  @Cacheable(cacheNames = CARD_BY_IBAN_CACHE)
  public List<Card> findByIban(Iban iban) {
    return cardRepo.findByIban(iban);
  }



}
