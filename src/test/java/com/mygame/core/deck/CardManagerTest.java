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
        Card card = new MoneyCard("m1", "1M", 1);
        cardManager.playCard(card);

        Card drawn = cardManager.drawCard();

        assertSame(card, drawn);
        assertEquals(0, cardManager.getDiscardPileSize());
        assertEquals(0, cardManager.getDrawPileSize());
    }
}

