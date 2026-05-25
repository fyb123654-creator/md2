package com.mygame.cards.action;

import com.mygame.cards.base.ActionCard;
import com.mygame.cards.base.CardType;
import com.mygame.core.GameManager;
import com.mygame.model.Color;
import com.mygame.model.PlayerManagement;

import java.util.Set;

public final class DoubleTheRentCard implements ActionCard {
    private final String id;
    private final String name;
    private final int value;
    private final Set<Color> validColors;

    public DoubleTheRentCard(String id, String name, int value, Set<Color> validColors) {
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
        return CardType.ACTION;
    }

    public Set<Color> getValidColors() {
        return validColors;
    }

    @Override
    public boolean execute(GameManager gameManager) {
        throw new IllegalStateException("Double The Rent cannot be played alone; it must be used with a rent card");
    }

    public int doubleRentAmount(GameManager gameManager, Color selectedColor) {
        if (gameManager == null) {
            throw new IllegalArgumentException("gameManager cannot be null");
        }
        if (selectedColor == null) {
            throw new IllegalArgumentException("selectedColor cannot be null");
        }
        if (!validColors.contains(selectedColor)) {
            throw new IllegalStateException("Selected color is not valid for this card: " + selectedColor);
        }

        PlayerManagement currentPlayer = gameManager.getCurrentPlayer();
        int rentAmount = currentPlayer.getRent(selectedColor) * 2;
        if (rentAmount <= 0) {
            throw new IllegalStateException("No rent available for the selected color");
        }
        return rentAmount;
    }
}
