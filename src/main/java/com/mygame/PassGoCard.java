package com.mygame;

public final class PassGoCard implements ActionCard {
    private final String id;
    private final String name;
    private final int value;

    public PassGoCard(String id, String name, int value) {
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

        // Pass Go: 从银行获得 2M（通过抽牌实现）
        // 在 Monopoly Deal 中，Pass Go 通常是获得 2 张 1M 的钱卡
        for (int i = 0; i < 2; i++) {
            Card moneyCard = gameManager.getCardManager().drawCard();
            if (moneyCard != null) {
                currentPlayer.addToHand(moneyCard);
            }
        }

        return true;
    }
}
