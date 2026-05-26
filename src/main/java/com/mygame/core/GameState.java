package com.mygame.core;

import com.mygame.ui.GameController;

import java.io.Serializable;

public final class GameState implements Serializable {
    private final GameManager gameManager;
    private final GameController gameController;

    public GameState(GameManager gameManager) {
        this(gameManager, null);
    }

    public GameState(GameManager gameManager, GameController gameController) {
        if (gameManager == null) {
            throw new IllegalArgumentException("gameManager cannot be null");
        }
        this.gameManager = gameManager;
        this.gameController = gameController;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public GameController getGameController() {
        return gameController;
    }
}

