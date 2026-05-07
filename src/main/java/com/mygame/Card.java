package com.mygame;

public interface Card {
    String getId();

    String getName();

    int getValue();

    CardType getCardType();

    default boolean isBankable() {
        return true;
    }

    default boolean canBeUsedAsMoney() {
        return true;
    }

    default boolean isActionCard() {
        return false;
    }

    default boolean isPropertyCard() {
        return false;
    }

    default boolean isMoneyCard() {
        return getCardType() == CardType.MONEY;
    }
}

