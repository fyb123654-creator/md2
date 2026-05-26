package com.mygame.cards.rent;

import com.mygame.cards.base.ActionCard;
import com.mygame.cards.base.CardType;
import com.mygame.core.GameManager;
import com.mygame.model.Color;
import com.mygame.model.PlayerManagement;
import com.mygame.model.PropertyZone;
import com.mygame.ui.Interactor;

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
    public boolean execute(GameManager gameManager) {
        if (gameManager == null) {
            throw new IllegalArgumentException("gameManager cannot be null");
        }

        PlayerManagement currentPlayer = gameManager.getCurrentPlayer();
        Interactor interactor = gameManager.getInteractor();
        if (interactor == null) {
            throw new IllegalStateException("interactor is not set");
        }

        PropertyZone selectedZone = interactor.choicePropertyZone(currentPlayer);
        if (selectedZone == null) {
            return false;
        }

        Color selectedColor = selectedZone.getColor();
        int rentAmount = currentPlayer.getRent(selectedColor);
        if (rentAmount <= 0) {
            throw new IllegalStateException("No rent available for the selected color");
        }

        PlayerManagement targetPlayer = interactor.choiceTargetPlayer(currentPlayer, gameManager.getPlayersView());
        if (targetPlayer == null) {
            return false;
        }

        gameManager.chargePlayer(currentPlayer, targetPlayer, rentAmount);
        return true;
    }
}

