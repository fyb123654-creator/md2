package com.mygame;

public interface ActionCard extends Card {
    boolean execute(GameManager gameManager);

    @Override
    default boolean isActionCard() {
        return true;
    }
}

