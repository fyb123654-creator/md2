package com.mygame.ui;

import com.mygame.app.GameApp;
import com.mygame.app.AppSettings;
import com.mygame.network.GameClient;
import com.mygame.network.GameServer;
import com.mygame.network.dto.GameStateData;

import javafx.application.Platform;
import javafx.animation.Animation;
import javafx.animation.ScaleTransition;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.util.ArrayList;
import java.util.List;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.util.List;

/**
 * Online lobby controller.
 * Handles host/join UI and basic network flow.
 */
public class NetworkGameController {

    @FXML private VBox mainPanel;
    @FXML private TextField serverAddressField;
    @FXML private TextField portField;
    @FXML private HBox playerCountButtonBox;
    @FXML private Button playerCount2Button;
    @FXML private Button playerCount3Button;
    @FXML private Button playerCount4Button;
    @FXML private Button playerCount5Button;
    @FXML private Button hostButton;
    @FXML private Button joinButton;
    @FXML private Button backToMenuButton;
    @FXML private Button readyButton;
    @FXML private Button startButton;
    @FXML private Label hintLabel;
    @FXML private Label statusLabel;
    @FXML private HBox gameArea;
    @FXML private Button endTurnButton;
    @FXML private Label turnInfoLabel;
    @FXML private VBox playerInfoBox;
    @FXML private HBox playerListBox;

    private GameServer gameServer;
    private GameClient gameClient;
    private boolean isHost;
    private int localPlayerIndex;
    private GameApp gameApp;
    private Stage primaryStage;
    private boolean isMyTurn = false;
    private int playerCount = 2;
    private final List<String> lobbyPlayers = new ArrayList<>();
    private boolean localReady = false;
    private ScaleTransition readyAttention;
    private boolean choosingHostPlayerCount = false;

    public void setGameApp(GameApp gameApp) {
        this.gameApp = gameApp;
    }

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    @FXML
    public void initialize() {
        serverAddressField.setText("127.0.0.1");
        portField.setText("12345");
        selectPlayerCount(2);

        gameArea.setVisible(false);
        endTurnButton.setDisable(true);
        if (startButton != null) startButton.setDisable(true);
        if (readyButton != null) readyButton.setDisable(true);
        if (playerInfoBox != null) playerInfoBox.setVisible(false);
        setPlayerCountButtonsVisible(false);
        if (hintLabel != null) {
            hintLabel.setText("Click Create Game to choose the room size, or Join Game to enter an existing room.");
        }

    }

    @FXML
    private void onBackToMenuClicked() {
        if (gameServer != null) {
            cleanup();
            gameServer = null;
            isHost = false;
            resetToChoosePlayerCountState();
            return;
        }
        if (gameClient != null) {
            cleanup();
            gameClient = null;
            isHost = false;
            resetToInitialState();
            return;
        }
        if (choosingHostPlayerCount) {
            resetToInitialState();
            return;
        }
        if (gameApp != null) {
            gameApp.showMainMenu();
        }
    }

    private void resetToInitialState() {
        choosingHostPlayerCount = false;
        lobbyPlayers.clear();
        refreshLobbyPlayerList();
        setPlayerCountButtonsVisible(false);
        setPlayerCountButtonsDisabled(false);
        setConnectionControlsDisabled(false);
        if (hostButton != null) {
            hostButton.setText("Create Game");
        }
        if (readyButton != null) {
            readyButton.setDisable(true);
            readyButton.setText("Ready");
        }
        stopReadyAttention();
        if (startButton != null) startButton.setDisable(true);
        if (hintLabel != null) {
            hintLabel.setText("Click Create Game to choose the room size, or Join Game to enter an existing room.");
        }
        if (statusLabel != null) {
            statusLabel.setText("Ready to connect...");
        }
    }

