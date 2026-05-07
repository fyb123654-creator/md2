package com.mygame;

public final class GameState {
    private final GameManager gameManager;

    public GameState(GameManager gameManager) {
        if (gameManager == null) {
            throw new IllegalArgumentException("gameManager cannot be null");
        }
        this.gameManager = gameManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }
}

