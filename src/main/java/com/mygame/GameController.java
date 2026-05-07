package com.mygame;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// 这个类就是用来存放所有交互逻辑的地方
public class GameController {

    // ---------------- UI 组件 ----------------
    // 这些是从 FXML 文件中绑定过来的界面元素
    @FXML private HBox myHandBox;         // 显示玩家手牌的区域
    @FXML private HBox myBankBox;         // 显示玩家银行区的区域
    @FXML private HBox myPropertyBox;     // 显示玩家物业区的区域
    @FXML private Label turnInfoLabel;    // 显示当前回合信息的文本
    @FXML private Button endTurnButton;   // 结束回合按钮

    // ---------------- 游戏数据模型 ----------------
    private GameManager gameManager;

    // ---------------- 1. 初始化方法 ----------------
    public void initializeGame(int playerCount) {
        gameManager = new GameManager();
        try {
            gameManager.setPlayerCount(playerCount);
            gameManager.startRound();
            updateUI(); // 游戏开始，立刻刷新界面
        } catch (Exception e) {
            showError("初始化失败: " + e.getMessage());
        }
    }

    // ---------------- 2. UI 刷新方法 ----------------
    // 只要游戏状态发生变化，就调用这个方法重新绘制界面
    private void updateUI() {
        PlayerManagement currentPlayer = gameManager.getCurrentPlayer();

        turnInfoLabel.setText("当前回合: " + currentPlayer.getName() +
                " | 剩余出牌次数: " + gameManager.getRemainingPlayCountThisTurn());

        endTurnButton.setDisable(gameManager.getPlayedCardsThisTurn() == 0 && !gameManager.hasCurrentPlayerEndedTurn());

        renderHandCards(currentPlayer);
        renderBankCards(currentPlayer);
        renderPropertyCards(currentPlayer);
    }

    // 绘制手牌
    private void renderHandCards(PlayerManagement player) {
        myHandBox.getChildren().clear(); // 清空之前显示的手牌

        // 遍历当前玩家的手牌(假设你的 PlayerManagement 里有 getHandCards() 方法)
        for (Card card : player.getHandCards()) {
            Button cardView = new Button(card.getName());
            cardView.setPrefSize(100, 150);

            // 【关键点】：在这里将每张手牌的点击事件，绑定到下面的 handleCardClick 方法中
            cardView.setOnAction(event -> handleCardClick(card));

            myHandBox.getChildren().add(cardView);
        }
    }

    // 绘制银行区
    private void renderBankCards(PlayerManagement player) {
        myBankBox.getChildren().clear();

        for (Card card : player.getBankCardsView()) {
            Label cardView = new Label(card.getName() + "(" + card.getValue() + "M)");
            cardView.setStyle("-fx-padding: 6 10; -fx-background-color: #fff7d6; -fx-border-color: #d8c37a;");
            myBankBox.getChildren().add(cardView);
        }
    }

    // 绘制物业区
    private void renderPropertyCards(PlayerManagement player) {
        myPropertyBox.getChildren().clear();

        for (Map.Entry<Color, PropertyZone> entry : player.getPropertyZonesView().entrySet()) {
            Color color = entry.getKey();
            PropertyZone zone = entry.getValue();

            StringBuilder zoneText = new StringBuilder();
            zoneText.append(color.name()).append(": ");

            boolean hasAny = false;
            for (PropertyCard propertyCard : zone.getPropertiesView()) {
                if (hasAny) {
                    zoneText.append(", ");
                }
                zoneText.append(propertyCard.getName());
                hasAny = true;
            }

            if (zone.getHouse() != null) {
                if (hasAny) {
                    zoneText.append(", ");
                }
                zoneText.append("House");
                hasAny = true;
            }

            if (zone.getHotel() != null) {
                if (hasAny) {
                    zoneText.append(", ");
                }
                zoneText.append("Hotel");
                hasAny = true;
            }

            if (!hasAny) {
                zoneText.append("(空)");
            }

            Label zoneView = new Label(zoneText.toString());
            zoneView.setStyle("-fx-padding: 6 10; -fx-background-color: #e9f3ff; -fx-border-color: #9ebee6;");
            myPropertyBox.getChildren().add(zoneView);
        }

        if (myPropertyBox.getChildren().isEmpty()) {
            Label emptyView = new Label("暂无物业");
            emptyView.setStyle("-fx-text-fill: #666666;");
            myPropertyBox.getChildren().add(emptyView);
        }
    }