    private void resetToChoosePlayerCountState() {
        choosingHostPlayerCount = true;
        lobbyPlayers.clear();
        refreshLobbyPlayerList();
        setPlayerCountButtonsVisible(true);
        setPlayerCountButtonsDisabled(false);
        setConnectionControlsDisabled(false);
        if (hostButton != null) {
            hostButton.setText("Confirm Create Game");
        }
        if (readyButton != null) {
            readyButton.setDisable(true);
            readyButton.setText("Ready");
        }
        stopReadyAttention();
        if (startButton != null) startButton.setDisable(true);
        if (hintLabel != null) {
            hintLabel.setText("Choose 2, 3, 4, or 5 players, then click Confirm Create Game.");
        }
        if (statusLabel != null) {
            statusLabel.setText("Choose the player count for this room.");
        }
    }

    @FXML
    private void onPlayerCount2Clicked() {
        selectPlayerCount(2);
    }

    @FXML
    private void onPlayerCount3Clicked() {
        selectPlayerCount(3);
    }

    @FXML
    private void onPlayerCount4Clicked() {
        selectPlayerCount(4);
    }

    @FXML
    private void onPlayerCount5Clicked() {
        selectPlayerCount(5);
    }

    private void selectPlayerCount(int count) {
        playerCount = count;
        updatePlayerCountButtons();
    }

    private void updatePlayerCountButtons() {
        setPlayerCountButtonStyle(playerCount2Button, playerCount == 2);
        setPlayerCountButtonStyle(playerCount3Button, playerCount == 3);
        setPlayerCountButtonStyle(playerCount4Button, playerCount == 4);
        setPlayerCountButtonStyle(playerCount5Button, playerCount == 5);
    }

    private void setPlayerCountButtonStyle(Button button, boolean selected) {
        if (button == null) return;
        if (selected) {
            button.setStyle("-fx-padding: 8 16; -fx-font-size: 14px; -fx-font-weight: bold; -fx-background-color: #3b82f6; -fx-text-fill: white; -fx-border-color: #1d4ed8; -fx-border-radius: 8; -fx-background-radius: 8;");
        } else {
            button.setStyle("-fx-padding: 8 16; -fx-font-size: 14px; -fx-font-weight: bold; -fx-background-color: #ffffff; -fx-text-fill: #2f4f6f; -fx-border-color: #cbd5e1; -fx-border-radius: 8; -fx-background-radius: 8;");
        }
    }

    private void setPlayerCountButtonsDisabled(boolean disabled) {
        if (playerCountButtonBox == null) return;
        playerCountButtonBox.getChildren().forEach(node -> node.setDisable(disabled));
    }

    private void setPlayerCountButtonsVisible(boolean visible) {
        if (playerCountButtonBox == null) return;
        playerCountButtonBox.setVisible(visible);
        playerCountButtonBox.setManaged(visible);
    }

    @FXML
    private void onHostButtonClicked() {
        if (!choosingHostPlayerCount && gameServer == null) {
            choosingHostPlayerCount = true;
            setPlayerCountButtonsVisible(true);
            setPlayerCountButtonsDisabled(false);
            if (hostButton != null) {
                hostButton.setText("Confirm Create Game");
            }
            if (hintLabel != null) {
                hintLabel.setText("Choose 2, 3, 4, or 5 players, then click Confirm Create Game.");
            }
            statusLabel.setText("Choose the player count for this room.");
            return;
        }

        try {
            int port = Integer.parseInt(portField.getText());

            statusLabel.setText("Creating server...");
            lobbyPlayers.clear();
            lobbyPlayers.add("Player1 (You)");
            refreshLobbyPlayerList();
            localReady = false;
            if (readyButton != null) {
                readyButton.setDisable(false);
                readyButton.setText("Ready");
                startReadyAttention();
            }
            if (startButton != null) {
                startButton.setDisable(true);
            }

            gameServer = new GameServer(port, playerCount, AppSettings.getInstance().getPlayerName(), AppSettings.getInstance().getAvatarId());
            gameServer.setListener(new GameServer.OnGameStateChangeListener() {
                @Override
                public void onStateChanged(GameStateData state) {
                    Platform.runLater(() -> updateGameUI(state));
                }

                @Override
                public void onClientConnected(String playerName) {
                    Platform.runLater(() -> {
                        // Ignore synthetic callbacks
                        if (!"RoomUpdate".equals(playerName)) {
                            lobbyPlayers.add(playerName);
                            refreshLobbyPlayerList();
                        }
                        statusLabel.setText("Players connected: " + lobbyPlayers.size() + "/" + playerCount);
                        updateStartButtonState();
                    });
                }

                @Override
                public void onGameStarted() {
                    Platform.runLater(() -> {
                        statusLabel.setText("Game started! You are Player 1");
                        localPlayerIndex = 0;
                        startPlayerGame(0);
                    });
                }

                @Override
                public void onGameOver(String winner) {
                    Platform.runLater(() -> {
                        statusLabel.setText("Game Over! Winner: " + winner);
                    });
                }

                @Override
                public void onChatMessage(String playerId, String message) {
                    Platform.runLater(() -> {
                        GameController controller = GameController.getInstance();
                        if (controller != null) {
                            controller.receiveChatMessage(playerId, message);
                        }
                    });
                }
            });

            gameServer.start();
            isHost = true;
            setPlayerCountButtonsDisabled(true);
            if (hostButton != null) hostButton.setDisable(true);
            if (joinButton != null) joinButton.setDisable(true);
            if (portField != null) portField.setDisable(true);
            if (serverAddressField != null) serverAddressField.setDisable(true);
            statusLabel.setText("Server started. Waiting for players: " + lobbyPlayers.size() + "/" + playerCount);
            if (hintLabel != null) {
                hintLabel.setText("Click Ready first. When everyone is Ready, click Start (Host).");
            }

        } catch (NumberFormatException e) {
            showError("Please enter valid numbers");
        } catch (IOException e) {
            showError("Failed to start server: " + e.getMessage());
        }
    }

