package com.mygame.app;

public final class AppSettings {
    private static final AppSettings INSTANCE = new AppSettings();

    private int offlinePlayerCount = 3;
    private String playerName = "";
    private int avatarId = 0;
    private boolean onboardingShown = false;
    private String lastServerAddress = "";
    private int lastServerPort = 12345;

    private AppSettings() {
    }

    public static AppSettings getInstance() {
        return INSTANCE;
    }

    public int getOfflinePlayerCount() {
        return offlinePlayerCount;
    }

    public void setOfflinePlayerCount(int offlinePlayerCount) {
        this.offlinePlayerCount = offlinePlayerCount;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            this.playerName = "";
            return;
        }
        this.playerName = playerName.trim();
    }

    public int getAvatarId() {
        return avatarId;
    }

    public void setAvatarId(int avatarId) {
        this.avatarId = Math.max(0, avatarId);
    }

    public boolean isOnboardingShown() {
        return onboardingShown;
    }

    public void setOnboardingShown(boolean onboardingShown) {
        this.onboardingShown = onboardingShown;
    }

    public String getLastServerAddress() {
        return lastServerAddress;
    }

    public void setLastServerAddress(String lastServerAddress) {
        if (lastServerAddress == null || lastServerAddress.isBlank()) {
            this.lastServerAddress = "";
            return;
        }
        this.lastServerAddress = lastServerAddress.trim();
    }

    public int getLastServerPort() {
        return lastServerPort;
    }

    public void setLastServerPort(int lastServerPort) {
        if (lastServerPort < 1 || lastServerPort > 65535) {
            return;
        }
        this.lastServerPort = lastServerPort;
    }
}
