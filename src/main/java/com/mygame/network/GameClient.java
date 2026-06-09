package com.mygame.network;

import com.mygame.network.dto.GameStateData;
import com.mygame.network.protocol.NetworkProtocol;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Game client.
 * Connects to server and receives game state updates.
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
    private volatile GameStateData lastGameState;
    private volatile int assignedPlayerIndex = -1;
    private String localPlayerName = "";
    private int localAvatarId = 0;

    public interface OnMessageReceivedListener {
        void onConnected();
        void onConnectFailed(String error);
        void onGameStarted(int playerCount);
        void onGameStateReceived(GameStateData state);
        void onTurnChanged(int playerIndex);
        void onGameOver(String winner);
        void onChatMessage(String playerId, String message);
        void onError(String message);
        void onRequirePayment(int amount, String collectorId);
        void onAskJustSayNo(String sourcePlayer, String actionName);

        default void onRoomUpdate(String content) {
        }
    }

    public GameClient(String serverAddress, int port) {
        this.serverAddress = serverAddress;
        this.port = port;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public void setLocalPlayerName(String localPlayerName) {
        if (localPlayerName == null || localPlayerName.isBlank()) {
            this.localPlayerName = "";
            return;
        }
        this.localPlayerName = localPlayerName.trim();
    }

    public void setLocalAvatarId(int localAvatarId) {
        this.localAvatarId = Math.max(0, localAvatarId);
    }

    public void setListener(OnMessageReceivedListener listener) {
        this.listener = listener;
    }

    public int getAssignedPlayerIndex() {
        return assignedPlayerIndex;
    }

    public void connect() {
        executorService.submit(() -> {
            try {
                socket = new Socket(serverAddress, port);
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());
                connected = true;
                
                // Send connect request
                out.writeObject(NetworkProtocol.connect((localPlayerName == null ? "" : localPlayerName) + "|" + localAvatarId));
                out.flush();
                
                // Receive connect ack
                NetworkProtocol response = (NetworkProtocol) in.readObject();
                if (response.getType() == NetworkProtocol.MessageType.CONNECT_ACK) {
                    if (response.getContent().startsWith("OK")) {
                        assignedPlayerIndex = parseAssignedPlayerIndex(response.getContent());
                        if (listener != null) {
                            listener.onConnected();
                        }
                        
                        // Start listening for messages
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
                close();
            }
        });
    }

    private int parseAssignedPlayerIndex(String content) {
        if (content == null) {
            return -1;
        }
        String[] parts = content.split(":", 2);
        if (parts.length < 2) {
            return -1;
        }
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return -1;
        }
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
                break;
                
            case GAME_START:
                int playerCount = Integer.parseInt(message.getContent());
                if (listener != null) {
                    listener.onGameStarted(playerCount);
                }
                break;
                
            case GAME_STATE:
                lastGameState = message.getGameState();
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
            case REQUIRE_PAYMENT:
                if (listener != null) {
                    String[] parts = message.getContent().split(":", 2);
                    String amountText = parts.length > 0 ? parts[0] : "0";
                    String collector = parts.length > 1 ? parts[1] : "";
                    listener.onRequirePayment(Integer.parseInt(amountText), collector);
                }
                break;

            case ASK_JUST_SAY_NO:
                if (listener != null) {
                    String[] parts = message.getContent().split(":", 2);
                    String source = parts.length > 0 ? parts[0] : "";
                    String action = parts.length > 1 ? parts[1] : "";
                    listener.onAskJustSayNo(source, action);
                }
                break;

            case ROOM_UPDATE:
                if (listener != null) {
                    listener.onRoomUpdate(message.getContent());
                }
                break;
                
            default:
                break;
        }
    }

    public void sendAction(String action) {
        send(NetworkProtocol.playerAction(localPlayerName, action));
    }

    public void sendChat(String message) {
        send(NetworkProtocol.chat(localPlayerName, message));
    }

    public void sendToggleReady(boolean ready) {
        send(NetworkProtocol.toggleReady(ready));
    }

    private void send(NetworkProtocol message) {
        if (!connected) {
            return;
        }
        
        try {
            out.writeObject(message);
            out.reset(); // Prevent ObjectOutputStream memory leak
            out.flush();
        } catch (IOException e) {
            connected = false;
            if (listener != null) {
                listener.onError("Disconnected from server");
            }
            close();
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

    public GameStateData getLastGameState() {
        return lastGameState;
    }
}
