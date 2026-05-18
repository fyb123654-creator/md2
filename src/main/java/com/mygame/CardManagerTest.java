package com.mygame;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CardManagerTest {

    @Test
    void testCreateDefaultCardManager() {
        CardManager cm = CardManager.createDefaultCardManager();
        assertNotNull(cm);
        // 初始抽牌堆应为106张，弃牌堆0张
        assertEquals(106, cm.getDrawPileSize());
        assertEquals(0, cm.getDiscardPileSize());
    }

    @Test
    void testDrawCard() {
        CardManager cm = CardManager.createDefaultCardManager();
        int initialSize = cm.getDrawPileSize();
        Card card = cm.drawCard();
        assertNotNull(card);
        assertEquals(initialSize - 1, cm.getDrawPileSize());
    }

    @Test
    void testDrawCards() {
        CardManager cm = CardManager.createDefaultCardManager();
        int initialSize = cm.getDrawPileSize();
        java.util.List<Card> cards = cm.drawCards(5);
        assertEquals(5, cards.size());
        assertEquals(initialSize - 5, cm.getDrawPileSize());
    }

    @Test
    void testPlayCard() {
        CardManager cm = CardManager.createDefaultCardManager();
        Card card = cm.drawCard();
        int discardSizeBefore = cm.getDiscardPileSize();
        cm.playCard(card);
        assertEquals(discardSizeBefore + 1, cm.getDiscardPileSize());
    }
}