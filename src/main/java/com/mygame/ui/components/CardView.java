package com.mygame.ui.components;

import com.mygame.cards.action.*;
import com.mygame.cards.base.Card;
import com.mygame.cards.base.CardType;
import com.mygame.cards.money.MoneyCard;
import com.mygame.cards.property.*;
import com.mygame.cards.rent.*;
import com.mygame.rules.PropertyRentRules;

import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.scene.control.Button;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Popup;
import javafx.util.Duration;

/**
 * Card view component.
 */
public class CardView extends Button {

    private static final int CARD_WIDTH = 90;
    private static final int CARD_HEIGHT = 130;
    private static final int SMALL_CARD_WIDTH = 70;
    private static final int SMALL_CARD_HEIGHT = 95;

    private final Card card;
    private final boolean small;
    private VBox cardContent;
    private VBox backContent;
    private Popup hoverPreviewPopup;
    private boolean faceDown;

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

        cardContent = new VBox();

        cardContent.setPadding(new Insets(8));

        cardContent.setAlignment(javafx.geometry.Pos.CENTER);

        cardContent.setSpacing(4);

        String style = getCardStyle();

        cardContent.setStyle(style);

        HBox colorBar = createColorBar();

        cardContent.getChildren().add(colorBar);

        Label setSizeLabel = createPropertySetSizeLabel();
        if (setSizeLabel != null) {
            cardContent.getChildren().add(setSizeLabel);
        }

        Label nameLabel =
                new Label(truncateName(card.getName()));

        nameLabel.setFont(
                Font.font(
                        "Arial",
                        FontWeight.BOLD,
                        small ? 10 : 12
                )
        );

        nameLabel.setTextAlignment(
                TextAlignment.CENTER
        );

        nameLabel.setWrapText(true);

        nameLabel.setStyle(
                "-fx-text-fill: #1a1a1a;"
        );

        cardContent.getChildren().add(nameLabel);

        Label typeLabel =
                new Label(getCardTypeIcon());

        typeLabel.setFont(
                Font.font(
                        "Arial",
                        FontWeight.BOLD,
                        small ? 18 : 24
                )
        );

        typeLabel.setStyle(
                "-fx-text-fill: #4a4a4a;"
        );

        cardContent.getChildren().add(typeLabel);

        Label valueLabel =
                new Label(card.getValue() + "M");

        valueLabel.setFont(
                Font.font(
                        "Arial",
                        FontWeight.BOLD,
                        small ? 14 : 18
                )
        );

        valueLabel.setStyle(
                "-fx-text-fill: #8b4513;"
        );

        cardContent.getChildren().add(valueLabel);

        setGraphic(cardContent);

        DropShadow shadow = new DropShadow();

        shadow.setColor(
                Color.rgb(0, 0, 0, 0.3)
        );

        shadow.setRadius(6);

        shadow.setOffsetX(3);

        shadow.setOffsetY(3);

        setEffect(shadow);

        setStyle(
                "-fx-background-color: transparent; -fx-padding: 0;"
        );

        backContent = new VBox();
        backContent.setPadding(new Insets(8));
        backContent.setAlignment(javafx.geometry.Pos.CENTER);
        backContent.setSpacing(4);
        backContent.setPrefSize(width, height);
        backContent.setMinSize(width, height);
        backContent.setMaxSize(width, height);
        backContent.getStyleClass().add("card-back");
        Label backIcon = new Label("🎴");
        backIcon.setFont(Font.font("Arial", FontWeight.BOLD, small ? 18 : 24));
        backIcon.setStyle("-fx-text-fill: #1d4ed8;");
        Label backTitle = new Label(small ? "MD" : "MONOPOLY");
        backTitle.setFont(Font.font("Arial", FontWeight.EXTRA_BOLD, small ? 14 : 18));
        backTitle.setStyle("-fx-text-fill: #1e293b; -fx-letter-spacing: 1.2px;");
        Label backSub = new Label("DEAL");
        backSub.setFont(Font.font("Arial", FontWeight.BOLD, small ? 10 : 12));
        backSub.setStyle("-fx-text-fill: rgba(30,41,59,0.72); -fx-letter-spacing: 2px;");
        backContent.getChildren().addAll(backIcon, backTitle, backSub);

