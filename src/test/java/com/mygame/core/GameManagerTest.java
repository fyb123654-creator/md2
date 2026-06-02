package com.mygame.core;

import com.mygame.cards.base.Card;
import com.mygame.cards.money.MoneyCard;
import com.mygame.cards.property.StandardPropertyCard;
import com.mygame.core.deck.CardManager;
import com.mygame.model.Color;
import com.mygame.model.PlayerManagement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class GameManagerTest {

    private static CardManager deterministicCardManager() {
        List<Card> drawnOrder = new ArrayList<>();

        drawnOrder.add(new MoneyCard("c0", "1M", 1));
        drawnOrder.add(new StandardPropertyCard("c1", "Baltic Avenue", 1, Color.BROWN, Map.of(1, 1, 2, 2)));
        drawnOrder.add(new MoneyCard("c2", "2M", 2));
        drawnOrder.add(new MoneyCard("c3", "3M", 3));
        drawnOrder.add(new MoneyCard("c4", "1M", 1));

        drawnOrder.add(new MoneyCard("c5", "1M", 1));
        drawnOrder.add(new MoneyCard("c6", "2M", 2));
        drawnOrder.add(new MoneyCard("c7", "3M", 3));
        drawnOrder.add(new MoneyCard("c8", "4M", 4));
        drawnOrder.add(new MoneyCard("c9", "5M", 5));

        drawnOrder.add(new MoneyCard("c10", "1M", 1));
        drawnOrder.add(new StandardPropertyCard("c11", "Mediterranean Avenue", 1, Color.BROWN, Map.of(1, 1, 2, 2)));
        drawnOrder.add(new MoneyCard("c12", "2M", 2));
        drawnOrder.add(new MoneyCard("c13", "3M", 3));

        List<Card> deck = new ArrayList<>();
        for (int i = drawnOrder.size() - 1; i >= 0; i--) {
            deck.add(drawnOrder.get(i));
        }
        return new CardManager(deck);
    }

    @Test
    void startRound_dealsInitialHandsAndDrawsForFirstPlayer() {
        GameManager gameManager = new GameManager();
        gameManager.setPlayerCount(2);
        gameManager.startRound(deterministicCardManager());

        List<PlayerManagement> players = gameManager.getPlayersView();
        assertEquals(2, players.size());
        assertEquals(7, players.get(0).getHandCardCount());
        assertEquals(5, players.get(1).getHandCardCount());
        assertEquals(3, gameManager.getRemainingPlayCountThisTurn());
        assertFalse(gameManager.hasWinner());
    }

    @Test
    void depositMoneyCard_doesNotPutCardInDiscardPile() {
        GameManager gameManager = new GameManager();
        gameManager.setPlayerCount(2);
        CardManager cardManager = deterministicCardManager();
        gameManager.startRound(cardManager);

        PlayerManagement me = gameManager.getCurrentPlayer();
        Card money = me.getHandCardsView().stream().filter(Card::canBeUsedAsMoney).findFirst().orElseThrow();
        int handBefore = me.getHandCardCount();
        int playedBefore = gameManager.getPlayedCardsThisTurn();

        gameManager.depositMoneyCard(money);

        assertEquals(handBefore - 1, me.getHandCardCount());
        assertEquals(1, me.getBankCardsView().size());
        assertEquals(0, cardManager.getDiscardPileSize());
        assertEquals(playedBefore + 1, gameManager.getPlayedCardsThisTurn());
    }

    @Test
    void placePropertyCard_doesNotPutCardInDiscardPileAndAddsToZone() {
        GameManager gameManager = new GameManager();
        gameManager.setPlayerCount(2);
        CardManager cardManager = deterministicCardManager();
        gameManager.startRound(cardManager);

        PlayerManagement me = gameManager.getCurrentPlayer();
        Card property = me.getHandCardsView().stream().filter(Card::isPropertyCard).findFirst().orElseThrow();
        int handBefore = me.getHandCardCount();
        int playedBefore = gameManager.getPlayedCardsThisTurn();

        gameManager.placePropertyCard(property, me, Color.BROWN);

        assertEquals(1, me.getPropertyCount(Color.BROWN));
        assertEquals(handBefore - 1, me.getHandCardCount());
        assertEquals(0, cardManager.getDiscardPileSize());
        assertEquals(playedBefore + 1, gameManager.getPlayedCardsThisTurn());
    }

    @Test
    void advanceTurn_switchesCurrentPlayerAndDrawsTwoCards() {
        GameManager gameManager = new GameManager();
        gameManager.setPlayerCount(2);
        gameManager.startRound(deterministicCardManager());

        assertEquals(0, gameManager.getCurrentPlayerIndex());
        gameManager.confirmCurrentPlayerTurnEnded();
        gameManager.advanceTurn();

        assertEquals(1, gameManager.getCurrentPlayerIndex());
        assertEquals(7, gameManager.getCurrentPlayer().getHandCardCount());
    }

    @Test
    void stealPropertyCard_canTriggerWinner() {
        GameManager gameManager = new GameManager();
        gameManager.setPlayerCount(2);
        gameManager.startRound(deterministicCardManager());

        PlayerManagement me = gameManager.getCurrentPlayer();
        PlayerManagement target = gameManager.getPlayersView().get(1);

        me.addProperty(Color.BROWN, new StandardPropertyCard("p1", "Brown A", 1, Color.BROWN, Map.of(1, 1, 2, 2)));
        me.addProperty(Color.BROWN, new StandardPropertyCard("p2", "Brown B", 1, Color.BROWN, Map.of(1, 1, 2, 2)));
        me.addProperty(Color.DARK_BLUE, new StandardPropertyCard("p3", "Blue A", 4, Color.DARK_BLUE, Map.of(1, 3, 2, 8)));
        me.addProperty(Color.DARK_BLUE, new StandardPropertyCard("p4", "Blue B", 4, Color.DARK_BLUE, Map.of(1, 3, 2, 8)));
        me.addProperty(Color.UTILITY, new StandardPropertyCard("p5", "Utility A", 2, Color.UTILITY, Map.of(1, 1, 2, 2)));

        StandardPropertyCard utilityToSteal = new StandardPropertyCard("p6", "Utility B", 2, Color.UTILITY, Map.of(1, 1, 2, 2));
        target.addProperty(Color.UTILITY, utilityToSteal);

        assertEquals(1, me.getPropertyCount(Color.UTILITY));
        assertEquals(1, target.getPropertyCount(Color.UTILITY));
        assertFalse(gameManager.hasWinner());
        assertTrue(gameManager.stealPropertyCard(target, utilityToSteal));
        assertTrue(gameManager.hasWinner());
        assertEquals(2, me.getPropertyCount(Color.UTILITY));
        assertEquals(0, target.getPropertyCount(Color.UTILITY));
        assertTrue(me.isSetComplete(Color.UTILITY));
        assertEquals(me.getName(), gameManager.getWinner().getName());
    }
}
