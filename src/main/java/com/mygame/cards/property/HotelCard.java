package com.mygame.cards.property;

import com.mygame.cards.base.ActionCard;
import com.mygame.cards.base.CardType;
import com.mygame.core.GameManager;
import com.mygame.core.interaction.GameInteractor;
import com.mygame.model.Color;
import com.mygame.model.PlayerManagement;
import com.mygame.model.PropertyZone;

public final class HotelCard implements ActionCard {
    private final String id;
    private final String name;
    private final int value;
    private static final int ADDED_RENT_VALUE = 5;

    public HotelCard(String id, String name, int value) {
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

    public int getAddedRentValue() {
        return ADDED_RENT_VALUE;
    }

    @Override
    public boolean execute(GameManager gameManager) {
        if (gameManager == null) {
            throw new IllegalArgumentException("gameManager cannot be null");
        }

        PlayerManagement currentPlayer = gameManager.getCurrentPlayer();
        GameInteractor interactor = gameManager.getInteractor();
        if (interactor == null) {
            throw new IllegalStateException("interactor is not set");
        }

        PropertyZone selectedZone = interactor.choiceBuildingPropertyZone(currentPlayer, this);
        if (selectedZone == null) {
            return false;
        }

        Color selectedColor = selectedZone.getColor();
        if (!currentPlayer.isSetComplete(selectedColor)) {
            throw new IllegalStateException("Hotel can only be placed on a complete property set: " + selectedColor);
        }

        currentPlayer.addBuilding(selectedColor, new BuildingCard(id, name, value, ADDED_RENT_VALUE));
        return true;
    }
}
