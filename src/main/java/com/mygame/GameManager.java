package com.mygame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GameManager {
    private static final int MIN_PLAYER_COUNT = 2;
    private static final int MAX_PLAYER_COUNT = 5;
    private static final int INITIAL_HAND_CARD_COUNT = 5;
    private static final int TURN_DRAW_CARD_COUNT = 2;
    private static final int MAX_PLAY_COUNT_PER_TURN = 3;
  
    private int playerCount;
    private final List<PlayerManagement> players;
    private CardManager cardManager;
    private int currentPlayerIndex;
    private int playedCardsThisTurn;
    private boolean currentPlayerEndedTurn;
    private boolean gameStarted;

    public GameManager() {
        this.players = new ArrayList<>();
        this.currentPlayerIndex = -1;
        this.playedCardsThisTurn = 0;
        this.currentPlayerEndedTurn = false;
        this.gameStarted = false;
    }

    public static CardManager createGameCardManager() {
        return CardManager.createDefaultCardManager();
    }

    public void setPlayerCount(int playerCount) {
        validatePlayerCount(playerCount);
        this.playerCount = playerCount;
        this.players.clear();
        for (int i = 1; i <= playerCount; i++) {
            players.add(new PlayerManagement(String.valueOf(i), "Player " + i));
        }
        this.currentPlayerIndex = -1;
        this.playedCardsThisTurn = 0;
        this.currentPlayerEndedTurn = false;
        this.gameStarted = false;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public List<PlayerManagement> getPlayersView() {
        return Collections.unmodifiableList(players);
    }

    public CardManager getCardManager() {
        return cardManager;
    }

    public int getCurrentPlayerIndex() {
        ensureGameStarted();
        return currentPlayerIndex;
    }

    public PlayerManagement getCurrentPlayer() {
        ensureGameStarted();
        return players.get(currentPlayerIndex);
    }

    public int getPlayedCardsThisTurn() {
        ensureGameStarted();
        return playedCardsThisTurn;
    }

    public int getRemainingPlayCountThisTurn() {
        ensureGameStarted();
        return MAX_PLAY_COUNT_PER_TURN - playedCardsThisTurn;
    }

    public boolean canCurrentPlayerPlayCard() {
        ensureGameStarted();
        return playedCardsThisTurn < MAX_PLAY_COUNT_PER_TURN;
    }

    public void prepareRound() {
        ensurePlayerCountIsSet();
        cardManager = createGameCardManager();
        currentPlayerIndex = -1;
        playedCardsThisTurn = 0;
        currentPlayerEndedTurn = false;
        gameStarted = false;
    }

    public void startRound() {
        prepareRound();
        dealInitialHands();
        currentPlayerIndex = 0;
        gameStarted = true;
        beginCurrentPlayerTurn();
    }

    public void confirmCurrentPlayerTurnEnded() {
        ensureGameStarted();
        currentPlayerEndedTurn = true;
    }

    public boolean hasCurrentPlayerEndedTurn() {
        ensureGameStarted();
        return currentPlayerEndedTurn;
    }

    public boolean canAdvanceTurn() {
        ensureGameStarted();
        return currentPlayerEndedTurn && getCurrentPlayer().getHandCardCount() <= PlayerManagement.MAX_HAND_SIZE;
    }

    public void advanceTurn() {
        ensureGameStarted();
        if (!canAdvanceTurn()) {
            throw new IllegalStateException("current player must confirm turn end and have at most 7 hand cards before advancing turn");
        }
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        beginCurrentPlayerTurn();
    }

    public void play(Card card, PlayTarget target, Color color) {
        ensureCanPlay();
        PlayerManagement currentPlayer = getCurrentPlayer();
        currentPlayer.playCard(card, cardManager);
        switch (target) {
            case ACTION -> handleActionCard(card);
            case BANK -> handleMoneyCard(card, currentPlayer);
            case PROPERTY -> handlePropertyCard(card, currentPlayer, color);
            default -> throw new IllegalStateException("unsupported play target: " + target);
        }

        playedCardsThisTurn++;
    }

    public void playActionCard(Card card) {
        play(card, PlayTarget.ACTION, null);
    }

    public void depositMoneyCard(Card card) {
        play(card, PlayTarget.BANK, null);
    }

    public void placePropertyCard(PropertyCard propertyCard, Color color) {
        play(propertyCard, PlayTarget.PROPERTY, color);
    }

    public void removeFromCurrentPlayerHand(Card card) {
        ensureGameStarted();
        getCurrentPlayer().removeFromHand(card);
    }

    private void beginCurrentPlayerTurn() {
        playedCardsThisTurn = 0;
        currentPlayerEndedTurn = false;
        PlayerManagement currentPlayer = players.get(currentPlayerIndex);
        List<Card> cards = cardManager.drawCards(TURN_DRAW_CARD_COUNT);
        for (Card card : cards) {
            currentPlayer.addToHand(card);
        }
    }


    private void handleActionCard(Card card) {
        if (!card.isActionCard()) {
            throw new IllegalArgumentException("card is not an action card: " + card.getName());
        }
    }

    private void handleMoneyCard(Card card, PlayerManagement currentPlayer) {
        currentPlayer.depositToBank(card);
    }

    private void handlePropertyCard(Card card, PlayerManagement currentPlayer, Color color) {
        if (!(card instanceof PropertyCard propertyCard)) {
            throw new IllegalArgumentException("card is not a property card: " + card.getName());
        }
        if (propertyCard.getCardType() == CardType.BUILDING) {
            handleBuildingCard(card, currentPlayer, color);
            return;
        }
        if (color == null) {
            throw new IllegalArgumentException("color cannot be null when placing a property card");
        }
        currentPlayer.addProperty(color, propertyCard);
    }

    private void handleBuildingCard(Card card, PlayerManagement currentPlayer, Color color) {
        if (color == null) {
            throw new IllegalArgumentException("color cannot be null when placing a building card");
        }
        if (!(card instanceof BuildingCard buildingCard)) {
            throw new IllegalArgumentException("card is not a building card: " + card.getName());
        }
        currentPlayer.addBuilding(color, buildingCard);
    }

    private void ensureCanPlay() {
        ensureGameStarted();
        if (playedCardsThisTurn >= MAX_PLAY_COUNT_PER_TURN) {
            throw new IllegalStateException("current player has already played the maximum number of cards this turn");
        }
    }

    private void dealInitialHands() {
        for (PlayerManagement player : players) {
            List<Card> cards = cardManager.drawCards(INITIAL_HAND_CARD_COUNT);
            for (Card card : cards) {
                player.addToHand(card);
            }
        }
    }

    private void ensureGameStarted() {
        if (!gameStarted || currentPlayerIndex < 0 || currentPlayerIndex >= players.size()) {
            throw new IllegalStateException("game has not started");
        }
    }

    private void ensurePlayerCountIsSet() {
        if (playerCount < MIN_PLAYER_COUNT || playerCount > MAX_PLAYER_COUNT || players.size() != playerCount) {
            throw new IllegalStateException("player count must be set between 2 and 5 before starting a round");
        }
    }

    private static void validatePlayerCount(int playerCount) {
        if (playerCount < MIN_PLAYER_COUNT || playerCount > MAX_PLAYER_COUNT) {
            throw new IllegalArgumentException("playerCount must be between 2 and 5");
        }
    }
}

