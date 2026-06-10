package com.mygame.cards.action;

import com.mygame.cards.base.Card;
import com.mygame.cards.money.MoneyCard;
import com.mygame.cards.property.StandardPropertyCard;
import com.mygame.core.GameManager;
import com.mygame.core.deck.CardManager;
import com.mygame.core.interaction.GameInteractor;
import com.mygame.model.Color;
import com.mygame.model.PlayerManagement;
import com.mygame.model.PropertyZone;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for action cards: SlyDeal, DealBreaker, DebtCollector,
 * PassGo, It'sMyBirthday, ForcedDeal.
 *
 * <p>A mock {@link GameInteractor} is used to simulate UI choices
 * so the tests can run without a graphical environment.</p>
 */
@DisplayName("Action Card Tests")
public class ActionCardTest {

    // ---- Mock interactor state ----
    private PlayerManagement mockTargetPlayer;
    private Card mockStolenCard;
    private PropertyZone mockPropertyZone;
    private Card mockUserPropertyCard;
    private Card mockTargetPropertyCard;
    private List<Card> mockPaymentAssets;
    private boolean mockShouldJustSayNo;

    private GameInteractor mockInteractor;

    @BeforeEach
    void setUpInteractor() {
        mockTargetPlayer = null;
        mockStolenCard = null;
        mockPropertyZone = null;
        mockUserPropertyCard = null;
        mockTargetPropertyCard = null;
        mockPaymentAssets = null;
        mockShouldJustSayNo = false;

        mockInteractor = new GameInteractor() {
            @Override
            public PlayerManagement choiceTargetPlayer(PlayerManagement currentPlayer,
                    List<PlayerManagement> players) {
                if (mockTargetPlayer != null) return mockTargetPlayer;
                for (PlayerManagement p : players) {
                    if (p != currentPlayer) return p;
                }
                return null;
            }

            @Override
            public Card choiceStealablePropertyCard(PlayerManagement targetPlayer) {
                return mockStolenCard;
            }

            @Override
            public PropertyZone choicePropertyZone(PlayerManagement player) {
                return mockPropertyZone;
            }

            @Override
            public PropertyZone choiceBuildingPropertyZone(PlayerManagement player, Card buildingCard) {
                return mockPropertyZone;
            }

            @Override
            public Card choiceProperty(PlayerManagement targetPlayer) {
                Card result = mockTargetPropertyCard != null ? mockTargetPropertyCard : mockUserPropertyCard;
                mockTargetPropertyCard = null;
                return result;
            }

            @Override
            public boolean confirmJustSayNo(PlayerManagement responder,
                    PlayerManagement opponent, String actionToCancel) {
                return mockShouldJustSayNo;
            }

            @Override
            public boolean confirmUseDoubleTheRent(PlayerManagement player,
                    Color selectedColor, int baseRentAmount) {
                return false;
            }

            @Override
            public List<Card> showSelectableAssets(PlayerManagement targetPlayer, int requiredAmount) {
                if (mockPaymentAssets != null) return mockPaymentAssets;
                List<Card> result = new ArrayList<>();
                int sum = 0;
                for (Card c : targetPlayer.getBankCardsView()) {
                    sum += c.getValue();
                    result.add(c);
                    if (sum >= requiredAmount) break;
                }
                return sum >= requiredAmount ? result : new ArrayList<>();
            }
        };
    }

    // ---- Helper: create a 2-player GameManager with a custom deck ----
    private GameManager setupTwoPlayerGame(List<Card> deckCards) {
        GameManager gm = new GameManager();
        gm.setPlayerCount(2, List.of("Alice", "Bob"));
        java.util.Collections.reverse(deckCards);
        CardManager cm = new CardManager(deckCards);
        gm.startRound(cm);
        gm.setInteractor(mockInteractor);
        return gm;
    }

    /** Find a card in the current player's hand by class. */
    @SuppressWarnings("unchecked")
    private <T extends Card> T findCardInHand(GameManager gm, Class<T> cardClass) {
        for (Card c : gm.getCurrentPlayer().getHandCardsView()) {
            if (cardClass.isInstance(c)) return (T) c;
        }
        throw new AssertionError("No " + cardClass.getSimpleName() + " found in hand");
    }

    // ====================================================================
    // SlyDeal Tests
    // ====================================================================

