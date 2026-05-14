package com.mygame;

public class SimpleActionCard implements ActionCard {
    private final String id;
    private final String name;
    private final int value;

    public SimpleActionCard(String id, String name, int value) {
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
        throw new UnsupportedOperationException("Action resolution should be implemented by game engine");
    }
}