    @FXML
    private void onJoinButtonClicked() {
        try {
            String address = serverAddressField.getText();
            int port = Integer.parseInt(portField.getText());

            statusLabel.setText("Connecting to " + address + ":" + port + "...");
            lobbyPlayers.clear();
            refreshLobbyPlayerList();
            localReady = false;
            if (readyButton != null) {
                readyButton.setDisable(true);
                readyButton.setText("Ready");
                stopReadyAttention();
            }
            if (startButton != null) {
                startButton.setDisable(true);
            }

            gameClient = new GameClient(address, port);
            gameClient.setLocalPlayerName(AppSettings.getInstance().getPlayerName());
            gameClient.setLocalAvatarId(AppSettings.getInstance().getAvatarId());
            gameClient.setListener(new GameClient.OnMessageReceivedListener() {
                @Override
                public void onConnected() {
                    Platform.runLater(() -> {
                        localPlayerIndex = gameClient.getAssignedPlayerIndex();
                        statusLabel.setText("Connected as Player " + (localPlayerIndex + 1) + ". Waiting for the host to start the game...");
                        if (hintLabel != null) {
                            hintLabel.setText("Click Ready. The host will Start when everyone is Ready.");
                        }
                        if (readyButton != null) {
                            readyButton.setDisable(false);
                            startReadyAttention();
                        }
                    });
                }

                @Override
                public void onConnectFailed(String error) {
                    Platform.runLater(() -> {
                        showError(normalizeConnectionError(error));
                        statusLabel.setText("Disconnected");
                        setConnectionControlsDisabled(false);
                        setPlayerCountButtonsDisabled(false);
                        if (readyButton != null) {
                            readyButton.setDisable(true);
                            readyButton.setText("Ready");
                        }
                        stopReadyAttention();
                    });
                }

                @Override
                public void onGameStarted(int playerCount) {
                    Platform.runLater(() -> {
                        statusLabel.setText("Game started!");
                        NetworkGameController.this.playerCount = playerCount;
                        int assignedIndex = gameClient.getAssignedPlayerIndex();
                        if (assignedIndex < 1 || assignedIndex >= playerCount) {
                            showError("Invalid player assignment from server.");
                            return;
                        }
                        localPlayerIndex = assignedIndex;
                        startPlayerGame(assignedIndex);
                    });
                }

                @Override
                public void onRequirePayment(int amount, String collectorId) {
                    Platform.runLater(() -> {
                        GameController.getInstance().handleRequirePayment(amount, collectorId);
                    });
                }

                @Override
                public void onAskJustSayNo(String sourcePlayer, String actionName) {
                    Platform.runLater(() -> {
                        GameController.getInstance().handleAskJustSayNo(sourcePlayer, actionName);
                    });
                }

                @Override
                public void onGameStateReceived(GameStateData state) {
                    Platform.runLater(() -> {
                        updateGameUI(state);
                    });
                }

                @Override
                public void onTurnChanged(int playerIndex) {
                    Platform.runLater(() -> {
                        turnInfoLabel.setText("Current turn: Player " + (playerIndex + 1));
                        isMyTurn = (playerIndex == localPlayerIndex);
                        endTurnButton.setDisable(!isMyTurn);
                    });
                }

                @Override
                public void onGameOver(String winner) {
                    Platform.runLater(() -> {
                        statusLabel.setText("Game Over! Winner: " + winner);
                    });
                }

                @Override
                public void onChatMessage(String playerId, String message) {
                    Platform.runLater(() -> {
                        GameController controller = GameController.getInstance();
                        if (controller != null) {
                            controller.receiveChatMessage(playerId, message);
                        }
                    });
                }

                @Override
                public void onError(String message) {
                    Platform.runLater(() -> {
                        showError(message);
                    });
                }

                @Override
                public void onRoomUpdate(String content) {
                    Platform.runLater(() -> {
                        applyRoomUpdate(content);
                    });
                }
            });

            gameClient.connect();
            isHost = false;
            setPlayerCountButtonsDisabled(true);
            setConnectionControlsDisabled(true);

        } catch (NumberFormatException e) {
            showError("Please enter valid port number");
        }
    }

