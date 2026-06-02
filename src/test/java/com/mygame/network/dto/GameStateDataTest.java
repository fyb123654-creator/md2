package com.mygame.network.dto;

import com.mygame.cards.money.MoneyCard;
import com.mygame.cards.property.StandardPropertyCard;
import com.mygame.core.GameManager;
import com.mygame.model.Color;
import com.mygame.model.PlayerManagement;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class GameStateDataTest {

    @Test
    void cardDataRoundTrip_moneyCard() {
        MoneyCard original = new MoneyCard("m1", "1M", 1);
        GameStateData.CardData data = GameStateData.CardData.fromCard(original);
        var restored = data.toCard();

        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getName(), restored.getName());
        assertEquals(original.getValue(), restored.getValue());
        assertEquals(original.getCardType(), restored.getCardType());
    }

    @Test
    void cardDataRoundTrip_standardPropertyCard() {
        StandardPropertyCard original = new StandardPropertyCard("sp1", "Baltic Avenue", 1, Color.BROWN, Map.of(1, 1, 2, 2));
        GameStateData.CardData data = GameStateData.CardData.fromCard(original);
        var restored = data.toCard();

        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getName(), restored.getName());
        assertEquals(original.getCardType(), restored.getCardType());
    }

    @Test
    void fromGameManager_populatesCurrentPlayerAndPlayers() {
        GameManager gameManager = new GameManager();
        gameManager.setPlayerCount(2);
        gameManager.startRound();

        GameStateData state = GameStateData.fromGameManager(gameManager);

        assertEquals(0, state.getCurrentPlayerIndex());
        assertEquals(2, state.getPlayers().size());
        assertTrue(state.isGameStarted());
        assertFalse(state.isGameOver());
    }

    @Test
    void playerData_fromPlayerManagement_hasCorrectHandCount() {
        PlayerManagement player = new PlayerManagement("p1", "Alice");
        player.addToHand(new MoneyCard("m1", "1M", 1));
        player.addToHand(new MoneyCard("m2", "2M", 2));

        GameStateData.PlayerData data = GameStateData.PlayerData.fromPlayerManagement(player);

        assertEquals("p1", data.getPlayerId());
        assertEquals("Alice", data.getPlayerName());
        assertEquals(2, data.getHandCardCount());
        assertEquals(2, data.getHandCards().size());
    }
}