    // ---------------- 3. 用户交互处理方法 (你问的代码放这里) ----------------

    // 处理卡牌点击事件
    private void handleCardClick(Card card) {
        if (!gameManager.canCurrentPlayerPlayCard()) {
            showError("本回合出牌次数已达上限！");
            return;
        }

        // 弹出一个简单的对话框，让玩家选择打牌方式
        ChoiceDialog<String> dialog = new ChoiceDialog<>("作为行动牌使用", "作为行动牌使用", "存入银行", "放置为房产");
        dialog.setTitle("打出卡牌");
        dialog.setHeaderText("你要如何打出 [" + card.getName() + "] ?");
        dialog.setContentText("选择操作:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(choice -> {
            try {
                // 根据玩家的选择，调用 GameManager 中对应的打牌方法
                switch (choice) {
                    case "作为行动牌使用" -> gameManager.playActionCard(card);
                    case "存入银行" -> gameManager.depositMoneyCard(card); // 注意：之前你的方法名叫 depositMoneyCard，请根据实际情况修改
                    case "放置为房产" -> {
                        if (!(card instanceof PropertyCard propertyCard)) {
                            throw new IllegalArgumentException("该卡牌不能作为房产放置: " + card.getName());
                        }

                        Set<Color> playableColors = propertyCard.getPlayableColors();
                        if (playableColors == null || playableColors.isEmpty()) {
                            throw new IllegalArgumentException("该房产卡没有可用颜色: " + card.getName());
                        }

                        Color selectedColor;
                        if (playableColors.size() == 1) {
                            selectedColor = playableColors.iterator().next();
                        } else {
                            List<Color> colorOptions = new ArrayList<>(playableColors);
                            ChoiceDialog<Color> colorDialog = new ChoiceDialog<>(colorOptions.get(0), colorOptions);
                            colorDialog.setTitle("选择房产颜色");
                            colorDialog.setHeaderText("请选择 [" + card.getName() + "] 要放置到的颜色区");
                            colorDialog.setContentText("颜色:");

                            Optional<Color> colorResult = colorDialog.showAndWait();
                            if (colorResult.isEmpty()) {
                                return;
                            }
                            selectedColor = colorResult.get();
                        }

                        gameManager.placePropertyCard(propertyCard, selectedColor);
                    }
                }

                // 【核心】：打牌成功后，一定要调用 updateUI 刷新界面！
                updateUI();

            } catch (Exception e) {
                // 捕获你在 GameManager 里抛出的异常，比如 "card is not an action card"
                showError("操作失败: " + e.getMessage());
            }
        });
    }

    // 处理“结束回合”按钮点击事件
    // 注意：这个方法需要在你的 FXML 文件中绑定，比如 <Button onAction="#onEndTurnClicked" text="结束回合"/>
    @FXML
    private void onEndTurnClicked() {
        try {
            gameManager.confirmCurrentPlayerTurnEnded();

            // 检查手牌数量，如果超过最大限制（比如7张），提示玩家弃牌
            if (gameManager.getCurrentPlayer().getHandCardCount() > PlayerManagement.MAX_HAND_SIZE) {
                showError("手牌超过限制，请点击手牌进行丢弃：");
                // TODO: 这里需要进入“弃牌模式”。你可以设置一个 boolean 变量 isDiscardMode = true;
                // 然后在上面的 handleCardClick 中判断：如果是弃牌模式，点击手牌就是调用 removeFromCurrentPlayerHand，而不是 playCard。
                return;
            }

            // 进入下一个玩家的回合
            gameManager.advanceTurn();
            updateUI(); // 刷新界面，显示下一个玩家的信息

        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    // ---------------- 4. 辅助方法 ----------------
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("提示");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
