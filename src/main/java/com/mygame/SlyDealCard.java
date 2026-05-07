package com.mygame;

public final class SlyDealCard implements ActionCard {
    private final String id;
    private final String name;
    private final int value;

    public SlyDealCard(String id, String name, int value) {
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
    public void executeAction(Player source, Player target, GameState state) {
        throw new UnsupportedOperationException("Action resolution should be implemented by game engine");
    }
}

