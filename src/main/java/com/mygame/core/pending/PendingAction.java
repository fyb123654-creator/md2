package com.mygame.core.pending;

import java.io.Serializable;

public class PendingAction implements Serializable {

    private int sourcePlayer;

    private int targetPlayer;

    private String actionType;

    private String cardId;

    private boolean resolved;

    public PendingAction(
            int sourcePlayer,
            int targetPlayer,
            String actionType,
            String cardId) {

        this.sourcePlayer = sourcePlayer;
        this.targetPlayer = targetPlayer;
        this.actionType = actionType;
        this.cardId = cardId;
    }

    public int getSourcePlayer() {
        return sourcePlayer;
    }

    public int getTargetPlayer() {
        return targetPlayer;
    }

    public String getActionType() {
        return actionType;
    }

    public String getCardId() {
        return cardId;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }
}
