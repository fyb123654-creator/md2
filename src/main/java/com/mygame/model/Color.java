package com.mygame.model;

public enum Color {
    DARK_BLUE("#1f4e79", "#dce7f2", "Dark Blue"),
    BLACK("#444444", "#e6e6e6", "Black"),
    BROWN("#8b5a2b", "#f4e4d4", "Brown"),
    LIGHT_BLUE("#6aa9ff", "#e7f1ff", "Light Blue"),
    PINK("#d95fa2", "#fbe3f0", "Pink"),
    ORANGE("#e67e22", "#fce8d6", "Orange"),
    RED("#d64545", "#f9dede", "Red"),
    YELLOW("#d4ac0d", "#f9efc2", "Yellow"),
    GREEN("#2e8b57", "#def3e7", "Green"),
    RAILROAD("#5d6d7e", "#e8edf2", "Railroad"),
    UTILITY("#7d3c98", "#eee3f7", "Utility"),
    WILD("#7f8c8d", "#eceff1", "Wild");

    private final String mainColor;
    private final String lightColor;
    private final String displayName;

    Color(String mainColor, String lightColor, String displayName) {
        this.mainColor = mainColor;
        this.lightColor = lightColor;
        this.displayName = displayName;
    }

    public String getMainColor() {
        return mainColor;
    }

    public String getLightColor() {
        return lightColor;
    }

    public String getDisplayName() {
        return displayName;
    }
}
