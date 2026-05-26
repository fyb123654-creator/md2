package com.mygame.app;

import com.mygame.network.GameClient;
import com.mygame.network.GameServer;
import com.mygame.ui.GameController;
import com.mygame.ui.NetworkGameController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.io.IOException;

public class GameApp extends Application {

    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;
        showModeSelection();
    }

    private void showModeSelection() {
        VBox root = new VBox(20);
        root.setStyle("-fx-padding: 40; -fx-alignment: center;");

        Label title = new Label("Monopoly Deal");
        title.setStyle("-fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: #2f4f6f;");

        Button singlePlayerBtn = new Button("Single Player");
        singlePlayerBtn.setStyle("-fx-padding: 15 40; -fx-font-size: 18px; -fx-font-weight: bold; " +
                "-fx-background-color: linear-gradient(#4CAF50, #45a049); -fx-text-fill: white; " +
                "-fx-border-radius: 12; -fx-background-radius: 12;");
        singlePlayerBtn.setOnAction(e -> startSinglePlayer());

        Button onlineBtn = new Button("Online Multiplayer");
        onlineBtn.setStyle("-fx-padding: 15 40; -fx-font-size: 18px; -fx-font-weight: bold; " +
                "-fx-background-color: linear-gradient(#2196F3, #1976D2); -fx-text-fill: white; " +
                "-fx-border-radius: 12; -fx-background-radius: 12;");
        onlineBtn.setOnAction(e -> startOnlineMultiplayer());

        root.getChildren().addAll(title, singlePlayerBtn, onlineBtn);

        Scene scene = new Scene(root, 400, 300);
        primaryStage.setTitle("Monopoly Deal - Mode Selection");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void startSinglePlayer() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/GameView.fxml"));
            Parent root = loader.load();

            GameController controller = loader.getController();
            controller.initializeGame(3);

            Scene scene = new Scene(root, 1280, 720);
            applyTheme(scene);
            primaryStage.setTitle("Monopoly Deal");
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startOnlineMultiplayer() {
        System.out.println("Starting online multiplayer...");
        try {
            System.out.println("Loading FXML...");
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/NetworkGameView.fxml"));

            if (loader.getLocation() == null) {
                System.out.println("FXML file not found!");
                throw new IOException("FXML file not found: /NetworkGameView.fxml");
            }
            System.out.println("FXML location: " + loader.getLocation());
            
            Parent root = loader.load();
            System.out.println("FXML loaded successfully");

            NetworkGameController controller = loader.getController();
            controller.setGameApp(this);
            System.out.println("Controller loaded");
            
            primaryStage.setOnCloseRequest(e -> controller.cleanup());

            Scene scene = new Scene(root, 600, 400);
            applyTheme(scene);
            primaryStage.setTitle("Monopoly Deal - Online");
            primaryStage.setScene(scene);
            primaryStage.show();
            System.out.println("Online mode window displayed");
        } catch (Exception e) {
            System.out.println("Error loading online mode: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Called after the online connection succeeds; switches to the main game view.
    public void startOnlineGame(boolean isHost, int playerIndex, int playerCount, GameServer gameServer, GameClient gameClient) {
        System.out.println("startOnlineGame called: isHost=" + isHost
                + ", playerIndex=" + playerIndex
                + ", playerCount=" + playerCount
                + ", gameServer=" + (gameServer != null ? "set" : "NULL")
                + ", gameClient=" + (gameClient != null ? "set" : "NULL"));
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/GameView.fxml"));
            Parent root = loader.load();

            GameController controller = loader.getController();
            System.out.println("GameController loaded, setting online mode...");
            // Configure online mode first, then initialize the game.
            controller.setOnlineMode(true, playerIndex);
            controller.setGameServer(gameServer);
            controller.setGameClient(gameClient);
            // Only the host initializes the game; clients wait for server state.
            if (isHost) {
                System.out.println("Host: calling initializeGame(" + playerCount + ")");
                controller.initializeGame(playerCount);
                // Safety net: if the server already broadcast the first state before GameController existed,
                // sync it once after the scene is created.
                if (gameServer != null && gameServer.getLastBroadcastState() != null) {
                    controller.updateFromServerState(gameServer.getLastBroadcastState());
                }
            } else {
                System.out.println("Client: skipping initializeGame, waiting for server state");
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
            System.out.println("Online game started - Player " + (playerIndex + 1));
        } catch (Exception e) {
            System.out.println("Error starting online game: " + e.getMessage());
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
