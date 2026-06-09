package com.mygame.ui;

import com.mygame.app.AppSettings;
import com.mygame.app.GameApp;
import com.mygame.app.AvatarVisuals;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

public class OfflineLobbyController {

    @FXML private StackPane rootPane;
    @FXML private VBox heroPanel;
    @FXML private StackPane heroArtPane;
    @FXML private VBox playerConfigBox;
    @FXML private Label hintLabel;
    @FXML private Button playerCount2Button;
    @FXML private Button playerCount3Button;
    @FXML private Button playerCount4Button;
    @FXML private Button playerCount5Button;
    @FXML private Button backButton;
    @FXML private Button startButton;

    private GameApp gameApp;
    private int playerCount = 2;
    private final List<TextField> nameFields = new ArrayList<>();
    private final List<ComboBox<Integer>> avatarPickers = new ArrayList<>();
    private final List<StackPane> avatarPreviews = new ArrayList<>();

    @FXML
    public void initialize() {
        ensureBackgroundsApplied();
        int saved = AppSettings.getInstance().getOfflinePlayerCount();
        selectPlayerCount(saved >= 2 && saved <= 5 ? saved : 2);
        rebuildPlayerRows();
        refreshHint();
    }

    public void setGameApp(GameApp gameApp) {
        this.gameApp = gameApp;
    }

    private void ensureBackgroundsApplied() {
        if (rootPane != null) {
            rootPane.getStyleClass().addAll("menu-root", "menu-shell");
            applyPanelBackground(rootPane, "/images/background.png", true);
        }
        if (heroPanel != null) {
            heroPanel.getStyleClass().addAll("menu-card", "hero-image-panel", "offline-lobby-hero-panel");
            applyPanelBackground(heroPanel, "/images/menu/menu-hero.png", false);
        }
        if (heroArtPane != null) {
            heroArtPane.getStyleClass().addAll("image-slot", "menu-panel-art-slot");
            applyPanelBackground(heroArtPane, "/images/menu/menu-panel-art.png", false);
        }
    }

    private void applyPanelBackground(Region node, String resourcePath, boolean fullScreenBackground) {
        if (node == null || resourcePath == null || resourcePath.isBlank()) {
            return;
        }
        try {
            var url = getClass().getResource(resourcePath);
            if (url == null) {
                return;
            }
            StringBuilder style = new StringBuilder();
            if (fullScreenBackground) {
                style.append("-fx-background-color: #0b1220;");
            } else {
                style.append("-fx-background-color: rgba(255,255,255,0.10);");
            }
            style.append("-fx-background-image: url('").append(url.toExternalForm()).append("');")
                    .append("-fx-background-position: center center;")
                    .append("-fx-background-repeat: no-repeat;")
                    .append("-fx-background-size: cover;");
            String existing = node.getStyle();
            node.setStyle((existing == null ? "" : existing) + style);
        } catch (Exception ignored) {
        }
    }

    @FXML
    private void onPlayerCount2Clicked() { selectPlayerCount(2); }

    @FXML
    private void onPlayerCount3Clicked() { selectPlayerCount(3); }

    @FXML
    private void onPlayerCount4Clicked() { selectPlayerCount(4); }

    @FXML
    private void onPlayerCount5Clicked() { selectPlayerCount(5); }

    @FXML
    private void onBackClicked() {
        if (gameApp != null) {
            gameApp.showMainMenu();
        }
    }

    @FXML
    private void onStartClicked() {
        List<String> names = new ArrayList<>();
        List<Integer> avatars = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            String name = nameFields.get(i).getText();
            if (name == null || name.trim().isEmpty()) {
                name = "Player" + (i + 1);
            } else {
                name = name.trim();
            }
            Integer avatarId = avatarPickers.get(i).getValue();
            if (avatarId == null) {
                avatarId = i % 5;
            }
            names.add(name);
            avatars.add(Math.max(0, avatarId));
        }

        AppSettings.getInstance().setOfflinePlayerCount(playerCount);
        if (!names.isEmpty()) {
            AppSettings.getInstance().setPlayerName(names.get(0));
        }
        if (!avatars.isEmpty()) {
            AppSettings.getInstance().setAvatarId(avatars.get(0));
        }

