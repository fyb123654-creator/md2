package com.mygame.ui;

import com.mygame.network.GameClient;
import com.mygame.network.GameServer;
import com.mygame.network.dto.GameStateData;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.IOException;

public class OnlineGameController {

    @FXML private HBox myHandBox;
    @FXML private HBox handActionBox;
    @FXML private HBox myBankBox;
    @FXML private HBox myPropertyBox;
    @FXML private VBox opponentAreaBox;
    @FXML private Label turnInfoLabel;
    @FXML private Button endTurnButton;

    private GameClient gameClient;
    private GameServer gameServer;
    private boolean isHost;
    private int localPlayerIndex;
    private boolean isMyTurn = false;

    @FXML
    public void initialize() {
        endTurnButton.setDisable(true);
    }

    public void startAsHost(int port, int playerCount) throws IOException {
        isHost = true;
        localPlayerIndex = 0;

        gameServer = new GameServer(port, playerCount);
        gameServer.setListener(new GameServer.OnGameStateChangeListener() {
            @Override
            public void onStateChanged(GameStateData state) {
                Platform.runLater(() -> updateFromState(state));
            }

            @Override
            public void onClientConnected(String playerName) {
                Platform.runLater(() -> {
                    turnInfoLabel.setText("Client connected: " + playerName);
                });
            }

            @Override
            public void onGameStarted() {
                Platform.runLater(() -> {
                    turnInfoLabel.setText("Game started! You are Player 1");
                    isMyTurn = true;
                    endTurnButton.setDisable(false);
                });
            }

            @Override
            public void onGameOver(String winner) {
                Platform.runLater(() -> {
                    turnInfoLabel.setText("Game Over! Winner: " + winner);
                    isMyTurn = false;
                    endTurnButton.setDisable(true);
                });
            }
        });

        gameServer.start();
        turnInfoLabel.setText("Server started on port " + port + ". Waiting for players...");
    }

    public void startAsClient(String address, int port) {
        isHost = false;
        localPlayerIndex = 1;

        gameClient = new GameClient(address, port);
        gameClient.setListener(new GameClient.OnMessageReceivedListener() {
            @Override
            public void onConnected() {
                Platform.runLater(() -> {
                    turnInfoLabel.setText("Connected! Waiting for game...");
                });
            }

            @Override
            public void onConnectFailed(String error) {
                Platform.runLater(() -> showError("Connection failed: " + error));
            }

            @Override
            public void onGameStarted(int playerCount) {
                Platform.runLater(() -> {
                    turnInfoLabel.setText("Game started! You are Player 2");
                });
            }

            @Override
            public void onGameStateReceived(GameStateData state) {
                Platform.runLater(() -> updateFromState(state));
            }

            @Override
            public void onTurnChanged(int playerIndex) {
                Platform.runLater(() -> {
                    isMyTurn = (playerIndex == localPlayerIndex);
                    endTurnButton.setDisable(!isMyTurn);
                });
            }

            @Override
            public void onGameOver(String winner) {
                Platform.runLater(() -> {
                    turnInfoLabel.setText("Game Over! Winner: " + winner);
                    isMyTurn = false;
                    endTurnButton.setDisable(true);
                });
            }

            @Override
            public void onError(String message) {
                Platform.runLater(() -> showError(message));
            }

            @Override
            public void onChatMessage(String playerId, String message) {}

            @Override
            public void onRequirePayment(int amount, String collectorId) {
                Platform.runLater(() -> {
                    if (GameController.getInstance() != null) {
                        GameController.getInstance().handleRequirePayment(amount, collectorId);
                    }
                });
            }

            @Override
            public void onAskJustSayNo(String sourcePlayer, String actionName) {
                Platform.runLater(() -> {
                    if (GameController.getInstance() != null) {
                        GameController.getInstance().handleAskJustSayNo(sourcePlayer, actionName);
                    }
                });
            }
        });

        gameClient.connect();
    }

    private void updateFromState(GameStateData state) {
        int currentPlayer = state.getCurrentPlayerIndex();
        isMyTurn = (currentPlayer == localPlayerIndex);
        endTurnButton.setDisable(!isMyTurn);

        turnInfoLabel.setText("Current turn: Player " + (currentPlayer + 1) +
                " | Cards: " + state.getPlayedCardsThisTurn() + "/" + state.getMaxPlayCountPerTurn() +
                (isMyTurn ? " (You)" : ""));
    }

    @FXML
    private void onEndTurnClicked() {
        if (!isMyTurn) return;

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
        if (gameServer != null) gameServer.stop();
        if (gameClient != null) gameClient.close();
    }
}
