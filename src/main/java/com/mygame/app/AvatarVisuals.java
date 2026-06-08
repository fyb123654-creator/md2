package com.mygame.app;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.io.InputStream;

public final class AvatarVisuals {
    private static final int AVATAR_COUNT = 5;

    private AvatarVisuals() {
    }

    public static String getAvatarFileName(int avatarId) {
        return "avatar-" + (Math.floorMod(avatarId, AVATAR_COUNT) + 1) + ".png";
    }

    public static String getAvatarResourcePath(int avatarId) {
        return "/images/avatars/" + getAvatarFileName(avatarId);
    }

    public static StackPane createAvatarNode(int avatarId, String fallbackText, double diameter) {
        StackPane container = new StackPane();
        container.setAlignment(Pos.CENTER);
        container.setPrefSize(diameter, diameter);
        container.setMinSize(diameter, diameter);
        container.setMaxSize(diameter, diameter);

        Circle base = new Circle(diameter / 2.0);
        base.setFill(getFallbackColor(avatarId));
        base.setStroke(Color.rgb(255, 255, 255, 0.86));
        base.setStrokeWidth(Math.max(2, diameter * 0.06));

        Image avatarImage = loadAvatarImage(avatarId);
        if (avatarImage != null) {
            ImageView imageView = new ImageView(avatarImage);
            imageView.setFitWidth(diameter - 6);
            imageView.setFitHeight(diameter - 6);
            imageView.setPreserveRatio(false);
            Circle clip = new Circle((diameter - 6) / 2.0);
            clip.setCenterX((diameter - 6) / 2.0);
            clip.setCenterY((diameter - 6) / 2.0);
            imageView.setClip(clip);
            container.getChildren().addAll(base, imageView);
            return container;
        }

        Label fallback = new Label(resolveFallbackText(fallbackText));
        fallback.setStyle("-fx-text-fill: white; -fx-font-weight: 900; -fx-font-size: " + Math.max(12, (int) Math.round(diameter * 0.36)) + "px;");
        container.getChildren().addAll(base, fallback);
        return container;
    }

    private static String resolveFallbackText(String fallbackText) {
        if (fallbackText == null || fallbackText.isBlank()) {
            return "?";
        }
        String value = fallbackText.trim();
        return value.substring(0, 1).toUpperCase();
    }

    private static Image loadAvatarImage(int avatarId) {
        try (InputStream stream = AvatarVisuals.class.getResourceAsStream(getAvatarResourcePath(avatarId))) {
            if (stream == null) {
                return null;
            }
            return new Image(stream);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Color getFallbackColor(int avatarId) {
        return switch (Math.floorMod(avatarId, AVATAR_COUNT)) {
            case 0 -> Color.web("#3b82f6");
            case 1 -> Color.web("#22c55e");
            case 2 -> Color.web("#f59e0b");
            case 3 -> Color.web("#ef4444");
            default -> Color.web("#a855f7");
        };
    }
}
