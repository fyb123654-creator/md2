package com.mygame;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;
import javafx.geometry.Pos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class GameController {

    // ---------------- UI components ----------------
    @FXML private HBox myHandBox;
    @FXML private HBox handActionBox;
    @FXML private HBox myBankBox;
    @FXML private HBox myPropertyBox;
    @FXML private VBox opponentAreaBox;
    @FXML private Label turnInfoLabel;
    @FXML private Button endTurnButton;

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

    // 静态实例用于接收服务器状态
    private static GameController instance;
    
    @FXML
    public void initialize() {
        instance = this;
        // 调试：检查控件是否正确注入
        System.out.println("GameController initialized");
        System.out.println("myHandBox: " + myHandBox);
        System.out.println("handActionBox: " + handActionBox);
        System.out.println("endTurnButton: " + endTurnButton);
    }
    
    public static GameController getInstance() {
        return instance;
    }

    // ---------------- 1. Initialization ----------------
    public void initializeGame(int playerCount) {
        System.out.println("initializeGame called: isOnlineMode=" + isOnlineMode
                + ", isHost=" + isHost()
                + ", gameServer=" + (gameServer != null ? "set" : "NULL")
                + ", playerCount=" + playerCount);

        // 只有非联机模式或 Host 才能初始化游戏
        if (isOnlineMode && !isHost()) {
            System.out.println("Client should not initialize game - waiting for server state");
            return;
        }

        discardMode = false;
        selectedHandCard = null;

        // 始终在本地线程创建 GameManager（Host 也自己创建，然后共享给 Server）
        gameManager = new GameManager();
        interactor = new Interactor();
        gameManager.setInteractor(interactor);
        gameManager.setPlayerCount(playerCount);
        gameManager.startRound();

        if (isOnlineMode && isHost() && gameServer != null) {
            gameServer.setGameManager(gameManager);
            System.out.println("Shared local GameManager with server");
            // 广播 gameStart 和初始状态（此时客户端输出流已就绪）
            gameServer.broadcast(NetworkProtocol.gameStart(playerCount));
            gameServer.broadcastGameState();
        }

        System.out.println("initializeGame complete, gameManager=" + (gameManager != null ? "set" : "NULL")
                + ", calling updateUI");
        updateUI();
    }
    
    public void setOnlineMode(boolean online, int playerIndex) {
        this.isOnlineMode = online;
        this.localPlayerIndex = playerIndex;
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
    
    public void updateFromServerState(GameStateData state) {
        // 根据服务器状态更新UI
        if (state == null) return;

        System.out.println("updateFromServerState called: currentPlayerIndex=" + state.getCurrentPlayerIndex()
                + ", localPlayerIndex=" + localPlayerIndex
                + ", isMyTurn=" + (state.getCurrentPlayerIndex() == localPlayerIndex)
                + ", isHost=" + isHost()
                + ", handCardCount=" + (state.getPlayers().size() > localPlayerIndex
                    ? state.getPlayers().get(localPlayerIndex).getHandCards().size() : "N/A"));

        boolean wasMyTurn = isMyTurn;
        isMyTurn = (state.getCurrentPlayerIndex() == localPlayerIndex);
        endTurnButton.setDisable(!isMyTurn);

        // 更新回合信息
        turnInfoLabel.setText("Current turn: Player " + (state.getCurrentPlayerIndex() + 1)
                + (isMyTurn ? " (You)" : "")
                + " | Cards: " + state.getPlayedCardsThisTurn() + "/" + state.getMaxPlayCountPerTurn());

        // 新回合开始，清除选中状态
        if (!wasMyTurn && isMyTurn) {
            selectedHandCard = null;
            handActionBox.getChildren().clear();
        }

        // Host：gameManager 是服务器的实例，直接 refresh 即可渲染真实 Card 对象
        // Client：gameManager 为 null，用服务器发来的匿名 Card 数据渲染
        if (isOnlineMode && isHost() && gameManager != null) {
            System.out.println("Host: refreshing UI from local gameManager");
            updateUI();
        } else if (isOnlineMode && !isHost()) {
            System.out.println("Client: rendering from server state");
            updateHandCardsFromServer(state);
            updateBankCardsFromServer(state);
            updatePropertyCardsFromServer(state);
            updateOpponentAreaFromServer(state);
            updateCardDisabledState();
        }
    }
    
    private void updateCardDisabledState() {
        System.out.println("updateCardDisabledState called, isMyTurn: " + isMyTurn);
        System.out.println("myHandBox children count: " + myHandBox.getChildren().size());
        
        // 更新所有手牌的禁用状态
        for (var node : myHandBox.getChildren()) {
            if (node instanceof CardView cardView) {
                boolean shouldDisable = !isMyTurn;
                cardView.setDisable(shouldDisable);
                System.out.println("Card disabled: " + shouldDisable + ", card: " + cardView);
            }
        }
    }
    
    private void updateHandCardsFromServer(GameStateData state) {
        // 获取本地玩家的数据
        List<GameStateData.PlayerData> players = state.getPlayers();
        if (localPlayerIndex >= players.size()) {
            return;
        }
        
        GameStateData.PlayerData localPlayerData = players.get(localPlayerIndex);
        
        // 清空当前手牌显示
        myHandBox.getChildren().clear();
        
        // 添加手牌（带点击事件）
        for (GameStateData.CardData cardData : localPlayerData.getHandCards()) {
            Card card = cardData.toCard();
            CardView cardView = new CardView(card);
            cardView.setOnAction(event -> {
                System.out.println("Card clicked: " + card.getName());
                handleCardClick(card);
            });
            // 确保卡片可点击
            cardView.setDisable(false);
            cardView.setOpacity(1.0);
            cardView.setMouseTransparent(false);
            myHandBox.getChildren().add(cardView);
        }
        System.out.println("Hand cards updated: " + localPlayerData.getHandCards().size() + " cards");
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

            Label colorTitle = new Label(color.name());
            colorTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + toFxColor(color) + ";");
            colorGroup.getChildren().add(colorTitle);

            HBox propertyRow = new HBox(8);
            propertyRow.setAlignment(Pos.CENTER_LEFT);

            for (GameStateData.CardData cardData : zoneData.getProperties()) {
                Card card = cardData.toCard();
                CardView cardView = new CardView(card, true);
                cardView.setDisable(true);
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
            container.setStyle("-fx-background-color: white; -fx-border-color: #d9d9d9; -fx-border-radius: 8; -fx-background-radius: 8;");

            Label nameLabel = new Label(playerData.getPlayerName());
            nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

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
                Label colorLabel = new Label("[" + color.name() + "]");
                colorLabel.setStyle("-fx-padding: 6 10; -fx-background-color: " + toSoftFxColor(color)
                        + "; -fx-border-color: " + toFxColor(color) + "; -fx-border-radius: 6; -fx-font-weight: bold;");
                propertyRow.getChildren().add(colorLabel);
                hasProperty = true;
            }
            if (!hasProperty) {
                propertyRow.getChildren().add(new Label("None"));
            }

            container.getChildren().addAll(nameLabel, handCountLabel, bankRow, propertyRow);
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
        // 如果是联机模式的客户端且 gameManager 为 null，不更新 UI
        if (isOnlineMode && !isHost() && gameManager == null) {
            turnInfoLabel.setText("Waiting for server...");
            return;
        }

        PlayerManagement currentPlayer = gameManager.getCurrentPlayer();
        // 联机模式下始终渲染本地玩家的手牌，而非当前回合玩家的手牌
        PlayerManagement localPlayer = isOnlineMode
            ? gameManager.getPlayersView().get(localPlayerIndex)
            : currentPlayer;

        turnInfoLabel.setText("Current turn: " + currentPlayer.getName() +
                " | Remaining plays: " + gameManager.getRemainingPlayCountThisTurn());
        renderOpponentArea(localPlayer);
        renderHandCards(localPlayer);
        renderBankCards(localPlayer);
        renderPropertyCards(localPlayer);
        renderHandActionButtons();
    }

    private void renderOpponentArea(PlayerManagement currentPlayer) {
        opponentAreaBox.getChildren().clear();

        List<PlayerManagement> players = gameManager.getPlayersView();
        for (PlayerManagement player : players) {
            if (player == currentPlayer) {
                continue;
            }
            VBox playerCard = createPlayerSummaryCard(player, false);
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
        renderCardButtonRow(myHandBox, player.getHandCards(), true);
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
            int requiredCount = player.getRequiredSetSize(color);
            int currentCount = player.getPropertyCount(color);

            VBox colorGroup = new VBox(8);
            colorGroup.setPadding(new Insets(8));
            colorGroup.setStyle("-fx-background-color: #fafafa; -fx-border-color: #d9d9d9; -fx-border-radius: 8; -fx-background-radius: 8;");

            Label colorTitle = new Label(color.name() + "  " + currentCount + "/" + requiredCount);
            colorTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + toFxColor(color) + ";");
            colorGroup.getChildren().add(colorTitle);

            HBox propertyRow = new HBox(8);
            propertyRow.setAlignment(Pos.CENTER_LEFT);

            boolean hasAnyCard = false;
            for (PropertyCard propertyCard : zone.getPropertiesView()) {
                hasAnyCard = true;
                CardView cardView = new CardView(propertyCard, true);
                cardView.setDisable(true);
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
            Label emptyView = new Label("暂无物业");
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
        }
    }

    // ---------------- 3. 用户交互处理方法 (你问的代码放这里) ----------------

    // 处理卡牌点击事件
    private void handleCardClick(Card card) {
        System.out.println("handleCardClick called, isOnlineMode: " + isOnlineMode
                + ", isHost: " + isHost()
                + ", gameManager: " + (gameManager != null ? "set" : "NULL")
                + ", gameServer: " + (gameServer != null ? "set" : "NULL")
                + ", localPlayerIndex: " + localPlayerIndex);

        // 如果是联机模式客户端，走客户端逻辑（不依赖 gameManager）
        if (isOnlineMode && !isHost()) {
            System.out.println("Routing to handleOnlineCardClick");
            handleOnlineCardClick(card);
            return;
        }

        // gameManager 为 null 时尝试恢复
        if (gameManager == null) {
            System.out.println("gameManager is null, attempting recovery...");
            if (isOnlineMode) {
                showError("Game not initialized - please restart");
                return;
            }
            // 单机模式：创建一个新的
            gameManager = new GameManager();
            if (interactor == null) {
                interactor = new Interactor();
            }
            gameManager.setInteractor(interactor);
            updateUI();
        }
        
        if (discardMode) {
            try {
                gameManager.removeFromCurrentPlayerHand(card);

                if (gameManager.getCurrentPlayer().getHandCardCount() <= PlayerManagement.MAX_HAND_SIZE) {
                    discardMode = false;
                    gameManager.advanceTurn();
                }

                updateUI();
            } catch (Exception e) {
                showError("Failed to discard: " + e.getMessage());
            }
            return;
        }

        // 联机 Host 也必须检查是否自己的回合
        if (isOnlineMode && !isMyTurn) {
            showError("It's not your turn!");
            return;
        }

        if (!gameManager.canCurrentPlayerPlayCard()) {
            showError("You have reached the maximum number of plays this turn!");
            return;
        }

        selectedHandCard = card;
        renderHandActionButtons();
    }
    
    // 联机模式下的卡牌点击处理
    private void handleOnlineCardClick(Card card) {
        System.out.println("handleOnlineCardClick called: " + card.getName()
                + ", cardType: " + card.getCardType()
                + ", isMyTurn: " + isMyTurn
                + ", gameClient: " + (gameClient != null ? "set" : "NULL"));
        if (!isMyTurn) {
            showError("It's not your turn!");
            return;
        }

        selectedHandCard = card;
        renderOnlineHandActionButtons();
        System.out.println("Online action buttons rendered for card: " + card.getName());
    }
    
    // 联机Host：广播状态到所有客户端
    private void broadcastStateIfHost() {
        if (isOnlineMode && isHost() && gameServer != null) {
            gameServer.broadcastGameState();
        }
    }

    // 联机Client：发送操作到服务器
    private void sendActionToServer(String action) {
        if (isOnlineMode && !isHost() && gameClient != null) {
            gameClient.sendAction(action);
        }
    }

    // 联机模式下的手牌操作按钮
    private void renderOnlineHandActionButtons() {
        handActionBox.getChildren().clear();
        if (selectedHandCard == null) {
            return;
        }

        HBox buttonRow = new HBox(14);
        buttonRow.setAlignment(Pos.CENTER);
        buttonRow.setMaxWidth(Double.MAX_VALUE);

        List<Button> buttons = new ArrayList<>();
        if (selectedHandCard.isMoneyCard()) {
            buttons.add(createActionOptionButton("Deposit to bank", () -> sendDepositAction(selectedHandCard)));
        } else if (selectedHandCard.isActionCard()) {
            buttons.add(createActionOptionButton("Play as action card", () -> sendPlayAction(selectedHandCard)));
            buttons.add(createActionOptionButton("存入银行", () -> sendDepositAction(selectedHandCard)));
        } else if (selectedHandCard.isPropertyCard()) {
            buttons.add(createActionOptionButton("存入银行", () -> sendDepositAction(selectedHandCard)));
            buttons.add(createActionOptionButton("Place as property", () -> sendPlacePropertyAction(selectedHandCard)));
        }

        if (buttons.isEmpty()) {
            return;
        }

        buttonRow.getChildren().addAll(buttons);
        handActionBox.getChildren().add(buttonRow);
    }
    
    // 发送出牌操作到服务器
    private void sendPlayAction(Card card) {
        sendActionToServer("PLAY_ACTION:" + card.getId());
        selectedHandCard = null;
        handActionBox.getChildren().clear();
    }

    private void sendDepositAction(Card card) {
        sendActionToServer("DEPOSIT:" + card.getId());
        selectedHandCard = null;
        handActionBox.getChildren().clear();
    }

    private void sendPlacePropertyAction(Card card) {
        sendActionToServer("PLACE_PROPERTY:" + card.getId());
        selectedHandCard = null;
        handActionBox.getChildren().clear();
    }

    private void renderHandActionButtons() {
        handActionBox.getChildren().clear();
        if (selectedHandCard == null || discardMode || !gameManager.canCurrentPlayerPlayCard()) {
            return;
        }

        HBox buttonRow = new HBox(14);
        buttonRow.setAlignment(Pos.CENTER);
        buttonRow.setMaxWidth(Double.MAX_VALUE);

        List<Button> buttons = new ArrayList<>();
        if (selectedHandCard.isMoneyCard()) {
            buttons.add(createActionOptionButton("Deposit to bank", () -> doDepositToBank(selectedHandCard)));
        } else if (selectedHandCard.isActionCard()) {
            buttons.add(createActionOptionButton("Play as action card", () -> doPlayActionCard(selectedHandCard)));
            buttons.add(createActionOptionButton("存入银行", () -> doDepositToBank(selectedHandCard)));
        } else if (selectedHandCard.isPropertyCard()) {
            buttons.add(createActionOptionButton("存入银行", () -> doDepositToBank(selectedHandCard)));
            buttons.add(createActionOptionButton("Place as property", () -> doPlacePropertyCard(selectedHandCard)));
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

    private void doPlayActionCard(Card card) {
        try {
            gameManager.playActionCard(card);
            selectedHandCard = null;
            updateUI();
            broadcastStateIfHost();
        } catch (Exception e) {
            showError("Action failed: " + e.getMessage());
        }
    }

    private void doDepositToBank(Card card) {
        try {
            gameManager.depositMoneyCard(card);
            selectedHandCard = null;
            updateUI();
            broadcastStateIfHost();
        } catch (Exception e) {
            showError("操作失败: " + e.getMessage());
        }
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

            gameManager.placePropertyCard(propertyCard, gameManager.getCurrentPlayer(), selectedColor);
            selectedHandCard = null;
            updateUI();
            broadcastStateIfHost();
        } catch (Exception e) {
            showError("操作失败: " + e.getMessage());
        }
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

    // Handle the End Turn button click
    // This method must be bound in the FXML file, for example: <Button onAction="#onEndTurnClicked" text="End Turn"/>
    @FXML
    private void onEndTurnClicked() {
        // 如果是联机模式且不是 Host，需要通过网络发送操作
        if (isOnlineMode && !isHost()) {
            sendEndTurnAction();
            return;
        }
        
        // 单机模式或 Host 直接处理
        try {
            gameManager.confirmCurrentPlayerTurnEnded();

            // Check the hand size and prompt the player to discard if over the limit
            if (gameManager.getCurrentPlayer().getHandCardCount() > PlayerManagement.MAX_HAND_SIZE) {
                startDiscardMode();
                return;
            }

            // Move to the next player's turn
            gameManager.advanceTurn();
            discardMode = false;
            updateUI();
            broadcastStateIfHost();

        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void sendEndTurnAction() {
        sendActionToServer("END_TURN");
    }


    private void startDiscardMode() {
        discardMode = true;
        selectedHandCard = null;
        showError("Your hand exceeds the limit. Click a hand card to discard it.");
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

    private VBox createPlayerSummaryCard(PlayerManagement player, boolean isCurrentPlayer) {
        VBox container = new VBox(8);
        container.setPadding(new Insets(12));
        container.setStyle("-fx-background-color: white; -fx-border-color: #d9d9d9; -fx-border-radius: 8; -fx-background-radius: 8;");

        Label nameLabel = new Label((isCurrentPlayer ? "[我] " : "") + player.getName());
        nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

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
            int requiredCount = player.getRequiredSetSize(color);
            int currentCount = player.getPropertyCount(color);
            Label colorLabel = new Label("[" + color.name() + "] " + currentCount + "/" + requiredCount);
            colorLabel.setStyle("-fx-padding: 6 10; -fx-background-color: " + toSoftFxColor(color)
                    + "; -fx-border-color: " + toFxColor(color) + "; -fx-border-radius: 6; -fx-font-weight: bold;");
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

        container.getChildren().addAll(nameLabel, handCountLabel, bankRow, propertyRow);
        return container;
    }

    // CardView组件已替代此方法

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

    // ---------------- 4. 辅助方法 ----------------
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Notice");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}