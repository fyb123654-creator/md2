package com.mygame.cards.action;

import com.mygame.cards.base.ActionCard;
import com.mygame.cards.base.CardType;
import com.mygame.core.GameManager;

public final class JustSayNoCard implements ActionCard {
    private final String id;
    private final String name;
    private final int value;

    public JustSayNoCard(String id, String name, int value) {
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
        return CardType.ACTION;
    }

    @Override
    public boolean execute(GameManager gameManager) {
        throw new UnsupportedOperationException("This card can not be used actively");
    }
}
