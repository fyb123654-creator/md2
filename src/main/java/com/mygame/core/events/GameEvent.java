package com.mygame.core.events;

public class GameEvent {
    private final GameEventType type;
    private final String message;

    public GameEvent(GameEventType type, String message) {
        if (type == null) {
            throw new IllegalArgumentException("type cannot be null");
        }
        if (message == null) {
            message = "";
        }
        this.type = type;
        this.message = message;
    }

    public GameEventType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }
}
