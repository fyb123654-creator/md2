package com.mygame.cards.base;

import com.mygame.core.GameManager;

public interface ActionCard extends Card {
    boolean execute(GameManager gameManager);

    @Override
    default boolean isActionCard() {
        return true;
    }
}

