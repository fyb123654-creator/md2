package com.mygame;

import java.util.EnumSet;
import java.util.Set;

public final class MultiColorRentCard implements ActionCard {
    private final String id;
    private final String name;
    private final int value;
    private final Set<Color> validColors;

    public MultiColorRentCard(String id, String name, int value) {
        this.id = id;
        this.name = name;
        this.value = value;
        this.validColors = EnumSet.allOf(Color.class);
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

    public Set<Color> getValidColors() {
        return validColors;
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
        if (!validColors.contains(selectedColor)) {
            throw new IllegalStateException("所选房产颜色不符合该租金牌可收费颜色: " + selectedColor);
        }

        int rentAmount = currentPlayer.getRent(selectedColor);
        if (rentAmount <= 0) {
            throw new IllegalStateException("该颜色当前无可收取租金");
        }

        PlayerManagement targetPlayer = interactor.choiceTargetPlayer(currentPlayer, gameManager.getPlayersView());
        if (targetPlayer == null) {
            return false;
        }

        rentAmount = gameManager.resolveRentAmountWithDoubleTheRent(currentPlayer, selectedColor, rentAmount);
        gameManager.chargePlayer(currentPlayer, targetPlayer, rentAmount);
        return true;
    }
}
