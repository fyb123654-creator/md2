package com.mygame.app;

import com.mygame.network.GameClient;
import com.mygame.network.GameServer;
import com.mygame.ui.GameController;
import com.mygame.ui.NetworkGameController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class GameApp extends Application {

    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;
        showModeSelection();
    }

    private void showModeSelection() {
        VBox root = new VBox(16);
        root.getStyleClass().add("menu-root");
        root.setStyle("-fx-padding: 48; -fx-alignment: center;");

        Label title = new Label("Monopoly Deal");
        title.setStyle("-fx-font-size: 44px; -fx-font-weight: 800; -fx-text-fill: white;");

        TextField nameField = new TextField(AppSettings.getInstance().getPlayerName());
        nameField.setMaxWidth(360);
        nameField.setPromptText("Enter your name");

        Button singlePlayerBtn = new Button("Single Player");
        singlePlayerBtn.setStyle("-fx-padding: 15 40; -fx-font-size: 18px; -fx-font-weight: bold; " +
                "-fx-background-color: linear-gradient(#4CAF50, #45a049); -fx-text-fill: white; " +
                "-fx-border-radius: 12; -fx-background-radius: 12;");
        singlePlayerBtn.setOnAction(e -> {
            AppSettings.getInstance().setPlayerName(nameField.getText());
            startSinglePlayer();
        });

        Button onlineBtn = new Button("Online Multiplayer");
        onlineBtn.setStyle("-fx-padding: 15 40; -fx-font-size: 18px; -fx-font-weight: bold; " +
                "-fx-background-color: linear-gradient(#2196F3, #1976D2); -fx-text-fill: white; " +
                "-fx-border-radius: 12; -fx-background-radius: 12;");
        onlineBtn.setOnAction(e -> {
            AppSettings.getInstance().setPlayerName(nameField.getText());
            startOnlineMultiplayer();
        });

        Button helpBtn = new Button("Help");
        helpBtn.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Help");
            alert.setHeaderText("Quick Rules");
            alert.setContentText(
                    "Goal: collect 3 complete property sets.\n" +
                            "Each turn: draw 2 cards, play up to 3 cards, then keep max 7 cards in hand.\n" +
                            "Money cards go to Bank, property cards go to Property Area, action cards apply effects.\n" +
                            "\n" +
                            "Tip: click a hand card to see available actions.");
            alert.showAndWait();
        });

        root.getChildren().addAll(title, nameField, singlePlayerBtn, onlineBtn, helpBtn);

        Scene scene = new Scene(root, 960, 640);
        applyTheme(scene);
        primaryStage.setTitle("Monopoly Deal - Mode Selection");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void startSinglePlayer() {
        try {
            LoadedView<GameController> view = ViewLoader.load("/GameView.fxml");
            Parent root = view.getRoot();
            GameController controller = view.getController();

            Scene scene = new Scene(root, 1280, 720);
            applyTheme(scene);
            primaryStage.setTitle("Monopoly Deal");
            primaryStage.setScene(scene);
            primaryStage.show();
            Platform.runLater(() -> controller.initializeGame(0));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startOnlineMultiplayer() {
        try {
            LoadedView<NetworkGameController> view = ViewLoader.load("/NetworkGameView.fxml");
            Parent root = view.getRoot();
            NetworkGameController controller = view.getController();
            controller.setGameApp(this);
            
            primaryStage.setOnCloseRequest(e -> controller.cleanup());

            Scene scene = new Scene(root, 600, 400);
            applyTheme(scene);
            primaryStage.setTitle("Monopoly Deal - Online");
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Called after the online connection succeeds; switches to the main game view.
    public void startOnlineGame(boolean isHost, int playerIndex, int playerCount, GameServer gameServer, GameClient gameClient) {
        try {
            LoadedView<GameController> view = ViewLoader.load("/GameView.fxml");
            Parent root = view.getRoot();
            GameController controller = view.getController();
            // Configure online mode first, then initialize the game.
            controller.setOnlineMode(true, playerIndex);
            controller.setGameServer(gameServer);
            controller.setGameClient(gameClient);
            // Only the host initializes the game; clients wait for server state.
            if (isHost) {
                controller.initializeGame(playerCount);
                // Safety net: if the server already broadcast the first state before GameController existed,
                // sync it once after the scene is created.
                if (gameServer != null && gameServer.getLastBroadcastState() != null) {
                    controller.updateFromServerState(gameServer.getLastBroadcastState());
                }
            } else {
                // Prevent the client from getting stuck if the GAME_STATE arrives before the controller is ready.
                if (gameClient != null && gameClient.getLastGameState() != null) {
                    controller.updateFromServerState(gameClient.getLastGameState());
                }
            }

            Scene scene = new Scene(root, 1280, 720);
            applyTheme(scene);
            primaryStage.setTitle("Monopoly Deal - Online Game");
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void applyTheme(Scene scene) {
        try {
            var url = getClass().getResource("/theme.css");
            if (url != null) {
                scene.getStylesheets().add(url.toExternalForm());
            }
        } catch (Exception ignored) {
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
