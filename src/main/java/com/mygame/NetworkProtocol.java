package com.mygame;

import java.io.Serializable;

/**
 * 网络通信协议定义
 * 用于服务器和客户端之间的消息传输
 */
public class NetworkProtocol implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum MessageType {
        CONNECT,           // 客户端连接请求
        CONNECT_ACK,       // 连接确认
        GAME_START,        // 游戏开始
        PLAYER_ACTION,     // 玩家操作
        GAME_STATE,        // 游戏状态同步
        TURN_CHANGE,       // 回合变更
        GAME_OVER,         // 游戏结束
        CHAT_MESSAGE,      // 聊天消息
        ERROR              // 错误消息
    }

    private MessageType type;
    private String playerId;
    private String content;
    private GameStateData gameState;
    private String errorMessage;

    // 空构造函数用于反序列化
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