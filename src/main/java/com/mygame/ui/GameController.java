package com.mygame.ui;

import com.mygame.cards.action.*;
import com.mygame.cards.base.*;
import com.mygame.cards.money.*;
import com.mygame.cards.property.*;
import com.mygame.cards.rent.*;
import com.mygame.app.AppSettings;
import com.mygame.app.GameApp;
import com.mygame.core.GameManager;
import com.mygame.core.events.GameEventListener;
import com.mygame.model.*;
import com.mygame.network.GameClient;
import com.mygame.network.GameServer;
import com.mygame.network.dto.GameStateData;
import com.mygame.network.protocol.NetworkProtocol;
import com.mygame.rules.PropertyRentRules;
import com.mygame.ui.components.CardView;
import com.mygame.ui.model.PlayTarget;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.animation.FadeTransition;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.util.Duration;
import javafx.geometry.Bounds;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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
    private StackPane clientAvatarPane;
    @FXML
    private Label clientInfoLabel;
    @FXML
    private Label turnInfoLabel;
    @FXML
    private Button endTurnButton;
    @FXML
    private Button helpButton;
    @FXML
    private Button navBackButton;
    @FXML
    private Label hintLabel;
    @FXML
    private TextArea logArea;
    @FXML
    private TextArea chatArea;
    @FXML
    private TextField chatInput;
    @FXML
    private Button sendChatButton;
    @FXML
    private StackPane drawPilePane;
    @FXML
    private StackPane discardPilePane;
    @FXML
    private Label drawPileCountLabel;
    @FXML
    private Label discardPileCountLabel;

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
    private GameApp gameApp;

    // Static instance used by network callbacks
    private static GameController instance;
    private GameEventListener gameEventListener;
    private final List<String> logLines = new ArrayList<>();
    private final List<String> chatLines = new ArrayList<>();
    private boolean winnerDialogShown = false;
    private boolean discardNoticeShown = false;
    private String defaultEndTurnText = "End Turn";
    private volatile int lastServerLocalHandCount = -1;
    private final Set<String> lastRenderedHandCardIds = new HashSet<>();
    private CardView hoveredHandCard;

    @FXML
    public void initialize() {
        instance = this;
        if (endTurnButton != null && endTurnButton.getText() != null && !endTurnButton.getText().isBlank()) {
            defaultEndTurnText = endTurnButton.getText();
        }
        if (chatInput != null) {
            chatInput.setOnAction(e -> onSendChatClicked());
        }
        setupHandPresentation();
        setupPileVisuals();
        setupDropTargets();
    }

    private void setupHandPresentation() {
        if (myHandBox != null) {
            myHandBox.setSpacing(-54);
        }
    }

    private void setupPileVisuals() {
        updatePileCounts(0, 0);
    }

    private void setupDropTargets() {
        installDropTarget(myBankBox, PlayTarget.BANK);
        installDropTarget(myPropertyBox, PlayTarget.PROPERTY);
        installDropTarget(discardPilePane, PlayTarget.DISCARD);
    }

    private void installDropTarget(Node node, PlayTarget target) {
        if (node == null) {
            return;
        }

        node.setOnDragOver(e -> {
            Dragboard db = e.getDragboard();
            if (db == null || !db.hasString()) {
                return;
            }
            String value = db.getString();
            if (value == null || !value.startsWith("CARD:")) {
                return;
            }
            e.acceptTransferModes(TransferMode.MOVE);
            e.consume();
        });

        node.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            if (db == null || !db.hasString()) {
                e.setDropCompleted(false);
                return;
            }
            String value = db.getString();
            if (value == null || !value.startsWith("CARD:")) {
                e.setDropCompleted(false);
                return;
            }

            String cardId = value.substring("CARD:".length());
            Card card = findHandCardById(cardId);
            if (card == null) {
                e.setDropCompleted(false);
                return;
            }

            boolean completed = handleDropPlay(card, target);
            e.setDropCompleted(completed);
            e.consume();
        });
    }

    private Card findHandCardById(String cardId) {
        if (cardId == null) {
            return null;
        }
        for (var node : myHandBox.getChildren()) {
            if (node instanceof CardView cv && cv.getCard() != null && cardId.equals(cv.getCard().getId())) {
                return cv.getCard();
            }
        }
        if (gameManager != null) {
            PlayerManagement player = isOnlineMode
                    ? (localPlayerIndex < gameManager.getPlayersView().size() ? gameManager.getPlayersView().get(localPlayerIndex) : null)
                    : gameManager.getCurrentPlayer();
            if (player != null) {
                for (Card c : player.getHandCardsView()) {
                    if (cardId.equals(c.getId())) {
                        return c;
                    }
                }
            }
        }
        return null;
    }

    private boolean handleDropPlay(Card card, PlayTarget target) {
        if (card == null) {
            return false;
        }

        if (target == PlayTarget.DISCARD) {
            if (!discardMode) {
                showError("You can only discard to the discard pile when discard mode is active.");
                return false;
            }
            handleCardClick(card);
            return true;
        }

        if (isOnlineMode) {
            if (!isMyTurn || discardMode) {
                showError("It's not your turn.");
                return false;
            }
            if (target == PlayTarget.BANK) {
                sendDepositAction(card);
                return true;
            }
            if (target == PlayTarget.PROPERTY) {
                sendPlacePropertyAction(card);
                return true;
            }
            return false;
        }

        if (gameManager == null || !gameManager.canCurrentPlayerPlayCard()) {
            return false;
        }
        if (target == PlayTarget.BANK) {
            doDepositToBank(card);
            return true;
        }
        if (target == PlayTarget.PROPERTY) {
            doPlacePropertyCard(card);
            return true;
        }
        return false;
    }

    private void updatePileCounts(int drawCount, int discardCount) {
        if (drawPileCountLabel != null) {
            drawPileCountLabel.setText(String.valueOf(Math.max(0, drawCount)));
        }
        if (discardPileCountLabel != null) {
            discardPileCountLabel.setText(String.valueOf(Math.max(0, discardCount)));
        }
        renderPile(drawPilePane, drawPileCountLabel, drawCount);
        renderPile(discardPilePane, discardPileCountLabel, discardCount);
    }

    private void renderPile(StackPane pilePane, Label countLabel, int count) {
        if (pilePane == null) {
            return;
        }
        pilePane.getChildren().clear();

        int visible = Math.min(5, Math.max(0, count));
        for (int i = 0; i < visible; i++) {
            StackPane back = new StackPane();
            back.getStyleClass().add("card-back");
            back.setStyle("-fx-background-color: linear-gradient(to bottom right, #ffffff, #dbeafe);"
                    + " -fx-border-color: #3b82f6;"
                    + " -fx-border-width: 3;"
                    + " -fx-background-radius: 12;"
                    + " -fx-border-radius: 12;"
                    + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 10, 0.2, 0, 3);");
            back.setPrefSize(90, 130);
            back.setMinSize(90, 130);
            back.setMaxSize(90, 130);
            VBox stamp = new VBox(2);
            stamp.setAlignment(Pos.CENTER);
            stamp.setMouseTransparent(true);
            Label icon = new Label("🎴");
            icon.setStyle("-fx-font-size: 22px; -fx-text-fill: #1d4ed8; -fx-font-weight: 900;");
            Label title = new Label("MD");
            title.setStyle("-fx-font-size: 14px; -fx-text-fill: #0f172a; -fx-font-weight: 900; -fx-letter-spacing: 1.2px;");
            Label sub = new Label("DEAL");
            sub.setStyle("-fx-font-size: 10px; -fx-text-fill: rgba(15,23,42,0.72); -fx-font-weight: 800; -fx-letter-spacing: 2px;");
            stamp.getChildren().addAll(icon, title, sub);
            back.getChildren().add(stamp);
            back.setTranslateX(i * 1.8);
            back.setTranslateY(-i * 1.8);
            pilePane.getChildren().add(back);
        }

        if (countLabel != null) {
            pilePane.getChildren().add(countLabel);
            StackPane.setAlignment(countLabel, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(countLabel, new Insets(0, 6, 6, 0));
        }
    }

    public static GameController getInstance() {
        return instance;
    }

    public void setGameApp(GameApp gameApp) {
        this.gameApp = gameApp;
    }

    public void cleanup() {
        if (gameClient != null) {
            gameClient.close();
        }
        if (gameServer != null) {
            if (isOnlineMode && isHost()) {
                var gm = gameServer.getGameManager();
                if (gm != null && gm.isGameStarted() && !gm.isGameOver()) {
                    gameServer.handleHostLeaving();
                } else {
                    gameServer.stop();
                }
            } else {
                gameServer.stop();
            }
        }
    }

    // ---------------- 1. Initialization ----------------
    public void initializeGame(int playerCount) {
        if (isOnlineMode && !isHost()) {
            return;
        }

        chatLines.clear();
        if (chatArea != null) {
            chatArea.clear();
        }

        if (!isOnlineMode) {
            if (playerCount < 2 || playerCount > 5) {
                playerCount = promptForOfflinePlayerCount();
            }
        }

        discardMode = false;
        selectedHandCard = null;
        if (navBackButton != null) {
            navBackButton.setText(isOnlineMode ? "Exit to Lobby" : "Exit to Menu");
        }

        // Offline: create local GameManager.
        // Online host: use server-side GameManager instance.
        interactor = new Interactor();

        if (isOnlineMode && isHost() && gameServer != null) {
            GameManager serverManager = gameServer.getGameManager();
            if (serverManager == null) {
                serverManager = new GameManager();
                gameServer.setGameManager(serverManager);
            }
            if (serverManager.getCardManager() == null) {
                serverManager.setPlayerCount(playerCount);
                serverManager.startRound();
            }
            bindGameManager(serverManager);
        } else {
            GameManager localManager = new GameManager();
            List<String> names = new ArrayList<>();
            for (int i = 1; i <= playerCount; i++) {
                if (i == 1) {
                    String localName = AppSettings.getInstance().getPlayerName();
                    names.add(localName == null || localName.isBlank() ? "Player1" : localName);
                } else {
                    names.add("Player" + i);
                }
            }
            localManager.setPlayerCount(playerCount, names);
            for (int i = 0; i < localManager.getPlayersView().size(); i++) {
                int avatarId = i == 0 ? AppSettings.getInstance().getAvatarId() : i;
                localManager.getPlayersView().get(i).setAvatarId(avatarId);
            }
            localManager.startRound();
            bindGameManager(localManager);
        }

        updateUI();
        if (!AppSettings.getInstance().isOnboardingShown()) {
            AppSettings.getInstance().setOnboardingShown(true);
            onHelpClicked();
        }

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

    @FXML
    private void onSendChatClicked() {
        if (chatInput == null) {
            return;
        }
        String text = chatInput.getText();
        if (text == null) {
            return;
        }
        text = text.trim();
        if (text.isEmpty()) {
            return;
        }
        chatInput.clear();
        sendChatMessage(text);
    }

    public void receiveChatMessage(String playerId, String message) {
        Platform.runLater(() -> appendChatLine(playerId, message));
    }

    private void sendChatMessage(String message) {
        if (isOnlineMode) {
            String sender = AppSettings.getInstance().getPlayerName();
            if (sender == null || sender.isBlank()) {
                if (gameManager != null && localPlayerIndex >= 0 && localPlayerIndex < gameManager.getPlayersView().size()) {
                    sender = gameManager.getPlayersView().get(localPlayerIndex).getName();
                } else {
                    sender = "Player" + (localPlayerIndex + 1);
                }
            }
            if (isHost() && gameServer != null) {
                appendChatLine(sender, message);
                gameServer.broadcast(NetworkProtocol.chat(sender, message));
                return;
            }
            if (gameClient != null) {
                gameClient.sendChat(message);
                return;
            }
            appendChatLine(sender, message);
            return;
        }
        String sender = AppSettings.getInstance().getPlayerName();
        if (sender == null || sender.isBlank()) {
            sender = "Player1";
        }
        appendChatLine(sender, message);
    }

    private void trimAndRefresh(List<String> lines, TextArea area) {
        while (lines.size() > GameManager.MAX_LOG_LINES) {
            lines.remove(0);
        }
        if (area != null) {
            area.setText(String.join("\n", lines));
            area.positionCaret(area.getText().length());
        }
    }

    private void appendChatLine(String playerId, String message) {
        String line = (playerId == null || playerId.isBlank() ? "Player" : playerId) + ": " + (message == null ? "" : message);
        chatLines.add(line);
        trimAndRefresh(chatLines, chatArea);
    }

    private int promptForOfflinePlayerCount() {
        List<Integer> options = List.of(2, 3, 4, 5);
        int initial = AppSettings.getInstance().getOfflinePlayerCount();
        if (!options.contains(initial)) {
            initial = 2;
        }

        ChoiceDialog<Integer> dialog = new ChoiceDialog<>(initial, options);
        dialog.setTitle("Game Setup");
        dialog.setHeaderText("Select number of players");
        dialog.setContentText("Players:");
        Optional<Integer> result = dialog.showAndWait();

        int selected = result.orElse(initial);
        AppSettings.getInstance().setOfflinePlayerCount(selected);
        return selected;
    }

    public void setOnlineMode(boolean online, int playerIndex) {
        this.isOnlineMode = online;
        this.localPlayerIndex = playerIndex;
        if (navBackButton != null) {
            navBackButton.setText(online ? "Exit to Lobby" : "Exit to Menu");
        }

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

    private void bindGameManager(GameManager manager) {
        if (this.gameManager != null && gameEventListener != null) {
            this.gameManager.removeEventListener(gameEventListener);
        }
        this.gameManager = manager;
        if (this.gameManager == null) {
            return;
        }
        this.gameManager.setInteractor(interactor);
        if (gameEventListener == null) {
            gameEventListener = event -> Platform.runLater(() -> appendLog(event.getMessage()));
        }
        this.gameManager.addEventListener(gameEventListener);
        appendLog("Game ready");
    }

    private void appendLog(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        logLines.add(message);
        trimAndRefresh(logLines, logArea);
    }

    public void updateFromServerState(GameStateData state) {
        // Update UI based on server state
        if (state == null) return;

        if (isOnlineMode && !isHost()) {
            syncLocalGameManagerFromState(state);
            if (state.getEventLog() != null) {
                logLines.clear();
                logLines.addAll(state.getEventLog());
                trimAndRefresh(logLines, logArea);
            }
        }

        boolean wasMyTurn = isMyTurn;
        isMyTurn = (state.getCurrentPlayerIndex() == localPlayerIndex);
        updatePileCounts(state.getDrawPileCount(), state.getDiscardPileCount());
        int localHandCount = state.getPlayers().size() > localPlayerIndex
                ? state.getPlayers().get(localPlayerIndex).getHandCards().size()
                : 0;
        lastServerLocalHandCount = localHandCount;
        if (discardMode && (!isMyTurn || localHandCount <= PlayerManagement.MAX_HAND_SIZE)) {
            discardMode = false;
            discardNoticeShown = false;
            restoreEndTurnButtonTextIfNeeded();
            selectedHandCard = null;
            handActionBox.getChildren().clear();
        }
        endTurnButton.setDisable(!isMyTurn || discardMode);

        updateClientInfo();
        String currentPlayerName = state.getPlayers().size() > state.getCurrentPlayerIndex()
                ? state.getPlayers().get(state.getCurrentPlayerIndex()).getPlayerName()
                : "Player";
        int remainingPlays = state.getMaxPlayCountPerTurn() - state.getPlayedCardsThisTurn();
        turnInfoLabel.setText("Turn: " + currentPlayerName + " | Remaining plays: " + remainingPlays);
        if (hintLabel != null) {
            if (state.getWinner() != null && !state.getWinner().isBlank()) {
                hintLabel.setText("Winner: " + state.getWinner());
                discardMode = false;
                discardNoticeShown = false;
                restoreEndTurnButtonTextIfNeeded();
                if (endTurnButton != null) {
                    endTurnButton.setDisable(true);
                }
                showWinnerDialogAndExit(state.getWinner(), buildWinnerSummaryText(state));
            } else if (discardMode) {
                hintLabel.setText(buildDiscardHintText());
            } else if (!isMyTurn) {
                hintLabel.setText("Waiting for your turn.");
            } else if (remainingPlays <= 0) {
                hintLabel.setText("No plays left. End your turn.");
            } else {
                hintLabel.setText("Click a hand card to play it as action / bank / property.");
            }
        }

        // New turn: clear selection
        if (!wasMyTurn && isMyTurn) {
            selectedHandCard = null;
            handActionBox.getChildren().clear();
        }

        // Host: render from real GameManager objects
        // Client: render from DTOs
        if (isOnlineMode && isHost() && gameManager != null) {
            updateUI();
        } else if (isOnlineMode && !isHost()) {
            updateHandCardsFromServer(state);
            updateBankCardsFromServer(state);
            updatePropertyCardsFromServer(state);
            updateOpponentAreaFromServer(state);
            updateCardDisabledState();
        }
    }

    private void updateCardDisabledState() {
        // Update disabled state for all hand cards
        for (var node : myHandBox.getChildren()) {
            if (node instanceof CardView cardView) {
                boolean shouldDisable = !isMyTurn;
                cardView.setDisable(shouldDisable);
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
        List<Card> cards = new ArrayList<>();
        for (GameStateData.CardData cardData : localPlayerData.getHandCards()) {
            cards.add(cardData.toCard());
        }
        renderHandCardsList(cards);
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
            installLabelTooltip(colorTitle, colorTitle.getText());
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
            HBox header = new HBox(10);
            header.setAlignment(Pos.CENTER_LEFT);
            StackPane avatarPane = new StackPane();
            renderAvatarInto(avatarPane, playerData.getAvatarId(), playerData.getPlayerName(), 14);
            header.getChildren().addAll(avatarPane, nameLabel);

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
                installLabelTooltip(colorLabel, colorLabel.getText());
                propertyRow.getChildren().add(colorLabel);
                hasProperty = true;
            }
            if (!hasProperty) {
                propertyRow.getChildren().add(new Label("None"));
            }

            container.getChildren().addAll(header, handCountLabel, bankRow, propertyRow);
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

        if (gameManager.getCardManager() != null) {
            updatePileCounts(gameManager.getCardManager().getDrawPileSize(), gameManager.getCardManager().getDiscardPileSize());
        }

        updateClientInfo();
        String turnPlayerName = currentPlayer.getName();
        turnInfoLabel.setText("Turn: " + turnPlayerName + " | Remaining plays: " + gameManager.getRemainingPlayCountThisTurn());
        if (hintLabel != null) {
            if (gameManager.hasWinner()) {
                hintLabel.setText("Winner: " + gameManager.getWinner().getName());
                discardMode = false;
                discardNoticeShown = false;
                restoreEndTurnButtonTextIfNeeded();
                if (endTurnButton != null) {
                    endTurnButton.setDisable(true);
                }
                showWinnerDialogAndExit(gameManager.getWinner().getName(), buildWinnerSummaryText());
            } else if (discardMode) {
                hintLabel.setText(buildDiscardHintText());
            } else if (isOnlineMode && !isMyTurn) {
                hintLabel.setText("Waiting for your turn.");
            } else if (gameManager.getRemainingPlayCountThisTurn() <= 0) {
                hintLabel.setText("No plays left. End your turn.");
            } else {
                hintLabel.setText("Click a hand card to play it as action / bank / property.");
            }
        }
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
        renderHandCardsList(player.getHandCards());
    }

    private void renderHandCardsList(List<Card> cards) {
        if (myHandBox == null) {
            return;
        }

        Set<String> newIds = new HashSet<>();
        for (Card c : cards) {
            if (c != null && c.getId() != null) {
                newIds.add(c.getId());
            }
        }

        Set<String> added = new HashSet<>();
        boolean animateNewCards = !lastRenderedHandCardIds.isEmpty();
        if (animateNewCards) {
            for (String id : newIds) {
                if (!lastRenderedHandCardIds.contains(id)) {
                    added.add(id);
                }
            }
        }
        lastRenderedHandCardIds.clear();
        lastRenderedHandCardIds.addAll(newIds);

        myHandBox.getChildren().clear();
        hoveredHandCard = null;

        if (cards.isEmpty()) {
            Label emptyView = new Label("No cards");
            emptyView.setStyle("-fx-text-fill: #666666;");
            myHandBox.getChildren().add(emptyView);
            return;
        }

        boolean enabled = discardMode || !isOnlineMode || isMyTurn;

        List<CardView> created = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            CardView cardView = new CardView(card);
            double baseViewOrder = -i;
            cardView.getProperties().put("handBaseViewOrder", baseViewOrder);
            cardView.setViewOrder(baseViewOrder);
            cardView.setDisable(!enabled);
            cardView.setOnAction(event -> handleCardClick(card));
            installHandCardHover(cardView);
            installHandCardDrag(cardView, card);
            myHandBox.getChildren().add(cardView);
            created.add(cardView);
        }

        if (animateNewCards && drawPilePane != null && !added.isEmpty()) {
            for (CardView cv : created) {
                if (cv.getCard() != null && added.contains(cv.getCard().getId())) {
                    animateDrawFromPile(cv);
                }
            }
        }
    }

    private void installHandCardHover(CardView cardView) {
        if (cardView == null) {
            return;
        }
        cardView.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> applyHandHover(cardView));
        cardView.addEventHandler(MouseEvent.MOUSE_EXITED, e -> clearHandHover(cardView));
    }

    private void applyHandHover(CardView cardView) {
        if (cardView == null) {
            return;
        }
        if (hoveredHandCard != null && hoveredHandCard != cardView) {
            clearHandHover(hoveredHandCard);
        }
        hoveredHandCard = cardView;
        cardView.setViewOrder(-10000);
        cardView.setTranslateY(-18);
        cardView.setScaleX(1.12);
        cardView.setScaleY(1.12);
    }

    private void clearHandHover(CardView cardView) {
        if (cardView == null) {
            return;
        }
        Object base = cardView.getProperties().get("handBaseViewOrder");
        if (base instanceof Number n) {
            cardView.setViewOrder(n.doubleValue());
        } else {
            cardView.setViewOrder(0);
        }
        cardView.setTranslateY(0);
        cardView.setScaleX(1);
        cardView.setScaleY(1);
        if (hoveredHandCard == cardView) {
            hoveredHandCard = null;
        }
    }

    private void installHandCardDrag(CardView cardView, Card card) {
        if (cardView == null || card == null) {
            return;
        }
        cardView.setOnDragDetected(e -> {
            boolean enabled = discardMode || !isOnlineMode || isMyTurn;
            if (!enabled) {
                return;
            }
            Dragboard db = cardView.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString("CARD:" + card.getId());
            db.setContent(content);
            cardView.setViewOrder(-10000);
            e.consume();
        });
        cardView.setOnDragDone(e -> {
            clearHandHover(cardView);
        });
    }

    private void animateDrawFromPile(CardView cardView) {
        if (cardView == null || drawPilePane == null) {
            return;
        }
        cardView.setFaceDown(true);
        Platform.runLater(() -> {
            if (cardView.getScene() == null) {
                cardView.setFaceDown(false);
                return;
            }
            Bounds from = drawPilePane.localToScene(drawPilePane.getBoundsInLocal());
            Bounds to = cardView.localToScene(cardView.getBoundsInLocal());
            double dx = from.getMinX() - to.getMinX();
            double dy = from.getMinY() - to.getMinY();
            cardView.setTranslateX(dx);
            cardView.setTranslateY(dy);
            TranslateTransition tt = new TranslateTransition(Duration.millis(260), cardView);
            tt.setToX(0);
            tt.setToY(0);
            tt.setInterpolator(Interpolator.EASE_OUT);
            tt.setOnFinished(ev -> cardView.playFlip(false));
            tt.play();
        });
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
            installLabelTooltip(colorTitle, colorTitle.getText());
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
        if (!isMyTurn) {
            showError("It's not your turn!");
            return;
        }

        selectedHandCard = card;
        renderOnlineHandActionButtons();
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
        if (!isHost() && gameClient != null) {
            gameClient.sendAction(action);
        } else if (isHost() && gameServer != null) {
            gameServer.processHostAction(action);
        }
    }

    private void renderOnlineHandActionButtons() {
        handActionBox.getChildren().clear();
        if (selectedHandCard == null) {
            return;
        }

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
        animateHandCardToDiscard(card, () -> removeCardFromHandUI(card));
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
            ButtonType yesButton = new ButtonType("Yes", ButtonBar.ButtonData.YES);
            ButtonType noButton = new ButtonType("No", ButtonBar.ButtonData.NO);
            Alert alert = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    "You have a Double The Rent card. Play it together to double this rent?",
                    yesButton,
                    noButton
            );
            alert.setTitle("Double The Rent found");
            alert.setHeaderText(null);
            if (alert.showAndWait().orElse(noButton) == yesButton) {
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

        animateHandCardToDiscard(rentCard, () -> removeCardFromHandUI(rentCard));
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
            animateHandCardToDiscard(card, () -> {
                try {
                    gameManager.playActionCard(card);
                    selectedHandCard = null;
                    updateUI();
                    broadcastStateIfHost();
                } catch (Exception e) {
                    showError("Action failed: " + e.getMessage());
                }
            });
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
            int finalRentAmount = rentAmount;
            PlayerManagement finalTargetPlayer = targetPlayer;
            animateHandCardToDiscard(rentCard, () -> {
                try {
                    gameManager.removeFromCurrentPlayerHand(rentCard);
                    gameManager.getCardManager().playCard(rentCard);
                    gameManager.recordPlayedCardAfterExternalResolution();

                    if (rentCard.getCardType() == CardType.RENT_WILDCOLOR) {
                        gameManager.chargePlayer(currentPlayer, finalTargetPlayer, finalRentAmount);
                    } else {
                        gameManager.chargeAllOpponents(currentPlayer, finalRentAmount);
                    }

                    selectedHandCard = null;
                    updateUI();
                    broadcastStateIfHost();
                } catch (Exception e) {
                    showError("Action failed: " + e.getMessage());
                }
            });
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

    private void animateHandCardToDiscard(Card card, Runnable after) {
        if (card == null) {
            if (after != null) after.run();
            return;
        }
        if (myHandBox == null) {
            if (after != null) after.run();
            return;
        }
        if (discardPilePane == null) {
            if (after != null) after.run();
            return;
        }

        CardView sourceView = null;
        for (var node : myHandBox.getChildren()) {
            if (node instanceof CardView cv) {
                if (cv.getCard() != null && cv.getCard().getId().equals(card.getId())) {
                    sourceView = cv;
                    break;
                }
            }
        }

        if (sourceView == null) {
            if (after != null) after.run();
            return;
        }

        CardView finalSourceView = sourceView;
        finalSourceView.playFlip(true, () -> animateHandCardToTarget(card, discardPilePane, after));
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
        sourceView.setViewOrder(-10000);

        TranslateTransition tt = new TranslateTransition(Duration.millis(220), sourceView);
        tt.setByX(dx);
        tt.setByY(dy);
        tt.setOnFinished(e -> {
            if (after != null) after.run();
            if (sourceView.getParent() != null) {
                sourceView.setTranslateX(0);
                sourceView.setTranslateY(0);
                Object base = sourceView.getProperties().get("handBaseViewOrder");
                if (base instanceof Number n) {
                    sourceView.setViewOrder(n.doubleValue());
                } else {
                    sourceView.setViewOrder(0);
                }
                sourceView.setDisable(false);
            }
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

    @FXML
    private void onHelpClicked() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Help");
        alert.setHeaderText("Quick Rules");
        alert.setContentText(
                "Goal: collect 3 complete property sets.\n" +
                        "Each turn: draw 2 cards, play up to 3 cards, then keep max 7 cards in hand.\n" +
                        "Money cards go to Bank, property cards go to Property Area, action cards apply effects.\n" +
                        "Tip: click a hand card to see available actions.\n" +
                        "\n" +
                        "Win condition: the first player who has 3 complete property sets wins.");
        alert.showAndWait();
    }

    // Handle the End Turn button click
    // This method must be bound in the FXML file, for example: <Button onAction="#onEndTurnClicked" text="End Turn"/>

    // Replacement for original onEndTurnClicked
    @FXML
    private void onEndTurnClicked() {
        if (gameManager != null && gameManager.hasWinner()) {
            discardMode = false;
            if (endTurnButton != null) {
                endTurnButton.setDisable(true);
            }
            if (hintLabel != null) {
                hintLabel.setText("Winner: " + gameManager.getWinner().getName());
            }
            return;
        }
        if (isOnlineMode) { // In online mode both host and client send a command
            if (discardMode) {
                return;
            }
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
        // 1) Discard mode first
        if (discardMode) {
            if (isOnlineMode) { // Online discard command
                animateHandCardToDiscard(card, () -> {
                    sendActionToServer("DISCARD:" + card.getId());
                    removeCardFromHandUI(card);
                });
                return;
            }
            // Offline discard logic
            animateHandCardToDiscard(card, () -> {
                try {
                    gameManager.removeFromCurrentPlayerHand(card);
                    gameManager.getCardManager().playCard(card);
                    if (gameManager.getCurrentPlayer().getHandCardCount() <= PlayerManagement.MAX_HAND_SIZE) {
                        discardMode = false;
                        gameManager.advanceTurn();
                    }
                    updateUI();
                    broadcastStateIfHost();
                } catch (Exception e) {
                    showError("Failed to discard: " + e.getMessage());
                }
            });
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
        if (hintLabel != null) {
            hintLabel.setText(buildDiscardHintText());
        }
        if (endTurnButton != null) {
            endTurnButton.setDisable(true);
            endTurnButton.setText("Discard");
        }
        if (!discardNoticeShown) {
            discardNoticeShown = true;
            showError("Hand limit exceeded. Click hand cards to discard until you have 7 or fewer.");
        }
    }

    private void restoreEndTurnButtonTextIfNeeded() {
        if (endTurnButton == null) {
            return;
        }
        if (!discardMode && endTurnButton.getText() != null && !endTurnButton.getText().equals(defaultEndTurnText)) {
            endTurnButton.setText(defaultEndTurnText);
        }
    }

    private String buildDiscardHintText() {
        int current = getLocalHandCardCount();
        int need = Math.max(0, current - PlayerManagement.MAX_HAND_SIZE);
        if (need <= 0) {
            return "Discard mode: click a hand card to discard.";
        }
        return "Discard mode: hand " + current + "/" + PlayerManagement.MAX_HAND_SIZE + " (discard " + need + " card" + (need > 1 ? "s" : "") + ").";
    }

    private void showWinnerDialogAndExit(String winnerName, String summaryText) {
        if (winnerDialogShown) {
            return;
        }
        winnerDialogShown = true;

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Game Over");
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("OK", ButtonBar.ButtonData.OK_DONE));

        VBox root = new VBox(10);
        root.getStyleClass().add("overlay");
        root.setStyle("-fx-padding: 18; -fx-alignment: center;");

        Label title = new Label("Winner");
        title.setStyle("-fx-font-size: 26px; -fx-font-weight: 800;");
        Label winner = new Label(winnerName);
        winner.setStyle("-fx-font-size: 22px; -fx-font-weight: 700; -fx-text-fill: #1d4ed8;");

        TextArea summary = new TextArea(summaryText == null ? "" : summaryText);
        summary.setEditable(false);
        summary.setWrapText(true);
        summary.setPrefRowCount(8);
        summary.setPrefWidth(520);

        root.getChildren().addAll(title, winner, summary);
        dialog.getDialogPane().setContent(root);
        try {
            var url = getClass().getResource("/theme.css");
            if (url != null) {
                dialog.getDialogPane().getStylesheets().add(url.toExternalForm());
            }
        } catch (Exception ignored) {
        }

        dialog.showAndWait();
        Platform.runLater(this::onNavigateBackClicked);
    }

    private String buildWinnerSummaryText() {
        if (gameManager == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (PlayerManagement p : gameManager.getPlayersView()) {
            sb.append(p.getName())
                    .append(" | Sets: ").append(p.getCompleteSetCount()).append("/").append(PlayerManagement.REQUIRED_COMPLETE_SETS_TO_WIN)
                    .append(" | Bank: ").append(p.getBankTotalValue()).append("M")
                    .append(" | Hand: ").append(p.getHandCardCount())
                    .append("\n");
        }
        return sb.toString().trim();
    }

    private String buildWinnerSummaryText(GameStateData state) {
        if (state == null || state.getPlayers() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (GameStateData.PlayerData p : state.getPlayers()) {
            int bank = 0;
            if (p.getBankCards() != null) {
                for (GameStateData.CardData c : p.getBankCards()) {
                    bank += c.getValue();
                }
            }
            int sets = 0;
            if (p.getPropertyZones() != null) {
                for (var entry : p.getPropertyZones().entrySet()) {
                    Color color = entry.getKey();
                    GameStateData.PropertyZoneData zone = entry.getValue();
                    int count = zone == null || zone.getProperties() == null ? 0 : zone.getProperties().size();
                    PropertyRentRules.RentRule rule = PropertyRentRules.RULES.get(color);
                    if (rule != null && count >= rule.getMaxSetSize()) {
                        sets++;
                    }
                }
            }
            int hand = p.getHandCards() == null ? 0 : p.getHandCards().size();
            sb.append(p.getPlayerName())
                    .append(" | Sets: ").append(sets).append("/").append(PlayerManagement.REQUIRED_COMPLETE_SETS_TO_WIN)
                    .append(" | Bank: ").append(bank).append("M")
                    .append(" | Hand: ").append(hand)
                    .append("\n");
        }
        return sb.toString().trim();
    }

    private int getLocalHandCardCount() {
        if (isOnlineMode && lastServerLocalHandCount >= 0) {
            return lastServerLocalHandCount;
        }
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
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        StackPane avatarPane = new StackPane();
        renderAvatarInto(avatarPane, player.getAvatarId(), player.getName(), 14);
        header.getChildren().addAll(avatarPane, nameLabel);

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
            installLabelTooltip(colorLabel, colorLabel.getText());
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

        container.getChildren().addAll(header, handCountLabel, bankRow, propertyRow);
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
        if (gameManager == null) {
            clientInfoLabel.setText("Player");
            return;
        }

        PlayerManagement shownPlayer;
        String prefix;
        if (!isOnlineMode) {
            shownPlayer = gameManager.getCurrentPlayer();
            prefix = "Current player: ";
        } else {
            if (localPlayerIndex < 0 || localPlayerIndex >= gameManager.getPlayersView().size()) {
                clientInfoLabel.setText("Player");
                return;
            }
            shownPlayer = gameManager.getPlayersView().get(localPlayerIndex);
            prefix = "You are: ";
        }

        clientInfoLabel.setText(prefix + shownPlayer.getName() + " | Complete sets: " + shownPlayer.getCompleteSetCount() + "/" + PlayerManagement.REQUIRED_COMPLETE_SETS_TO_WIN);
        renderAvatarInto(clientAvatarPane, shownPlayer.getAvatarId(), shownPlayer.getName(), 20);
    }

    private void renderAvatarInto(StackPane container, int avatarId, String name, double radius) {
        if (container == null) {
            return;
        }
        container.getChildren().clear();
        Circle circle = new Circle(radius);
        circle.setFill(getAvatarColor(avatarId));
        Label initial = new Label(extractInitial(name));
        initial.setStyle("-fx-text-fill: white; -fx-font-weight: 900; -fx-font-size: " + Math.max(12, (int) Math.round(radius)) + "px;");
        container.getChildren().addAll(circle, initial);
    }

    @FXML
    private void onNavigateBackClicked() {
        cleanup();
        if (gameApp == null) {
            return;
        }
        if (isOnlineMode) {
            gameApp.showOnlineLobby();
        } else {
            gameApp.showMainMenu();
        }
    }

    private String extractInitial(String name) {
        if (name == null) {
            return "?";
        }
        String value = name.trim();
        if (value.isEmpty()) {
            return "?";
        }
        return value.substring(0, 1).toUpperCase();
    }

    private javafx.scene.paint.Color getAvatarColor(int avatarId) {
        return switch (Math.floorMod(avatarId, 5)) {
            case 0 -> javafx.scene.paint.Color.web("#3b82f6");
            case 1 -> javafx.scene.paint.Color.web("#22c55e");
            case 2 -> javafx.scene.paint.Color.web("#f59e0b");
            case 3 -> javafx.scene.paint.Color.web("#ef4444");
            default -> javafx.scene.paint.Color.web("#a855f7");
        };
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

    private void installLabelTooltip(Label label, String text) {
        if (label == null) {
            return;
        }
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            label.setTooltip(null);
            return;
        }
        Tooltip tooltip = label.getTooltip();
        if (tooltip == null) {
            tooltip = new Tooltip(value);
            label.setTooltip(tooltip);
        } else {
            tooltip.setText(value);
        }
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
        return player.calculateAssetTotalValue();
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
        if (interactor == null) interactor = new Interactor();
        GameManager localManager = new GameManager();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < state.getPlayers().size(); i++) {
            String n = state.getPlayers().get(i).getPlayerName();
            if (n == null || n.isBlank()) {
                n = "Player" + (i + 1);
            }
            names.add(n);
        }
        localManager.setPlayerCount(state.getPlayers().size(), names);
        localManager.syncTurnStateFromNetwork(state.getCurrentPlayerIndex(), state.getPlayedCardsThisTurn());
        bindGameManager(localManager);

        // Restore all players
        for (int i = 0; i < state.getPlayers().size(); i++) {
            GameStateData.PlayerData pData = state.getPlayers().get(i);
            PlayerManagement pm = gameManager.getPlayersView().get(i);
            pm.setAvatarId(pData.getAvatarId());

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
