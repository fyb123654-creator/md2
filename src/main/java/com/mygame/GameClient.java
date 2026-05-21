package com.mygame;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 游戏客户端类
 * 连接到服务器并接收游戏状态更新
 */
public class GameClient {
    private Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private String serverAddress;
    private int port;
    private boolean connected;
    private ExecutorService executorService;
    private OnMessageReceivedListener listener;

    public interface OnMessageReceivedListener {
        void onConnected();
        void onConnectFailed(String error);
        void onGameStarted(int playerCount);
        void onGameStateReceived(GameStateData state);
        void onTurnChanged(int playerIndex);
        void onGameOver(String winner);
        void onChatMessage(String playerId, String message);
        void onError(String message);
    }

    public GameClient(String serverAddress, int port) {
        this.serverAddress = serverAddress;
        this.port = port;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public void setListener(OnMessageReceivedListener listener) {
        this.listener = listener;
    }

    public void connect() {
        executorService.submit(() -> {
            try {
                socket = new Socket(serverAddress, port);
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());
                connected = true;
                
                // 发送连接请求
                out.writeObject(NetworkProtocol.connect("Player"));
                out.flush();
                
                // 接收连接确认
                NetworkProtocol response = (NetworkProtocol) in.readObject();
                if (response.getType() == NetworkProtocol.MessageType.CONNECT_ACK) {
                    if (response.getContent().startsWith("OK")) {
                        System.out.println("Connected to server: " + response.getContent());
                        if (listener != null) {
                            listener.onConnected();
                        }
                        
                        // 开始监听消息
                        listenForMessages();
                    } else {
                        if (listener != null) {
                            listener.onConnectFailed(response.getContent());
                        }
                        close();
                    }
                }
                
            } catch (IOException | ClassNotFoundException e) {
                if (listener != null) {
                    listener.onConnectFailed("Connection failed: " + e.getMessage());
                }
                e.printStackTrace();
            }
        });
    }

    private void listenForMessages() {
        executorService.submit(() -> {
            while (connected) {
                try {
                    NetworkProtocol message = (NetworkProtocol) in.readObject();
                    handleMessage(message);
                } catch (IOException | ClassNotFoundException e) {
                    if (connected) {
                        if (listener != null) {
                            listener.onError("Disconnected from server");
                        }
                    }
                    break;
                }
            }
        });
    }

    private void handleMessage(NetworkProtocol message) {
        switch (message.getType()) {
            case CONNECT_ACK:
                System.out.println("Connection acknowledged: " + message.getContent());
                break;
                
            case GAME_START:
                int playerCount = Integer.parseInt(message.getContent());
                if (listener != null) {
                    listener.onGameStarted(playerCount);
                }
                break;
                
            case GAME_STATE:
                if (listener != null) {
                    listener.onGameStateReceived(message.getGameState());
                }
                break;
                
            case TURN_CHANGE:
                int playerIndex = Integer.parseInt(message.getContent());
                if (listener != null) {
                    listener.onTurnChanged(playerIndex);
                }
                break;
                
            case GAME_OVER:
                if (listener != null) {
                    listener.onGameOver(message.getContent());
                }
                close();
                break;
                
            case CHAT_MESSAGE:
                if (listener != null) {
                    listener.onChatMessage(message.getPlayerId(), message.getContent());
                }
                break;
                
            case ERROR:
                if (listener != null) {
                    listener.onError(message.getErrorMessage());
                }
                break;
                
            default:
                System.out.println("Unknown message type: " + message.getType());
        }
    }

    public void sendAction(String action) {
        send(NetworkProtocol.playerAction("LocalPlayer", action));
    }

    public void sendChat(String message) {
        send(NetworkProtocol.chat("LocalPlayer", message));
    }

    private void send(NetworkProtocol message) {
        if (!connected) {
            System.out.println("Not connected to server");
            return;
        }
        
        try {
            out.writeObject(message);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        connected = false;
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
            executorService.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isConnected() {
        return connected;
    }
}
