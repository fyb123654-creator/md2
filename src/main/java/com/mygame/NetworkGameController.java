package com.mygame;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

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
    private GameController gameController;
    private boolean isHost;
    private int localPlayerIndex;

    @FXML
    public void initialize() {
        // 设置默认值
        serverAddressField.setText("127.0.0.1");
        portField.setText("12345");
        playerCountField.setText("2");
        
        // 隐藏游戏区域
        gameArea.setVisible(false);
        
        // 添加数字验证
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
            int playerCount = Integer.parseInt(playerCountField.getText());
            
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
                        startGameUI(true, 0);
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
            localPlayerIndex = 0;
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
                        startGameUI(false, 1); // 第二个连接的玩家
                    });
                }

                @Override
                public void onGameStateReceived(GameStateData state) {
                    Platform.runLater(() -> updateGameUI(state));
                }

                @Override
                public void onTurnChanged(int playerIndex) {
                    Platform.runLater(() -> {
                        turnInfoLabel.setText("Current turn: Player " + (playerIndex + 1));
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
            localPlayerIndex = 1;
            
        } catch (NumberFormatException e) {
            showError("Please enter valid port number");
        }
    }

    private void startGameUI(boolean isHost, int playerIndex) {
        mainPanel.setVisible(false);
        gameArea.setVisible(true);
        
        // 初始化游戏控制器
        gameController = new GameController();
        int playerCount = Integer.parseInt(playerCountField.getText());
        gameController.initializeGame(playerCount);
        
        // 更新UI
        turnInfoLabel.setText("Current turn: Player 1");
    }

    private void updateGameUI(GameStateData state) {
        if (gameController != null) {
            // 更新回合信息
            turnInfoLabel.setText("Current turn: Player " + (state.getCurrentPlayerIndex() + 1)
                    + " | Cards played: " + state.getPlayedCardsThisTurn()
                    + "/" + state.getMaxPlayCountPerTurn());
            
            // 更新玩家信息显示
            updatePlayerDisplays(state);
        }
    }

    private void updatePlayerDisplays(GameStateData state) {
        // 这里需要根据state更新各个玩家的显示
        // 包括手牌数量、银行、物业等
        List<GameStateData.PlayerData> players = state.getPlayers();
        for (int i = 0; i < players.size(); i++) {
            GameStateData.PlayerData player = players.get(i);
            boolean isLocalPlayer = (isHost && i == 0) || (!isHost && i == 1);
            
            if (isLocalPlayer) {
                // 更新本地玩家显示
                System.out.println("Local player " + player.getPlayerName() + " hand: " + player.getHandCardCount());
            } else {
                // 更新对手显示
                System.out.println("Opponent " + player.getPlayerName() + " hand: " + player.getHandCardCount());
            }
        }
    }

    @FXML
    private void onEndTurnClicked() {
        if (isHost && gameServer != null) {
            // 主机端处理回合结束
            gameServer.getGameManager().confirmCurrentPlayerTurnEnded();
            
            if (gameServer.getGameManager().canAdvanceTurn()) {
                gameServer.getGameManager().advanceTurn();
            }
            
            // 广播新状态
            gameServer.broadcastGameState();
        } else if (!isHost && gameClient != null) {
            // 客户端发送回合结束请求
            gameClient.sendAction("END_TURN");
        }
        
        updateUI();
    }

    private void updateUI() {
        if (gameController != null && gameServer != null) {
            GameStateData state = GameStateData.fromGameManager(gameServer.getGameManager());
            updateGameUI(state);
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
