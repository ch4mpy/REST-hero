package com.c4soft.resthero.card.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.c4soft.resthero.card.CacheConfiguration;
import com.c4soft.resthero.card.domain.Card;
import com.c4soft.resthero.commons.domain.Iban;

@SpringBootTest(classes = {CacheConfiguration.class, CardRepository.class})
@ActiveProfiles("h2")
class CardRepositoryTest {

  @MockitoBean
  JpaCardRepository jpaCardRepo;

  @Autowired
  CardRepository cardService;

  Map<String, Card> cardDatabase = new HashMap<>();

  String cardNumber;

  Iban accountIban;

  Card.Ceilings ceilings;

  @BeforeEach
  void setUp() {
    cardNumber = "123456";
    accountIban = Iban.of("FR76 1111222233334444");
    ceilings = Card.Ceilings.builder().transaction(50000).rolling30(100000).build();

    final var cards = new ConcurrentHashMap<String, Card>();
    cards.put(cardNumber, Card.create(cardNumber, accountIban, ceilings));

    when(jpaCardRepo.findById(cardNumber))
        .thenAnswer(invocation -> Optional.ofNullable(cards.get(invocation.getArgument(0))));
    when(jpaCardRepo.findByIban(accountIban))
        .thenAnswer(
            invocation -> cards
                .values()
                .stream()
                .filter(c -> c.getIban().equals(accountIban))
                .toList());
    when(jpaCardRepo.save(org.mockito.ArgumentMatchers.any(Card.class))).thenAnswer(invocation -> {
      Card card = invocation.getArgument(0);
      cards.put(card.getNumber(), card);
      return card;
    });
  }

  /** The path variable's converter reads past the cache: each call reaches the database. */
  @Test
  @DirtiesContext
  void whenFindStoredByNumberTwice_thenTheDatabaseIsReadTwiceAndNothingIsCached() {
    cardService.findStoredByNumber(cardNumber);
    cardService.findStoredByNumber(cardNumber);
    cardService.findStoredByNumber("unknown");

    verify(jpaCardRepo, times(2)).findById(cardNumber);
    // neither the known card nor the unknown number went into the cache
    cardService.findByNumber(cardNumber);
    cardService.findByNumber("unknown");
    verify(jpaCardRepo, times(3)).findById(cardNumber);
    verify(jpaCardRepo, times(2)).findById("unknown");
  }

  @Test
  @DirtiesContext
  // prevent cache operation conflict between tests
  void givenFindByIdCalledTwiceWithSameNumber_whenSaveCardWithSameNumberAndCallFindByIdAgain_thenCacheUpdatedAndFindByIdCalledOnlyOnceOverall() {
    // cardService.findByNumber called twice, but underlying cardRepo.findById should be called only
    // once.
    var actual = cardService.findByNumber(cardNumber);
    var actual2 = cardService.findByNumber(cardNumber);
    assertEquals(100000, actual.get().getCeilings().getRolling30());
    assertEquals(100000, actual2.get().getCeilings().getRolling30());

    // save a new Card instance with the same card number and different ceilings
    // (do not work with a reference to the instance already in the cache)
    var card = cardService.save(
        Card.create(
            cardNumber,
            accountIban,
            Card.Ceilings.builder().transaction(150000).rolling30(300000).build()));
    assertEquals(300000, card.getCeilings().getRolling30());

    // retrieve the card from the cache to verify that it was updated when saving the new instance
    var actual3 = cardService.findByNumber(cardNumber);
    assertEquals(300000, actual3.get().getCeilings().getRolling30());

    // only the 1st call to cardService.findByNumber should delegate to cardRepo.findById
    // (save should @CachePut here, not @CacheEvict)
    verify(jpaCardRepo, times(1)).findById(cardNumber);
  }

  @Test
  @DirtiesContext
  // prevent cache operation conflict between tests
  void givenFindByIbanCalledTwiceWithSameIban_whenSaveCardWithSameIbanAndCallFindByIbanAgain_thenCacheEvictedAndFindByIbanCalledOnlyTwiceOverall() {
    // cardService.findByNumber called twice, but underlying cardRepo.findById should be called only
    // once.
    var actual = cardService.findByIban(accountIban);
    var actual2 = cardService.findByIban(accountIban);
    assertEquals(100000, actual.get(0).getCeilings().getRolling30());
    assertEquals(100000, actual2.get(0).getCeilings().getRolling30());

    // save a new Card instance with the same card number and different ceilings
    // (do not work with a reference to the instance already in the cache)
    var card = cardService.save(
        Card.create(
            cardNumber,
            accountIban,
            Card.Ceilings.builder().transaction(150000).rolling30(300000).build()));
    assertEquals(300000, card.getCeilings().getRolling30());

    // retrieve the card from the cache to verify that it was evicted when saving the new instance
    var actual3 = cardService.findByIban(accountIban);
    assertEquals(300000, actual3.get(0).getCeilings().getRolling30());

    // only the 1st and 3rd calls to cardService.findByNumber should delegate to cardRepo.findById
    // (save should @CacheEvict here, not @CachePut)
    verify(jpaCardRepo, times(2)).findByIban(accountIban);
  }

}