    private void setConnectionControlsDisabled(boolean disabled) {
        if (hostButton != null) hostButton.setDisable(disabled);
        if (joinButton != null) joinButton.setDisable(disabled);
        if (portField != null) portField.setDisable(disabled);
        if (serverAddressField != null) serverAddressField.setDisable(disabled);
    }

    private String normalizeConnectionError(String error) {
        if (error == null || error.isBlank()) {
            return "Connection failed.";
        }
        String message = error;
        if (message.startsWith("ERROR: ")) {
            message = message.substring("ERROR: ".length());
        }
        if ("Game is full".equalsIgnoreCase(message)) {
            return "This room is full. You cannot join it.";
        }
        return "Connection failed: " + message;
    }

    @FXML
    private void onReadyClicked() {
        if (!isHost && (gameClient == null || !gameClient.isConnected())) {
            showError("Not connected to server.");
            return;
        }
        localReady = !localReady;
        if (readyButton != null) {
            readyButton.setText(localReady ? "Ready ✓" : "Ready");
            setReadyButtonColor(localReady);
        }
        if (localReady) {
            stopReadyAttention();
        } else {
            startReadyAttention();
        }

        if (isHost) {
            if (gameServer != null) {
                gameServer.setHostReady(localReady);
                updateStartButtonState();
                statusLabel.setText("Ready state updated. Players: " + lobbyPlayers.size() + "/" + playerCount);
            }
        } else {
            if (gameClient != null && gameClient.isConnected()) {
                gameClient.sendToggleReady(localReady);
            }
        }
    }

    @FXML
    private void onStartClicked() {
        if (!isHost) return;
        if (gameServer == null) return;

        if (!gameServer.canStartGame()) {
            showError("Cannot start: all players must be connected and ready.");
            return;
        }
        boolean ok = gameServer.startGameManually();
        if (!ok) {
            showError("Failed to start the game.");
        }
    }

    private void updateStartButtonState() {
        if (startButton == null) return;
        if (!isHost || gameServer == null) {
            startButton.setDisable(true);
            return;
        }
        startButton.setDisable(!gameServer.canStartGame());
        if (hintLabel != null) {
            hintLabel.setText(startButton.isDisable()
                    ? "Waiting: all players must be connected and Ready."
                    : "All players are Ready. Click Start to begin.");
        }
    }