        setFaceDown(false);
        installHoverZoom();
    }

    private void installHoverZoom() {
        if (cardContent == null) return;

        setOnMouseEntered(e -> showHoverPreview(e.getScreenX(), e.getScreenY()));
        setOnMouseExited(e -> hideHoverPreview());
    }

    private void showHoverPreview(double screenX, double screenY) {
        if (getScene() == null || getScene().getWindow() == null) return;

        if (hoverPreviewPopup == null) {
            hoverPreviewPopup = new Popup();
            hoverPreviewPopup.setAutoHide(false);
            hoverPreviewPopup.setHideOnEscape(true);
        } else {
            hoverPreviewPopup.getContent().clear();
        }

        VBox preview = createHoverPreviewContent();
        preview.setMouseTransparent(true);
        preview.setStyle(preview.getStyle() + "; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 18, 0.25, 0, 6);");
        hoverPreviewPopup.getContent().add(preview);
        hoverPreviewPopup.show(getScene().getWindow(), screenX + 18, screenY + 18);
    }

    private VBox createHoverPreviewContent() {
        int width = small ? (int) Math.round(SMALL_CARD_WIDTH * 2.1) : (int) Math.round(CARD_WIDTH * 2.0);
        int height = small ? (int) Math.round(SMALL_CARD_HEIGHT * 2.1) : (int) Math.round(CARD_HEIGHT * 2.0);

        VBox preview = new VBox();
        preview.setPrefSize(width, height);
        preview.setMinSize(width, height);
        preview.setMaxSize(width, height);

        preview.setPadding(new Insets(12));
        preview.setAlignment(javafx.geometry.Pos.CENTER);
        preview.setSpacing(6);
        preview.setStyle(getCardStyle());

        HBox colorBar = createColorBar();
        colorBar.setPrefHeight(12);
        colorBar.setMinHeight(12);
        colorBar.setMaxHeight(12);
        preview.getChildren().add(colorBar);

        Label setSizeLabel = createPropertySetSizeLabel(true);
        if (setSizeLabel != null) {
            preview.getChildren().add(setSizeLabel);
        }

        Label nameLabel = new Label(card.getName());
        nameLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        nameLabel.setTextAlignment(TextAlignment.CENTER);
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(width - 24);
        nameLabel.setStyle("-fx-text-fill: #1a1a1a;");
        preview.getChildren().add(nameLabel);

        Label typeLabel = new Label(getCardTypeIcon());
        typeLabel.setFont(Font.font("Arial", FontWeight.BOLD, 34));
        typeLabel.setStyle("-fx-text-fill: #4a4a4a;");
        preview.getChildren().add(typeLabel);

        Label valueLabel = new Label(card.getValue() + "M");
        valueLabel.setFont(Font.font("Arial", FontWeight.BOLD, 26));
        valueLabel.setStyle("-fx-text-fill: #8b4513;");
        preview.getChildren().add(valueLabel);

        return preview;
    }

    private Label createPropertySetSizeLabel() {
        return createPropertySetSizeLabel(false);
    }

    private Label createPropertySetSizeLabel(boolean preview) {
        if (!(card instanceof PropertyCard propertyCard)) {
            return null;
        }
        com.mygame.model.Color active = propertyCard.getCurrentActiveColor();
        PropertyRentRules.RentRule rule = PropertyRentRules.RULES.get(active);
        if (rule == null) {
            return null;
        }
        int setSize = rule.getMaxSetSize();
        Label label = new Label("Set: " + setSize);
        label.setFont(Font.font("Arial", FontWeight.BOLD, preview ? 16 : (small ? 10 : 11)));
        label.setStyle("-fx-text-fill: rgba(26,26,26,0.78);");
        return label;
    }

    private void hideHoverPreview() {
        if (hoverPreviewPopup != null) {
            hoverPreviewPopup.hide();
        }
    }

    private HBox createColorBar() {

        HBox colorBar = new HBox();

        colorBar.setPrefHeight(8);

        colorBar.setMinHeight(8);

        colorBar.setMaxHeight(8);

        String color = getCardColorBar();

        colorBar.setStyle(
                "-fx-background-color: " + color + ";"
        );

        return colorBar;
    }

    private String getCardStyle() {

        String baseStyle =
                "-fx-background-radius: 12; " +
                        "-fx-border-radius: 12; " +
                        "-fx-border-width: 2;";

        switch (card.getCardType()) {

            case PROPERTY_STANDARD:

                if (card instanceof StandardPropertyCard pc) {

                    com.mygame.model.Color color =
                            pc.getCurrentActiveColor();

                    return baseStyle +
                            " -fx-background-color: "
                            + color.getLightColor()
                            + "; " +
                            " -fx-border-color: "
                            + color.getMainColor()
                            + ";";
                }

                break;

            case PROPERTY_WILD_BICOLOR:

                if (card instanceof BiColorWildPropertyCard bc) {

                    com.mygame.model.Color[] colors =
                            bc.getPlayableColors()
                                    .toArray(
                                            new com.mygame.model.Color[0]
                                    );

                    String c1 =
                            colors[0].getLightColor();

                    String c2 =
                            colors[1].getLightColor();

                    String border =
                            bc.getCurrentActiveColor()
                                    .getMainColor();

                    return baseStyle +
                            " -fx-background-color: linear-gradient(to right, "
                            + c1
                            + " 50%, "
                            + c2
                            + " 50%); "
                            +
                            " -fx-border-color: "
                            + border
                            + ";";
                }

                break;

            case PROPERTY_WILD_MULTICOLOR:

                return baseStyle +
                        " -fx-background-color: linear-gradient(to right, "
                        + "#ff7675 0%, "
                        + "#74b9ff 25%, "
                        + "#55efc4 50%, "
                        + "#ffeaa7 75%, "
                        + "#a29bfe 100%); "
                        +
                        " -fx-border-color: #636e72;";

            case MONEY:

                return baseStyle +
                        " -fx-background-color: linear-gradient(to bottom, #fff7d6, #f5e6a3); " +
                        " -fx-border-color: #d4ac0d;";

            case ACTION:

                return baseStyle +
                        " -fx-background-color: linear-gradient(to bottom, #e8f4f8, #b8d8e6); " +
                        " -fx-border-color: #2980b9;";

            case BUILDING:

                String name =
                        card.getName().toLowerCase();

                if (name.contains("house")) {

                    return baseStyle +
                            " -fx-background-color: linear-gradient(to bottom, #f5deb3, #deb887); " +
                            " -fx-border-color: #8b4513;";
                }

                else if (name.contains("hotel")) {

                    return baseStyle +
                            " -fx-background-color: linear-gradient(to bottom, #d2b48c, #bc9a6a); " +
                            " -fx-border-color: #654321;";
                }

                break;

            case RENT_BICOLOR:

                if (card instanceof BiColorRentCard rentCard) {

                    return baseStyle +
                            " -fx-background-color: "
                            + rentCard.getSelectedColor().getLightColor()
                            + "; " +
                            " -fx-border-color: "
                            + rentCard.getSelectedColor().getMainColor()
                            + ";";
                }

                break;

            case RENT_WILDCOLOR:

                return baseStyle +
                        " -fx-background-color: linear-gradient(to bottom, #fce4ec, #f8bbd9); " +
                        " -fx-border-color: #e91e63;";
        }

        return baseStyle +
                " -fx-background-color: white; " +
                " -fx-border-color: #cccccc;";
    }

    private String getCardColorBar() {

        switch (card.getCardType()) {

            case PROPERTY_STANDARD:

                if (card instanceof StandardPropertyCard pc) {

                    return pc.getCurrentActiveColor()
                            .getMainColor();
                }

                break;

            case PROPERTY_WILD_BICOLOR:

                if (card instanceof BiColorWildPropertyCard bc) {

                    com.mygame.model.Color[] colors =
                            bc.getPlayableColors()
                                    .toArray(
                                            new com.mygame.model.Color[0]
                                    );

                    return "linear-gradient(to right, "
                            + colors[0].getMainColor()
                            + " 50%, "
                            + colors[1].getMainColor()
                            + " 50%)";
                }

                break;

            case PROPERTY_WILD_MULTICOLOR:

                return "linear-gradient(to right, red, orange, yellow, green, blue, purple)";

            case MONEY:

                return "#d4ac0d";

            case ACTION:

                return "#2980b9";

            case BUILDING:

                return "#8b4513";

            case RENT_BICOLOR:

                if (card instanceof BiColorRentCard rentCard) {

                    com.mygame.model.Color[] colors =
                            rentCard.getValidColors()
                                    .toArray(
                                            new com.mygame.model.Color[0]
                                    );

                    return "linear-gradient(to right, "
                            + colors[0].getMainColor()
                            + " 50%, "
                            + colors[1].getMainColor()
                            + " 50%)";
                }

                break;

            case RENT_WILDCOLOR:

                return "#e91e63";
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

                return "🏨";

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

    public boolean isFaceDown() {
        return faceDown;
    }

    public void setFaceDown(boolean faceDown) {
        this.faceDown = faceDown;
        setGraphic(faceDown ? backContent : cardContent);
    }

    public void playFlip(boolean toFaceDown) {
        playFlip(toFaceDown, null);
    }

    public void playFlip(boolean toFaceDown, Runnable after) {
        if (toFaceDown == this.faceDown) {
            if (after != null) {
                after.run();
            }
            return;
        }
        ScaleTransition t1 = new ScaleTransition(Duration.millis(120), this);
        t1.setFromX(1);
        t1.setToX(0);
        t1.setInterpolator(Interpolator.EASE_IN);
        t1.setOnFinished(e -> setFaceDown(toFaceDown));

        ScaleTransition t2 = new ScaleTransition(Duration.millis(120), this);
        t2.setFromX(0);
        t2.setToX(1);
        t2.setInterpolator(Interpolator.EASE_OUT);

        SequentialTransition seq = new SequentialTransition(t1, t2);
        if (after != null) {
            seq.setOnFinished(e -> after.run());
        }
        seq.play();
    }
}
