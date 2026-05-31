package com.mygame.app;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

public final class ViewLoader {
    private ViewLoader() {
    }

    public static <T> LoadedView<T> load(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(ViewLoader.class.getResource(fxmlPath));
            Parent root = loader.load();
            @SuppressWarnings("unchecked")
            T controller = (T) loader.getController();
            return new LoadedView<>(root, controller);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load view: " + fxmlPath, e);
        }
    }
}

