package com.mygame.cards.money;

import com.mygame.cards.base.Card;
import com.mygame.cards.base.CardType;

public final class MoneyCard implements Card {
    private final String id;
    private final String name;
    private final int value;

    public MoneyCard(String id, String name, int value) {
        this.id = id;
        this.name = name;
        this.value = value;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getValue() {
        return value;
    }

    @Override
    public CardType getCardType() {
        return CardType.MONEY;
    }
}

