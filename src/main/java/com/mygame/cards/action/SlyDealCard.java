package com.mygame.cards.action;

import com.mygame.cards.base.ActionCard;
import com.mygame.cards.base.Card;
import com.mygame.cards.base.CardType;
import com.mygame.core.GameManager;
import com.mygame.model.PlayerManagement;

public final class SlyDealCard implements ActionCard {
    private final String id;
    private final String name;
    private final int value;

    public SlyDealCard(String id, String name, int value) {
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

        Card stealableCard = gameManager.chooseStealablePropertyCard(targetPlayer);
        if (stealableCard == null) {
            return false;
        }

        boolean success = gameManager.stealPropertyCard(targetPlayer, stealableCard);
        if (!success) {
            throw new IllegalStateException("failed to steal property card for " + currentPlayer.getName());
        }
        return true;
    }
}