    @Test
    @DisplayName("SlyDeal: steal an incomplete-set property")
    void slyDeal_stealsProperty() {
        PlayerManagement bob;
        StandardPropertyCard targetProp = new StandardPropertyCard(
                "tp1", "Oriental Ave", 1, Color.LIGHT_BLUE, Map.of(1, 1, 2, 2, 3, 3));

        List<Card> deck = new ArrayList<>();
        deck.add(new SlyDealCard("sly", "Sly Deal", 3));
        for (int i = 0; i < 14; i++) deck.add(new MoneyCard("m" + i, "1M", 1));

        GameManager gm = setupTwoPlayerGame(deck);

        bob = gm.getPlayersView().get(1);
        bob.addProperty(Color.LIGHT_BLUE, targetProp);
        mockStolenCard = targetProp;
        mockTargetPlayer = bob;

        PlayerManagement alice = gm.getCurrentPlayer();
        gm.playActionCard(findCardInHand(gm, SlyDealCard.class));

        assertEquals(1, alice.getPropertyCount(Color.LIGHT_BLUE));
        assertEquals(0, bob.getPropertyCount(Color.LIGHT_BLUE));
    }

    @Test
    @DisplayName("SlyDeal: blocked by JustSayNo (throws because steal fails)")
    void slyDeal_blockedByJustSayNo() {
        StandardPropertyCard targetProp = new StandardPropertyCard(
                "tp1", "Park Place", 4, Color.DARK_BLUE, Map.of(1, 3, 2, 8));

        List<Card> deck = new ArrayList<>();
        deck.add(new SlyDealCard("sly", "Sly Deal", 3));
        for (int i = 0; i < 14; i++) deck.add(new MoneyCard("m" + i, "1M", 1));

        GameManager gm = setupTwoPlayerGame(deck);

        // Give Bob a JustSayNo card directly (so he can cancel the SlyDeal)
        PlayerManagement bob = gm.getPlayersView().get(1);
        bob.addToHand(new JustSayNoCard("jsn", "Just Say No!", 4));
        bob.addProperty(Color.DARK_BLUE, targetProp);
        mockStolenCard = targetProp;
        mockTargetPlayer = bob;
        mockShouldJustSayNo = true;

        PlayerManagement alice = gm.getCurrentPlayer();
        Card slyFromHand = findCardInHand(gm, SlyDealCard.class);

        // SlyDeal throws when stealPropertyCard returns false (JustSayNo blocked it)
        assertThrows(IllegalStateException.class, () -> {
            gm.playActionCard(slyFromHand);
        });

        // Alice should NOT have gained the property
        assertEquals(0, alice.getPropertyCount(Color.DARK_BLUE));
        assertEquals(1, bob.getPropertyCount(Color.DARK_BLUE));
    }

    @Test
    @DisplayName("SlyDeal: null target cancels action")
    void slyDeal_nullTargetCancels() {
        List<Card> deck = new ArrayList<>();
        deck.add(new SlyDealCard("sly", "Sly Deal", 3));
        for (int i = 0; i < 14; i++) deck.add(new MoneyCard("m" + i, "1M", 1));

        GameManager gm = setupTwoPlayerGame(deck);
        mockTargetPlayer = null; // cancel

        PlayerManagement alice = gm.getCurrentPlayer();
        int before = alice.getHandCardCount();
        gm.playActionCard(findCardInHand(gm, SlyDealCard.class));
        assertEquals(before, alice.getHandCardCount()); // card not consumed
    }

    // ====================================================================
    // DealBreaker Tests
    // ====================================================================

    @Test
    @DisplayName("DealBreaker: steal a complete property set")
    void dealBreaker_stealsCompleteSet() {
        List<Card> deck = new ArrayList<>();
        deck.add(new DealBreakerCard("db", "Deal Breaker", 5));
        for (int i = 0; i < 14; i++) deck.add(new MoneyCard("m" + i, "1M", 1));

        GameManager gm = setupTwoPlayerGame(deck);

        PlayerManagement bob = gm.getPlayersView().get(1);
        bob.addProperty(Color.BROWN, new StandardPropertyCard("b1", "Med Ave", 1, Color.BROWN, Map.of(1, 1, 2, 2)));
        bob.addProperty(Color.BROWN, new StandardPropertyCard("b2", "Baltic Ave", 1, Color.BROWN, Map.of(1, 1, 2, 2)));
        assertTrue(bob.isSetComplete(Color.BROWN));

        mockTargetPlayer = bob;
        mockPropertyZone = bob.getPropertyZonesView().get(Color.BROWN);

        PlayerManagement alice = gm.getCurrentPlayer();
        gm.playActionCard(findCardInHand(gm, DealBreakerCard.class));

        assertEquals(2, alice.getPropertyCount(Color.BROWN));
        assertTrue(alice.isSetComplete(Color.BROWN));
        assertEquals(0, bob.getPropertyCount(Color.BROWN));
    }

