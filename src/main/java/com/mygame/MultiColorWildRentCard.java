package com.mygame;

public final class MultiColorWildRentCard implements ActionCard {
    private final String id;
    private final String name;
    private final int value;

    public MultiColorWildRentCard(String id, String name, int value) {
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
        return CardType.RENT_WILDCOLOR;
    }

    @Override
    public void executeAction(Player source, Player target, GameState state) {
        throw new UnsupportedOperationException("Wild rent resolution should be implemented by game engine");
    }
}

