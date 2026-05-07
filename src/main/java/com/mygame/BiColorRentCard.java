package com.mygame;

import java.util.Set;

public final class BiColorRentCard implements ActionCard {
    private final String id;
    private final String name;
    private final int value;
    private final Set<Color> validColors;

    public BiColorRentCard(String id, String name, int value, Set<Color> validColors) {
        this.id = id;
        this.name = name;
        this.value = value;
        this.validColors = Set.copyOf(validColors);
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
        return CardType.RENT_BICOLOR;
    }

    public Set<Color> getValidColors() {
        return validColors;
    }

    @Override
    public void executeAction(Player source, Player target, GameState state) {
        throw new UnsupportedOperationException("Rent resolution should be implemented by game engine");
    }
}

