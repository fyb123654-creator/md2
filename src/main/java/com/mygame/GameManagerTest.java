package com.mygame;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameManagerTest {

    @Test
    void testSetPlayerCountAndStart() {
        GameManager gm = new GameManager();
        gm.setPlayerCount(3);
        gm.prepareRound();
        gm.startRound();
        assertEquals(3, gm.getPlayerCount());
        assertNotNull(gm.getCurrentPlayer());
        assertTrue(gm.getCurrentPlayer().getHandCardCount() > 0);
    }

    @Test
    void testAdvanceTurn() {
        GameManager gm = new GameManager();
        gm.setPlayerCount(2);
        gm.startRound();
        PlayerManagement first = gm.getCurrentPlayer();
        gm.confirmCurrentPlayerTurnEnded();
        // 确保手牌不超过上限（初始只有5张，不需要弃牌）
        gm.advanceTurn();
        PlayerManagement second = gm.getCurrentPlayer();
        assertNotEquals(first, second);
    }
}