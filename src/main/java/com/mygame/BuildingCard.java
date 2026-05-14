package com.mygame;

public final class BuildingCard implements ActionCard {
    private final String id;
    private final String name;
    private final int value;
    private final int addedRentValue;

    public BuildingCard(String id, String name, int value, int addedRentValue) {
        this.id = id;
        this.name = name;
        this.value = value;
        this.addedRentValue = addedRentValue;
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
        throw new UnsupportedOperationException("Action resolution should be implemented by game engine");
    }

    public int getAddedRentValue() {
        return addedRentValue;
    }

    public boolean canAttachTo(PropertySet propertySet) {
        return propertySet != null && propertySet.isComplete() && !propertySet.isRailroad() && !propertySet.isUtility();
    }
}

