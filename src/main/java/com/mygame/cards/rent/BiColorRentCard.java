package com.mygame.cards.rent;

import com.mygame.cards.base.ActionCard;
import com.mygame.cards.base.CardType;
import com.mygame.core.GameManager;
import com.mygame.model.Color;
import com.mygame.model.PlayerManagement;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class BiColorRentCard implements ActionCard {
    private final String id;
    private final String name;
    private final int value;
    private final EnumSet<Color> validColors;
    private Color selectedColor;

    public BiColorRentCard(String id, String name, int value, Set<Color> validColors) {
        this.id = id;
        this.name = name;
        this.value = value;
        this.validColors = EnumSet.copyOf(validColors);
        this.selectedColor = this.validColors.iterator().next();
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
        return Collections.unmodifiableSet(validColors);
    }

    public Color getSelectedColor() {
        return selectedColor;
    }

    public void setSelectedColor(Color selectedColor) {
        if (selectedColor == null) {
            throw new IllegalArgumentException("selectedColor cannot be null");
        }
        if (!validColors.contains(selectedColor)) {
            throw new IllegalArgumentException("Selected color is not valid for this rent card: " + selectedColor);
        }
        this.selectedColor = selectedColor;
    }

    public void switchToNextColor() {
        for (Color color : validColors) {
            if (color != selectedColor) {
                selectedColor = color;
                return;
            }
        }
    }

    @Override
    public boolean execute(GameManager gameManager) {
        if (gameManager == null) {
            throw new IllegalArgumentException("gameManager cannot be null");
        }

        PlayerManagement currentPlayer = gameManager.getCurrentPlayer();
        if (!validColors.contains(selectedColor)) {
            throw new IllegalStateException("Selected color is not valid for this rent card: " + selectedColor);
        }

        int rentAmount = currentPlayer.getRent(selectedColor);
        if (rentAmount <= 0) {
            throw new IllegalStateException("No rent available for the selected color");
        }

        rentAmount = gameManager.resolveRentAmountWithDoubleTheRent(currentPlayer, selectedColor, rentAmount);
        gameManager.chargeAllOpponents(currentPlayer, rentAmount);
        return true;
    }
}