    private void applyRoomUpdate(String content) {
        // Format: "P1=1,P2=0,P3=1"
        if (content == null || content.isBlank()) return;
        lobbyPlayers.clear();
        String[] parts = content.split(",");
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            String[] kv = p.split("=");
            String label = "Player " + (i + 1);
            boolean ready = kv.length > 1 && "1".equals(kv[1]);
            if (!isHost && i == 1) label += " (You)";
            lobbyPlayers.add(label + (ready ? " ✓" : ""));
        }
        refreshLobbyPlayerList();
        statusLabel.setText("Lobby: " + lobbyPlayers.size() + " players");
    }

    private void refreshLobbyPlayerList() {
        if (playerInfoBox == null || playerListBox == null) return;
        playerInfoBox.setVisible(true);
        playerListBox.getChildren().clear();
        for (String p : lobbyPlayers) {
            Label chip = new Label(p);
            chip.getStyleClass().add("chip");
            playerListBox.getChildren().add(chip);
        }
    }

    private void startReadyAttention() {
        if (readyButton == null || readyButton.isDisable()) return;
        if (readyAttention != null) {
            readyAttention.stop();
        }
        readyAttention = new ScaleTransition(Duration.millis(550), readyButton);
        readyAttention.setFromX(1.0);
        readyAttention.setFromY(1.0);
        readyAttention.setToX(1.06);
        readyAttention.setToY(1.06);
        readyAttention.setAutoReverse(true);
        readyAttention.setCycleCount(Animation.INDEFINITE);
        readyAttention.play();
    }

    private void stopReadyAttention() {
        if (readyAttention != null) {
            readyAttention.stop();
            readyAttention = null;
        }
        if (readyButton != null) {
            readyButton.setScaleX(1.0);
            readyButton.setScaleY(1.0);
        }
    }

    private void setReadyButtonColor(boolean ready) {
        if (readyButton == null) return;
        var classes = readyButton.getStyleClass();
        classes.remove("primary");
        classes.remove("success");
        if (ready) {
            classes.add("success");
        } else {
            classes.add("primary");
        }
    }

    private void startPlayerGame(int playerIndex) {
        try {
            gameApp.startOnlineGame(isHost, playerIndex, playerCount, gameServer, gameClient);
        } catch (Exception e) {
            showError("Failed to start game: " + e.getMessage());
        }
    }

    private void updateGameUI(GameStateData state) {
        if (state == null) return;

        int currentPlayer = state.getCurrentPlayerIndex();
        isMyTurn = (currentPlayer == localPlayerIndex);

        turnInfoLabel.setText("Current turn: Player " + (currentPlayer + 1)
                + (isMyTurn ? " (You)" : "")
                + " | Cards played: " + state.getPlayedCardsThisTurn()
                + "/" + state.getMaxPlayCountPerTurn());

        endTurnButton.setDisable(!isMyTurn);
        updatePlayerDisplays(state);

        // Always forward to GameController (it handles host/client differences)
        GameController gameController = GameController.getInstance();
        if (gameController != null) {
            gameController.updateFromServerState(state);
        }
    }

    private void updatePlayerDisplays(GameStateData state) {
        List<GameStateData.PlayerData> players = state.getPlayers();
        for (int i = 0; i < players.size(); i++) {
            GameStateData.PlayerData player = players.get(i);
            boolean isLocalPlayer = (i == localPlayerIndex);

            if (isLocalPlayer) {
                if (statusLabel != null) {
                    statusLabel.setText("You: " + player.getPlayerName()
                            + " | Hand: " + player.getHandCardCount()
                            + " | Bank cards: " + player.getBankCards().size());
                }
            }
        }
    }

    @FXML
    private void onEndTurnClicked() {
        if (!isMyTurn) {
            showError("It's not your turn!");
            return;
        }

        if (isHost && gameServer != null) {
            gameServer.getGameManager().confirmCurrentPlayerTurnEnded();
            if (gameServer.getGameManager().canAdvanceTurn()) {
                gameServer.getGameManager().advanceTurn();
            }
            gameServer.broadcastGameState();
        } else if (!isHost && gameClient != null) {
            gameClient.sendAction("END_TURN");
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void cleanup() {
        if (gameServer != null) {
            if (isHost) {
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
        if (gameClient != null) {
            gameClient.close();
        }
    }
}
