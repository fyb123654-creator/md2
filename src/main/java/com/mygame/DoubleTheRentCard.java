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
        // Double The Rent 卡牌的执行逻辑由 GameManager.resolveRentAmountWithDoubleTheRent 处理
        // 这里不需要额外操作，直接返回 true
        return true;
    }
}
