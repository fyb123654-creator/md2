package com.mygame;

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
        throw new IllegalStateException("Double The Rent 不能单独打出，必须与租金卡一起使用");
    }

    public int doubleRentAmount(GameManager gameManager, Color selectedColor) {
        if (gameManager == null) {
            throw new IllegalArgumentException("gameManager cannot be null");
        }
        if (selectedColor == null) {
            throw new IllegalArgumentException("selectedColor cannot be null");
        }
        if (!validColors.contains(selectedColor)) {
            throw new IllegalStateException("所选房产颜色不符合该租金牌可收费颜色: " + selectedColor);
        }

        PlayerManagement currentPlayer = gameManager.getCurrentPlayer();
        int rentAmount = currentPlayer.getRent(selectedColor) * 2;
        if (rentAmount <= 0) {
            throw new IllegalStateException("该颜色当前无可收取租金");
        }
        return rentAmount;
    }
}
