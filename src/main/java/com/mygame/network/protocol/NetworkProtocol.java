package com.mygame.network.protocol;

import com.mygame.network.dto.GameStateData;

import java.io.Serializable;

/**
 * Network protocol definition.
 * Used for message transfer between server and client.
 */
public class NetworkProtocol implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum MessageType {
        CONNECT,           // Client connect request
        CONNECT_ACK,       // Connect ack
        GAME_START,        // Game start
        PLAYER_ACTION,     // Player action
        GAME_STATE,        // Game state sync
        TURN_CHANGE,       // Turn change
        GAME_OVER,         // Game over
        CHAT_MESSAGE,      // Chat message
        ERROR,
        ROOM_UPDATE,
        TOGGLE_READY,
        REQUIRE_PAYMENT,       // Server -> client
        PAYMENT_RESPONSE,      // Client -> server
        ASK_JUST_SAY_NO,       // Server -> client
        JUST_SAY_NO_RESPONSE   // Client -> server
    }

    private MessageType type;
    private String playerId;
    private String content;
    private GameStateData gameState;
    private String errorMessage;

    // Empty constructor for deserialization
    public NetworkProtocol() {}

    public static NetworkProtocol connect(String playerId) {
        NetworkProtocol msg = new NetworkProtocol();
        msg.type = MessageType.CONNECT;
        msg.playerId = playerId;
        return msg;
    }

    public static NetworkProtocol connectAck(boolean success, String message) {
        NetworkProtocol msg = new NetworkProtocol();
        msg.type = MessageType.CONNECT_ACK;
        msg.content = success ? "OK" : "ERROR: " + message;
        return msg;
    }

    public static NetworkProtocol gameStart(int playerCount) {
        NetworkProtocol msg = new NetworkProtocol();
        msg.type = MessageType.GAME_START;
        msg.content = String.valueOf(playerCount);
        return msg;
    }

    public static NetworkProtocol playerAction(String playerId, String action) {
        NetworkProtocol msg = new NetworkProtocol();
        msg.type = MessageType.PLAYER_ACTION;
        msg.playerId = playerId;
        msg.content = action;
        return msg;
    }

    public static NetworkProtocol gameState(GameStateData state) {
        NetworkProtocol msg = new NetworkProtocol();
        msg.type = MessageType.GAME_STATE;
        msg.gameState = state;
        return msg;
    }

    public static NetworkProtocol turnChange(int playerIndex) {
        NetworkProtocol msg = new NetworkProtocol();
        msg.type = MessageType.TURN_CHANGE;
        msg.content = String.valueOf(playerIndex);
        return msg;
    }

    public static NetworkProtocol gameOver(String winner) {
        NetworkProtocol msg = new NetworkProtocol();
        msg.type = MessageType.GAME_OVER;
        msg.content = winner;
        return msg;
    }

    public static NetworkProtocol chat(String playerId, String message) {
        NetworkProtocol msg = new NetworkProtocol();
        msg.type = MessageType.CHAT_MESSAGE;
        msg.playerId = playerId;
        msg.content = message;
        return msg;
    }

    public static NetworkProtocol error(String message) {
        NetworkProtocol msg = new NetworkProtocol();
        msg.type = MessageType.ERROR;
        msg.errorMessage = message;
        return msg;
    }

    public static NetworkProtocol roomUpdate(String content) {
        NetworkProtocol msg = new NetworkProtocol();
        msg.type = MessageType.ROOM_UPDATE;
        msg.content = content;
        return msg;
    }

    public static NetworkProtocol toggleReady(boolean ready) {
        NetworkProtocol msg = new NetworkProtocol();
        msg.type = MessageType.TOGGLE_READY;
        msg.content = ready ? "1" : "0";
        return msg;
    }

    // Getters and Setters
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }
    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public GameStateData getGameState() { return gameState; }
    public void setGameState(GameStateData gameState) { this.gameState = gameState; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
