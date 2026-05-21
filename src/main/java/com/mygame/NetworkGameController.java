package com.mygame;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

/**
 * 联机游戏控制器
 * 处理网络对战的UI和逻辑
 */
public class NetworkGameController {

    @FXML private VBox mainPanel;
    @FXML private TextField serverAddressField;
    @FXML private TextField portField;
    @FXML private TextField playerCountField;
    @FXML private Button hostButton;
    @FXML private Button joinButton;
    @FXML private Label statusLabel;
    @FXML private HBox gameArea;
    @FXML private Button endTurnButton;
    @FXML private Label turnInfoLabel;

    private GameServer gameServer;
    private GameClient gameClient;
    private boolean isHost;
    private int localPlayerIndex;
    private GameApp gameApp;
    private Stage primaryStage;
    private boolean isMyTurn = false;
    private int playerCount = 2;

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
        playerCountField.setText("2");

        gameArea.setVisible(false);
        endTurnButton.setDisable(true);

        playerCountField.textProperty().addListener((obs, old, val) -> {
            if (!val.matches("[2-5]")) {
                playerCountField.setText(old);
            }
        });
    }

    @FXML
    private void onHostButtonClicked() {
        try {
            int port = Integer.parseInt(portField.getText());
            playerCount = Integer.parseInt(playerCountField.getText());

            statusLabel.setText("Creating server...");

            gameServer = new GameServer(port, playerCount);
            gameServer.setListener(new GameServer.OnGameStateChangeListener() {
                @Override
                public void onStateChanged(GameStateData state) {
                    Platform.runLater(() -> updateGameUI(state));
                }

                @Override
                public void onClientConnected(String playerName) {
                    Platform.runLater(() -> {
                        statusLabel.setText("Client connected: " + playerName);
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
            });

            gameServer.start();
            isHost = true;
            statusLabel.setText("Server started on port " + port + ". Waiting for players...");

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

            gameClient = new GameClient(address, port);
            gameClient.setListener(new GameClient.OnMessageReceivedListener() {
                @Override
                public void onConnected() {
                    Platform.runLater(() -> {
                        statusLabel.setText("Connected to server!");
                    });
                }

                @Override
                public void onConnectFailed(String error) {
                    Platform.runLater(() -> {
                        showError("Connection failed: " + error);
                        statusLabel.setText("Disconnected");
                    });
                }

                @Override
                public void onGameStarted(int playerCount) {
                    Platform.runLater(() -> {
                        statusLabel.setText("Game started!");
                        localPlayerIndex = 1;
                        startPlayerGame(1);
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
                    // 聊天功能
                }

                @Override
                public void onError(String message) {
                    Platform.runLater(() -> {
                        showError(message);
                    });
                }
            });

            gameClient.connect();
            isHost = false;

        } catch (NumberFormatException e) {
            showError("Please enter valid port number");
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

        // 始终转发给 GameController（它内部区分 Host/Client 处理）
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
                System.out.println("Local Player " + (i + 1) + ": " + player.getPlayerName()
                        + " | Hand: " + player.getHandCardCount()
                        + " | Bank cards: " + player.getBankCards().size());
            } else {
                System.out.println("Opponent " + (i + 1) + ": " + player.getPlayerName()
                        + " | Hand: " + player.getHandCardCount()
                        + " | Bank cards: " + player.getBankCards().size());
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
            gameServer.stop();
        }
        if (gameClient != null) {
            gameClient.close();
        }
    }
}