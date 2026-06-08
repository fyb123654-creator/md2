package com.mygame.app;

import com.mygame.network.GameClient;
import com.mygame.network.GameServer;
import com.mygame.ui.GameController;
import com.mygame.ui.NetworkGameController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class GameApp extends Application {

    private Stage primaryStage;
    private MediaPlayer bgmPlayer;
    private double bgmVolume = 0.62;

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;
        showMainMenu();
    }

    public void showMainMenu() {
        StackPane root = new StackPane();
        root.getStyleClass().addAll("menu-root", "menu-shell");

        HBox shell = new HBox(24);
        shell.setMaxWidth(1180);
        shell.setAlignment(Pos.CENTER);

        TextField nameField = new TextField(AppSettings.getInstance().getPlayerName());
        nameField.setMaxWidth(Double.MAX_VALUE);
        nameField.setPromptText("Enter your name");
        nameField.getStyleClass().add("dark-field");

        Label title = new Label("Monopoly Deal");
        title.getStyleClass().add("hero-title");
        Label subtitle = new Label("A redesigned card table with image slots, layered panels, and a cleaner flow from menu to match.");
        subtitle.getStyleClass().add("hero-subtitle");
        subtitle.setWrapText(true);

        TilePane avatarRow = new TilePane();
        avatarRow.setHgap(12);
        avatarRow.setVgap(12);
        avatarRow.setPrefColumns(3);
        avatarRow.setAlignment(Pos.CENTER_LEFT);
        List<Button> avatarButtons = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            int avatarId = i;
            Button avatarButton = new Button();
            avatarButton.setPrefSize(92, 92);
            avatarButton.setMinSize(92, 92);
            avatarButton.setMaxSize(92, 92);
            avatarButton.getStyleClass().add("avatar-select-button");
            VBox graphic = new VBox(6);
            graphic.setAlignment(Pos.CENTER);
            graphic.getChildren().add(AvatarVisuals.createAvatarNode(avatarId, "A" + (avatarId + 1), 54));
            avatarButton.setGraphic(graphic);
            avatarButton.setOnAction(e -> {
                AppSettings.getInstance().setAvatarId(avatarId);
                updateAvatarButtonStyles(avatarButtons);
            });
            avatarButtons.add(avatarButton);
            avatarRow.getChildren().add(avatarButton);
        }
        updateAvatarButtonStyles(avatarButtons);

        Label avatarLabel = new Label("Avatar Selection");
        avatarLabel.getStyleClass().add("panel-field-label");

        Button singlePlayerBtn = createMenuActionButton("Play Offline", "success");
        singlePlayerBtn.setOnAction(e -> {
            persistPlayerName(nameField);
            startSinglePlayer();
        });

        Button onlineBtn = createMenuActionButton("Online Multiplayer", "primary");
        onlineBtn.setOnAction(e -> {
            persistPlayerName(nameField);
            startOnlineMultiplayer();
        });

        Button helpBtn = createMenuActionButton("Quick Rules", null);
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

        VBox leftHero = new VBox(18);
        leftHero.setPadding(new Insets(28));
        leftHero.setAlignment(Pos.TOP_LEFT);
        leftHero.getStyleClass().addAll("menu-card", "hero-image-panel");
        leftHero.setPrefWidth(520);
        applyPanelBackground(leftHero, "/images/menu/menu-hero.png");
        VBox offlineRulesCard = createRulesCard(
                "Offline Rules",
                "1. Start a local match with 2 to 5 players.",
                "2. Every turn draws 2 cards and allows up to 3 plays.",
                "3. End the turn with 7 or fewer cards in hand.",
                "4. Build 3 complete property sets to win the match."
        );
        offlineRulesCard.getStyleClass().add("dark-rules-card");
        VBox heroShield = new VBox(12, title, subtitle, offlineRulesCard);
        heroShield.getStyleClass().add("hero-copy-shield");
        leftHero.getChildren().add(heroShield);

        VBox rightPanel = new VBox(16);
        rightPanel.setPadding(new Insets(28));
        rightPanel.setAlignment(Pos.TOP_LEFT);
        rightPanel.getStyleClass().add("menu-card");
        rightPanel.setPrefWidth(440);
        Label panelTitle = new Label("Player Setup");
        panelTitle.getStyleClass().add("panel-title");
        Label panelCopy = new Label("Pick a name, choose an avatar, and move into offline or online play.");
        panelCopy.getStyleClass().add("panel-copy");
        panelCopy.setWrapText(true);
        StackPane rightPanelArt = createPanelAccentSlot("/images/menu/menu-panel-art.png", 372, 150);
        VBox.setVgrow(nameField, Priority.NEVER);
        rightPanel.getChildren().addAll(panelTitle, panelCopy, nameField, avatarLabel, avatarRow, singlePlayerBtn, onlineBtn, helpBtn, rightPanelArt);

        shell.getChildren().addAll(leftHero, rightPanel);
        root.getChildren().add(shell);

        Scene scene = new Scene(root, 1280, 760);
        applyTheme(scene);
        primaryStage.setTitle("Monopoly Deal - Mode Selection");
        primaryStage.setScene(scene);
        primaryStage.show();
        Platform.runLater(this::ensureBgmPlaying);
    }

    private void startSinglePlayer() {
        try {
            LoadedView<GameController> view = ViewLoader.load("/GameView.fxml");
            Parent root = view.getRoot();
            GameController controller = view.getController();
            controller.setGameApp(this);
            primaryStage.setOnCloseRequest(e -> controller.cleanup());

            Scene scene = new Scene(root, 1440, 860);
            applyTheme(scene);
            primaryStage.setTitle("Monopoly Deal");
            primaryStage.setScene(scene);
            primaryStage.show();
            Platform.runLater(() -> controller.initializeGame(0));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateAvatarButtonStyles(List<Button> buttons) {
        int selected = AppSettings.getInstance().getAvatarId();
        for (int i = 0; i < buttons.size(); i++) {
            Button b = buttons.get(i);
            boolean active = i == selected;
            b.setStyle("-fx-background-color: transparent; -fx-padding: 0; -fx-border-radius: 999; -fx-background-radius: 999; -fx-border-width: 3; -fx-border-color: "
                    + (active ? "#ffffff" : "rgba(255,255,255,0.25)") + ";");
        }
    }

    public void ensureBgmPlaying() {
        if (bgmPlayer != null) {
            bgmPlayer.setMute(false);
            bgmPlayer.setVolume(bgmVolume);
            if (bgmPlayer.getStatus() != MediaPlayer.Status.PLAYING) {
                bgmPlayer.seek(javafx.util.Duration.ZERO);
                bgmPlayer.play();
            }
            return;
        }
        try {
            var url = resolveBgmResource();
            if (url == null) {
                System.err.println("BGM resource not found. Tried /audio/bgm.wav, /audio/bgm.mp3, /audio/bgm.m4a");
                return;
            }
            Media media = new Media(url.toExternalForm());
            media.setOnError(() -> {
                if (media.getError() != null) {
                    media.getError().printStackTrace();
                }
            });
            bgmPlayer = new MediaPlayer(media);
            bgmPlayer.setCycleCount(MediaPlayer.INDEFINITE);
            bgmPlayer.setOnReady(() -> {
                bgmPlayer.setMute(false);
                bgmPlayer.setVolume(bgmVolume);
                bgmPlayer.seek(javafx.util.Duration.ZERO);
                bgmPlayer.play();
            });
            bgmPlayer.setOnError(() -> {
                if (bgmPlayer.getError() != null) {
                    bgmPlayer.getError().printStackTrace();
                }
            });
            bgmPlayer.setAutoPlay(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private java.net.URL resolveBgmResource() {
        String[] candidates = {"/audio/bgm.wav", "/audio/bgm.mp3", "/audio/bgm.m4a"};
        for (String candidate : candidates) {
            var url = getClass().getResource(candidate);
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    public void setBgmVolume(double volume) {
        bgmVolume = Math.max(0.0, Math.min(1.0, volume));
        if (bgmPlayer != null) {
            bgmPlayer.setVolume(bgmVolume);
        }
    }

    public double getBgmVolume() {
        return bgmVolume;
    }

    private void persistPlayerName(TextField nameField) {
        String value = nameField == null ? "" : nameField.getText();
        AppSettings.getInstance().setPlayerName(value == null ? "" : value.trim());
    }

    private Button createMenuActionButton(String text, String intentStyleClass) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMinHeight(56);
        button.getStyleClass().addAll("button", "image-backed-button");
        if (intentStyleClass != null && !intentStyleClass.isBlank()) {
            button.getStyleClass().add(intentStyleClass);
        }
        button.setMouseTransparent(false);
        button.setDisable(false);
        installButtonGraphic(button);
        return button;
    }

    private void installButtonGraphic(Button button) {
        if (button == null) {
            return;
        }
        button.setGraphic(null);
        button.setContentDisplay(ContentDisplay.TEXT_ONLY);
        if (!button.getStyleClass().contains("image-backed-button")) {
            button.getStyleClass().add("image-backed-button");
        }
    }

    private StackPane createImageSlot(String title, String resourcePath, double width, double height) {
        StackPane slot = new StackPane();
        slot.getStyleClass().add("image-slot");
        slot.setPrefSize(width, height);
        slot.setMinSize(width, height);
        slot.setMaxWidth(width);
        String normalizedPath = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
        var url = getClass().getResource(normalizedPath);
        if (url != null) {
            slot.setStyle(
                    "-fx-background-color: transparent;" +
                            "-fx-background-image: url('" + url.toExternalForm() + "');" +
                            "-fx-background-position: center center;" +
                            "-fx-background-repeat: no-repeat;" +
                            "-fx-background-size: cover;"
            );
        }
        return slot;
    }

    private StackPane createPanelAccentSlot(String resourcePath, double width, double height) {
        StackPane slot = new StackPane();
        slot.getStyleClass().addAll("image-slot", "panel-accent-slot");
        slot.setPrefSize(width, height);
        slot.setMinSize(width, height);
        slot.setMaxWidth(width);
        try {
            String normalizedPath = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
            var url = getClass().getResource(normalizedPath);
            if (url != null) {
                slot.setStyle("-fx-background-color: rgba(255,255,255,0.08);"
                        + "-fx-background-image: url('" + url.toExternalForm() + "');"
                        + "-fx-background-position: center center;"
                        + "-fx-background-repeat: no-repeat;"
                        + "-fx-background-size: contain;");
            }
        } catch (Exception ignored) {
        }
        Region shield = new Region();
        shield.getStyleClass().add("panel-accent-shield");
        shield.setMouseTransparent(true);
        slot.getChildren().add(shield);
        return slot;
    }

    private VBox createRulesCard(String title, String... rules) {
        VBox card = new VBox(8);
        card.getStyleClass().add("rules-card");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("panel-title");
        card.getChildren().add(titleLabel);
        if (rules != null) {
            for (String rule : rules) {
                Label line = new Label(rule);
                line.getStyleClass().add("rule-line");
                line.setWrapText(true);
                card.getChildren().add(line);
            }
        }
        return card;
    }

    private void applyPanelBackground(Region node, String resourcePath) {
        if (node == null || resourcePath == null || resourcePath.isBlank()) {
            return;
        }
        try {
            var url = getClass().getResource(resourcePath);
            if (url == null) {
                return;
            }
            node.setStyle("-fx-background-color: rgba(255,255,255,0.10);"
                    + "-fx-background-image: url('" + url.toExternalForm() + "');"
                    + "-fx-background-position: center center;"
                    + "-fx-background-repeat: no-repeat;"
                    + "-fx-background-size: cover;");
        } catch (Exception ignored) {
        }
    }

    private void startOnlineMultiplayer() {
        showOnlineLobby();
    }

    public void showOnlineLobby() {
        try {
            LoadedView<NetworkGameController> view = ViewLoader.load("/NetworkGameView.fxml");
            Parent root = view.getRoot();
            NetworkGameController controller = view.getController();
            controller.setGameApp(this);
            controller.setPrimaryStage(primaryStage);
            
            primaryStage.setOnCloseRequest(e -> controller.cleanup());

            Scene scene = new Scene(root, 1220, 760);
            applyTheme(scene);
            primaryStage.setTitle("Monopoly Deal - Online");
            primaryStage.setScene(scene);
            primaryStage.show();
            Platform.runLater(this::ensureBgmPlaying);
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
            controller.setGameApp(this);
            primaryStage.setOnCloseRequest(e -> controller.cleanup());
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

            Scene scene = new Scene(root, 1440, 860);
            applyTheme(scene);
            primaryStage.setTitle("Monopoly Deal - Online Game");
            primaryStage.setScene(scene);
            primaryStage.show();
            Platform.runLater(this::ensureBgmPlaying);
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
