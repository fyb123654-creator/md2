package com.mygame.cards.action;

import com.mygame.cards.base.ActionCard;
import com.mygame.cards.base.CardType;
import com.mygame.cards.property.BuildingCard;
import com.mygame.cards.property.PropertyCard;
import com.mygame.core.GameManager;
import com.mygame.core.interaction.GameInteractor;
import com.mygame.model.Color;
import com.mygame.model.PlayerManagement;
import com.mygame.model.PropertyZone;

import java.util.ArrayList;
import java.util.List;

public final class DealBreakerCard implements ActionCard {
    private final String id;
    private final String name;
    private final int value;

    public DealBreakerCard(String id, String name, int value) {
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
        if (gameManager == null) {
            throw new IllegalArgumentException("gameManager cannot be null");
        }

        PlayerManagement currentPlayer = gameManager.getCurrentPlayer();
        GameInteractor interactor = gameManager.getInteractor();
        if (interactor == null) {
            throw new IllegalStateException("interactor is not set");
        }

        PlayerManagement targetPlayer = interactor.choiceTargetPlayer(currentPlayer, gameManager.getPlayersView());
        if (targetPlayer == null) {
            return false;
        }

        PropertyZone selectedZone = interactor.choicePropertyZone(targetPlayer);
        if (selectedZone == null) {
            return false;
        }

        Color selectedColor = selectedZone.getColor();
        if (!targetPlayer.isSetComplete(selectedColor)) {
            throw new IllegalStateException("You can only take a completed property set: " + selectedColor);
        }

        List<PropertyCard> propertiesToTransfer = new ArrayList<>(selectedZone.getPropertiesView());
        for (PropertyCard propertyCard : propertiesToTransfer) {
            if (selectedZone.removeCard(propertyCard)) {
                currentPlayer.addProperty(selectedColor, propertyCard);
            }
        }

        BuildingCard house = selectedZone.getHouse();
        if (house != null && selectedZone.removeCard(house)) {
            currentPlayer.addBuilding(selectedColor, house);
        }

        BuildingCard hotel = selectedZone.getHotel();
        if (hotel != null && selectedZone.removeCard(hotel)) {
            currentPlayer.addBuilding(selectedColor, hotel);
        }
        return true;
    }
}