    @Test
    @DisplayName("DealBreaker: fails on incomplete set")
    void dealBreaker_failsOnIncompleteSet() {
        List<Card> deck = new ArrayList<>();
        deck.add(new DealBreakerCard("db", "Deal Breaker", 5));
        for (int i = 0; i < 14; i++) deck.add(new MoneyCard("m" + i, "1M", 1));

        GameManager gm = setupTwoPlayerGame(deck);

        PlayerManagement bob = gm.getPlayersView().get(1);
        bob.addProperty(Color.BROWN, new StandardPropertyCard("b1", "Med Ave", 1, Color.BROWN, Map.of(1, 1, 2, 2)));
        assertFalse(bob.isSetComplete(Color.BROWN));

        mockTargetPlayer = bob;
        mockPropertyZone = bob.getPropertyZonesView().get(Color.BROWN);

        assertThrows(IllegalStateException.class, () -> {
            gm.playActionCard(findCardInHand(gm, DealBreakerCard.class));
        });
    }

    // ====================================================================
    // DebtCollector Tests
    // ====================================================================

    @Test
    @DisplayName("DebtCollector: charges 5M from target")
    void debtCollector_chargesFiveMillion() {
        List<Card> deck = new ArrayList<>();
        deck.add(new DebtCollectorCard("dc", "Debt Collector", 3));
        for (int i = 0; i < 14; i++) deck.add(new MoneyCard("m" + i, "1M", 1));

        GameManager gm = setupTwoPlayerGame(deck);

        PlayerManagement alice = gm.getCurrentPlayer();
        PlayerManagement bob = gm.getPlayersView().get(1);
        bob.depositToBank(new MoneyCard("b10", "10M", 10));
        mockTargetPlayer = bob;
        // Default mock behavior: auto-selects from target's actual bank cards

        int aliceHandBefore = alice.getHandCardCount();
        gm.playActionCard(findCardInHand(gm, DebtCollectorCard.class));

        // Bob should have lost the 10M card from bank
        assertEquals(0, bob.getBankCardsView().size());
        assertEquals(aliceHandBefore, alice.getHandCardCount());
        assertTrue(alice.getHandCardsView().stream().anyMatch(c -> "b10".equals(c.getId())));
    }

    // ====================================================================
    // PassGo Tests
    // ====================================================================

    @Test
    @DisplayName("PassGo: draws 2 extra cards")
    void passGo_drawsTwoCards() {
        List<Card> deck = new ArrayList<>();
        deck.add(new PassGoCard("pg", "Pass Go", 1));
        for (int i = 0; i < 20; i++) deck.add(new MoneyCard("m" + i, "1M", 1));

        GameManager gm = setupTwoPlayerGame(deck);
        PlayerManagement alice = gm.getCurrentPlayer();
        int before = alice.getHandCardCount();

        gm.playActionCard(findCardInHand(gm, PassGoCard.class));

        // PassGo removed from hand (-1), then 2 drawn = net +1
        assertEquals(before + 1, alice.getHandCardCount());
    }

    // ====================================================================
    // It'sMyBirthday Tests
    // ====================================================================

    @Test
    @DisplayName("It'sMyBirthday: charges 2M from all opponents")
    void itsMyBirthday_chargesAllOpponents() {
        List<Card> deck = new ArrayList<>();
        deck.add(new ItsMyBirthdayCard("imb", "It's My Birthday", 2));
        for (int i = 0; i < 20; i++) deck.add(new MoneyCard("m" + i, "1M", 1));

        GameManager gm = new GameManager();
        gm.setPlayerCount(3, List.of("Alice", "Bob", "Charlie"));
        java.util.Collections.reverse(deck);
        gm.startRound(new CardManager(deck));
        gm.setInteractor(mockInteractor);

        gm.getPlayersView().get(1).depositToBank(new MoneyCard("b5", "5M", 5));
        gm.getPlayersView().get(2).depositToBank(new MoneyCard("c5", "5M", 5));

        PlayerManagement alice = gm.getCurrentPlayer();
        int aliceHandBefore = alice.getHandCardCount();

        gm.playActionCard(findCardInHand(gm, ItsMyBirthdayCard.class));

        // After charging both opponents, Alice should have gained cards in hand
        assertEquals(0, gm.getPlayersView().get(1).getBankTotalValue());
        assertEquals(0, gm.getPlayersView().get(2).getBankTotalValue());
        assertEquals(aliceHandBefore + 1, alice.getHandCardCount());
        assertTrue(alice.getHandCardsView().stream().anyMatch(c -> "b5".equals(c.getId())));
        assertTrue(alice.getHandCardsView().stream().anyMatch(c -> "c5".equals(c.getId())));
    }
}
