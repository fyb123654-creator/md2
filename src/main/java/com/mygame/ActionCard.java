package com.mygame;

public interface ActionCard extends Card {
    void executeAction(Player source, Player target, GameState state);

    @Override
    default boolean isActionCard() {
        return true;
    }
}