        if (gameApp != null) {
            gameApp.startOfflineGameFromLobby(playerCount, names, avatars);
        }
    }

    private void selectPlayerCount(int count) {
        playerCount = count;
        highlightSelected(playerCount2Button, count == 2);
        highlightSelected(playerCount3Button, count == 3);
        highlightSelected(playerCount4Button, count == 4);
        highlightSelected(playerCount5Button, count == 5);
        rebuildPlayerRows();
        refreshHint();
    }

    private void highlightSelected(Button button, boolean selected) {
        if (button == null) return;
        if (selected) {
            if (!button.getStyleClass().contains("player-count-selected")) {
                button.getStyleClass().add("player-count-selected");
            }
        } else {
            button.getStyleClass().remove("player-count-selected");
        }
    }

    private void rebuildPlayerRows() {
        if (playerConfigBox == null) return;
        playerConfigBox.getChildren().clear();
        nameFields.clear();
        avatarPickers.clear();
        avatarPreviews.clear();

        int myAvatar = AppSettings.getInstance().getAvatarId();
        String myName = AppSettings.getInstance().getPlayerName();
        if (myName == null || myName.isBlank()) {
            myName = "Player1";
        }

        for (int i = 0; i < playerCount; i++) {
            VBox card = new VBox(10);
            card.getStyleClass().add("info-card");
            card.setStyle("-fx-padding: 14;");

            Label title = new Label("Player " + (i + 1));
            title.getStyleClass().add("panel-field-label");

            TextField nameField = new TextField(i == 0 ? myName : ("Player" + (i + 1)));
            nameField.getStyleClass().add("dark-field");
            nameField.setPromptText("Enter name");

            HBox avatarRow = new HBox(10);
            avatarRow.setAlignment(Pos.CENTER_LEFT);
            avatarRow.setPadding(new Insets(2, 0, 0, 0));

            StackPane avatarPreview = new StackPane();
            avatarPreview.setPrefSize(54, 54);

            ComboBox<Integer> avatarPicker = new ComboBox<>();
            avatarPicker.setItems(FXCollections.observableArrayList(0, 1, 2, 3, 4));
            avatarPicker.setMaxWidth(Double.MAX_VALUE);
            avatarPicker.setValue(i == 0 ? myAvatar : (i % 5));
            avatarPicker.setButtonCell(new javafx.scene.control.ListCell<>() {
                @Override
                protected void updateItem(Integer item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText("");
                    } else {
                        setText("Avatar " + (item + 1));
                    }
                }
            });
            avatarPicker.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
                @Override
                protected void updateItem(Integer item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText("");
                        setGraphic(null);
                        return;
                    }
                    setText("Avatar " + (item + 1));
                    Node node = AvatarVisuals.createAvatarNode(item, "A" + (item + 1), 26);
                    setGraphic(node);
                }
            });
            avatarPicker.valueProperty().addListener((obs, oldV, newV) -> updateAvatarPreview(avatarPreview, newV, nameField.getText()));

            avatarRow.getChildren().addAll(avatarPreview, avatarPicker);
            card.getChildren().addAll(title, nameField, avatarRow);

            nameField.textProperty().addListener((obs, oldV, newV) -> updateAvatarPreview(avatarPreview, avatarPicker.getValue(), newV));
            updateAvatarPreview(avatarPreview, avatarPicker.getValue(), nameField.getText());

            playerConfigBox.getChildren().add(card);
            nameFields.add(nameField);
            avatarPickers.add(avatarPicker);
            avatarPreviews.add(avatarPreview);
        }
    }

    private void updateAvatarPreview(StackPane target, Integer avatarId, String name) {
        if (target == null) return;
        int id = avatarId == null ? 0 : avatarId;
        String letter = (name == null || name.isBlank()) ? "A" : String.valueOf(Character.toUpperCase(name.trim().charAt(0)));
        target.getChildren().setAll(AvatarVisuals.createAvatarNode(id, letter, 44));
    }

    private void refreshHint() {
        if (hintLabel == null) return;
        hintLabel.setText("Choose players, then start the match. Each player draws 2 cards and can play up to 3 cards per turn.");
    }
}
