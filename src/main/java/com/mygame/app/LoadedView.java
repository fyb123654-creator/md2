package com.mygame.app;

import javafx.scene.Parent;

public final class LoadedView<T> {
    private final Parent root;
    private final T controller;

    public LoadedView(Parent root, T controller) {
        if (root == null) {
            throw new IllegalArgumentException("root cannot be null");
        }
        this.root = root;
        this.controller = controller;
    }

    public Parent getRoot() {
        return root;
    }

    public T getController() {
        return controller;
    }
}

