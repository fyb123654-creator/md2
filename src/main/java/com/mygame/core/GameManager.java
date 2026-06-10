package com.mygame.core;

import com.mygame.cards.action.*;
import com.mygame.cards.base.*;
import com.mygame.cards.money.*;
import com.mygame.cards.property.*;
import com.mygame.cards.rent.*;
import com.mygame.core.deck.CardManager;
import com.mygame.core.events.GameEvent;
import com.mygame.core.events.GameEventListener;
import com.mygame.core.events.GameEventType;
import com.mygame.core.interaction.GameInteractor;
import com.mygame.core.pending.PendingAction;
import com.mygame.model.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GameManager {
    public static final int MIN_PLAYER_COUNT = 2;
    public static final int MAX_PLAYER_COUNT = 5;
    public static final int INITIAL_HAND_CARD_COUNT = 5;
    public static final int TURN_DRAW_CARD_COUNT = 2;
    public static final int MAX_PLAY_COUNT_PER_TURN = 3;
    public static final int HOUSE_ADDED_RENT = 3;
    public static final int HOTEL_ADDED_RENT = 5;
    public static final int DEBT_COLLECTOR_AMOUNT = 5;
    public static final int BIRTHDAY_AMOUNT = 2;
    public static final int MAX_LOG_LINES = 200;
  
    private int playerCount;
    private final List<PlayerManagement> players;
    private CardManager cardManager;
    private int currentPlayerIndex;
    private int playedCardsThisTurn;
    private boolean currentPlayerEndedTurn;
    private boolean gameStarted;
    private PlayerManagement winner;
    private GameInteractor interactor;
    private boolean canSelectPropertyCard;
    private boolean canSelectBankCard;
    private final List<Card> selectedPropertyCardBuffer;
    private final List<Card> selectedBankCardBuffer;
    private GamePhase gamePhase = GamePhase.NORMAL_TURN;
    private PendingAction pendingAction;
    private final List<GameEventListener> eventListeners;

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
        this.eventListeners = new ArrayList<>();
    }

    public static CardManager createGameCardManager() {
        return CardManager.createDefaultCardManager();
    }

    public enum GamePhase {
        NORMAL_TURN,
        WAITING_RESPONSE
    }

    public void setPlayerCount(int playerCount) {
        List<String> names = new ArrayList<>();
        for (int i = 1; i <= playerCount; i++) {
            names.add("Player" + i);
        }
        setPlayerCount(playerCount, names);
    }

    public void setPlayerCount(int playerCount, List<String> playerNames) {
        validatePlayerCount(playerCount);
        if (playerNames == null || playerNames.size() != playerCount) {
            throw new IllegalArgumentException("playerNames must have size " + playerCount);
        }

        this.playerCount = playerCount;
        this.players.clear();
        for (int i = 1; i <= playerCount; i++) {
            String name = Objects.requireNonNullElse(playerNames.get(i - 1), "").trim();
            if (name.isBlank()) {
                name = "Player" + i;
            }
            players.add(new PlayerManagement(String.valueOf(i), name));
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

    public boolean isGameStarted() {
        return gameStarted;
    }

    public CardManager getCardManager() {
        return cardManager;
    }

    public void setInteractor(GameInteractor interactor) {
        this.interactor = interactor;
    }

    public GameInteractor getInteractor() {
        return interactor;
    }

    public void addEventListener(GameEventListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener cannot be null");
        }
        if (!eventListeners.contains(listener)) {
            eventListeners.add(listener);
        }
    }

    public void removeEventListener(GameEventListener listener) {
        eventListeners.remove(listener);
    }

    private final List<String> eventLog = new ArrayList<>();

    public List<String> getEventLog() {
        return Collections.unmodifiableList(eventLog);
    }

    private void fireEvent(GameEventType type, String message) {
        eventLog.add(message);
        if (eventLog.size() > MAX_LOG_LINES) {
            eventLog.remove(0);
        }
        if (eventListeners.isEmpty()) {
            return;
        }
        GameEvent event = new GameEvent(type, message);
        for (GameEventListener listener : new ArrayList<>(eventListeners)) {
            listener.onGameEvent(event);
        }
    }

    public boolean canSelectPropertyCard() {
        return canSelectPropertyCard;
    }

    public GamePhase getGamePhase() {
        return gamePhase;
    }

    public void setGamePhase(GamePhase gamePhase) {
        this.gamePhase = gamePhase;
    }

    public PendingAction getPendingAction() {
        return pendingAction;
    }

    public void setPendingAction(PendingAction pendingAction) {
        this.pendingAction = pendingAction;
    }

    public boolean hasPendingAction() {
        return pendingAction != null
                && !pendingAction.isResolved();
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

    public void syncTurnStateFromNetwork(int currentPlayerIndex, int playedCardsThisTurn) {
        if (currentPlayerIndex < 0 || currentPlayerIndex >= players.size()) {
            throw new IllegalArgumentException("invalid current player index: " + currentPlayerIndex);
        }
        if (playedCardsThisTurn < 0 || playedCardsThisTurn > MAX_PLAY_COUNT_PER_TURN) {
            throw new IllegalArgumentException("invalid played card count: " + playedCardsThisTurn);
        }
        this.currentPlayerIndex = currentPlayerIndex;
        this.playedCardsThisTurn = playedCardsThisTurn;
        this.currentPlayerEndedTurn = false;
        this.gameStarted = true;
    }

    public PlayerManagement getWinner() {
        return winner;
    }

    public boolean hasWinner() {
        return winner != null;
    }

    public void prepareRound() {
        prepareRound(null);
    }

    public void startRound() {
        startRound(null);
    }

    public void startRound(CardManager customCardManager) {
        prepareRound(customCardManager);
        dealInitialHands();
        currentPlayerIndex = 0;
        gameStarted = true;
        beginCurrentPlayerTurn();
        fireEvent(GameEventType.ROUND_STARTED, "Round started (" + playerCount + " players)");
    }

    private void prepareRound(CardManager customCardManager) {
        ensurePlayerCountIsSet();
        cardManager = customCardManager != null ? customCardManager : createGameCardManager();
        currentPlayerIndex = -1;
        playedCardsThisTurn = 0;
        currentPlayerEndedTurn = false;
        gameStarted = false;
        winner = null;
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
            throw new IllegalStateException("current player must confirm turn end and have at most " + PlayerManagement.MAX_HAND_SIZE + " hand cards before advancing turn");
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
        checkDeckExhaustionEndGameIfStuck();
    }

    public void playActionCard(Card card) {
        ensureCanPlay();
        if (!(card instanceof ActionCard actionCard)) {
            throw new IllegalArgumentException("card is not an action card: " + card.getName());
        }
        boolean completed = actionCard.execute(this);
        if (!completed) {
            return;
        }
        removeFromCurrentPlayerHand(card);
        if (!(card instanceof HouseCard) && !(card instanceof HotelCard)) {
            cardManager.playCard(card);
        }
        playedCardsThisTurn++;
        checkVictoryCondition();
        fireEvent(GameEventType.ACTION_RESOLVED, getCurrentPlayer().getName() + " played " + card.getName());
        checkDeckExhaustionEndGameIfStuck();
    }

    /**
     * For online server usage: when some action cards are resolved outside
     * {@link ActionCard#execute(GameManager)} (because the client makes choices and sends the result back),
     * the server should explicitly record that a card was played this turn.
     *
     * <p>This method only updates counters and win checks. It does not move cards between piles.</p>
     */
    public void recordPlayedCardAfterExternalResolution() {
        ensureCanPlay();
        playedCardsThisTurn++;
        checkVictoryCondition();
        checkDeckExhaustionEndGameIfStuck();
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
        checkVictoryCondition();
        fireEvent(GameEventType.PROPERTY_STOLEN, currentPlayer.getName() + " stole " + card.getName() + " from " + targetPlayer.getName());
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

        if (findJustSayNoCard(targetPlayer) == null || interactor == null) {
            return false;
        }

        PlayerManagement responder = targetPlayer;
        PlayerManagement opponent = sourcePlayer;
        boolean canceled = false;
        boolean counteringJustSayNo = false;

        while (true) {
            Card justSayNoCard = findJustSayNoCard(responder);
            if (justSayNoCard == null) {
                return canceled;
            }

            String actionToCancel = counteringJustSayNo ? "Just Say No" : actionName;
            boolean shouldUse = interactor.confirmJustSayNo(responder, opponent, actionToCancel);
            if (!shouldUse) {
                return canceled;
            }

            if (!responder.removeFromHand(justSayNoCard)) {
                return canceled;
            }
            cardManager.playCard(justSayNoCard);

            canceled = responder == targetPlayer;
            counteringJustSayNo = true;
            PlayerManagement previousResponder = responder;
            responder = opponent;
            opponent = previousResponder;
        }
    }

    private Card findJustSayNoCard(PlayerManagement player) {
        return player.findJustSayNoCard();
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

        // 没钱就不收租了，且不再弹出支付选择框
        int totalAssetValue = calculateAssetTotalValue(payer);
        if (totalAssetValue <= 0) {
            return;
        }

        if (totalAssetValue <= amount) {
            // transferAllAssetsToCollectorHand 不应该淘汰玩家（线下模式也要改一下）
            payer.transferAllAssetsTo(collector);
            checkVictoryCondition();
            return;
        }

        int paid = 0;
        List<Card> bankCards = new ArrayList<>(payer.getBankCardsView());
        for (Card c : bankCards) {
            if (paid >= amount) break;
            if (payer.removeFromBank(c)) {
                collector.addToHand(c);
                paid += c.getValue();
            }
        }

        if (paid < amount) {
            List<Card> propCards = new ArrayList<>();
            for (PropertyZone zone : payer.getPropertyZonesView().values()) {
                if (zone.getPropertiesView() != null) propCards.addAll(zone.getPropertiesView());
                if (zone.getHouse() != null) propCards.add(zone.getHouse());
                if (zone.getHotel() != null) propCards.add(zone.getHotel());
            }
            propCards.sort(java.util.Comparator.comparingInt(Card::getValue));
            for (Card c : propCards) {
                if (paid >= amount) break;
                if (payer.removeFromPropertyZones(c)) {
                    collector.addToHand(c);
                    paid += c.getValue();
                }
            }
        }

        checkVictoryCondition();
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

        if (doubleTheRentCard == null || interactor == null || getRemainingPlayCountThisTurn() < 2) {
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
        recordPlayedCardAfterExternalResolution();
        return baseRentAmount * 2;
    }

    private void beginCurrentPlayerTurn() {
        playedCardsThisTurn = 0;
        currentPlayerEndedTurn = false;
        ensureCurrentPlayerIsActiveOrEndGame();
        if (winner != null) {
            return;
        }
        PlayerManagement currentPlayer = players.get(currentPlayerIndex);
        
        int drawCount = TURN_DRAW_CARD_COUNT;
        if (currentPlayer.getHandCardCount() == 0) {
            drawCount = 5;
        }
        
        List<Card> cards = cardManager.drawCards(drawCount);
        for (Card card : cards) {
            currentPlayer.addToHand(card);
        }
        fireEvent(GameEventType.TURN_STARTED, "Turn: " + currentPlayer.getName() + " (+" + cards.size() + " card" + (cards.size() == 1 ? "" : "s") + ")");
        checkDeckExhaustionEndGameIfStuck();
    }

    private void ensureCurrentPlayerIsActiveOrEndGame() {
        if (winner != null || players.isEmpty()) {
            return;
        }
        int activeCount = 0;
        int lastActiveIndex = -1;
        for (int i = 0; i < players.size(); i++) {
            if (!players.get(i).isEliminated()) {
                activeCount++;
                lastActiveIndex = i;
            }
        }
        if (activeCount == 1 && lastActiveIndex >= 0) {
            winner = players.get(lastActiveIndex);
            currentPlayerEndedTurn = true;
            fireEvent(GameEventType.WINNER_DETERMINED, "Winner: " + winner.getName());
            return;
        }
        if (activeCount <= 0) {
            return;
        }
        if (currentPlayerIndex < 0) {
            currentPlayerIndex = 0;
        }
        if (currentPlayerIndex >= players.size()) {
            currentPlayerIndex = currentPlayerIndex % players.size();
        }
        if (!players.get(currentPlayerIndex).isEliminated()) {
            return;
        }
        int idx = currentPlayerIndex - 1;
        for (int step = 0; step < players.size(); step++) {
            idx = (idx + 1) % players.size();
            if (!players.get(idx).isEliminated()) {
                currentPlayerIndex = idx;
                return;
            }
        }
    }

    public void eliminatePlayer(int playerIndex) {
        ensureGameStarted();
        if (playerIndex < 0 || playerIndex >= players.size()) {
            return;
        }
        PlayerManagement player = players.get(playerIndex);
        if (player.isEliminated()) {
            return;
        }
        player.setEliminated(true);
        ensureCurrentPlayerIsActiveOrEndGame();
    }

    private void checkDeckExhaustionEndGameIfStuck() {
        if (winner != null || cardManager == null) {
            return;
        }
        if (cardManager.getDrawPileSize() > 0 || cardManager.getDiscardPileSize() > 0) {
            return;
        }
        for (PlayerManagement player : players) {
            if (player.isEliminated()) {
                continue;
            }
            if (player.getHandCardCount() > 0) {
                return;
            }
        }
        winner = determineWinnerByScore();
        currentPlayerEndedTurn = true;
        fireEvent(GameEventType.WINNER_DETERMINED, "Winner: " + winner.getName());
    }

    private PlayerManagement determineWinnerByScore() {
        PlayerManagement best = null;
        int bestCompleteSets = -1;
        int bestPropertyCount = -1;
        int bestBankValue = -1;
        int bestHandCount = -1;

        for (PlayerManagement player : players) {
            if (player.isEliminated()) {
                continue;
            }
            int completeSets = player.getCompleteSetCount();
            int propertyCount = 0;
            for (PropertyZone zone : player.getPropertyZonesView().values()) {
                propertyCount += zone.getPropertiesView().size();
                if (zone.getHouse() != null) propertyCount++;
                if (zone.getHotel() != null) propertyCount++;
            }
            int bankValue = player.getBankTotalValue();
            int handCount = player.getHandCardCount();

            if (best == null
                    || completeSets > bestCompleteSets
                    || (completeSets == bestCompleteSets && propertyCount > bestPropertyCount)
                    || (completeSets == bestCompleteSets && propertyCount == bestPropertyCount && bankValue > bestBankValue)
                    || (completeSets == bestCompleteSets && propertyCount == bestPropertyCount && bankValue == bestBankValue && handCount > bestHandCount)) {
                best = player;
                bestCompleteSets = completeSets;
                bestPropertyCount = propertyCount;
                bestBankValue = bankValue;
                bestHandCount = handCount;
            }
        }
        return best != null ? best : players.get(0);
    }

    private int calculateAssetTotalValue(PlayerManagement player) {
        return player.calculateAssetTotalValue();
    }

    private Color findPropertyCardColor(PlayerManagement player, Card card) {
        return player.findColorOfProperty(card);
    }

    private void transferAllAssetsToCollectorHand(PlayerManagement collector, PlayerManagement payer) {
        payer.transferAllAssetsTo(collector);
        // 不再淘汰玩家
    }

    public void depositMoneyCard(Card card) {
        PlayerManagement currentPlayer = getCurrentPlayer();
        if (!card.canBeUsedAsMoney()) {
            throw new IllegalArgumentException("card cannot be deposited as money: " + card.getName());
        }
        removeFromCurrentPlayerHand(card);
        currentPlayer.depositToBank(card);
        recordPlayedCardAfterExternalResolution();
        fireEvent(GameEventType.CARD_BANKED, currentPlayer.getName() + " banked " + card.getName());
    }

    public void placePropertyCard(Card card, PlayerManagement currentPlayer, Color color) {
        if (card instanceof HouseCard || card instanceof HotelCard || card instanceof BuildingCard) {
            handleBuildingCard(card, currentPlayer, color);
            removeFromCurrentPlayerHand(card);
            recordPlayedCardAfterExternalResolution();
            return;
        }
        if (!(card instanceof PropertyCard propertyCard)) {
            throw new IllegalArgumentException("card is not a property card: " + card.getName());
        }
        if (color == null) {
            throw new IllegalArgumentException("color cannot be null when placing a property card");
        }
        if (!propertyCard.getPlayableColors().contains(color)) {
            throw new IllegalArgumentException("property card cannot be used as " + color);
        }
        removeFromCurrentPlayerHand(card);
        currentPlayer.addProperty(color, propertyCard);
        recordPlayedCardAfterExternalResolution();
        fireEvent(GameEventType.PROPERTY_PLACED, currentPlayer.getName() + " placed " + card.getName() + " as " + color.name());
    }

    private void handleBuildingCard(Card card, PlayerManagement currentPlayer, Color color) {
        if (color == null) {
            throw new IllegalArgumentException("color cannot be null when placing a building card");
        }
        BuildingCard buildingCard;
        if (card instanceof BuildingCard existingBuildingCard) {
            buildingCard = existingBuildingCard;
        } else if (card instanceof HouseCard) {
            buildingCard = new BuildingCard(card.getId(), card.getName(), card.getValue(), HOUSE_ADDED_RENT);
        } else if (card instanceof HotelCard) {
            buildingCard = new BuildingCard(card.getId(), card.getName(), card.getValue(), HOTEL_ADDED_RENT);
        } else {
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

    public void checkVictoryCondition() {
        if (winner != null) {
            return;
        }

        for (PlayerManagement player : players) {
            if (player.hasWon()) {
                winner = player;
                currentPlayerEndedTurn = true;
                fireEvent(GameEventType.WINNER_DETERMINED, "Winner: " + player.getName());
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

