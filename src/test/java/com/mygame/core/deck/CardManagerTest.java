package com.mygame.core.deck;

import com.mygame.cards.base.Card;
import com.mygame.cards.money.MoneyCard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CardManagerTest {

    @Test
    void drawCard_reshufflesDiscardWhenDrawPileEmpty() {
        CardManager cardManager = new CardManager(List.of());
        Card card1 = new MoneyCard("m1", "1M", 1);
        Card card2 = new MoneyCard("m2", "2M", 2);
        cardManager.playCard(card1);
        cardManager.playCard(card2);

        Card drawn1 = cardManager.drawCard();
        Card drawn2 = cardManager.drawCard();

        assertNotNull(drawn1);
        assertNotNull(drawn2);
        assertNotSame(drawn1, drawn2);
        assertTrue(drawn1 == card1 || drawn1 == card2);
        assertTrue(drawn2 == card1 || drawn2 == card2);
        assertEquals(0, cardManager.getDiscardPileSize());
        assertEquals(0, cardManager.getDrawPileSize());
    }
}

