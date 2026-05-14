package com.mygame;

public final class ItsMyBirthdayCard implements ActionCard {
    private final String id;
    private final String name;
    private final int value;

    public ItsMyBirthdayCard(String id, String name, int value) {
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
        for (PlayerManagement player : gameManager.getPlayersView()) {
            if (player != currentPlayer) {
                gameManager.chargePlayer(currentPlayer, player, 2);
            }
        }
        return true;
    }
}
