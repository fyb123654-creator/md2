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
    private PlayerManagement winner;
    private Interactor interactor;
    private boolean canSelectPropertyCard;
    private boolean canSelectBankCard;
    private final List<Card> selectedPropertyCardBuffer;
    private final List<Card> selectedBankCardBuffer;

    public GameManager() {
        this.players = new ArrayList<>();
        this.currentPlayerIndex = -1;
        this.playedCardsThisTurn = 0;
        this.currentPlayerEndedTurn = false;
        this.gameStarted = false;
        this.winner = null;
        this.canSelectPropertyCard = false;
        this.canSelectBankCard = false;
        this.selectedPropertyCardBuffer = new ArrayList<>();
        this.selectedBankCardBuffer = new ArrayList<>();
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
        this.winner = null;
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

    public void setInteractor(Interactor interactor) {
        this.interactor = interactor;
    }

    public Interactor getInteractor() {
        return interactor;
    }

    public boolean canSelectPropertyCard() {
        return canSelectPropertyCard;
    }

    public void setCanSelectPropertyCard(boolean canSelectPropertyCard) {
        this.canSelectPropertyCard = canSelectPropertyCard;
        if (!canSelectPropertyCard) {
            selectedPropertyCardBuffer.clear();
        }
    }

    public boolean canSelectBankCard() {
        return canSelectBankCard;
    }

    public void setCanSelectBankCard(boolean canSelectBankCard) {
        this.canSelectBankCard = canSelectBankCard;
        if (!canSelectBankCard) {
            selectedBankCardBuffer.clear();
        }
    }

    public List<Card> getSelectedPropertyCardBufferView() {
        return Collections.unmodifiableList(selectedPropertyCardBuffer);
    }

    public List<Card> getSelectedBankCardBufferView() {
        return Collections.unmodifiableList(selectedBankCardBuffer);
    }

    public void cacheSelectedPropertyCard(Card card) {
        if (card == null) {
            throw new IllegalArgumentException("card cannot be null");
        }
        if (!canSelectPropertyCard) {
            return;
        }
        if (!selectedPropertyCardBuffer.contains(card)) {
            selectedPropertyCardBuffer.add(card);
        }
    }

    public void cacheSelectedBankCard(Card card) {
        if (card == null) {
            throw new IllegalArgumentException("card cannot be null");
        }
        if (!canSelectBankCard) {
            return;
        }
        if (!selectedBankCardBuffer.contains(card)) {
            selectedBankCardBuffer.add(card);
        }
    }

    public void clearSelectedCardBuffers() {
        selectedPropertyCardBuffer.clear();
        selectedBankCardBuffer.clear();
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
        return playedCardsThisTurn < MAX_PLAY_COUNT_PER_TURN && winner == null;
    }

    public PlayerManagement getWinner() {
        return winner;
    }

    public boolean hasWinner() {
        return winner != null;
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

    public void play(Card card, Color color) {
        ensureCanPlay();
        PlayerManagement currentPlayer = getCurrentPlayer();
        currentPlayer.playCard(card, cardManager);
        playedCardsThisTurn++;
        checkVictoryCondition();
    }

    public void playActionCard(Card card) {
        if (!(card instanceof ActionCard actionCard)) {
            throw new IllegalArgumentException("card is not an action card: " + card.getName());
        }
        boolean completed = actionCard.execute(this);
        if (!completed) {
            return;
        }
        removeFromCurrentPlayerHand(card);
        cardManager.playCard(card);
        playedCardsThisTurn++;
        checkVictoryCondition();
    }

    public PlayerManagement chooseTargetPlayerExcludingCurrent() {
        ensureGameStarted();
        if (interactor == null) {
            throw new IllegalStateException("interactor is not set");
        }
        return interactor.choiceTargetPlayer(getCurrentPlayer(), getPlayersView());
    }

    public Card chooseStealablePropertyCard(PlayerManagement targetPlayer) {
        ensureGameStarted();
        if (targetPlayer == null) {
            throw new IllegalArgumentException("targetPlayer cannot be null");
        }
        if (interactor == null) {
            throw new IllegalStateException("interactor is not set");
        }
        return interactor.choiceStealablePropertyCard(targetPlayer);
    }

    public boolean stealPropertyCard(PlayerManagement targetPlayer, Card card) {
        ensureGameStarted();
        if (targetPlayer == null) {
            throw new IllegalArgumentException("targetPlayer cannot be null");
        }
        if (card == null) {
            throw new IllegalArgumentException("card cannot be null");
        }
        if (!(card instanceof PropertyCard)) {
            throw new IllegalArgumentException("card is not a property card: " + card.getName());
        }
        if (targetPlayer.isSetComplete(findPropertyCardColor(targetPlayer, card))) {
            return false;
        }

        if (tryCancelWithJustSayNo(targetPlayer, getCurrentPlayer(), "Sly Deal")) {
            return false;
        }

        PlayerManagement currentPlayer = getCurrentPlayer();
        Color color = findPropertyCardColor(targetPlayer, card);
        if (color == null) {
            return false;
        }
        if (!targetPlayer.removeFromPropertyZones(card)) {
            return false;
        }
        currentPlayer.addProperty(color, (PropertyCard) card);
        return true;
    }

    public boolean tryCancelWithJustSayNo(PlayerManagement targetPlayer, PlayerManagement sourcePlayer, String actionName) {
        ensureGameStarted();
        if (targetPlayer == null) {
            throw new IllegalArgumentException("targetPlayer cannot be null");
        }
        if (sourcePlayer == null) {
            throw new IllegalArgumentException("sourcePlayer cannot be null");
        }
        if (actionName == null || actionName.isBlank()) {
            actionName = "action";
        }

        Card justSayNoCard = findJustSayNoCard(targetPlayer);
        if (justSayNoCard == null || interactor == null) {
            return false;
        }

        boolean shouldCancel = interactor.confirmJustSayNo(targetPlayer, sourcePlayer, actionName);
        if (!shouldCancel) {
            return false;
        }

        if (!targetPlayer.removeFromHand(justSayNoCard)) {
            return false;
        }
        cardManager.playCard(justSayNoCard);
        playedCardsThisTurn++;
        checkVictoryCondition();
        return true;
    }

    private Card findJustSayNoCard(PlayerManagement player) {
        for (Card card : player.getHandCardsView()) {
            if (card instanceof JustSayNoCard) {
                return card;
            }
        }
        return null;
    }

    public void removeFromCurrentPlayerHand(Card card) {
        ensureGameStarted();
        getCurrentPlayer().removeFromHand(card);
    }

    public void chargePlayer(PlayerManagement collector, PlayerManagement payer, int amount) {
        if (collector == null) {
            throw new IllegalArgumentException("collector cannot be null");
        }
        if (payer == null) {
            throw new IllegalArgumentException("payer cannot be null");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("amount cannot be negative");
        }
        if (interactor == null) {
            throw new IllegalStateException("interactor is not set");
        }

        if (tryCancelWithJustSayNo(payer, collector, "charge")) {
            return;
        }

        int totalAssetValue = calculateAssetTotalValue(payer);

        if (totalAssetValue < amount) {
            transferAllAssetsToCollectorHand(collector, payer);
            return;
        }

        List<Card> selectedCards = interactor.showSelectableAssets(payer, amount);
        if (selectedCards.isEmpty()) {
            return;
        }

        int selectedValue = 0;
        for (Card card : selectedCards) {
            selectedValue += card.getValue();
        }
        if (selectedValue < amount) {
            throw new IllegalStateException("selected card total value is less than required amount");
        }

        for (Card card : selectedCards) {
            if (payer.removeFromBank(card) || payer.removeFromPropertyZones(card)) {
                collector.addToHand(card);
            }
        }
    }

    public void chargeAllOpponents(PlayerManagement collector, int amount) {
        if (collector == null) {
            throw new IllegalArgumentException("collector cannot be null");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("amount cannot be negative");
        }
        ensureGameStarted();

        for (PlayerManagement player : players) {
            if (player == collector) {
                continue;
            }
            chargePlayer(collector, player, amount);
        }
    }

    public int resolveRentAmountWithDoubleTheRent(PlayerManagement player, Color selectedColor, int baseRentAmount) {
        ensureGameStarted();
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }
        if (selectedColor == null) {
            throw new IllegalArgumentException("selectedColor cannot be null");
        }
        if (baseRentAmount < 0) {
            throw new IllegalArgumentException("baseRentAmount cannot be negative");
        }

        Card doubleTheRentCard = null;
        for (Card handCard : player.getHandCardsView()) {
            if (handCard instanceof DoubleTheRentCard) {
                doubleTheRentCard = handCard;
                break;
            }
        }

        if (doubleTheRentCard == null || interactor == null) {
            return baseRentAmount;
        }

        boolean useDouble = interactor.confirmUseDoubleTheRent(player, selectedColor, baseRentAmount);
        if (!useDouble) {
            return baseRentAmount;
        }

        if (!player.removeFromHand(doubleTheRentCard)) {
            return baseRentAmount;
        }
        cardManager.playCard(doubleTheRentCard);
        return baseRentAmount * 2;
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

    private int calculateAssetTotalValue(PlayerManagement player) {
        int total = 0;

        for (Card bankCard : player.getBankCardsView()) {
            total += bankCard.getValue();
        }

        for (PropertyZone zone : player.getPropertyZonesView().values()) {
            for (PropertyCard propertyCard : zone.getPropertiesView()) {
                total += propertyCard.getValue();
            }
            if (zone.getHouse() != null) {
                total += zone.getHouse().getValue();
            }
            if (zone.getHotel() != null) {
                total += zone.getHotel().getValue();
            }
        }

        return total;
    }

    private Color findPropertyCardColor(PlayerManagement player, Card card) {
        for (java.util.Map.Entry<Color, PropertyZone> entry : player.getPropertyZonesView().entrySet()) {
            PropertyZone zone = entry.getValue();
            if (zone.getPropertiesView().contains(card)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void transferAllAssetsToCollectorHand(PlayerManagement collector, PlayerManagement payer) {
        List<Card> bankCards = new ArrayList<>(payer.getBankCardsView());
        for (Card card : bankCards) {
            if (payer.removeFromBank(card)) {
                collector.addToHand(card);
            }
        }

        List<Card> propertyCards = new ArrayList<>();
        for (PropertyZone zone : payer.getPropertyZonesView().values()) {
            propertyCards.addAll(zone.getPropertiesView());
            if (zone.getHouse() != null) {
                propertyCards.add(zone.getHouse());
            }
            if (zone.getHotel() != null) {
                propertyCards.add(zone.getHotel());
            }
        }

        for (Card card : propertyCards) {
            if (payer.removeFromPropertyZones(card)) {
                collector.addToHand(card);
            }
        }
    }

    public void depositMoneyCard(Card card) {
        play(card, null);
        PlayerManagement currentPlayer = getCurrentPlayer();
        if (!card.canBeUsedAsMoney()) {
            throw new IllegalArgumentException("card cannot be deposited as money: " + card.getName());
        }
        currentPlayer.depositToBank(card);
    }

    public void placePropertyCard(Card card, PlayerManagement currentPlayer, Color color) {
        play(card, color);
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
        if (winner != null) {
            throw new IllegalStateException("game has already ended");
        }
        if (playedCardsThisTurn >= MAX_PLAY_COUNT_PER_TURN) {
            throw new IllegalStateException("current player has already played the maximum number of cards this turn");
        }
    }

    private void checkVictoryCondition() {
        if (winner != null) {
            return;
        }

        for (PlayerManagement player : players) {
            if (player.hasWon()) {
                winner = player;
                currentPlayerEndedTurn = true;
                break;
            }
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

    public boolean isGameOver() {
        return winner != null;
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

