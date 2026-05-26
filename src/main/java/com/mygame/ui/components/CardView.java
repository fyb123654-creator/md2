package com.mygame.ui.components;

import com.mygame.cards.action.*;
import com.mygame.cards.base.Card;
import com.mygame.cards.base.CardType;
import com.mygame.cards.money.MoneyCard;
import com.mygame.cards.property.*;
import com.mygame.cards.rent.*;

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
import javafx.scene.SnapshotParameters;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.transform.Transform;
import javafx.stage.Popup;

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
    private Popup hoverPreviewPopup;

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

        installHoverZoom();
    }

    private void installHoverZoom() {
        // Previous "scale on hover" caused jitter/blur on some layouts because changing the node bounds
        // can trigger enter/exit repeatedly (especially inside HBox/ScrollPane).
        //
        // New approach: show an enlarged preview popup (snapshot) near the cursor.
        // This keeps the original layout stable and makes the preview crisp.
        if (cardContent == null) return;

        setOnMouseEntered(e -> showHoverPreview(e.getScreenX(), e.getScreenY()));
        setOnMouseMoved(e -> moveHoverPreview(e.getScreenX(), e.getScreenY()));
        setOnMouseExited(e -> hideHoverPreview());
    }

    private void showHoverPreview(double screenX, double screenY) {
        if (getScene() == null || getScene().getWindow() == null) return;

        if (hoverPreviewPopup == null) {
            hoverPreviewPopup = new Popup();
            hoverPreviewPopup.setAutoHide(true);
            hoverPreviewPopup.setHideOnEscape(true);
        } else {
            hoverPreviewPopup.getContent().clear();
        }

        double scale = small ? 1.8 : 1.6;
        SnapshotParameters params = new SnapshotParameters();
        params.setTransform(Transform.scale(scale, scale));

        WritableImage img = cardContent.snapshot(params, null);
        ImageView iv = new ImageView(img);
        iv.setSmooth(true);
        iv.setPreserveRatio(true);
        iv.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 18, 0.25, 0, 6);");

        hoverPreviewPopup.getContent().add(iv);
        hoverPreviewPopup.show(getScene().getWindow(), screenX + 18, screenY + 18);
    }

    private void moveHoverPreview(double screenX, double screenY) {
        if (hoverPreviewPopup == null || !hoverPreviewPopup.isShowing()) return;
        hoverPreviewPopup.setX(screenX + 18);
        hoverPreviewPopup.setY(screenY + 18);
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
}
