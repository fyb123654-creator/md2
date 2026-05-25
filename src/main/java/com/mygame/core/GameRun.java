package com.mygame.core;

import com.mygame.model.PlayerManagement;

public class GameRun {
    private final GameManager gameManager;

    public GameRun(int playerCount) {
        this.gameManager = new GameManager();
        this.gameManager.setPlayerCount(playerCount);
        this.gameManager.startRound();
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public PlayerManagement getCurrentPlayer() {
        return gameManager.getCurrentPlayer();
    }

    public void nextTurn() {
        gameManager.advanceTurn();
    }
}

