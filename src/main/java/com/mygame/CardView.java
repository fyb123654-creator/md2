package com.mygame;

import javafx.scene.control.Button;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Rectangle;

/**
 * 卡牌视图组件 - 实现Monopoly卡牌的实体图片效果
 */
public class CardView extends Button {

    private static final int CARD_WIDTH = 90;
    private static final int CARD_HEIGHT = 130;
    private static final int SMALL_CARD_WIDTH = 70;
    private static final int SMALL_CARD_HEIGHT = 95;

    private final Card card;
    private final boolean small;

    public CardView(Card card) {
        this(card, false);
    }

    public CardView(Card card, boolean small) {
        this.card = card;
        this.small = small;

        initialize();
    }

    private void initialize() {
        int width = small ? SMALL_CARD_WIDTH : CARD_WIDTH;
        int height = small ? SMALL_CARD_HEIGHT : CARD_HEIGHT;

        setPrefSize(width, height);
        setMinSize(width, height);
        setMaxSize(width, height);

        // 创建卡牌内容
        VBox cardContent = new VBox();
        cardContent.setPadding(new Insets(8));
        cardContent.setAlignment(javafx.geometry.Pos.CENTER);
        cardContent.setSpacing(4);

        // 根据卡牌类型设置样式
        String style = getCardStyle();
        cardContent.setStyle(style);

        // 添加卡牌颜色条（顶部）
        HBox colorBar = createColorBar();
        cardContent.getChildren().add(colorBar);

        // 添加卡牌名称
        Label nameLabel = new Label(truncateName(card.getName()));
        nameLabel.setFont(Font.font("Arial", FontWeight.BOLD, small ? 10 : 12));
        nameLabel.setTextAlignment(TextAlignment.CENTER);
        nameLabel.setWrapText(true);
        nameLabel.setStyle("-fx-text-fill: #1a1a1a; -fx-text-alignment: center;");
        cardContent.getChildren().add(nameLabel);

        // 添加卡牌类型图标/标识
        Label typeLabel = new Label(getCardTypeIcon());
        typeLabel.setFont(Font.font("Arial", FontWeight.BOLD, small ? 18 : 24));
        typeLabel.setStyle("-fx-text-fill: #4a4a4a;");
        cardContent.getChildren().add(typeLabel);

        // 添加卡牌价值
        Label valueLabel = new Label(card.getValue() + "M");
        valueLabel.setFont(Font.font("Arial", FontWeight.BOLD, small ? 14 : 18));
        valueLabel.setStyle("-fx-text-fill: #8b4513;");
        cardContent.getChildren().add(valueLabel);

        // 设置按钮内容
        setGraphic(cardContent);

        // 添加阴影效果
        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.rgb(0, 0, 0, 0.3));
        shadow.setRadius(6);
        shadow.setOffsetX(3);
        shadow.setOffsetY(3);
        setEffect(shadow);

