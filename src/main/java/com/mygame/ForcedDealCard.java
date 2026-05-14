package com.mygame;

public final class ForcedDealCard implements ActionCard {
    private final String id;
    private final String name;
    private final int value;

    public ForcedDealCard(String id, String name, int value) {
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
        Interactor interactor = gameManager.getInteractor();
        if (interactor == null) {
            throw new IllegalStateException("interactor is not set");
        }

        PlayerManagement targetPlayer = gameManager.chooseTargetPlayerExcludingCurrent();
        if (targetPlayer == null) {
            return false;
        }

        // 尝试取消
        if (gameManager.tryCancelWithJustSayNo(targetPlayer, currentPlayer, "Forced Deal")) {
            return false;
        }

        // 选择目标玩家的一张可交易财产卡
        Card targetCard = gameManager.chooseStealablePropertyCard(targetPlayer);
        if (targetCard == null) {
            return false;
        }

        // 选择当前玩家的一张财产卡
        Card sourceCard = interactor.choiceStealablePropertyCard(currentPlayer);
        if (sourceCard == null) {
            return false;
        }

        // 交换财产卡
        Color targetColor = gameManager.findPropertyCardColor(targetPlayer, targetCard);
        Color sourceColor = gameManager.findPropertyCardColor(currentPlayer, sourceCard);

        if (targetColor != null) {
            targetPlayer.removeFromPropertyZones(targetCard);
            currentPlayer.addProperty(targetColor, (PropertyCard) targetCard);
        }

        if (sourceColor != null) {
            currentPlayer.removeFromPropertyZones(sourceCard);
            targetPlayer.addProperty(sourceColor, (PropertyCard) sourceCard);
        }

        return true;
    }
}
