package com.mygame.ui;

import com.mygame.cards.action.*;
import com.mygame.cards.base.*;
import com.mygame.cards.money.*;
import com.mygame.app.AvatarVisuals;
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
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.animation.FadeTransition;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.util.Duration;
import javafx.geometry.Bounds;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class GameController {
    private static final int TURN_TIME_LIMIT_SECONDS = 180;
    private static final int TURN_HINT_WARNING_SECONDS = 20;
    private static final int TURN_ALERT_SECONDS = 10;

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
    private Label myPropertiesTitleLabel;
    @FXML
    private HBox opponentAreaBox;
    @FXML
    private StackPane clientAvatarPane;
    @FXML
    private StackPane rootStack;
    @FXML
    private BorderPane gameRootPane;
    @FXML
    private Pane animationLayer;
    @FXML
    private StackPane tableBoardPane;
    @FXML
    private StackPane tableCenterPane;
    @FXML
    private StackPane tablePlayedCardPane;
    @FXML
    private HBox tableCurrentBankBox;
    @FXML
    private FlowPane tableCurrentPropertyBox;
    @FXML
    private StackPane chatImagePane;
    @FXML
    private StackPane actionImagePane;
    @FXML
    private VBox feedPanel;
    @FXML
    private VBox actionPanel;
    @FXML
    private Label clientInfoLabel;
    @FXML
    private Label turnInfoLabel;
    @FXML
    private Label timerLabel;
    @FXML
    private Label actingPlayerLabel;
    @FXML
    private Label loadingLabel;
    @FXML
    private Button endTurnButton;
    @FXML
    private Button helpButton;
    @FXML
    private Button navBackButton;
    @FXML
    private Button discardModeButton;
    @FXML
    private Button handDrawerToggleButton;
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
    @FXML
    private ScrollPane handDrawerScrollPane;
    @FXML
    private VBox floatingHandSurface;
    @FXML
    private ScrollPane opponentScrollPane;
    @FXML
    private ScrollPane myBankScrollPane;
    @FXML
    private ScrollPane myPropertyScrollPane;

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
    private volatile int lastServerPlayedCardsThisTurn = 0;
    private volatile int lastServerMaxPlayCountPerTurn = GameManager.MAX_PLAY_COUNT_PER_TURN;
    private final Set<String> lastRenderedHandCardIds = new HashSet<>();
    private CardView hoveredHandCard;
    private boolean handDrawerExpanded = false;
    private Timeline turnTimer;
    private ParallelTransition suggestedCardPulse;
    private int displayedTurnSeconds = TURN_TIME_LIMIT_SECONDS;
    private int trackedTurnIndex = -1;
    private int trackedTurnClockId = -1;
    private Card latestTableActionCard;
    private String latestTableActionTitle = "Latest Action";

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
        setupAuxiliaryPanels();
        applyExplicitImageSurfaces();
    }

    private void setupHandPresentation() {
        if (myHandBox != null) {
            myHandBox.setSpacing(8);
        }
    }

    private void setupAuxiliaryPanels() {
        if (timerLabel != null) {
            timerLabel.setText(TURN_TIME_LIMIT_SECONDS + "s");
        }
        if (actingPlayerLabel != null) {
            actingPlayerLabel.setText("Table ready");
        }
        if (loadingLabel != null) {
            loadingLabel.setText("Waiting for player action");
        }
        if (discardModeButton != null) {
            discardModeButton.setDisable(true);
        }
        installButtonGraphics();
        updateHandDrawerState();
        installScrollSupport();
        renderTablePlaceholder();
        updateTimerDisplay();
        if (floatingHandSurface != null) {
            floatingHandSurface.setPickOnBounds(false);
            floatingHandSurface.setManaged(false);
            floatingHandSurface.setMaxWidth(1120);
            floatingHandSurface.setViewOrder(-900);
        }
        if (handDrawerScrollPane != null) {
            handDrawerScrollPane.setPickOnBounds(true);
        }
        if (handDrawerToggleButton != null) {
            handDrawerToggleButton.setPickOnBounds(true);
        }
        if (tableBoardPane != null) {
            tableBoardPane.setPickOnBounds(false);
        }
        if (handActionBox != null) {
            handActionBox.setPickOnBounds(false);
            handActionBox.setViewOrder(-200);
        }
        if (animationLayer != null) {
            animationLayer.toFront();
        }
        if (rootStack != null) {
            rootStack.widthProperty().addListener((obs, oldValue, newValue) -> positionFloatingHandSurface());
            rootStack.heightProperty().addListener((obs, oldValue, newValue) -> positionFloatingHandSurface());
        }
        if (floatingHandSurface != null) {
            floatingHandSurface.heightProperty().addListener((obs, oldValue, newValue) -> positionFloatingHandSurface());
            Platform.runLater(this::positionFloatingHandSurface);
        }
    }

    private void updateSuggestedHandCards(List<CardView> cardViews, List<Card> cards, boolean enabled) {
        stopSuggestedCardPulse();
        if (cardViews == null || cardViews.isEmpty()) {
            return;
        }
        boolean shouldSuggest = enabled && isMyTurn && displayedTurnSeconds <= TURN_HINT_WARNING_SECONDS && !discardMode;
        if (!shouldSuggest) {
            return;
        }
        List<String> suggestedIds = determineSuggestedCardIds(cards);
        if (suggestedIds.isEmpty()) {
            return;
        }
        boolean urgent = displayedTurnSeconds <= TURN_ALERT_SECONDS;
        ParallelTransition pulse = new ParallelTransition();
        for (CardView cardView : cardViews) {
            Card card = cardView.getCard();
            if (card == null || card.getId() == null || !suggestedIds.contains(card.getId())) {
                continue;
            }
            if (!cardView.getStyleClass().contains("suggested-hand-card")) {
                cardView.getStyleClass().add("suggested-hand-card");
            }
            if (urgent && !cardView.getStyleClass().contains("urgent-suggested-hand-card")) {
                cardView.getStyleClass().add("urgent-suggested-hand-card");
            }
            TranslateTransition lift = new TranslateTransition(Duration.millis(480), cardView);
            lift.setFromY(0);
            lift.setToY(-8);
            lift.setAutoReverse(true);
            lift.setCycleCount(Animation.INDEFINITE);
            ScaleTransition zoom = new ScaleTransition(Duration.millis(480), cardView);
            zoom.setFromX(1.0);
            zoom.setFromY(1.0);
            zoom.setToX(1.04);
            zoom.setToY(1.04);
            zoom.setAutoReverse(true);
            zoom.setCycleCount(Animation.INDEFINITE);
            pulse.getChildren().addAll(lift, zoom);
        }
        suggestedCardPulse = pulse.getChildren().isEmpty() ? null : pulse;
        if (suggestedCardPulse != null) {
            suggestedCardPulse.play();
        }
    }

    private List<String> determineSuggestedCardIds(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<String> ids = new ArrayList<>();
        Card propertyCard = findFirstCard(cards, Card::isPropertyCard);
        Card moneyCard = findFirstCard(cards, Card::canBeUsedAsMoney);
        Card actionCard = findFirstCard(cards, Card::isActionCard);
        if (propertyCard != null && propertyCard.getId() != null) {
            ids.add(propertyCard.getId());
        }
        if (moneyCard != null && moneyCard.getId() != null && !ids.contains(moneyCard.getId())) {
            ids.add(moneyCard.getId());
        }
        if (actionCard != null && actionCard.getId() != null && !ids.contains(actionCard.getId())) {
            ids.add(actionCard.getId());
        }
        return ids;
    }

    private void stopSuggestedCardPulse() {
        if (suggestedCardPulse != null) {
            suggestedCardPulse.stop();
            suggestedCardPulse = null;
        }
    }

    private void installButtonGraphics() {
        installButtonGraphic(sendChatButton);
        installButtonGraphic(navBackButton);
        installButtonGraphic(helpButton);
        installButtonGraphic(endTurnButton);
        installButtonGraphic(discardModeButton);
        installButtonGraphic(handDrawerToggleButton);
    }

    private void installButtonGraphic(Button button) {
        if (button == null) {
            return;
        }
        button.setGraphic(null);
        button.setContentDisplay(ContentDisplay.TEXT_ONLY);
        button.setPickOnBounds(true);
        if (!button.getStyleClass().contains("image-backed-button")) {
            button.getStyleClass().add("image-backed-button");
        }
    }

    private void applyExplicitImageSurfaces() {
        applyBackgroundImage(gameRootPane, "/images/background.png", "#f6f7fb");
        applyBackgroundImage(feedPanel, "/images/ui/log-panel.png", "rgba(255,255,255,0.82)");
        applyBackgroundImage(actionPanel, "/images/ui/action-panel.png", "rgba(255,255,255,0.82)");
        applyBackgroundImage(tableCenterPane, "/images/ui/table-surface.png", "rgba(18,42,72,0.78)");
        applyBackgroundImage(handDrawerScrollPane, "/images/ui/hand-surface.png", "rgba(255,255,255,0.18)");
        installTextAreaSurface(logArea, "/images/ui/log-panel.png");
        installTextAreaSurface(chatArea, "/images/ui/chat-box.png");
        Platform.runLater(() -> {
            applyRoundedClip(feedPanel, 18);
            applyRoundedClip(actionPanel, 18);
            applyTableSurfaceClip(tableCenterPane);
            applyRoundedClip(handDrawerScrollPane, 22);
            applyRoundedClip(floatingHandSurface, 22);
            applyRoundedClip(logArea, 12);
            applyRoundedClip(chatArea, 12);
            applyButtonClips();
        });
    }

    private void applyBackgroundImage(Region node, String resourcePath, String fallbackColor) {
        if (node == null) {
            return;
        }
        StringBuilder style = new StringBuilder();
        if (fallbackColor != null && !fallbackColor.isBlank()) {
            style.append("-fx-background-color: ").append(fallbackColor).append(";");
        }
        try {
            var url = getClass().getResource(resourcePath);
            if (url != null) {
                style.append("-fx-background-image: url('").append(url.toExternalForm()).append("');")
                        .append("-fx-background-position: center center;")
                        .append("-fx-background-repeat: no-repeat;")
                        .append("-fx-background-size: cover;");
            }
        } catch (Exception ignored) {
        }
        if (!style.isEmpty()) {
            node.setStyle(style.toString());
        }
    }

    private void applyTextAreaSurface(TextArea textArea, String resourcePath) {
        if (textArea == null) {
            return;
        }
        try {
            Node content = textArea.lookup(".content");
            if (!(content instanceof Region region)) {
                return;
            }
            var url = getClass().getResource(resourcePath);
            String style = "-fx-background-color: rgba(255,255,255,0.32);"
                    + "-fx-background-radius: 12;"
                    + "-fx-border-radius: 12;";
            if (url != null) {
                style += "-fx-background-image: url('" + url.toExternalForm() + "');"
                        + "-fx-background-position: center center;"
                        + "-fx-background-repeat: no-repeat;"
                        + "-fx-background-size: cover;";
            }
            region.setStyle(style);
            applyRoundedClip(region, 12);
        } catch (Exception ignored) {
        }
    }

    private void installTextAreaSurface(TextArea textArea, String resourcePath) {
        if (textArea == null) {
            return;
        }
        Platform.runLater(() -> applyTextAreaSurface(textArea, resourcePath));
        textArea.skinProperty().addListener((obs, oldSkin, newSkin) ->
                Platform.runLater(() -> applyTextAreaSurface(textArea, resourcePath)));
        textArea.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                Platform.runLater(() -> applyTextAreaSurface(textArea, resourcePath));
            }
        });
    }

    private void applyRoundedClip(Region node, double arc) {
        if (node == null) {
            return;
        }
        Rectangle clip = new Rectangle();
        clip.setArcWidth(arc);
        clip.setArcHeight(arc);
        clip.widthProperty().bind(node.widthProperty());
        clip.heightProperty().bind(node.heightProperty());
        node.setClip(clip);
    }

    private void applyTableSurfaceClip(Region node) {
        if (node == null) {
            return;
        }
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(node.widthProperty());
        clip.heightProperty().bind(node.heightProperty());
        clip.setArcWidth(110);
        clip.setArcHeight(72);
        node.setClip(clip);
    }

    private void applyButtonClips() {
        applyButtonClip(sendChatButton, 18);
        applyButtonClip(navBackButton, 18);
        applyButtonClip(helpButton, 18);
        applyButtonClip(endTurnButton, 18);
        applyButtonClip(discardModeButton, 18);
        applyButtonClip(handDrawerToggleButton, 999);
    }

    private void applyButtonClip(Button button, double arc) {
        if (button == null) {
            return;
        }
        applyRoundedClip(button, arc);
    }

    @FXML
    private void onToggleHandDrawerClicked() {
        handDrawerExpanded = !handDrawerExpanded;
        updateHandDrawerState();
    }

    @FXML
    private void onDiscardModeClicked() {
        if (discardMode) {
            return;
        }
        if (getLocalHandCardCount() > PlayerManagement.MAX_HAND_SIZE) {
            startDiscardMode();
            return;
        }
        showError("Discard is only available when your hand exceeds the limit.");
    }

    private void updateHandDrawerState() {
        if (handDrawerScrollPane != null) {
            handDrawerScrollPane.setManaged(handDrawerExpanded);
            handDrawerScrollPane.setVisible(handDrawerExpanded);
            handDrawerScrollPane.setPrefHeight(handDrawerExpanded ? 176 : 0);
            handDrawerScrollPane.setMinHeight(handDrawerExpanded ? 176 : 0);
            handDrawerScrollPane.setMaxHeight(handDrawerExpanded ? 176 : 0);
        }
        if (handDrawerToggleButton != null) {
            handDrawerToggleButton.setText(handDrawerExpanded ? "▼" : "▲");
        }
        Platform.runLater(this::positionFloatingHandSurface);
    }

    private void positionFloatingHandSurface() {
        if (rootStack == null || floatingHandSurface == null) {
            return;
        }
        floatingHandSurface.applyCss();
        floatingHandSurface.autosize();
        double width = Math.min(1120, Math.max(760, rootStack.getWidth() - 28));
        floatingHandSurface.setPrefWidth(width);
        floatingHandSurface.setMaxWidth(width);
        floatingHandSurface.applyCss();
        floatingHandSurface.autosize();
        double x = Math.max(12, (rootStack.getWidth() - floatingHandSurface.getWidth()) / 2.0);
        double y = Math.max(0, rootStack.getHeight() - floatingHandSurface.getHeight() - 2);
        floatingHandSurface.relocate(x, y);
        floatingHandSurface.toFront();
        if (animationLayer != null) {
            animationLayer.toFront();
        }
    }

    private void installScrollSupport() {
        installHorizontalWheelSupport(handDrawerScrollPane);
        installHorizontalWheelSupport(myBankScrollPane);
        installHorizontalWheelSupport(myPropertyScrollPane);
        installVerticalWheelSupport(opponentScrollPane);
    }

    private void installHorizontalWheelSupport(ScrollPane scrollPane) {
        if (scrollPane == null) {
            return;
        }
        scrollPane.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, event -> {
            if (Math.abs(event.getDeltaY()) < 0.001 && Math.abs(event.getDeltaX()) < 0.001) {
                return;
            }
            double contentWidth = scrollPane.getContent() == null ? 0 : scrollPane.getContent().getLayoutBounds().getWidth();
            double viewportWidth = scrollPane.getViewportBounds().getWidth();
            double range = Math.max(1, contentWidth - viewportWidth);
            double delta = event.getDeltaY() != 0 ? event.getDeltaY() : event.getDeltaX();
            double next = scrollPane.getHvalue() - (delta / range);
            scrollPane.setHvalue(Math.max(0, Math.min(1, next)));
            event.consume();
        });
    }

    private void installVerticalWheelSupport(ScrollPane scrollPane) {
        if (scrollPane == null) {
            return;
        }
        scrollPane.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, event -> {
            if (Math.abs(event.getDeltaY()) < 0.001) {
                return;
            }
            double contentHeight = scrollPane.getContent() == null ? 0 : scrollPane.getContent().getLayoutBounds().getHeight();
            double viewportHeight = scrollPane.getViewportBounds().getHeight();
            double range = Math.max(1, contentHeight - viewportHeight);
            double next = scrollPane.getVvalue() - (event.getDeltaY() / range);
            scrollPane.setVvalue(Math.max(0, Math.min(1, next)));
            event.consume();
        });
    }

    private void refreshSideActions() {
        if (navBackButton != null) {
            navBackButton.setDisable(false);
        }
        if (helpButton != null) {
            helpButton.setDisable(false);
        }
        if (sendChatButton != null) {
            sendChatButton.setDisable(false);
        }
        if (discardModeButton != null) {
            discardModeButton.setDisable(!discardMode && getLocalHandCardCount() <= PlayerManagement.MAX_HAND_SIZE);
        }
        if (loadingLabel != null) {
            if (discardMode) {
                loadingLabel.setText("Discard mode is active.");
            } else if (isOnlineMode && !isMyTurn) {
                loadingLabel.setText("Waiting for the current player");
            } else {
                loadingLabel.setText("You can play, bank, place, or end your turn.");
            }
        }
        updateTimerWarningState();
    }

    private void syncDisplayedTurnTimer(int currentTurnIndex) {
        syncDisplayedTurnTimer(currentTurnIndex, -1);
    }

    private void syncDisplayedTurnTimer(int currentTurnIndex, int currentTurnClockId) {
        if (currentTurnClockId >= 0) {
            if (currentTurnClockId != trackedTurnClockId) {
                trackedTurnClockId = currentTurnClockId;
                trackedTurnIndex = currentTurnIndex;
                displayedTurnSeconds = TURN_TIME_LIMIT_SECONDS;
                latestTableActionCard = null;
                latestTableActionTitle = "Latest Action";
                restartTurnTimer();
                updateTimerDisplay();
                if (interactor instanceof Interactor) {
                    ((Interactor) interactor).closeActiveDialogs();
                }
                return;
            }
            trackedTurnIndex = currentTurnIndex;
            updateTimerDisplay();
            return;
        }
        trackedTurnClockId = -1;
        if (currentTurnIndex != trackedTurnIndex) {
            trackedTurnIndex = currentTurnIndex;
            displayedTurnSeconds = TURN_TIME_LIMIT_SECONDS;
            latestTableActionCard = null;
            latestTableActionTitle = "Latest Action";
            restartTurnTimer();
            updateTimerDisplay();
            if (interactor instanceof Interactor) {
                ((Interactor) interactor).closeActiveDialogs();
            }
            return;
        }
        updateTimerDisplay();
    }

    private void restartTurnTimer() {
        stopTurnTimer();
        turnTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (displayedTurnSeconds > 0) {
                displayedTurnSeconds--;
                updateTimerDisplay();
                if (displayedTurnSeconds == 0) {
                    handleTurnTimeout();
                }
            }
        }));
        turnTimer.setCycleCount(Animation.INDEFINITE);
        turnTimer.play();
    }

    private void stopTurnTimer() {
        if (turnTimer != null) {
            turnTimer.stop();
            turnTimer = null;
        }
    }

    private void updateTimerDisplay() {
        if (timerLabel != null) {
            timerLabel.setText(displayedTurnSeconds + "s");
        }
        updateTimePressureHint();
        updateTimerWarningState();
    }

    private void updateTimerWarningState() {
        boolean warning = displayedTurnSeconds <= TURN_ALERT_SECONDS;
        toggleStyleClass(timerLabel, "timer-warning", warning);
        toggleStyleClass(endTurnButton, "turn-warning", warning && isMyTurn && !discardMode);
        toggleStyleClass(discardModeButton, "turn-warning", warning && isMyTurn && discardMode);
        toggleStyleClass(handDrawerToggleButton, "turn-warning", warning && isMyTurn && handDrawerExpanded);
        toggleStyleClass(tableCenterPane, "turn-warning", warning && isMyTurn);
        toggleStyleClass(actionPanel, "turn-warning", warning && isMyTurn);
    }

    private void updateTimePressureHint() {
        if (hintLabel == null || !isMyTurn || displayedTurnSeconds > TURN_HINT_WARNING_SECONDS) {
            return;
        }
        if (discardMode) {
            hintLabel.setText(buildDiscardHintText());
            return;
        }
        String suggestion = buildSuggestedTurnAction();
        if (suggestion == null || suggestion.isBlank()) {
            hintLabel.setText("Time is running out. End your turn soon.");
            return;
        }
        hintLabel.setText("Time is running out. " + suggestion);
    }

    private String buildSuggestedTurnAction() {
        List<Card> localHandCards = getLocalHandCards();
        if (localHandCards.isEmpty()) {
            return "No playable card remains. End your turn.";
        }
        if (getLocalHandCardCount() > PlayerManagement.MAX_HAND_SIZE) {
            return "Discard extra cards until your hand is 7 or fewer.";
        }
        if (selectedHandCard != null) {
            if (selectedHandCard.isMoneyCard()) {
                return "You can bank " + selectedHandCard.getName() + ".";
            }
            if (selectedHandCard.isPropertyCard()) {
                return "You can place or bank " + selectedHandCard.getName() + ".";
            }
            if (selectedHandCard.isActionCard()) {
                return "You can play or bank " + selectedHandCard.getName() + ".";
            }
        }
        Card propertyCard = findFirstCard(localHandCards, Card::isPropertyCard);
        if (propertyCard != null) {
            return "Try placing " + propertyCard.getName() + " as property.";
        }
        Card moneyCard = findFirstCard(localHandCards, Card::canBeUsedAsMoney);
        if (moneyCard != null) {
            return "Try banking " + moneyCard.getName() + ".";
        }
        Card actionCard = findFirstCard(localHandCards, Card::isActionCard);
        if (actionCard != null) {
            return "Try playing " + actionCard.getName() + " as an action.";
        }
        return "You can still play or end your turn.";
    }

    private Card findFirstCard(List<Card> cards, java.util.function.Predicate<Card> predicate) {
        if (cards == null || predicate == null) {
            return null;
        }
        for (Card card : cards) {
            if (card != null && predicate.test(card)) {
                return card;
            }
        }
        return null;
    }

    private void handleTurnTimeout() {
        if (gameManager != null && gameManager.hasWinner()) {
            return;
        }
        if (isOnlineMode) {
            // Online mode: GameServer handles timeout autonomously. Just show a hint.
            if (loadingLabel != null) {
                loadingLabel.setText("Time expired. Waiting for server...");
            }
            if (hintLabel != null) {
                hintLabel.setText("Time expired. Server is ending turn automatically.");
            }
            selectedHandCard = null;
            handActionBox.getChildren().clear();
            return;
        }
        if (loadingLabel != null) {
            loadingLabel.setText("Time expired. Auto-ending turn");
        }
        if (hintLabel != null) {
            hintLabel.setText("Time expired. The turn is being closed automatically.");
        }
        selectedHandCard = null;
        handActionBox.getChildren().clear();
        handleOfflineTurnTimeout();
    }

    private void handleOfflineTurnTimeout() {
        if (gameManager == null) {
            return;
        }
        try {
            int discardedCount = Math.max(0, getLocalHandCardCount() - PlayerManagement.MAX_HAND_SIZE);
            autoDiscardOverflowOffline();
            discardMode = false;
            discardNoticeShown = false;
            restoreEndTurnButtonTextIfNeeded();
            gameManager.confirmCurrentPlayerTurnEnded();
            gameManager.advanceTurn();
            if (discardedCount > 0) {
                appendLog("Time expired. Discarded " + discardedCount + " extra card(s) automatically.");
            }
            appendLog("Time expired. Turn advanced automatically.");
            updateUI();
        } catch (Exception e) {
            showError("Timeout handling failed: " + e.getMessage());
        }
    }

    private void handleOnlineTurnTimeout() {
        List<Card> overflowCards = getOverflowDiscardCards();
        if (overflowCards.isEmpty()) {
            sendEndTurnAction();
            return;
        }
        for (Card card : overflowCards) {
            if (card != null) {
                sendActionToServer("DISCARD:" + card.getId());
                removeCardFromHandUI(card);
            }
        }
        PauseTransition finishDelay = new PauseTransition(Duration.millis(220L * overflowCards.size() + 220));
        finishDelay.setOnFinished(event -> sendEndTurnAction());
        finishDelay.play();
    }

    private void autoDiscardOverflowOffline() {
        List<Card> overflowCards = getOverflowDiscardCards();
        if (overflowCards.isEmpty()) {
            return;
        }
        for (Card card : overflowCards) {
            gameManager.removeFromCurrentPlayerHand(card);
            gameManager.getCardManager().playCard(card);
        }
    }

    private List<Card> getOverflowDiscardCards() {
        List<Card> localHandCards = new ArrayList<>(getLocalHandCards());
        int overflow = Math.max(0, localHandCards.size() - PlayerManagement.MAX_HAND_SIZE);
        if (overflow <= 0) {
            return java.util.Collections.emptyList();
        }
        Collections.shuffle(localHandCards);
        return new ArrayList<>(localHandCards.subList(0, overflow));
    }

    private List<Card> getLocalHandCards() {
        List<Card> cards = new ArrayList<>();
        if (gameManager != null) {
            PlayerManagement player = isOnlineMode
                    ? (localPlayerIndex >= 0 && localPlayerIndex < gameManager.getPlayersView().size()
                    ? gameManager.getPlayersView().get(localPlayerIndex) : null)
                    : gameManager.getCurrentPlayer();
            if (player != null) {
                cards.addAll(player.getHandCardsView());
            }
        }
        if (!cards.isEmpty()) {
            return cards;
        }
        if (myHandBox != null) {
            for (Node node : myHandBox.getChildren()) {
                if (node instanceof CardView cardView && cardView.getCard() != null) {
                    cards.add(cardView.getCard());
                }
            }
        }
        return cards;
    }

    private void toggleStyleClass(Node node, String styleClass, boolean enabled) {
        if (node == null || styleClass == null || styleClass.isBlank()) {
            return;
        }
        if (enabled) {
            if (!node.getStyleClass().contains(styleClass)) {
                node.getStyleClass().add(styleClass);
            }
        } else {
            node.getStyleClass().remove(styleClass);
        }
    }

    private void renderTablePlaceholder() {
        if (tablePlayedCardPane == null) {
            return;
        }
        tablePlayedCardPane.getChildren().clear();
        Label label = new Label("Waiting");
        label.getStyleClass().add("slot-title");
        tablePlayedCardPane.getChildren().add(label);
    }

    private void showCardOnTable(Card card, String title) {
        if (tablePlayedCardPane == null) {
            return;
        }
        latestTableActionCard = card;
        latestTableActionTitle = title == null || title.isBlank() ? "Latest Action" : title;
        tablePlayedCardPane.getChildren().clear();
        VBox box = new VBox(8);
        box.setAlignment(Pos.CENTER);
        Label label = new Label(latestTableActionTitle);
        label.getStyleClass().add("slot-title");
        box.getChildren().add(label);
        if (card != null) {
            CardView preview = new CardView(card, true);
            preview.setDisable(true);
            box.getChildren().add(preview);
        }
        tablePlayedCardPane.getChildren().add(box);
    }

    private void renderCurrentTurnTable(PlayerManagement player) {
        renderCurrentTurnBank(player);
        renderCurrentTurnProperty(player);
        renderCurrentTurnAction();
    }

    private void renderCurrentTurnBank(PlayerManagement player) {
        if (tableCurrentBankBox == null) {
            return;
        }
        tableCurrentBankBox.getChildren().clear();
        if (player == null || player.getBankCardsView().isEmpty()) {
            tableCurrentBankBox.getChildren().add(buildTablePlaceholderChip("No bank cards"));
            return;
        }
        appendCardPreview(tableCurrentBankBox, player.getBankCardsView(), 2);
    }

    private void renderCurrentTurnProperty(PlayerManagement player) {
        if (tableCurrentPropertyBox == null) {
            return;
        }
        tableCurrentPropertyBox.getChildren().clear();
        if (player == null) {
            tableCurrentPropertyBox.getChildren().add(buildTablePlaceholderChip("No properties"));
            return;
        }
        List<Color> colors = new ArrayList<>();
        for (Map.Entry<Color, PropertyZone> entry : player.getPropertyZonesView().entrySet()) {
            if (player.getPropertyCount(entry.getKey()) > 0) {
                colors.add(entry.getKey());
            }
        }
        if (colors.isEmpty()) {
            tableCurrentPropertyBox.getChildren().add(buildTablePlaceholderChip("No properties"));
            return;
        }
        for (Color color : colors) {
            int count = player.getPropertyCount(color);
            tableCurrentPropertyBox.getChildren().add(buildPropertyChip(player, color, count));
        }
    }

    private void renderCurrentTurnAction() {
        if (latestTableActionCard == null) {
            renderTablePlaceholder();
            return;
        }
        showCardOnTable(latestTableActionCard, latestTableActionTitle);
    }

    private Label buildTablePlaceholderChip(String text) {
        Label chip = new Label(text);
        chip.getStyleClass().addAll("summary-chip", "table-summary-chip");
        chip.setTextOverrun(OverrunStyle.CLIP);
        return chip;
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
            if (!ensureOnlinePlayAllowed()) {
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
            if (gameManager != null) {
                int played = gameManager.getPlayedCardsThisTurn();
                int max = GameManager.MAX_PLAY_COUNT_PER_TURN;
                showError("You have already played " + played + "/" + max + " cards this turn. End your turn.");
            }
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
            applyBackgroundImage(back, "/images/cards/card-back.png", "linear-gradient(to bottom right, #ffffff, #dbeafe)");
            back.setStyle(back.getStyle()
                    + "-fx-border-color: #3b82f6;"
                    + "-fx-border-width: 3;"
                    + "-fx-background-radius: 12;"
                    + "-fx-border-radius: 12;"
                    + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 10, 0.2, 0, 3);");
            back.setPrefSize(90, 130);
            back.setMinSize(90, 130);
            back.setMaxSize(90, 130);
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
        stopTurnTimer();
        stopSuggestedCardPulse();
        trackedTurnIndex = -1;
        trackedTurnClockId = -1;
        displayedTurnSeconds = TURN_TIME_LIMIT_SECONDS;
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
        if (instance == this) {
            instance = null;
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
        trackedTurnIndex = -1;
        trackedTurnClockId = -1;
        displayedTurnSeconds = TURN_TIME_LIMIT_SECONDS;
        if (navBackButton != null) {
            navBackButton.setText("Exit to Lobby");
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
            List<String> names = promptForOfflinePlayerNames(playerCount);
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

    public void initializeOfflineGame(int playerCount, List<String> names, List<Integer> avatarIds) {
        if (isOnlineMode) {
            return;
        }
        if (playerCount < 2 || playerCount > 5) {
            showError("Invalid player count");
            return;
        }
        if (names == null || names.size() != playerCount) {
            showError("Invalid player names");
            return;
        }
        if (avatarIds == null || avatarIds.size() != playerCount) {
            showError("Invalid avatars");
            return;
        }

        chatLines.clear();
        if (chatArea != null) {
            chatArea.clear();
        }

        discardMode = false;
        selectedHandCard = null;
        trackedTurnIndex = -1;
        trackedTurnClockId = -1;
        displayedTurnSeconds = TURN_TIME_LIMIT_SECONDS;
        localPlayerIndex = 0;
        if (navBackButton != null) {
            navBackButton.setText("Exit to Lobby");
        }

        interactor = new Interactor();

        GameManager localManager = new GameManager();
        localManager.setPlayerCount(playerCount, names);
        for (int i = 0; i < localManager.getPlayersView().size(); i++) {
            localManager.getPlayersView().get(i).setAvatarId(Math.max(0, avatarIds.get(i)));
        }
        localManager.startRound();
        bindGameManager(localManager);

        updateUI();
        if (!AppSettings.getInstance().isOnboardingShown()) {
            AppSettings.getInstance().setOnboardingShown(true);
            onHelpClicked();
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

    private List<String> promptForOfflinePlayerNames(int count) {
        List<String> names = new ArrayList<>();
        
        // Player 1 uses AppSettings
        String localName = AppSettings.getInstance().getPlayerName();
        names.add(localName == null || localName.isBlank() ? "Player1" : localName);

        // Prompt for remaining players
        for (int i = 2; i <= count; i++) {
            TextInputDialog dialog = new TextInputDialog("Player" + i);
            dialog.setTitle("Player " + i + " Name");
            dialog.setHeaderText("Enter name for Player " + i);
            dialog.setContentText("Name:");
            Optional<String> result = dialog.showAndWait();
            
            String name = result.orElse("Player" + i).trim();
            if (name.isBlank()) {
                name = "Player" + i;
            }
            names.add(name);
        }
        return names;
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
            navBackButton.setText("Exit to Lobby");
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
        syncDisplayedTurnTimer(state.getCurrentPlayerIndex(), state.getTurnClockId());
        updatePileCounts(state.getDrawPileCount(), state.getDiscardPileCount());
        lastServerPlayedCardsThisTurn = state.getPlayedCardsThisTurn();
        lastServerMaxPlayCountPerTurn = state.getMaxPlayCountPerTurn();
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
        int remainingPlays = lastServerMaxPlayCountPerTurn - lastServerPlayedCardsThisTurn;
        turnInfoLabel.setText("Turn: " + currentPlayerName + " | Played: " + lastServerPlayedCardsThisTurn + "/" + lastServerMaxPlayCountPerTurn);
        if (actingPlayerLabel != null) {
            actingPlayerLabel.setText(currentPlayerName + (isMyTurn ? " is playing now" : " is making a move"));
        }
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
                hintLabel.setText("Played: " + lastServerPlayedCardsThisTurn + "/" + lastServerMaxPlayCountPerTurn + ". End your turn.");
            } else {
                hintLabel.setText("Click a hand card to play it as action / bank / property.");
            }
        }
        refreshSideActions();
        if (gameManager != null && state.getCurrentPlayerIndex() >= 0 && state.getCurrentPlayerIndex() < gameManager.getPlayersView().size()) {
            renderCurrentTurnTable(gameManager.getPlayersView().get(state.getCurrentPlayerIndex()));
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
        myBankBox.setSpacing(-54);

        for (GameStateData.CardData cardData : localPlayerData.getBankCards()) {
            Card card = cardData.toCard();
            CardView cardView = new CardView(card, true);
            cardView.setDisable(true);
            myBankBox.getChildren().add(cardView);
        }

        if (myBankBox.getChildren().isEmpty()) {
            myBankBox.setSpacing(14);
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
        PlayerManagement localPlayer = gameManager != null && localPlayerIndex < gameManager.getPlayersView().size()
                ? gameManager.getPlayersView().get(localPlayerIndex)
                : null;
        updateMyPropertiesTitle(localPlayer);

        for (var entry : localPlayerData.getPropertyZones().entrySet()) {
            Color color = entry.getKey();
            GameStateData.PropertyZoneData zoneData = entry.getValue();

            VBox colorGroup = new VBox(8);
            colorGroup.setPadding(new Insets(8));
            Label colorTitle = new Label(buildPropertySetTitle(localPlayer, color, zoneData.getProperties().size()));
            stylePropertySetGroup(colorGroup, colorTitle, localPlayer, color, zoneData.getProperties().size());
            installLabelTooltip(colorTitle, colorTitle.getText());
            colorGroup.getChildren().add(colorTitle);

            HBox propertyRow = new HBox(8);
            propertyRow.setAlignment(Pos.CENTER_LEFT);
            propertyRow.setSpacing(-54);

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
            PlayerManagement player = gameManager != null && i < gameManager.getPlayersView().size()
                    ? gameManager.getPlayersView().get(i)
                    : null;
            opponentAreaBox.getChildren().add(createCompactOpponentCard(playerData, player, i == state.getCurrentPlayerIndex()));
        }
        applyOpponentFanLayout(opponentAreaBox);

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
            turnInfoLabel.setText("Waiting for server");
            return;
        }

        PlayerManagement currentPlayer = gameManager.getCurrentPlayer();
        if (isOnlineMode && isHost() && gameServer != null) {
            syncDisplayedTurnTimer(gameManager.getCurrentPlayerIndex(), gameServer.getTurnClockId());
        } else {
            syncDisplayedTurnTimer(gameManager.getCurrentPlayerIndex());
        }
        // In online mode always render local player's hand (not the current turn player's hand)
        PlayerManagement localPlayer = isOnlineMode
                ? gameManager.getPlayersView().get(localPlayerIndex)
                : currentPlayer;

        if (gameManager.getCardManager() != null) {
            updatePileCounts(gameManager.getCardManager().getDrawPileSize(), gameManager.getCardManager().getDiscardPileSize());
        }

        updateClientInfo();
        String turnPlayerName = currentPlayer.getName();
        int played = gameManager.getPlayedCardsThisTurn();
        int max = GameManager.MAX_PLAY_COUNT_PER_TURN;
        turnInfoLabel.setText("Turn: " + turnPlayerName + " | Played: " + played + "/" + max);
        if (actingPlayerLabel != null) {
            actingPlayerLabel.setText(turnPlayerName + (isOnlineMode && !isMyTurn ? " is making a move" : " is on the table"));
        }
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
                hintLabel.setText("Played: " + played + "/" + max + ". End your turn.");
            } else {
                hintLabel.setText("Click a hand card to play it as action / bank / property.");
            }
        }
        renderOpponentArea(localPlayer);
        renderCurrentTurnTable(currentPlayer);
        renderHandCards(localPlayer);
        renderBankCards(localPlayer);
        renderPropertyCards(localPlayer);
        renderHandActionButtons();
        refreshSideActions();
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
            VBox playerCard = createCompactOpponentCard(player, isTurnPlayer);
            opponentAreaBox.getChildren().add(playerCard);
        }
        applyOpponentFanLayout(opponentAreaBox);

        if (opponentAreaBox.getChildren().isEmpty()) {
            Label emptyView = new Label("No other players");
            emptyView.setStyle("-fx-text-fill: #666666;");
            opponentAreaBox.getChildren().add(emptyView);
        }
    }

    private void applyOpponentFanLayout(HBox pane) {
        if (pane == null) {
            return;
        }
        int count = pane.getChildren().size();
        pane.setSpacing(count >= 4 ? 8 : 12);
        for (int i = 0; i < count; i++) {
            Node node = pane.getChildren().get(i);
            node.setTranslateY(0);
            node.setRotate(0);
        }
    }

    private VBox createCompactOpponentCard(PlayerManagement player, boolean isTurnPlayer) {
        VBox container = createCompactCardShell(
                player.getName(),
                player.getAvatarId(),
                player.getHandCardCount(),
                player.getCompleteSetCount(),
                isTurnPlayer,
                player.isEliminated()
        );
        HBox bankRow = new HBox(6);
        bankRow.setAlignment(Pos.CENTER_LEFT);
        appendCardPreview(bankRow, player.getBankCardsView(), 3);

        FlowPane propertyRow = new FlowPane(6, 4);
        propertyRow.setAlignment(Pos.CENTER_LEFT);
        appendPropertySummary(propertyRow, player);

        container.getChildren().addAll(createCompactSection("Bank", bankRow), createCompactSection("Properties", propertyRow));
        return container;
    }

    private VBox createCompactOpponentCard(GameStateData.PlayerData playerData, PlayerManagement player, boolean isTurnPlayer) {
        VBox container = createCompactCardShell(
                playerData.getPlayerName(),
                playerData.getAvatarId(),
                playerData.getHandCardCount(),
                resolveCompleteSetCount(playerData, player),
                isTurnPlayer,
                playerData.isEliminated()
        );

        HBox bankRow = new HBox(6);
        bankRow.setAlignment(Pos.CENTER_LEFT);
        List<Card> bankCards = new ArrayList<>();
        for (GameStateData.CardData cardData : playerData.getBankCards()) {
            bankCards.add(cardData.toCard());
        }
        appendCardPreview(bankRow, bankCards, 3);

        FlowPane propertyRow = new FlowPane(6, 4);
        propertyRow.setAlignment(Pos.CENTER_LEFT);
        appendPropertySummary(propertyRow, playerData, player);

        container.getChildren().addAll(createCompactSection("Bank", bankRow), createCompactSection("Properties", propertyRow));
        return container;
    }

    private VBox createCompactCardShell(String playerName, int avatarId, int handCount, int completeSetCount, boolean isTurnPlayer, boolean isEliminated) {
        VBox container = new VBox(8);
        container.getStyleClass().addAll("player-card", "compact-player-card");
        if (isEliminated) {
            container.setStyle("-fx-opacity: 0.5; -fx-background-color: #f0f0f0;");
        } else if (isTurnPlayer) {
            container.getStyleClass().add("current-turn");
            installTurnPulse(container);
        }

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        StackPane avatarPane = new StackPane();
        renderAvatarInto(avatarPane, avatarId, playerName, 13);

        VBox titleBox = new VBox(3);
        Label nameLabel = new Label(playerName == null || playerName.isBlank() ? "Player" : playerName);
        if (isEliminated) {
            nameLabel.setText(nameLabel.getText() + " (Eliminated)");
        }
        nameLabel.getStyleClass().add("compact-player-name");
        nameLabel.setTextOverrun(OverrunStyle.CLIP);
        Label handLabel = new Label("Hand: " + handCount + " | Sets: " + completeSetCount + "/" + PlayerManagement.REQUIRED_COMPLETE_SETS_TO_WIN);
        handLabel.getStyleClass().add("compact-player-meta");
        titleBox.getChildren().addAll(nameLabel, handLabel);

        header.getChildren().addAll(avatarPane, titleBox);
        container.getChildren().add(header);
        return container;
    }

    private VBox createCompactSection(String title, Pane contentRow) {
        VBox section = new VBox(6);
        section.getStyleClass().add("compact-section");
        Label label = new Label(title);
        label.getStyleClass().add("compact-section-title");
        section.getChildren().addAll(label, contentRow);
        return section;
    }

    private void appendCardPreview(HBox row, List<Card> cards, int limit) {
        if (cards == null || cards.isEmpty()) {
            row.getChildren().add(buildSummaryChip("None"));
            return;
        }
        row.setSpacing(-54);
        for (int i = 0; i < cards.size(); i++) {
            CardView preview = new CardView(cards.get(i), true);
            preview.setDisable(true);
            row.getChildren().add(preview);
        }
    }

    private void appendPropertySummary(Pane row, PlayerManagement player) {
        boolean hasProperty = false;
        for (Map.Entry<Color, PropertyZone> entry : player.getPropertyZonesView().entrySet()) {
            Color color = entry.getKey();
            int count = player.getPropertyCount(color);
            if (count <= 0) {
                continue;
            }
            hasProperty = true;
            row.getChildren().add(buildPropertyChip(player, color, count));
        }
        if (!hasProperty) {
            row.getChildren().add(buildSummaryChip("None"));
        }
    }

    private void appendPropertySummary(Pane row, GameStateData.PlayerData playerData, PlayerManagement player) {
        boolean hasProperty = false;
        for (var entry : playerData.getPropertyZones().entrySet()) {
            Color color = entry.getKey();
            int count = entry.getValue() == null || entry.getValue().getProperties() == null ? 0 : entry.getValue().getProperties().size();
            if (count <= 0) {
                continue;
            }
            hasProperty = true;
            row.getChildren().add(buildPropertyChip(player, color, count));
        }
        if (!hasProperty) {
            row.getChildren().add(buildSummaryChip("None"));
        }
    }

    private Label buildSummaryChip(String text) {
        Label chip = new Label(text);
        chip.getStyleClass().add("summary-chip");
        chip.setTextOverrun(OverrunStyle.CLIP);
        return chip;
    }

    private Label buildPropertyChip(PlayerManagement player, Color color, int currentCount) {
        boolean completeSet = isPropertySetComplete(player, color, currentCount);
        Label chip = new Label(buildCompactPropertyProgressText(player, color, currentCount));
        chip.getStyleClass().add("summary-chip");
        String background = completeSet ? "#fde68a" : toSoftFxColor(color);
        String border = completeSet ? "#f59e0b" : toFxColor(color);
        chip.setStyle("-fx-background-color: " + background + "; -fx-border-color: " + border + ";");
        chip.setTextOverrun(OverrunStyle.CLIP);
        String tooltipText = player == null
                ? color.name() + "  " + currentCount
                : buildPropertySetTitle(player, color, currentCount);
        installLabelTooltip(chip, tooltipText);
        return chip;
    }

    private String buildColorChipText(Color color) {
        if (color == null) {
            return "";
        }
        String name = color.getDisplayName();
        if (name == null || name.isBlank()) {
            return "";
        }
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
        }
        if ("Railroad".equalsIgnoreCase(name)) {
            return "RR";
        }
        if ("Utility".equalsIgnoreCase(name)) {
            return "UT";
        }
        return name.substring(0, 1).toUpperCase();
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

        updateSuggestedHandCards(created, cards, enabled);

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
        Card card = cardView.getCard();
        String cardId = card == null ? null : card.getId();
        cardView.setFaceDown(true);
        cardView.setOpacity(0);
        Platform.runLater(() -> {
            CardView resolvedTarget = cardId == null ? cardView : findHandCardViewById(cardId);
            CardView targetView = resolvedTarget == null ? cardView : resolvedTarget;
            targetView.setFaceDown(true);
            targetView.setOpacity(0);
            if (animationLayer == null || targetView.getScene() == null) {
                targetView.setOpacity(1);
                targetView.setFaceDown(false);
                return;
            }

            Bounds fromScene = drawPilePane.localToScene(drawPilePane.getBoundsInLocal());
            Bounds toScene = targetView.localToScene(targetView.getBoundsInLocal());

            Point2D from = animationLayer.sceneToLocal(fromScene.getMinX(), fromScene.getMinY());
            Point2D to = animationLayer.sceneToLocal(toScene.getMinX(), toScene.getMinY());

            CardView ghost = new CardView(card);
            ghost.setManaged(false);
            ghost.setMouseTransparent(true);
            ghost.setFaceDown(true);
            ghost.relocate(from.getX(), from.getY());
            animationLayer.getChildren().add(ghost);

            TranslateTransition fly = new TranslateTransition(Duration.millis(520), ghost);
            fly.setToX(to.getX() - from.getX());
            fly.setToY(to.getY() - from.getY());
            fly.setInterpolator(Interpolator.EASE_OUT);

            PauseTransition flipDelay = new PauseTransition(Duration.millis(260));
            flipDelay.setOnFinished(e -> ghost.playFlip(false));

            ParallelTransition seq = new ParallelTransition(fly, flipDelay);
            seq.setOnFinished(ev -> {
                animationLayer.getChildren().remove(ghost);
                CardView finalView = cardId == null ? targetView : findHandCardViewById(cardId);
                if (finalView == null) {
                    finalView = targetView;
                }
                finalView.setFaceDown(false);
                finalView.setOpacity(1);
            });
            seq.play();
        });
    }

    private CardView findHandCardViewById(String cardId) {
        if (cardId == null || myHandBox == null) {
            return null;
        }
        for (var node : myHandBox.getChildren()) {
            if (node instanceof CardView cv && cv.getCard() != null && cardId.equals(cv.getCard().getId())) {
                return cv;
            }
        }
        return null;
    }

    // Render bank cards
    private void renderBankCards(PlayerManagement player) {
        myBankBox.getChildren().clear();
        myBankBox.setSpacing(-54);
        for (Card card : player.getBankCardsView()) {
            CardView cardView = new CardView(card, true);
            cardView.setDisable(true);
            cardView.setOnAction(event -> handleBankCardClick(card));
            myBankBox.getChildren().add(cardView);
        }

        if (myBankBox.getChildren().isEmpty()) {
            myBankBox.setSpacing(14);
            Label emptyView = new Label("No cards");
            emptyView.setStyle("-fx-text-fill: #666666;");
            myBankBox.getChildren().add(emptyView);
        }
    }

    // Render property cards
    private void renderPropertyCards(PlayerManagement player) {
        myPropertyBox.getChildren().clear();
        updateMyPropertiesTitle(player);

        for (Map.Entry<Color, PropertyZone> entry : player.getPropertyZonesView().entrySet()) {
            Color color = entry.getKey();
            PropertyZone zone = entry.getValue();
            int currentCount = player.getPropertyCount(color);

            VBox colorGroup = new VBox(8);
            colorGroup.setPadding(new Insets(8));

            Label colorTitle = new Label(buildPropertySetTitle(player, color, currentCount));
            stylePropertySetGroup(colorGroup, colorTitle, player, color, currentCount);
            installLabelTooltip(colorTitle, colorTitle.getText());
            colorGroup.getChildren().add(colorTitle);

            HBox propertyRow = new HBox(8);
            propertyRow.setAlignment(Pos.CENTER_LEFT);
            propertyRow.setSpacing(-54);

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

    public void handleRemoteGameOver(String message) {
        if (message == null) {
            return;
        }
        if (message.startsWith("ABORTED:")) {
            showAbortDialogAndExit(message.substring("ABORTED:".length()).trim());
        }
    }

    private void showAbortDialogAndExit(String reason) {
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

        Label title = new Label("Match ended");
        title.setStyle("-fx-font-size: 26px; -fx-font-weight: 800;");
        Label detail = new Label(reason == null || reason.isBlank() ? "A player disconnected." : reason);
        detail.setStyle("-fx-font-size: 16px; -fx-font-weight: 700; -fx-text-fill: #b91c1c;");
        detail.setWrapText(true);
        detail.setMaxWidth(520);

        root.getChildren().addAll(title, detail);
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
        if (!ensureOnlinePlayAllowed()) {
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

    private int getOnlineRemainingPlays() {
        if (!isOnlineMode) {
            return GameManager.MAX_PLAY_COUNT_PER_TURN;
        }
        if (gameManager != null) {
            try {
                return gameManager.getRemainingPlayCountThisTurn();
            } catch (Exception ignored) {
            }
        }
        return lastServerMaxPlayCountPerTurn - lastServerPlayedCardsThisTurn;
    }

    private boolean ensureOnlinePlayAllowed() {
        if (!isOnlineMode) {
            return true;
        }
        if (!isMyTurn || discardMode) {
            showError("It's not your turn.");
            return false;
        }
        if (getOnlineRemainingPlays() <= 0) {
            int played = gameManager != null ? gameManager.getPlayedCardsThisTurn() : Math.max(0, lastServerPlayedCardsThisTurn);
            int max = gameManager != null ? GameManager.MAX_PLAY_COUNT_PER_TURN : Math.max(1, lastServerMaxPlayCountPerTurn);
            showError("You have already played " + played + "/" + max + " cards this turn. End your turn.");
            return false;
        }
        return true;
    }

    private void renderOnlineHandActionButtons() {
        handActionBox.getChildren().clear();
        if (selectedHandCard == null) {
            return;
        }
        if (!ensureOnlinePlayAllowed()) {
            selectedHandCard = null;
            return;
        }

        HBox buttonRow = new HBox(14);
        buttonRow.setAlignment(Pos.CENTER);
        buttonRow.setMaxWidth(Double.MAX_VALUE);
        buttonRow.getStyleClass().add("hand-action-row");

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
        if (!ensureOnlinePlayAllowed()) {
            return;
        }
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
        animateHandCardToDiscard(card, () -> {
            String actionStr = "PLAY_ACTION:" + card.getId() + ":" + targetPlayer.getPlayerId() + ":" + targetCard.getId();
            sendActionToServer(actionStr);
            selectedHandCard = null;
            handActionBox.getChildren().clear();
        });
    }

    // --- Online Forced Deal ---
    private void handleOnlineForcedDeal(Card card) {
        if (!ensureOnlinePlayAllowed()) {
            return;
        }
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

        Card myCard = interactor.choiceStealablePropertyCard(currentPlayer);
        if (myCard == null) return;
        Card targetCard = interactor.choiceStealablePropertyCard(targetPlayer);
        if (targetCard == null) return;

        // Build args
        animateHandCardToDiscard(card, () -> {
            String actionStr = "PLAY_ACTION:" + card.getId() + ":" + targetPlayer.getPlayerId() + ":" + myCard.getId() + ":" + targetCard.getId();
            sendActionToServer(actionStr);
            selectedHandCard = null;
            handActionBox.getChildren().clear();
        });
    }


    // --- Action dispatch routing (online) ---
    private void sendPlayAction(Card card) {
        if (!ensureOnlinePlayAllowed()) {
            return;
        }
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
        animateHandCardToDiscard(card, () -> {
            sendActionToServer("PLAY_ACTION:" + card.getId());
            selectedHandCard = null;
            handActionBox.getChildren().clear();
        });
    }
    // --- Handle rent cards and optionally stack Double The Rent ---
    private void handleOnlineRent(Card rentCard) {
        if (!ensureOnlinePlayAllowed()) {
            return;
        }
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


        // 4) For wild rent, choose target BEFORE animation (so cancel doesn't leave ghost card)
        String targetPlayerId = null;
        if (rentCard.getCardType() == CardType.RENT_WILDCOLOR) {
            PlayerManagement targetPlayer = interactor.choiceTargetPlayer(currentPlayer, gameManager.getPlayersView());
            if (targetPlayer == null) return;
            targetPlayerId = targetPlayer.getPlayerId();
        }

        // 5) Send action to server with rent mode + optional double card id
        String finalDoubleCardId = doubleCardId;
        String finalTargetPlayerId = targetPlayerId;
        animateHandCardToDiscard(rentCard, () -> {
            if (rentCard.getCardType() == CardType.RENT_WILDCOLOR) {
                // PLAY_ACTION:<rentId>:WILD_RENT:<color>:<targetPlayerId>:<doubleCardId>
                sendActionToServer("PLAY_ACTION:" + rentCard.getId() + ":WILD_RENT:" + selectedColor.name() + ":" + finalTargetPlayerId + ":" + finalDoubleCardId);
            } else {
                // Bi-color rent targets all opponents:
                // PLAY_ACTION:<rentId>:BI_RENT:<color>:<doubleCardId>
                sendActionToServer("PLAY_ACTION:" + rentCard.getId() + ":BI_RENT:" + selectedColor.name() + ":" + finalDoubleCardId);
            }
            selectedHandCard = null;
            handActionBox.getChildren().clear();
        });
    }

    // --- Online house/hotel: player chooses which color set to attach to ---
    private void handleOnlineBuilding(Card buildingCard) {
        if (!ensureOnlinePlayAllowed()) {
            return;
        }
        PlayerManagement currentPlayer = gameManager.getPlayersView().get(localPlayerIndex);

        PropertyZone zone = interactor.choiceBuildingPropertyZone(currentPlayer, buildingCard);
        if (zone == null) return;
        Color selectedColor = zone.getColor();

        // PLAY_ACTION:<cardId>:BUILDING:<color>
        animateHandCardToDiscard(buildingCard, () -> {
            sendActionToServer("PLAY_ACTION:" + buildingCard.getId() + ":BUILDING:" + selectedColor.name());
            selectedHandCard = null;
            handActionBox.getChildren().clear();
        });
    }
    private void handleOnlineDealBreaker(Card card) {
        if (!ensureOnlinePlayAllowed()) {
            return;
        }
        PlayerManagement currentPlayer = gameManager.getPlayersView().get(localPlayerIndex);

        PlayerManagement targetPlayer = interactor.choiceTargetPlayer(currentPlayer, gameManager.getPlayersView());
        if (targetPlayer == null) return;

        PropertyZone selectedZone = interactor.choicePropertyZone(targetPlayer);
        if (selectedZone == null) return;

        if (!targetPlayer.isSetComplete(selectedZone.getColor())) {
            showError("Takeover failed: only complete sets can be taken.");
            return;
        }

        animateHandCardToDiscard(card, () -> {
            String actionStr = "PLAY_ACTION:" + card.getId() + ":" + targetPlayer.getPlayerId() + ":" + selectedZone.getColor().name();
            sendActionToServer(actionStr);
            selectedHandCard = null;
            handActionBox.getChildren().clear();
        });
    }

    // --- Online Debt Collector ---
    private void handleOnlineDebtCollector(Card card) {
        if (!ensureOnlinePlayAllowed()) {
            return;
        }

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
        animateHandCardToDiscard(card, () -> {
            String actionStr =
                    "PLAY_ACTION:"
                            + card.getId()
                            + ":"
                            + targetPlayer.getPlayerId();

            sendActionToServer(actionStr);

            selectedHandCard = null;
            handActionBox.getChildren().clear();
        });
    }

    private boolean hasAnyProperty(PlayerManagement player) {
        if (player == null) return false;
        for (PropertyZone zone : player.getPropertyZonesView().values()) {
            if (!zone.getPropertiesView().isEmpty()) return true;
        }
        return false;
    }

    private void sendDepositAction(Card card) {
        if (!ensureOnlinePlayAllowed()) {
            return;
        }
        animateHandCardToTarget(card, myBankBox, () -> {
            sendActionToServer("DEPOSIT:" + card.getId());
            selectedHandCard = null;
            handActionBox.getChildren().clear();
        });
    }

    private void sendPlacePropertyAction(Card card) {
        if (!ensureOnlinePlayAllowed()) {
            return;
        }
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

        Color finalColor = selectedColor;
        animateHandCardToTarget(card, myPropertyBox, () -> {
            sendActionToServer("PLACE_PROPERTY:" + card.getId() + ":" + finalColor.name());
            selectedHandCard = null;
            handActionBox.getChildren().clear();
        });
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
        buttonRow.getStyleClass().add("hand-action-row");

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
        button.getStyleClass().addAll("button", "image-backed-button", "choice-action-button");
        installButtonGraphic(button);
        applyButtonClip(button, 18);
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
                    int playedBefore = gameManager.getPlayedCardsThisTurn();
                    gameManager.playActionCard(card);
                    if (gameManager.getPlayedCardsThisTurn() == playedBefore && !gameManager.hasWinner()) {
                        restoreFailedAnimatedTableAction();
                        updateUI();
                        return;
                    }
                    selectedHandCard = null;
                    updateUI();
                    broadcastStateIfHost();
                } catch (Exception e) {
                    restoreFailedAnimatedTableAction();
                    updateUI();
                    showError("Action failed: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            showError("Action failed: " + e.getMessage());
        }
    }

    private void doPlayRentCard(Card rentCard) {
        // For bi-color rent cards, pre-set the active color before the animation
        // so that execute() uses the correct color. All other interactions
        // (target selection, DoubleTheRent, charge) are handled by execute()
        // inside the standard gameManager.playActionCard() flow.
        // For wild rent cards, execute() handles everything including color
        // and target selection.
        if (rentCard instanceof BiColorRentCard biColorRentCard) {
            PlayerManagement currentPlayer = gameManager.getCurrentPlayer();
            Color selectedColor = promptForRentColor(rentCard, currentPlayer);
            if (selectedColor == null) {
                return;
            }
            biColorRentCard.setSelectedColor(selectedColor);

            int rentAmount = currentPlayer.getRent(selectedColor);
            if (rentAmount <= 0) {
                showError("No rent available for " + selectedColor.getDisplayName() + ".");
                return;
            }
        }

        animateHandCardToDiscard(rentCard, () -> {
            try {
                int playedBefore = gameManager.getPlayedCardsThisTurn();
                // Use the proven playActionCard flow: execute() handles
                // the rent charge, then the card is removed from hand,
                // discarded, and play count is recorded automatically.
                gameManager.playActionCard(rentCard);
                if (gameManager.getPlayedCardsThisTurn() == playedBefore && !gameManager.hasWinner()) {
                    restoreFailedAnimatedTableAction();
                    updateUI();
                    return;
                }
                selectedHandCard = null;
                updateUI();
                broadcastStateIfHost();
            } catch (Exception e) {
                // Ensure UI is refreshed even if the action fails
                restoreFailedAnimatedTableAction();
                updateUI();
                showError("Action failed: " + e.getMessage());
            }
        });
    }

    private void doDepositToBank(Card card) {
        animateHandCardToTarget(card, myBankBox, () -> {
            try {
                gameManager.depositMoneyCard(card);
                selectedHandCard = null;
                updateUI();
                broadcastStateIfHost();
            } catch (Exception e) {
                restoreFailedAnimatedTableAction();
                updateUI();
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
                    restoreFailedAnimatedTableAction();
                    updateUI();
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

        Platform.runLater(() -> animateCardGhostToTable(card, sourceView, targetNode, after));
    }

    private void animateCardGhostToTable(Card card, CardView sourceView, javafx.scene.Node targetNode, Runnable after) {
        if (card == null || sourceView == null || sourceView.getScene() == null || animationLayer == null) {
            if (after != null) {
                after.run();
            }
            return;
        }
        javafx.scene.Node visualTarget = tablePlayedCardPane != null ? tablePlayedCardPane : targetNode;
        Bounds fromScene = sourceView.localToScene(sourceView.getBoundsInLocal());
        Bounds toScene = visualTarget.localToScene(visualTarget.getBoundsInLocal());
        Point2D from = animationLayer.sceneToLocal(fromScene.getMinX(), fromScene.getMinY());
        Point2D to = animationLayer.sceneToLocal(
                toScene.getMinX() + (toScene.getWidth() - sourceView.getWidth()) / 2.0,
                toScene.getMinY() + (toScene.getHeight() - sourceView.getHeight()) / 2.0
        );

        CardView ghost = new CardView(card, sourceView.getWidth() < 80);
        ghost.setManaged(false);
        ghost.setMouseTransparent(true);
        ghost.relocate(from.getX(), from.getY());
        ghost.setViewOrder(-20000);
        animationLayer.getChildren().add(ghost);

        sourceView.setOpacity(0.16);
        sourceView.setDisable(true);

        TranslateTransition fly = new TranslateTransition(Duration.millis(260), ghost);
        fly.setToX(to.getX() - from.getX());
        fly.setToY(to.getY() - from.getY());
        fly.setInterpolator(Interpolator.EASE_OUT);

        PauseTransition hold = new PauseTransition(Duration.millis(120));
        FadeTransition fade = new FadeTransition(Duration.millis(150), ghost);
        fade.setFromValue(1);
        fade.setToValue(0);

        SequentialTransition seq = new SequentialTransition(fly, hold, fade);
        seq.setOnFinished(e -> {
            animationLayer.getChildren().remove(ghost);
            showCardOnTable(card, describeTableAction(targetNode));
            if (after != null) {
                after.run();
            }
            if (sourceView.getParent() != null) {
                sourceView.setOpacity(1);
                sourceView.setDisable(false);
                Object base = sourceView.getProperties().get("handBaseViewOrder");
                if (base instanceof Number n) {
                    sourceView.setViewOrder(n.doubleValue());
                } else {
                    sourceView.setViewOrder(0);
                }
            }
        });
        seq.play();
    }

    private void restoreFailedAnimatedTableAction() {
        latestTableActionCard = null;
        latestTableActionTitle = "Latest Action";
        renderCurrentTurnAction();
    }

    private String describeTableAction(javafx.scene.Node targetNode) {
        if (targetNode == null) {
            return "Latest Played Card";
        }
        if (targetNode == discardPilePane) {
            return "Discarded Card";
        }
        if (targetNode == myBankBox) {
            return "Card Played to Bank";
        }
        if (targetNode == myPropertyBox) {
            return "Card Played to Property";
        }
        return "Latest Played Card";
    }

    private Button createChoiceStyleButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().addAll("button", "image-backed-button", "choice-action-button");
        installButtonGraphic(button);
        applyButtonClip(button, 18);
        button.setOnAction(event -> action.run());
        return button;
    }


    private Color promptForPropertyColor(PropertyCard propertyCard, Set<Color> playableColors) {
        // Filter to only show colors that have valid rent rules.
        // This excludes WILD (no set size) and any future invalid colors.
        List<Color> options = new ArrayList<>(playableColors);
        options.removeIf(c -> !PropertyRentRules.RULES.containsKey(c));
        if (options.isEmpty()) {
            showError("No valid property color available for this card.");
            return null;
        }
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
            if (!ensureOnlinePlayAllowed()) {
                return;
            }

            selectedHandCard = card;
            renderOnlineHandActionButtons(); // Show online buttons (send PLAY_ACTION)
            return;
        }

        // 3) Offline mode
        if (gameManager != null && !gameManager.canCurrentPlayerPlayCard()) {
            int played = gameManager.getPlayedCardsThisTurn();
            int max = GameManager.MAX_PLAY_COUNT_PER_TURN;
            showError("You have already played " + played + "/" + max + " cards this turn. End your turn.");
            return;
        }
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
        refreshSideActions();
    }

    private void restoreEndTurnButtonTextIfNeeded() {
        if (endTurnButton == null) {
            return;
        }
        if (!discardMode && endTurnButton.getText() != null && !endTurnButton.getText().equals(defaultEndTurnText)) {
            endTurnButton.setText(defaultEndTurnText);
        }
        refreshSideActions();
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

        String eliminatedText = shownPlayer.isEliminated() ? " (Eliminated)" : "";
        clientInfoLabel.setText(prefix + shownPlayer.getName() + eliminatedText + " | Complete sets: " + shownPlayer.getCompleteSetCount() + "/" + PlayerManagement.REQUIRED_COMPLETE_SETS_TO_WIN);
        renderAvatarInto(clientAvatarPane, shownPlayer.getAvatarId(), shownPlayer.getName(), 20);
    }

    private void renderAvatarInto(StackPane container, int avatarId, String name, double radius) {
        if (container == null) {
            return;
        }
        container.getChildren().clear();
        container.getChildren().add(AvatarVisuals.createAvatarNode(avatarId, extractInitial(name), radius * 2));
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
            gameApp.showOfflineLobby();
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

    private String buildPropertySetTitle(PlayerManagement player, Color color, int currentCount) {
        if (player == null) {
            return color.name() + "  " + currentCount + "/?  Rent: ?";
        }

        int requiredCount = player.getRequiredSetSize(color);
        String requiredText = requiredCount == Integer.MAX_VALUE ? "?" : String.valueOf(requiredCount);
        int rent = player.getRent(color);
        String setState = isPropertySetComplete(player, color, currentCount) ? "  Complete Set" : "";
        return color.name() + "  " + currentCount + "/" + requiredText + "  Rent: " + rent + "M" + setState;
    }

    private String buildCompactPropertyProgressText(PlayerManagement player, Color color, int currentCount) {
        String prefix = buildColorChipText(color);
        int requiredCount = player == null ? getRequiredPropertySetSize(color) : player.getRequiredSetSize(color);
        String requiredText = requiredCount == Integer.MAX_VALUE ? "?" : String.valueOf(requiredCount);
        return prefix + " " + currentCount + "/" + requiredText;
    }

    private boolean isPropertySetComplete(PlayerManagement player, Color color, int currentCount) {
        if (player == null || color == null) {
            return false;
        }
        int requiredCount = player.getRequiredSetSize(color);
        return requiredCount != Integer.MAX_VALUE && currentCount >= requiredCount;
    }

    private void stylePropertySetGroup(VBox colorGroup, Label colorTitle, PlayerManagement player, Color color, int currentCount) {
        if (colorGroup == null || colorTitle == null) {
            return;
        }
        colorGroup.getStyleClass().removeAll("property-set-card", "complete-set");
        colorGroup.getStyleClass().add("property-set-card");
        colorTitle.getStyleClass().removeAll("property-set-title", "complete-set-title");
        colorTitle.getStyleClass().add("property-set-title");
        colorTitle.setTextFill(javafx.scene.paint.Paint.valueOf(toFxColor(color)));
        if (isPropertySetComplete(player, color, currentCount)) {
            colorGroup.getStyleClass().add("complete-set");
            colorTitle.getStyleClass().add("complete-set-title");
        }
    }

    private void updateMyPropertiesTitle(PlayerManagement player) {
        if (myPropertiesTitleLabel == null) {
            return;
        }
        if (player == null) {
            myPropertiesTitleLabel.setText("My Properties");
            return;
        }
        myPropertiesTitleLabel.setText("My Properties  " + player.getCompleteSetCount() + "/" + PlayerManagement.REQUIRED_COMPLETE_SETS_TO_WIN + " sets");
    }

    private int resolveCompleteSetCount(GameStateData.PlayerData playerData, PlayerManagement player) {
        if (player != null) {
            return player.getCompleteSetCount();
        }
        if (playerData == null || playerData.getPropertyZones() == null) {
            return 0;
        }
        int sets = 0;
        for (Map.Entry<Color, GameStateData.PropertyZoneData> entry : playerData.getPropertyZones().entrySet()) {
            GameStateData.PropertyZoneData zoneData = entry.getValue();
            int currentCount = zoneData == null || zoneData.getProperties() == null ? 0 : zoneData.getProperties().size();
            int requiredCount = getRequiredPropertySetSize(entry.getKey());
            if (requiredCount != Integer.MAX_VALUE && currentCount >= requiredCount) {
                sets++;
            }
        }
        return sets;
    }

    private int getRequiredPropertySetSize(Color color) {
        if (color == null) {
            return Integer.MAX_VALUE;
        }
        return switch (color) {
            case BROWN, DARK_BLUE, UTILITY -> 2;
            case LIGHT_BLUE, PINK, ORANGE, RED, YELLOW, GREEN -> 3;
            case RAILROAD -> 4;
            default -> Integer.MAX_VALUE;
        };
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
        Runnable displayTask = () -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Notice");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.show();
        };
        if (Platform.isFxApplicationThread()) {
            Platform.runLater(displayTask);
        } else {
            Platform.runLater(displayTask);
        }
    }

    // Client-side handling for payment request
    public void handleRequirePayment(int amount, String collectorId) {
        PlayerManagement me = gameManager.getPlayersView().get(localPlayerIndex);

        // Always show the popup so the player can choose what to give up,
        // even if they can't afford the full amount.
        List<Card> selectedAssets = interactor.showSelectableAssets(me, amount);
        if (selectedAssets == null || selectedAssets.isEmpty()) {
            selectedAssets = autoSelectAssetsForPayment(me, amount);
        }

        // Build card id list
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

    private List<Card> autoSelectAssetsForPayment(PlayerManagement player, int amount) {
        if (player == null || amount <= 0) {
            return java.util.Collections.emptyList();
        }
        List<Card> assets = new ArrayList<>();
        assets.addAll(player.getBankCardsView());
        for (PropertyZone zone : player.getPropertyZonesView().values()) {
            if (zone == null) continue;
            if (zone.getPropertiesView() != null) {
                assets.addAll(zone.getPropertiesView());
            }
            if (zone.getHouse() != null) assets.add(zone.getHouse());
            if (zone.getHotel() != null) assets.add(zone.getHotel());
        }
        Collections.shuffle(assets);
        List<Card> selected = new ArrayList<>();
        int value = 0;
        for (Card c : assets) {
            if (c == null || c.getId() == null) continue;
            selected.add(c);
            value += c.getValue();
            if (value >= amount) break;
        }
        if (value < amount) {
            return java.util.Collections.emptyList();
        }
        return selected;
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
            pm.setEliminated(pData.isEliminated());

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
