package com.mygame;

public final class DebtCollectorCard implements ActionCard {
    private final String id;
    private final String name;
    private final int value;

    public DebtCollectorCard(String id, String name, int value) {
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
        PlayerManagement targetPlayer = gameManager.chooseTargetPlayerExcludingCurrent();
        if (targetPlayer == null) {
            return false;
        }

        // Debt Collector: 从目标玩家收取 5M
        int amount = 5;

        // 尝试取消
        if (gameManager.tryCancelWithJustSayNo(targetPlayer, currentPlayer, "Debt Collector")) {
            return false;
        }

        gameManager.chargePlayer(currentPlayer, targetPlayer, amount);
        return true;
    }
}
