package com.mygame.cards.base;

import java.io.Serializable;

public interface Card extends Serializable {
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

