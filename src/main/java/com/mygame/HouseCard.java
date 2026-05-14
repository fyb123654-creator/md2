package com.mygame;

public final class HouseCard extends BuildingCard {
    private final String id;
    private final String name;
    private final int value;

    public HouseCard(String id, String name, int value) {
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
        return CardType.BUILDING;
    }

    @Override
    public boolean execute(GameManager gameManager) {
        // Building cards are handled through the placePropertyCard method
        return true;
    }
}