        // 设置背景为透明（内容已在cardContent中设置）
        setStyle("-fx-background-color: transparent; -fx-padding: 0;");
    }

    private HBox createColorBar() {
        HBox colorBar = new HBox();
        colorBar.setPrefHeight(8);
        colorBar.setMinHeight(8);
        colorBar.setMaxHeight(8);

        String color = getCardColorBar();
        colorBar.setStyle("-fx-background-color: " + color + ";");

        return colorBar;
    }

    private String getCardStyle() {
        String baseStyle = "-fx-background-radius: 12; -fx-border-radius: 12; -fx-border-width: 2;";

        // 根据卡牌类型设置背景和边框颜色
        switch (card.getCardType()) {
            case PROPERTY_STANDARD:
            case PROPERTY_WILD_BICOLOR:
            case PROPERTY_WILD_MULTICOLOR:
                if (card instanceof StandardPropertyCard) {
                    StandardPropertyCard pc = (StandardPropertyCard) card;
                    com.mygame.Color color = pc.getCurrentActiveColor();
                    return baseStyle + 
                        " -fx-background-color: " + color.getLightColor() + "; " +
                        " -fx-border-color: " + color.getMainColor() + ";";
                } else if (card instanceof PropertyCard) {
                    return baseStyle + 
                        " -fx-background-color: #ffffff; " +
                        " -fx-border-color: #7f8c8d;";
                }
                break;
            case MONEY:
                return baseStyle +
                        " -fx-background-color: linear-gradient(to bottom, #fff7d6, #f5e6a3); " +
                        " -fx-border-color: #d4ac0d;";
            case ACTION:
                return baseStyle +
                        " -fx-background-color: linear-gradient(to bottom, #e8f4f8, #b8d8e6); " +
                        " -fx-border-color: #2980b9;";
            case BUILDING:
                String name = card.getName().toLowerCase();
                if (name.contains("house")) {
                    return baseStyle +
                            " -fx-background-color: linear-gradient(to bottom, #f5deb3, #deb887); " +
                            " -fx-border-color: #8b4513;";
                } else if (name.contains("hotel")) {
                    return baseStyle +
                            " -fx-background-color: linear-gradient(to bottom, #d2b48c, #bc9a6a); " +
                            " -fx-border-color: #654321;";
                }
                break;
            case RENT_BICOLOR:
            case RENT_WILDCOLOR:
                return baseStyle +
                        " -fx-background-color: linear-gradient(to bottom, #fce4ec, #f8bbd9); " +
                        " -fx-border-color: #e91e63;";
        }

        // 默认样式
        return baseStyle +
                " -fx-background-color: #ffffff; " +
                " -fx-border-color: #cccccc;";
    }

    private String getCardColorBar() {
        switch (card.getCardType()) {
            case PROPERTY_STANDARD:
                if (card instanceof StandardPropertyCard) {
                    return ((StandardPropertyCard) card).getCurrentActiveColor().getMainColor();
                }
                break;
            case PROPERTY_WILD_BICOLOR:
                if (card instanceof BiColorWildPropertyCard) {
                    BiColorWildPropertyCard bc = (BiColorWildPropertyCard) card;
                    StringBuilder colors = new StringBuilder();
                    for (com.mygame.Color c : bc.getPlayableColors()) {
                        if (colors.length() > 0) colors.append(", ");
                        colors.append(c.getMainColor());
                    }
                    return colors.toString();
                }
                break;
            case PROPERTY_WILD_MULTICOLOR:
                return "#7f8c8d"; // 灰色表示百搭
            case MONEY:
                return "#d4ac0d"; // 金色
            case ACTION:
                return "#2980b9"; // 蓝色
            case BUILDING:
                String name = card.getName().toLowerCase();
                if (name.contains("house")) {
                    return "#8b4513"; // 棕色
                } else if (name.contains("hotel")) {
                    return "#654321"; // 深棕色
                }
                break;
            case RENT_BICOLOR:
            case RENT_WILDCOLOR:
                return "#e91e63"; // 粉红色
        }
        return "#cccccc";
    }

    private String getCardTypeIcon() {
        switch (card.getCardType()) {
            case PROPERTY_STANDARD:
            case PROPERTY_WILD_BICOLOR:
            case PROPERTY_WILD_MULTICOLOR:
                return "🏠";
            case MONEY:
                return "💰";
            case ACTION:
                return "⚡";
            case BUILDING:
                String name = card.getName().toLowerCase();
                if (name.contains("house")) {
                    return "🏠";
                } else if (name.contains("hotel")) {
                    return "🏨";
                }
                return "🔧";
            case RENT_BICOLOR:
            case RENT_WILDCOLOR:
                return "💸";
            default:
                return "❓";
        }
    }

    private String truncateName(String name) {
        if (name.length() > 20) {
            return name.substring(0, 17) + "...";
        }
        return name;
    }

    public Card getCard() {
        return card;
    }
}