package com.mygame.ui;

import com.mygame.cards.action.*;
import com.mygame.cards.base.*;
import com.mygame.cards.money.*;
import com.mygame.cards.property.*;
import com.mygame.cards.rent.*;
import com.mygame.core.GameManager;
import com.mygame.model.*;
import com.mygame.network.GameClient;
import com.mygame.network.GameServer;
import com.mygame.network.dto.GameStateData;
import com.mygame.ui.components.CardView;
import com.mygame.ui.model.PlayTarget;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.animation.FadeTransition;
import javafx.animation.Animation;
import javafx.animation.TranslateTransition;
import javafx.util.Duration;
import javafx.geometry.Bounds;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class GameController {

    // ---------------- UI components ----------------
    @FXML
    private HBox myHandBox;
    @FXML
    private HBox handActionBox;
    @FXML
    private HBox myBankBox;
    @FXML
    private HBox myPropertyBox;
    @FXML
    private VBox opponentAreaBox;
    @FXML
    private Label clientInfoLabel;
    @FXML
    private Label turnInfoLabel;
    @FXML
    private Button endTurnButton;

    // ---------------- Game data model ----------------
    private GameManager gameManager;
    private Interactor interactor;
    private boolean discardMode;
    private Card selectedHandCard;

    // ---------------- Online mode ----------------
    private boolean isOnlineMode = false;
    private int localPlayerIndex = 0;
    private boolean isMyTurn = false;
    private GameServer gameServer;
    private GameClient gameClient;

    // Static instance used by network callbacks
    private static GameController instance;

    @FXML
    public void initialize() {
        instance = this;
        // Debug: check FXML injection
        System.out.println("GameController initialized");
        System.out.println("myHandBox: " + myHandBox);
        System.out.println("handActionBox: " + handActionBox);
        System.out.println("endTurnButton: " + endTurnButton);
    }

    public static GameController getInstance() {
        return instance;
    }

    // ---------------- 1. Initialization ----------------
    public void initializeGame(int playerCount) {
        System.out.println("initializeGame called: isOnlineMode=" + isOnlineMode
                + ", isHost=" + isHost()
                + ", gameServer=" + (gameServer != null ? "set" : "NULL")
                + ", playerCount=" + playerCount);

        // Only offline mode or host can initialize the game
        if (isOnlineMode && !isHost()) {
            System.out.println("Client should not initialize game - waiting for server state");
            return;
        }

        discardMode = false;
        selectedHandCard = null;

        // Offline: create local GameManager.
        // Online host: use server-side GameManager instance.
        interactor = new Interactor();

        if (isOnlineMode && isHost() && gameServer != null) {
            gameManager = gameServer.getGameManager();
            if (gameManager == null) {
                gameManager = new GameManager();
                gameServer.setGameManager(gameManager);
            }
            // Ensure player count matches and game is started
            if (gameManager.getCardManager() == null) {
                gameManager.setPlayerCount(playerCount);
                gameManager.startRound();
            }
            gameManager.setInteractor(interactor);
        } else {
            gameManager = new GameManager();
            gameManager.setInteractor(interactor);
            gameManager.setPlayerCount(playerCount);
            gameManager.startRound();
        }

        System.out.println("initializeGame complete, gameManager=" + (gameManager != null ? "set" : "NULL")
                + ", calling updateUI");
        updateUI();

        // Online host: when the server broadcasts the initial state before the controller is ready,
        // isMyTurn may stay false until a later state update. Force a local sync once on init.
        if (isOnlineMode && isHost() && gameServer != null) {
            GameStateData last = gameServer.getLastBroadcastState();
            if (last != null) {
                updateFromServerState(last);
            } else if (gameManager != null) {
                updateFromServerState(GameStateData.fromGameManager(gameManager));
            }
        }
    }

    public void setOnlineMode(boolean online, int playerIndex) {
        this.isOnlineMode = online;
        this.localPlayerIndex = playerIndex;

        // Prevent the host from being blocked before the first GAME_STATE arrives.
        // The authoritative turn state will still be updated by updateFromServerState().
        if (online && playerIndex == 0) {
            isMyTurn = true;
            if (endTurnButton != null) {
                endTurnButton.setDisable(false);
            }
        }
    }

    public void setGameServer(GameServer gameServer) {
        this.gameServer = gameServer;
    }

    public void setGameClient(GameClient gameClient) {
        this.gameClient = gameClient;
    }

    private boolean isHost() {
        return localPlayerIndex == 0;
    }

    public void updateFromServerState(GameStateData state) {
        // Update UI based on server state
        if (state == null) return;

        if (isOnlineMode && !isHost()) {
            syncLocalGameManagerFromState(state);
        }

        System.out.println("updateFromServerState called: currentPlayerIndex=" + state.getCurrentPlayerIndex()
                + ", localPlayerIndex=" + localPlayerIndex
                + ", isMyTurn=" + (state.getCurrentPlayerIndex() == localPlayerIndex)
                + ", isHost=" + isHost()
                + ", handCardCount=" + (state.getPlayers().size() > localPlayerIndex
                ? state.getPlayers().get(localPlayerIndex).getHandCards().size() : "N/A"));

        boolean wasMyTurn = isMyTurn;
        isMyTurn = (state.getCurrentPlayerIndex() == localPlayerIndex);
        int localHandCount = state.getPlayers().size() > localPlayerIndex
                ? state.getPlayers().get(localPlayerIndex).getHandCards().size()
                : 0;
        if (discardMode && (!isMyTurn || localHandCount <= PlayerManagement.MAX_HAND_SIZE)) {
            discardMode = false;
            selectedHandCard = null;
            handActionBox.getChildren().clear();
        }
        endTurnButton.setDisable(!isMyTurn);

        updateClientInfo();
        turnInfoLabel.setText("Plays: " + state.getPlayedCardsThisTurn() + "/" + state.getMaxPlayCountPerTurn());

        // New turn: clear selection
        if (!wasMyTurn && isMyTurn) {
            selectedHandCard = null;
            handActionBox.getChildren().clear();
        }

        // Host: render from real GameManager objects
        // Client: render from DTOs
        if (isOnlineMode && isHost() && gameManager != null) {
            System.out.println("Host: refreshing UI from local gameManager");
            updateUI();
        } else if (isOnlineMode && !isHost()) {
            System.out.println("Client: rendering from server state");
            updateHandCardsFromServer(state);
            updateBankCardsFromServer(state);
            updatePropertyCardsFromServer(state);
            updateOpponentAreaFromServer(state);
            updateCardDisabledState();
        }
    }

    private void updateCardDisabledState() {
        System.out.println("updateCardDisabledState called, isMyTurn: " + isMyTurn);
        System.out.println("myHandBox children count: " + myHandBox.getChildren().size());

        // Update disabled state for all hand cards
        for (var node : myHandBox.getChildren()) {
            if (node instanceof CardView cardView) {
                boolean shouldDisable = !isMyTurn;
                cardView.setDisable(shouldDisable);
                System.out.println("Card disabled: " + shouldDisable + ", card: " + cardView);
            }
        }
    }

    private void updateHandCardsFromServer(GameStateData state) {
        // Get local player data
        List<GameStateData.PlayerData> players = state.getPlayers();
        if (localPlayerIndex >= players.size()) {
            return;
        }

        GameStateData.PlayerData localPlayerData = players.get(localPlayerIndex);

        // Clear current hand UI
        myHandBox.getChildren().clear();

        // Add hand cards with click handlers
        for (GameStateData.CardData cardData : localPlayerData.getHandCards()) {
            Card card = cardData.toCard();
            CardView cardView = new CardView(card);
            cardView.setOnAction(event -> {
                System.out.println("Card clicked: " + card.getName());
                handleCardClick(card);
            });
            // Ensure card is clickable
            cardView.setDisable(false);
            cardView.setOpacity(1.0);
            cardView.setMouseTransparent(false);
            myHandBox.getChildren().add(cardView);
        }
        System.out.println("Hand cards updated: " + localPlayerData.getHandCards().size() + " cards");
    }

    private void updateBankCardsFromServer(GameStateData state) {
        List<GameStateData.PlayerData> players = state.getPlayers();
        if (localPlayerIndex >= players.size()) return;

        GameStateData.PlayerData localPlayerData = players.get(localPlayerIndex);
        myBankBox.getChildren().clear();

        for (GameStateData.CardData cardData : localPlayerData.getBankCards()) {
            Card card = cardData.toCard();
            CardView cardView = new CardView(card, true);
            cardView.setDisable(true);
            myBankBox.getChildren().add(cardView);
        }

        if (myBankBox.getChildren().isEmpty()) {
            Label emptyView = new Label("No cards");
            emptyView.setStyle("-fx-text-fill: #666666;");
            myBankBox.getChildren().add(emptyView);
        }
    }

    private void updatePropertyCardsFromServer(GameStateData state) {
        List<GameStateData.PlayerData> players = state.getPlayers();
        if (localPlayerIndex >= players.size()) return;

        GameStateData.PlayerData localPlayerData = players.get(localPlayerIndex);
        myPropertyBox.getChildren().clear();

        for (var entry : localPlayerData.getPropertyZones().entrySet()) {
            Color color = entry.getKey();
            GameStateData.PropertyZoneData zoneData = entry.getValue();

            VBox colorGroup = new VBox(8);
            colorGroup.setPadding(new Insets(8));
            colorGroup.setStyle("-fx-background-color: #fafafa; -fx-border-color: #d9d9d9; -fx-border-radius: 8; -fx-background-radius: 8;");

            PlayerManagement localPlayer = gameManager != null && localPlayerIndex < gameManager.getPlayersView().size()
                    ? gameManager.getPlayersView().get(localPlayerIndex)
                    : null;
            Label colorTitle = new Label(buildPropertySetTitle(localPlayer, color, zoneData.getProperties().size()));
            colorTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + toFxColor(color) + ";");
            colorGroup.getChildren().add(colorTitle);

            HBox propertyRow = new HBox(8);
            propertyRow.setAlignment(Pos.CENTER_LEFT);

            for (GameStateData.CardData cardData : zoneData.getProperties()) {
                Card card = cardData.toCard();
                CardView cardView = new CardView(card, true);
                cardView.setDisable(!canClickPropertyAreaCard(card));
                cardView.setOnAction(event -> handlePropertyCardClick(card));
                propertyRow.getChildren().add(cardView);
            }

            if (zoneData.getHouse() != null) {
                Card card = zoneData.getHouse().toCard();
                CardView cardView = new CardView(card, true);
                cardView.setDisable(true);
                propertyRow.getChildren().add(cardView);
            }

            if (zoneData.getHotel() != null) {
                Card card = zoneData.getHotel().toCard();
                CardView cardView = new CardView(card, true);
                cardView.setDisable(true);
                propertyRow.getChildren().add(cardView);
            }

            colorGroup.getChildren().add(propertyRow);
            myPropertyBox.getChildren().add(colorGroup);
        }

        if (myPropertyBox.getChildren().isEmpty()) {
            Label emptyView = new Label("No properties");
            emptyView.setStyle("-fx-text-fill: #666666;");
            myPropertyBox.getChildren().add(emptyView);
        }
    }

    private void updateOpponentAreaFromServer(GameStateData state) {
        opponentAreaBox.getChildren().clear();
        List<GameStateData.PlayerData> players = state.getPlayers();

        for (int i = 0; i < players.size(); i++) {
            if (i == localPlayerIndex) continue;

            GameStateData.PlayerData playerData = players.get(i);
            VBox container = new VBox(8);
            container.setPadding(new Insets(12));
            container.getStyleClass().add("player-card");
            if (i == state.getCurrentPlayerIndex()) {
                container.getStyleClass().add("current-turn");
                installTurnPulse(container);
            }

            Label nameLabel = new Label(playerData.getPlayerName());
            nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

            Label handCountLabel = new Label("Hand cards: " + playerData.getHandCardCount());

            HBox bankRow = new HBox(8);
            bankRow.setAlignment(Pos.CENTER_LEFT);
            bankRow.getChildren().add(new Label("Bank:"));
            if (playerData.getBankCards().isEmpty()) {
                bankRow.getChildren().add(new Label("None"));
            } else {
                for (GameStateData.CardData cardData : playerData.getBankCards()) {
                    Card card = cardData.toCard();
                    CardView cardView = new CardView(card, true);
                    cardView.setDisable(true);
                    bankRow.getChildren().add(cardView);
                }
            }

            HBox propertyRow = new HBox(8);
            propertyRow.setAlignment(Pos.CENTER_LEFT);
            propertyRow.getChildren().add(new Label("Properties:"));
            boolean hasProperty = false;
            for (var entry : playerData.getPropertyZones().entrySet()) {
                Color color = entry.getKey();
                GameStateData.PropertyZoneData zoneData = entry.getValue();
                PlayerManagement player = gameManager != null && i < gameManager.getPlayersView().size()
                        ? gameManager.getPlayersView().get(i)
                        : null;
                Label colorLabel = new Label("[" + buildPropertySetTitle(player, color, zoneData.getProperties().size()) + "]");
                colorLabel.setStyle("-fx-padding: 6 10; -fx-background-color: " + toSoftFxColor(color)
                        + "; -fx-border-color: " + toFxColor(color) + "; -fx-border-radius: 6; -fx-font-weight: bold;");
                propertyRow.getChildren().add(colorLabel);
                hasProperty = true;
            }
            if (!hasProperty) {
                propertyRow.getChildren().add(new Label("None"));
            }

            container.getChildren().addAll(nameLabel, handCountLabel, bankRow, propertyRow);
            opponentAreaBox.getChildren().add(container);
        }

        if (opponentAreaBox.getChildren().isEmpty()) {
            Label emptyView = new Label("No other players");
            emptyView.setStyle("-fx-text-fill: #666666;");
            opponentAreaBox.getChildren().add(emptyView);
        }
    }

    // ---------------- 2. UI refresh ----------------
    private void updateUI() {
        // Online client may not have a local GameManager; keep UI in sync via DTO only
        if (isOnlineMode && !isHost() && gameManager == null) {
            updateClientInfo();
            turnInfoLabel.setText("Waiting for server...");
            return;
        }

        PlayerManagement currentPlayer = gameManager.getCurrentPlayer();
        // In online mode always render local player's hand (not the current turn player's hand)
        PlayerManagement localPlayer = isOnlineMode
                ? gameManager.getPlayersView().get(localPlayerIndex)
                : currentPlayer;

        updateClientInfo();
        turnInfoLabel.setText("Remaining plays: " + gameManager.getRemainingPlayCountThisTurn());
        renderOpponentArea(localPlayer);
        renderHandCards(localPlayer);
        renderBankCards(localPlayer);
        renderPropertyCards(localPlayer);
        renderHandActionButtons();
    }

    private void renderOpponentArea(PlayerManagement currentPlayer) {
        opponentAreaBox.getChildren().clear();

        List<PlayerManagement> players = gameManager.getPlayersView();
        int turnIndex = gameManager.getCurrentPlayerIndex();
        for (PlayerManagement player : players) {
            if (player == currentPlayer) {
                continue;
            }
            boolean isTurnPlayer = players.indexOf(player) == turnIndex;
            VBox playerCard = createPlayerSummaryCard(player, false, isTurnPlayer);
            opponentAreaBox.getChildren().add(playerCard);
        }

        if (opponentAreaBox.getChildren().isEmpty()) {
            Label emptyView = new Label("No other players");
            emptyView.setStyle("-fx-text-fill: #666666;");
            opponentAreaBox.getChildren().add(emptyView);
        }
    }

    // Render hand cards
    private void renderHandCards(PlayerManagement player) {
        renderCardButtonRow(myHandBox, player.getHandCards(), true);
    }

    // Render bank cards
    private void renderBankCards(PlayerManagement player) {
        myBankBox.getChildren().clear();
        for (Card card : player.getBankCardsView()) {
            CardView cardView = new CardView(card, true);
            cardView.setDisable(true);
            cardView.setOnAction(event -> handleBankCardClick(card));
            myBankBox.getChildren().add(cardView);
        }

        if (myBankBox.getChildren().isEmpty()) {
            Label emptyView = new Label("No cards");
            emptyView.setStyle("-fx-text-fill: #666666;");
            myBankBox.getChildren().add(emptyView);
        }
    }

    // Render property cards
    private void renderPropertyCards(PlayerManagement player) {
        myPropertyBox.getChildren().clear();

        for (Map.Entry<Color, PropertyZone> entry : player.getPropertyZonesView().entrySet()) {
            Color color = entry.getKey();
            PropertyZone zone = entry.getValue();
            int currentCount = player.getPropertyCount(color);

            VBox colorGroup = new VBox(8);
            colorGroup.setPadding(new Insets(8));
            colorGroup.setStyle("-fx-background-color: #fafafa; -fx-border-color: #d9d9d9; -fx-border-radius: 8; -fx-background-radius: 8;");

            Label colorTitle = new Label(buildPropertySetTitle(player, color, currentCount));
            colorTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + toFxColor(color) + ";");
            colorGroup.getChildren().add(colorTitle);

            HBox propertyRow = new HBox(8);
            propertyRow.setAlignment(Pos.CENTER_LEFT);

            boolean hasAnyCard = false;
            for (PropertyCard propertyCard : zone.getPropertiesView()) {
                hasAnyCard = true;
                CardView cardView = new CardView(propertyCard, true);
                cardView.setDisable(!canClickPropertyAreaCard(propertyCard));
                cardView.setOnAction(event -> handlePropertyCardClick(propertyCard));
                propertyRow.getChildren().add(cardView);
            }

            if (zone.getHouse() != null) {
                hasAnyCard = true;
                BuildingCard house = zone.getHouse();
                CardView cardView = new CardView(house, true);
                cardView.setDisable(true);
                propertyRow.getChildren().add(cardView);
            }

            if (zone.getHotel() != null) {
                hasAnyCard = true;
                BuildingCard hotel = zone.getHotel();
                CardView cardView = new CardView(hotel, true);
                cardView.setDisable(true);
                propertyRow.getChildren().add(cardView);
            }

            if (!hasAnyCard) {
                propertyRow.getChildren().add(new Label("None"));
            }

            colorGroup.getChildren().add(propertyRow);
            myPropertyBox.getChildren().add(colorGroup);
        }

        if (myPropertyBox.getChildren().isEmpty()) {
            Label emptyView = new Label("No properties");
            emptyView.setStyle("-fx-text-fill: #666666;");
            myPropertyBox.getChildren().add(emptyView);
        }
    }

    private void handleBankCardClick(Card card) {
        if (gameManager != null && gameManager.canSelectBankCard()) {
            gameManager.cacheSelectedBankCard(card);
        }
    }

    private void handlePropertyCardClick(Card card) {
        if (gameManager != null && gameManager.canSelectPropertyCard()) {
            gameManager.cacheSelectedPropertyCard(card);
            return;
        }

        if (!(card instanceof PropertyCard propertyCard) || !canSwitchPropertyColor(card)) {
            return;
        }

        Color selectedColor = promptForPropertyColor(propertyCard, propertyCard.getPlayableColors());
        if (selectedColor == null) {
            return;
        }

        if (isOnlineMode) {
            sendActionToServer("SWITCH_PROPERTY_COLOR:" + card.getId() + ":" + selectedColor.name());
            return;
        }

        PlayerManagement owner = gameManager.getCurrentPlayer();
        if (owner.movePropertyCardToColor(propertyCard, selectedColor)) {
            updateUI();
        }
    }

    private boolean canSwitchPropertyColor(Card card) {
        if (!(card instanceof BiColorWildPropertyCard || card instanceof MultiColorWildPropertyCard)) {
            return false;
        }
        if (isOnlineMode) {
            return isMyTurn;
        }
        return gameManager != null && gameManager.getCurrentPlayerIndex() >= 0;
    }

    private boolean canClickPropertyAreaCard(Card card) {
        return (gameManager != null && gameManager.canSelectPropertyCard() && card instanceof PropertyCard)
                || canSwitchPropertyColor(card);
    }

    // Card click handling in online mode
    private void handleOnlineCardClick(Card card) {
        System.out.println("handleOnlineCardClick called: " + card.getName()
                + ", cardType: " + card.getCardType()
                + ", isMyTurn: " + isMyTurn
                + ", gameClient: " + (gameClient != null ? "set" : "NULL"));
        if (!isMyTurn) {
            showError("It's not your turn!");
            return;
        }

        selectedHandCard = card;
        renderOnlineHandActionButtons();
        System.out.println("Online action buttons rendered for card: " + card.getName());
    }

    // Online host: broadcast state to all clients
    private void broadcastStateIfHost() {
        if (isOnlineMode && isHost() && gameServer != null) {
            gameServer.broadcastGameState();
        }
    }

    // Online client: send actions to server
    private void sendActionToServer(String action) {
        if (!isOnlineMode) return;
        System.out.println("sendActionToServer: " + action);
        if (!isHost() && gameClient != null) {
            gameClient.sendAction(action);
        } else if (isHost() && gameServer != null) {
            gameServer.processHostAction(action);
        } else {
            System.out.println("sendActionToServer: no valid connection");
        }
    }

    private void renderOnlineHandActionButtons() {
        handActionBox.getChildren().clear();
        if (selectedHandCard == null) {
            System.out.println("renderOnlineHandActionButtons: selectedHandCard is null");
            return;
        }
        System.out.println("renderOnlineHandActionButtons: " + selectedHandCard.getName() + ", type: " + selectedHandCard.getCardType());

        HBox buttonRow = new HBox(14);
        buttonRow.setAlignment(Pos.CENTER);
        buttonRow.setMaxWidth(Double.MAX_VALUE);

        List<Button> buttons = new ArrayList<>();

        // Add additional options based on card type
        if (isRentCard(selectedHandCard)) {
            buttons.add(createActionOptionButton("Play as action card", () -> sendPlayAction(selectedHandCard)));
            buttons.add(createActionOptionButton("Deposit to Bank", () -> sendDepositAction(selectedHandCard)));
        } else {
            buttons.add(createActionOptionButton("Deposit to Bank", () -> sendDepositAction(selectedHandCard)));
            if (selectedHandCard.isActionCard()) {
                if (selectedHandCard instanceof DoubleTheRentCard) {
                    showError("Double The Rent cannot be played alone! Use it with a Rent card.");
                } else {
                    buttons.add(createActionOptionButton("Play as action card", () -> sendPlayAction(selectedHandCard)));
                }
            } else if (selectedHandCard.isPropertyCard()) {
                buttons.add(createActionOptionButton("Place as property", () -> sendPlacePropertyAction(selectedHandCard)));
            }
        }

        buttonRow.getChildren().addAll(buttons);
        handActionBox.getChildren().add(buttonRow);
        System.out.println("Added " + buttons.size() + " buttons");
    }

    private void handleOnlineSlyDeal(Card card) {
        PlayerManagement currentPlayer = gameManager.getPlayersView().get(localPlayerIndex);

        PlayerManagement targetPlayer = interactor.choiceTargetPlayer(currentPlayer, gameManager.getPlayersView());
        if (targetPlayer == null) return; // User canceled

        // Check whether target has any property cards
        if (!hasAnyProperty(targetPlayer)) {
            showError("Takeover failed: target player has no property cards to take.");
            return;
        }

        Card targetCard = interactor.choiceStealablePropertyCard(targetPlayer);
        if (targetCard == null) return;

        // Build args and send to server
        String actionStr = "PLAY_ACTION:" + card.getId() + ":" + targetPlayer.getPlayerId() + ":" + targetCard.getId();
        sendActionToServer(actionStr);

        removeCardFromHandUI(card);
        selectedHandCard = null;
        handActionBox.getChildren().clear();
    }

    // --- Online Forced Deal ---
    private void handleOnlineForcedDeal(Card card) {
        PlayerManagement currentPlayer = gameManager.getPlayersView().get(localPlayerIndex);

        // Check whether we have a property to trade
        if (!hasAnyProperty(currentPlayer)) {
            showError("Trade failed: you must have at least one property card on the table to use Forced Deal.");
            return;
        }

        PlayerManagement targetPlayer = interactor.choiceTargetPlayer(currentPlayer, gameManager.getPlayersView());
        if (targetPlayer == null) return;

        // Check whether target has a property to trade
        if (!hasAnyProperty(targetPlayer)) {
            showError("Trade failed: target player has no tradable property cards.");
            return;
        }

        Card myCard = interactor.choicePorperty(currentPlayer);
        if (myCard == null) return;

        Card targetCard = interactor.choicePorperty(targetPlayer);
        if (targetCard == null) return;

        // Build args
        String actionStr = "PLAY_ACTION:" + card.getId() + ":" + targetPlayer.getPlayerId() + ":" + myCard.getId() + ":" + targetCard.getId();
        sendActionToServer(actionStr);

        removeCardFromHandUI(card);
        selectedHandCard = null;
        handActionBox.getChildren().clear();
    }


    // --- Action dispatch routing (online) ---
    private void sendPlayAction(Card card) {
        // 1) Block helper cards that cannot be played alone
        if (card instanceof DoubleTheRentCard) {
            showError("Operation failed: Double The Rent cannot be played alone. You will be prompted when playing a rent card.");
            selectedHandCard = null; handActionBox.getChildren().clear();
            return;
        } else if (card instanceof JustSayNoCard) {
            showError("Operation failed: Just Say No cannot be played proactively. You will be prompted when targeted.");
            selectedHandCard = null; handActionBox.getChildren().clear();
            return;
        }

        // 2) Cards requiring target or parameters
        if (card instanceof SlyDealCard) {
            handleOnlineSlyDeal(card);
            return;
        } else if (card instanceof ForcedDealCard) {
            handleOnlineForcedDeal(card);
            return;
        } else if (card instanceof DealBreakerCard) {
            handleOnlineDealBreaker(card);
            return;
        } else if (card instanceof DebtCollectorCard) {
            handleOnlineDebtCollector(card);
            return;
        } else if (card instanceof HouseCard || card instanceof HotelCard) {
            handleOnlineBuilding(card);
            return;
        } else if (card.getCardType() == CardType.RENT_BICOLOR || card.getCardType() == CardType.RENT_WILDCOLOR) {
            handleOnlineRent(card); // Route all rent cards through this handler
            return;
        }

        // 3) No-parameter action cards
        sendActionToServer("PLAY_ACTION:" + card.getId());
        removeCardFromHandUI(card);
        selectedHandCard = null;
        handActionBox.getChildren().clear();
    }
    // --- Handle rent cards and optionally stack Double The Rent ---
    private void handleOnlineRent(Card rentCard) {
        PlayerManagement currentPlayer = gameManager.getPlayersView().get(localPlayerIndex);

        Color selectedColor = promptForRentColor(rentCard, currentPlayer);
        if (selectedColor == null) return;
        if (rentCard instanceof BiColorRentCard biColorRentCard) {
            biColorRentCard.setSelectedColor(selectedColor);
        }

        if (currentPlayer.getRent(selectedColor) <= 0) {
            showError("No rent available for " + selectedColor.getDisplayName() + ".");
            return;
        }

        // 2) Check if we have a Double The Rent card
        Card doubleCard = null;
        for (Card c : currentPlayer.getHandCardsView()) {
            if (c instanceof DoubleTheRentCard) {
                doubleCard = c;
                break;
            }
        }

        // 3) Ask whether to stack it
        String doubleCardId = "NONE";
        if (doubleCard != null && gameManager.getRemainingPlayCountThisTurn() >= 2) {
            Alert alert = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    "You have a Double The Rent card. Play it together to double this rent?",
                    ButtonType.YES,
                    ButtonType.NO
            );
            alert.setTitle("Double The Rent found");
            alert.setHeaderText(null);
            if (alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                doubleCardId = doubleCard.getId();
            }
        }

        // 4) Send action to server with rent mode + optional double card id
        if (rentCard.getCardType() == CardType.RENT_WILDCOLOR) {
            // Wild rent targets a single player
            PlayerManagement targetPlayer = interactor.choiceTargetPlayer(currentPlayer, gameManager.getPlayersView());
            if (targetPlayer == null) return;
            // PLAY_ACTION:<rentId>:WILD_RENT:<color>:<targetPlayerId>:<doubleCardId>
            sendActionToServer("PLAY_ACTION:" + rentCard.getId() + ":WILD_RENT:" + selectedColor.name() + ":" + targetPlayer.getPlayerId() + ":" + doubleCardId);
        } else {
            // Bi-color rent targets all opponents:
            // PLAY_ACTION:<rentId>:BI_RENT:<color>:<doubleCardId>
            sendActionToServer("PLAY_ACTION:" + rentCard.getId() + ":BI_RENT:" + selectedColor.name() + ":" + doubleCardId);
        }

        removeCardFromHandUI(rentCard);
        selectedHandCard = null;
        handActionBox.getChildren().clear();
    }

    // --- Online house/hotel: player chooses which color set to attach to ---
    private void handleOnlineBuilding(Card buildingCard) {
        PlayerManagement currentPlayer = gameManager.getPlayersView().get(localPlayerIndex);

        PropertyZone zone = interactor.choiceBuildingPropertyZone(currentPlayer, buildingCard);
        if (zone == null) return;
        Color selectedColor = zone.getColor();

        // PLAY_ACTION:<cardId>:BUILDING:<color>
        sendActionToServer("PLAY_ACTION:" + buildingCard.getId() + ":BUILDING:" + selectedColor.name());

        removeCardFromHandUI(buildingCard);
        selectedHandCard = null;
        handActionBox.getChildren().clear();
    }
    private void handleOnlineDealBreaker(Card card) {
        PlayerManagement currentPlayer = gameManager.getPlayersView().get(localPlayerIndex);

        PlayerManagement targetPlayer = interactor.choiceTargetPlayer(currentPlayer, gameManager.getPlayersView());
        if (targetPlayer == null) return;

        PropertyZone selectedZone = interactor.choicePropertyZone(targetPlayer);
        if (selectedZone == null) return;

        if (!targetPlayer.isSetComplete(selectedZone.getColor())) {
            showError("Takeover failed: only complete sets can be taken.");
            return;
        }

        String actionStr = "PLAY_ACTION:" + card.getId() + ":" + targetPlayer.getPlayerId() + ":" + selectedZone.getColor().name();
        sendActionToServer(actionStr);

        removeCardFromHandUI(card);
        selectedHandCard = null;
        handActionBox.getChildren().clear();
    }

    // --- Online Debt Collector ---
    private void handleOnlineDebtCollector(Card card) {

        PlayerManagement currentPlayer =
                gameManager.getPlayersView().get(localPlayerIndex);

        // Choose target player
        PlayerManagement targetPlayer =
                interactor.choiceTargetPlayer(
                        currentPlayer,
                        gameManager.getPlayersView()
                );

        if (targetPlayer == null) {
            return;
        }

        // Send to server:
        // PLAY_ACTION:<cardId>:<targetPlayerId>
        String actionStr =
                "PLAY_ACTION:"
                        + card.getId()
                        + ":"
                        + targetPlayer.getPlayerId();

        sendActionToServer(actionStr);

        removeCardFromHandUI(card);
        selectedHandCard = null;
        handActionBox.getChildren().clear();
    }

    private boolean hasAnyProperty(PlayerManagement player) {
        if (player == null) return false;
        for (PropertyZone zone : player.getPropertyZonesView().values()) {
            if (!zone.getPropertiesView().isEmpty()) return true;
        }
        return false;
    }

    private void sendDepositAction(Card card) {
        sendActionToServer("DEPOSIT:" + card.getId());
        removeCardFromHandUI(card);
        selectedHandCard = null;
        handActionBox.getChildren().clear();
    }

    private void sendPlacePropertyAction(Card card) {
        if (!(card instanceof PropertyCard propertyCard)) {
            showError("This card cannot be placed as property: " + card.getName());
            return;
        }

        Set<Color> playableColors = propertyCard.getPlayableColors();
        if (playableColors == null || playableColors.isEmpty()) {
            showError("This property card has no available color to place it on");
            return;
        }

        Color selectedColor;
        if (playableColors.size() == 1) {
            selectedColor = playableColors.iterator().next();
        } else {
            selectedColor = promptForPropertyColor(propertyCard, playableColors);
            if (selectedColor == null) {
                return;
            }
        }

        sendActionToServer("PLACE_PROPERTY:" + card.getId() + ":" + selectedColor.name());
        removeCardFromHandUI(card);
        selectedHandCard = null;
        handActionBox.getChildren().clear();
    }

    private void renderHandActionButtons() {

        handActionBox.getChildren().clear();

        if (selectedHandCard == null) {
            return;
        }

        if (isOnlineMode && gameManager == null) {

            renderOnlineHandActionButtons();

            return;
        }

        if (gameManager == null) {
            return;
        }

        boolean canPlayCard = gameManager.canCurrentPlayerPlayCard();
        if (!canPlayCard) {
            return;
        }

        HBox buttonRow = new HBox(14);
        buttonRow.setAlignment(Pos.CENTER);
        buttonRow.setMaxWidth(Double.MAX_VALUE);

        List<Button> buttons = new ArrayList<>();
        if (selectedHandCard.isMoneyCard()) {
            if (canPlayCard) {
                buttons.add(createActionOptionButton("Deposit to Bank", () -> doDepositToBank(selectedHandCard)));
            }
        } else if (selectedHandCard.isActionCard()) {
            if (isRentCard(selectedHandCard)) {
                buttons.add(createActionOptionButton("Play as action card", () -> doPlayActionCard(selectedHandCard)));
                buttons.add(createActionOptionButton("Deposit to Bank", () -> doDepositToBank(selectedHandCard)));
            } else if (canPlayCard) {
                buttons.add(createActionOptionButton("Play as action card", () -> doPlayActionCard(selectedHandCard)));
                buttons.add(createActionOptionButton("Deposit to Bank", () -> doDepositToBank(selectedHandCard)));
            }
        } else if (selectedHandCard.isPropertyCard()) {
            if (canPlayCard) {
                buttons.add(createActionOptionButton("Deposit to Bank", () -> doDepositToBank(selectedHandCard)));
                buttons.add(createActionOptionButton("Place as property", () -> doPlacePropertyCard(selectedHandCard)));
            }
        }

        if (buttons.isEmpty()) {
            return;
        }

        buttonRow.getChildren().addAll(buttons);
        handActionBox.getChildren().add(buttonRow);
    }


    private Button createActionOptionButton(String text, Runnable action) {
        Button button = new Button(text);
        button.setMinHeight(42);
        button.setMinWidth(132);
        button.setStyle("-fx-padding: 12 20; -fx-font-size: 15px; -fx-font-weight: bold; -fx-background-color: linear-gradient(#ffffff, #f2f2f2); -fx-text-fill: #2f4f6f; -fx-border-color: #7aa2ff; -fx-border-width: 2; -fx-border-radius: 12; -fx-background-radius: 12; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.14), 8, 0.2, 0, 2);");
        button.setOnAction(event -> action.run());
        return button;
    }

    private boolean isRentCard(Card card) {
        return card != null
                && (card.getCardType() == CardType.RENT_BICOLOR
                || card.getCardType() == CardType.RENT_WILDCOLOR);
    }

    private Color promptForRentColor(Card rentCard, PlayerManagement currentPlayer) {
        List<Color> choices = new ArrayList<>();
        if (rentCard instanceof BiColorRentCard biColorRentCard) {
            for (Color color : biColorRentCard.getValidColors()) {
                if (currentPlayer.getRent(color) > 0) {
                    choices.add(color);
                }
            }
        } else {
            for (Color color : currentPlayer.getPropertyZonesView().keySet()) {
                if (currentPlayer.getRent(color) > 0) {
                    choices.add(color);
                }
            }
        }

        if (choices.isEmpty()) {
            showError("No available property color can collect rent.");
            return null;
        }

        ChoiceDialog<Color> dialog = new ChoiceDialog<>(choices.get(0), choices);
        dialog.setTitle("Choose Rent Color");
        dialog.setHeaderText("Choose the property color for " + rentCard.getName());
        dialog.setContentText("Color:");
        return dialog.showAndWait().orElse(null);
    }

    private String getBiColorRentSwitchText(Card card) {
        BiColorRentCard rentCard = (BiColorRentCard) card;
        Color nextColor = getNextBiColorRentColor(rentCard);
        return "Switch to " + nextColor.getDisplayName();
    }

    private String getBiColorRentPlayText(Card card) {
        BiColorRentCard rentCard = (BiColorRentCard) card;
        return "Collect " + rentCard.getSelectedColor().getDisplayName() + " rent";
    }

    private Color getNextBiColorRentColor(BiColorRentCard rentCard) {
        for (Color color : rentCard.getValidColors()) {
            if (color != rentCard.getSelectedColor()) {
                return color;
            }
        }
        return rentCard.getSelectedColor();
    }

    private void switchBiColorRentColor(Card card) {
        if (!(card instanceof BiColorRentCard rentCard)) {
            return;
        }
        rentCard.switchToNextColor();
        if (isOnlineMode && !isHost() && gameManager == null) {
            renderOnlineHandActionButtons();
        } else {
            updateUI();
        }
    }

    private void doPlayActionCard(Card card) {
        if (isRentCard(card)) {
            doPlayRentCard(card);
            return;
        }

        try {
            gameManager.playActionCard(card);
            selectedHandCard = null;
            updateUI();
            broadcastStateIfHost();
        } catch (Exception e) {
            showError("Action failed: " + e.getMessage());
        }
    }

    private void doPlayRentCard(Card rentCard) {
        try {
            PlayerManagement currentPlayer = gameManager.getCurrentPlayer();
            Color selectedColor = promptForRentColor(rentCard, currentPlayer);
            if (selectedColor == null) {
                return;
            }
            if (rentCard instanceof BiColorRentCard biColorRentCard) {
                biColorRentCard.setSelectedColor(selectedColor);
            }

            int rentAmount = currentPlayer.getRent(selectedColor);
            if (rentAmount <= 0) {
                showError("No rent available for " + selectedColor.getDisplayName() + ".");
                return;
            }

            PlayerManagement targetPlayer = null;
            if (rentCard.getCardType() == CardType.RENT_WILDCOLOR) {
                targetPlayer = interactor.choiceTargetPlayer(currentPlayer, gameManager.getPlayersView());
                if (targetPlayer == null) {
                    return;
                }
            }

            rentAmount = gameManager.resolveRentAmountWithDoubleTheRent(currentPlayer, selectedColor, rentAmount);
            gameManager.removeFromCurrentPlayerHand(rentCard);
            gameManager.getCardManager().playCard(rentCard);
            gameManager.recordPlayedCardAfterExternalResolution();

            if (rentCard.getCardType() == CardType.RENT_WILDCOLOR) {
                gameManager.chargePlayer(currentPlayer, targetPlayer, rentAmount);
            } else {
                gameManager.chargeAllOpponents(currentPlayer, rentAmount);
            }

            selectedHandCard = null;
            updateUI();
            broadcastStateIfHost();
        } catch (Exception e) {
            showError("Action failed: " + e.getMessage());
        }
    }

    private void doDepositToBank(Card card) {
        animateHandCardToTarget(card, myBankBox, () -> {
            try {
                gameManager.depositMoneyCard(card);
                selectedHandCard = null;
                updateUI();
                broadcastStateIfHost();
            } catch (Exception e) {
                showError("Operation failed: " + e.getMessage());
            }
        });
    }

    private void doPlacePropertyCard(Card card) {
        try {
            if (!(card instanceof PropertyCard propertyCard)) {
                throw new IllegalArgumentException("This card cannot be placed as property: " + card.getName());
            }

            Set<Color> playableColors = propertyCard.getPlayableColors();
            if (playableColors == null || playableColors.isEmpty()) {
                throw new IllegalStateException("This property card has no available color to place it on");
            }

            Color selectedColor;
            if (playableColors.size() == 1) {
                selectedColor = playableColors.iterator().next();
            } else {
                selectedColor = promptForPropertyColor(propertyCard, playableColors);
                if (selectedColor == null) {
                    return;
                }
            }

            Color finalSelectedColor = selectedColor;
            animateHandCardToTarget(card, myPropertyBox, () -> {
                try {
                    gameManager.placePropertyCard(propertyCard, gameManager.getCurrentPlayer(), finalSelectedColor);
                    selectedHandCard = null;
                    updateUI();
                    broadcastStateIfHost();
                } catch (Exception e) {
                    showError("Operation failed: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            showError("Operation failed: " + e.getMessage());
        }
    }

    private void removeCardFromHandUI(Card card) {
        if (card == null || myHandBox == null) return;
        myHandBox.getChildren().removeIf(node ->
            node instanceof CardView cv && cv.getCard() != null && cv.getCard().getId().equals(card.getId()));
    }

    private void animateHandCardToTarget(Card card, javafx.scene.Node targetNode, Runnable after) {
        if (card == null || myHandBox == null || targetNode == null) {
            if (after != null) after.run();
            return;
        }

        CardView foundView = null;
        for (var node : myHandBox.getChildren()) {
            if (node instanceof CardView cv) {
                if (cv.getCard() != null && cv.getCard().getId().equals(card.getId())) {
                    foundView = cv;
                    break;
                }
            }
        }

        final CardView sourceView = foundView;
        if (sourceView == null || sourceView.getScene() == null) {
            if (after != null) after.run();
            return;
        }

        Bounds from = sourceView.localToScene(sourceView.getBoundsInLocal());
        Bounds to = targetNode.localToScene(targetNode.getBoundsInLocal());
        double dx = (to.getMinX() - from.getMinX());
        double dy = (to.getMinY() - from.getMinY());

        sourceView.setDisable(true);
        sourceView.toFront();

        TranslateTransition tt = new TranslateTransition(Duration.millis(220), sourceView);
        tt.setByX(dx);
        tt.setByY(dy);
        tt.setOnFinished(e -> {
            sourceView.setTranslateX(0);
            sourceView.setTranslateY(0);
            if (after != null) after.run();
        });
        tt.play();
    }

    private Button createChoiceStyleButton(String text, Runnable action) {
        Button button = new Button(text);
        button.setStyle("-fx-padding: 10 18; -fx-font-size: 14px; -fx-font-weight: bold; -fx-background-color: #ffffff; -fx-text-fill: #2f4f6f; -fx-border-color: #7aa2ff; -fx-border-radius: 10; -fx-background-radius: 10;");
        button.setOnAction(event -> action.run());
        return button;
    }


    private Color promptForPropertyColor(PropertyCard propertyCard, Set<Color> playableColors) {
        List<Color> options = new ArrayList<>(playableColors);
        options.sort(Comparator.comparing(Enum::name));

        ChoiceDialog<Color> dialog = new ChoiceDialog<>(options.get(0), options);
        dialog.setTitle("Select property color");
        dialog.setHeaderText("Choose the color zone for " + propertyCard.getName());
        dialog.setContentText("Available colors:");

        Optional<Color> result = dialog.showAndWait();
        return result.orElse(null);
    }

    // Handle the End Turn button click
    // This method must be bound in the FXML file, for example: <Button onAction="#onEndTurnClicked" text="End Turn"/>

    // Replacement for original onEndTurnClicked
    @FXML
    private void onEndTurnClicked() {
        if (isOnlineMode) { // In online mode both host and client send a command
            if (getLocalHandCardCount() > PlayerManagement.MAX_HAND_SIZE) {
                startDiscardMode();
                return;
            }
            sendEndTurnAction();
            return;
        }

        // Offline mode: handle locally
        try {
            gameManager.confirmCurrentPlayerTurnEnded();
            if (gameManager.getCurrentPlayer().getHandCardCount() > PlayerManagement.MAX_HAND_SIZE) {
                startDiscardMode();
                return;
            }
            gameManager.advanceTurn();
            discardMode = false;
            updateUI();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void handleCardClick(Card card) {
        System.out.println("handleCardClick called, isOnlineMode: " + isOnlineMode + ", isHost: " + isHost() + ", discardMode: " + discardMode);

        // 1) Discard mode first
        if (discardMode) {
            if (isOnlineMode) { // Online discard command
                sendActionToServer("DISCARD:" + card.getId());
                removeCardFromHandUI(card);
                return;
            }
            // Offline discard logic
            try {
                gameManager.removeFromCurrentPlayerHand(card);
                gameManager.getCardManager().playCard(card);
                if (gameManager.getCurrentPlayer().getHandCardCount() <= PlayerManagement.MAX_HAND_SIZE) {
                    discardMode = false;
                    gameManager.advanceTurn();
                }
                updateUI();
            } catch (Exception e) {
                showError("Failed to discard: " + e.getMessage());
            }
            return;
        }

        // 2) Online mode (unified path; host does not bypass server)
        if (isOnlineMode) {
            if (!isMyTurn) {
                showError("It's not your turn!");
                return;
            }

            selectedHandCard = card;
            renderOnlineHandActionButtons(); // Show online buttons (send PLAY_ACTION)
            return;
        }

        // 3) Offline mode
        selectedHandCard = card;
        renderHandActionButtons();
    }

    private void sendEndTurnAction() {
        sendActionToServer("END_TURN");
    }


    private void startDiscardMode() {
        discardMode = true;
        selectedHandCard = null;
        handActionBox.getChildren().clear();
        showError("Your hand exceeds the limit. Click a hand card to discard it.");
    }

    private int getLocalHandCardCount() {
        if (gameManager != null) {
            PlayerManagement player = isOnlineMode
                    ? gameManager.getPlayersView().get(localPlayerIndex)
                    : gameManager.getCurrentPlayer();
            return player.getHandCardCount();
        }

        int count = 0;
        for (var node : myHandBox.getChildren()) {
            if (node instanceof CardView) {
                count++;
            }
        }
        return count;
    }

    private void renderCardButtonRow(HBox targetBox, List<Card> cards, boolean allowCurrentPlayerHandActions) {
        targetBox.getChildren().clear();
        for (Card card : cards) {
            CardView cardView = new CardView(card);
            cardView.setDisable(!allowCardClick(card, allowCurrentPlayerHandActions));
            cardView.setOnAction(event -> handleCardClick(card));
            targetBox.getChildren().add(cardView);
        }

        if (targetBox.getChildren().isEmpty()) {
            Label emptyView = new Label("No cards");
            emptyView.setStyle("-fx-text-fill: #666666;");
            targetBox.getChildren().add(emptyView);
        }
    }

    private VBox createPlayerSummaryCard(PlayerManagement player, boolean isCurrentPlayer, boolean isTurnPlayer) {
        VBox container = new VBox(8);
        container.setPadding(new Insets(12));
        container.getStyleClass().add("player-card");
        if (isTurnPlayer) {
            container.getStyleClass().add("current-turn");
            installTurnPulse(container);
        }

        Label nameLabel = new Label((isCurrentPlayer ? "[You] " : "") + player.getName());
        nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label handCountLabel = new Label("Hand cards: " + player.getHandCardCount());

        HBox bankRow = new HBox(8);
        bankRow.setAlignment(Pos.CENTER_LEFT);
        bankRow.getChildren().add(new Label("Bank:"));
        if (player.getBankCardsView().isEmpty()) {
            bankRow.getChildren().add(new Label("None"));
        } else {
            for (Card bankCard : player.getBankCardsView()) {
                CardView cardView = new CardView(bankCard, true);
                cardView.setDisable(true);
                bankRow.getChildren().add(cardView);
            }
        }

        HBox propertyRow = new HBox(8);
        propertyRow.setAlignment(Pos.CENTER_LEFT);
        propertyRow.getChildren().add(new Label("Property area:"));
        boolean hasProperty = false;
        for (Map.Entry<Color, PropertyZone> entry : player.getPropertyZonesView().entrySet()) {
            Color color = entry.getKey();
            PropertyZone zone = entry.getValue();
            Label colorLabel = new Label("[" + buildPropertySetTitle(player, color, player.getPropertyCount(color)) + "]");
            colorLabel.setStyle("-fx-padding: 6 10; -fx-background-color: " + toSoftFxColor(color)
                    + "; -fx-border-color: " + toFxColor(color) + "; -fx-border-radius: 6; -fx-font-weight: bold;");
            propertyRow.getChildren().add(colorLabel);
            for (PropertyCard propertyCard : zone.getPropertiesView()) {
                hasProperty = true;
                CardView cardView = new CardView(propertyCard, true);
                cardView.setDisable(true);
                propertyRow.getChildren().add(cardView);
            }
            if (zone.getHouse() != null) {
                hasProperty = true;
                BuildingCard house = zone.getHouse();
                CardView cardView = new CardView(house, true);
                cardView.setDisable(true);
                propertyRow.getChildren().add(cardView);
            }
            if (zone.getHotel() != null) {
                hasProperty = true;
                BuildingCard hotel = zone.getHotel();
                CardView cardView = new CardView(hotel, true);
                cardView.setDisable(true);
                propertyRow.getChildren().add(cardView);
            }
            if (!zone.getPropertiesView().isEmpty() || zone.getHouse() != null || zone.getHotel() != null) {
                hasProperty = true;
            }
        }
        if (!hasProperty) {
            propertyRow.getChildren().add(new Label("None"));
        }

        container.getChildren().addAll(nameLabel, handCountLabel, bankRow, propertyRow);
        return container;
    }

    private void installTurnPulse(VBox container) {
        FadeTransition ft = new FadeTransition(Duration.millis(650), container);
        ft.setFromValue(1.0);
        ft.setToValue(0.88);
        ft.setAutoReverse(true);
        ft.setCycleCount(Animation.INDEFINITE);
        ft.play();
    }

    // CardView component replaces this method

    private void updateClientInfo() {
        if (clientInfoLabel == null) return;
        if (gameManager == null || localPlayerIndex < 0 || localPlayerIndex >= gameManager.getPlayersView().size()) {
            clientInfoLabel.setText("Player");
            return;
        }

        PlayerManagement localPlayer = gameManager.getPlayersView().get(localPlayerIndex);
        clientInfoLabel.setText("You are: " + localPlayer.getName());
    }

    private String buildPropertySetTitle(PlayerManagement player, Color color, int currentCount) {
        if (player == null) {
            return color.name() + "  " + currentCount + "/?  Rent: ?";
        }

        int requiredCount = player.getRequiredSetSize(color);
        String requiredText = requiredCount == Integer.MAX_VALUE ? "?" : String.valueOf(requiredCount);
        int rent = player.getRent(color);
        return color.name() + "  " + currentCount + "/" + requiredText + "  Rent: " + rent + "M";
    }

    private String toFxColor(Color color) {
        return switch (color) {
            case BROWN -> "#8b5a2b";
            case DARK_BLUE -> "#1f4e79";
            case LIGHT_BLUE -> "#6aa9ff";
            case PINK -> "#d95fa2";
            case ORANGE -> "#e67e22";
            case RED -> "#d64545";
            case YELLOW -> "#d4ac0d";
            case GREEN -> "#2e8b57";
            case BLACK -> "#444444";
            case RAILROAD -> "#5d6d7e";
            case UTILITY -> "#7d3c98";
            case WILD -> "#7f8c8d";
        };
    }

    private String toSoftFxColor(Color color) {
        return switch (color) {
            case BROWN -> "#f4e4d4";
            case DARK_BLUE -> "#dce7f2";
            case LIGHT_BLUE -> "#e7f1ff";
            case PINK -> "#fbe3f0";
            case ORANGE -> "#fce8d6";
            case RED -> "#f9dede";
            case YELLOW -> "#f9efc2";
            case GREEN -> "#def3e7";
            case BLACK -> "#e6e6e6";
            case RAILROAD -> "#e8edf2";
            case UTILITY -> "#eee3f7";
            case WILD -> "#eceff1";
        };
    }

    private boolean allowCardClick(Card card, boolean allowCurrentPlayerHandActions) {
        if (discardMode) {
            return true;
        }
        if (allowCurrentPlayerHandActions) {
            return true;
        }
        return gameManager != null && (
                (gameManager.canSelectBankCard() && card.isBankable()) ||
                (gameManager.canSelectPropertyCard() && card instanceof PropertyCard)
        );
    }

    // ---------------- 4. Helper methods ----------------
    public void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Notice");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Client-side handling for payment request
    public void handleRequirePayment(int amount, String collectorId) {
        PlayerManagement me = gameManager.getPlayersView().get(localPlayerIndex);
        if (calculateAssetTotalValue(me) <= amount) {
            sendActionToServer("PAYMENT_RESPONSE:NONE");
            return;
        }

        // 1) Ask player to select assets
        List<Card> selectedAssets = interactor.showSelectableAssets(me, amount);

        // 2) Build card id list
        StringBuilder response = new StringBuilder("PAYMENT_RESPONSE:");
        if (selectedAssets != null && !selectedAssets.isEmpty()) {
            for (int i = 0; i < selectedAssets.size(); i++) {
                response.append(selectedAssets.get(i).getId());
                if (i < selectedAssets.size() - 1) response.append(",");
            }
        } else {
            response.append("NONE"); // bankrupt or no assets
        }

        sendActionToServer(response.toString());
    }

    private int calculateAssetTotalValue(PlayerManagement player) {
        int total = 0;
        for (Card card : player.getBankCardsView()) {
            total += card.getValue();
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

    public void handleAskJustSayNo(String sourcePlayer, String actionName) {
        PlayerManagement me = gameManager.getPlayersView().get(localPlayerIndex);
        Card justSayNoCard = null;
        for (Card c : me.getHandCardsView()) {
            if (c instanceof JustSayNoCard) {
                justSayNoCard = c; break;
            }
        }

        if (justSayNoCard != null) {
            boolean wantToUse = interactor.confirmJustSayNo(me, new PlayerManagement("dummy", sourcePlayer), actionName);
            if (wantToUse) {
                sendActionToServer("JUST_SAY_NO_RESPONSE:YES:" + justSayNoCard.getId());
                return;
            }
        }
        sendActionToServer("JUST_SAY_NO_RESPONSE:NO");
    }

    // Shadow-engine sync: clone server state into a local engine
    private void syncLocalGameManagerFromState(GameStateData state) {
        // Create a new local engine
        gameManager = new GameManager();
        if (interactor == null) interactor = new Interactor();
        gameManager.setInteractor(interactor);
        gameManager.setPlayerCount(state.getPlayers().size());
        gameManager.syncTurnStateFromNetwork(state.getCurrentPlayerIndex(), state.getPlayedCardsThisTurn());

        // Restore all players
        for (int i = 0; i < state.getPlayers().size(); i++) {
            GameStateData.PlayerData pData = state.getPlayers().get(i);
            PlayerManagement pm = gameManager.getPlayersView().get(i);

            // Restore hand
            for (GameStateData.CardData cd : pData.getHandCards()) {
                pm.addToHand(cd.toCard());
            }
            // Restore bank
            for (GameStateData.CardData cd : pData.getBankCards()) {
                pm.depositToBank(cd.toCard());
            }
            // Restore properties
            for (java.util.Map.Entry<Color, GameStateData.PropertyZoneData> entry : pData.getPropertyZones().entrySet()) {
                Color c = entry.getKey();
                GameStateData.PropertyZoneData zd = entry.getValue();
                for (GameStateData.CardData cd : zd.getProperties()) {
                    pm.addProperty(c, (PropertyCard) cd.toCard());
                }
                if (zd.getHouse() != null) pm.addBuilding(c, (BuildingCard) zd.getHouse().toCard());
                if (zd.getHotel() != null) pm.addBuilding(c, (BuildingCard) zd.getHotel().toCard());
            }
        }
    }
}
