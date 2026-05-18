package com.mygame;

public enum Color {
    DARK_BLUE("#1f4e79", "#dce7f2", "深蓝色"),
    BLACK("#444444", "#e6e6e6", "黑色"),
    BROWN("#8b5a2b", "#f4e4d4", "棕色"),
    LIGHT_BLUE("#6aa9ff", "#e7f1ff", "浅蓝色"),
    PINK("#d95fa2", "#fbe3f0", "粉色"),
    ORANGE("#e67e22", "#fce8d6", "橙色"),
    RED("#d64545", "#f9dede", "红色"),
    YELLOW("#d4ac0d", "#f9efc2", "黄色"),
    GREEN("#2e8b57", "#def3e7", "绿色"),
    RAILROAD("#5d6d7e", "#e8edf2", "铁路"),
    UTILITY("#7d3c98", "#eee3f7", "公共事业"),
    WILD("#7f8c8d", "#eceff1", "百搭");

    private final String mainColor;
    private final String lightColor;
    private final String chineseName;

    Color(String mainColor, String lightColor, String chineseName) {
        this.mainColor = mainColor;
        this.lightColor = lightColor;
        this.chineseName = chineseName;
    }

    public String getMainColor() {
        return mainColor;
    }

    public String getLightColor() {
        return lightColor;
    }

    public String getChineseName() {
        return chineseName;
    }
}