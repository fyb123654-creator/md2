package com.mygame;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 游戏服务器类
 * 处理客户端连接和游戏状态同步
 */
public class GameServer {
    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private List<ClientHandler> clients;
    private GameManager gameManager;
    private int port;
    private boolean running;
    private int expectedPlayerCount;
    private OnGameStateChangeListener listener;

    public interface OnGameStateChangeListener {
        void onStateChanged(GameStateData state);
        void onClientConnected(String playerName);
        void onGameStarted();
        void onGameOver(String winner);
    }

    public GameServer(int port, int playerCount) {
        this.port = port;
        this.expectedPlayerCount = playerCount;
        this.clients = new ArrayList<>();
        this.executorService = Executors.newFixedThreadPool(10);
        this.gameManager = new GameManager();
        gameManager.setPlayerCount(playerCount);
    }

    public void setListener(OnGameStateChangeListener listener) {
        this.listener = listener;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println("Server started on port " + port);
        
        // 接受客户端连接
        executorService.submit(() -> {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected: " + clientSocket.getInetAddress());
                    
                    if (clients.size() >= expectedPlayerCount - 1) {
                        ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                        out.writeObject(NetworkProtocol.connectAck(false, "Game is full"));
                        out.flush();
                        clientSocket.close();
                        continue;
                    }

                    // Host 占据 index 0，远程客户端从 1 开始
                    ClientHandler handler = new ClientHandler(clientSocket, clients.size() + 1);
                    clients.add(handler);
                    executorService.submit(handler);

                    if (listener != null) {
                        listener.onClientConnected("Player " + (clients.size() + 1));
                    }

                    // Host 占 1 个位置，远程客户端需要 expectedPlayerCount - 1 个
                    if (clients.size() == expectedPlayerCount - 1) {
                        startGame();
                    }
                    
                } catch (IOException e) {
                    if (running) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void startGame() {
        System.out.println("All players connected, starting game...");

        // 只通知 Host 所有玩家就位，不广播消息（客户端输出流可能尚未就绪）
        // Host 在 initializeGame 中设置 GameManager 后会广播 gameStart 和初始状态
        if (listener != null) {
            listener.onGameStarted();
        }
    }

    public void broadcastGameState() {
        GameStateData state = GameStateData.fromGameManager(gameManager);
        broadcast(NetworkProtocol.gameState(state));
        
        if (listener != null) {
            listener.onStateChanged(state);
        }
        
        // 检查游戏是否结束
        if (gameManager.isGameOver()) {
            String winner = gameManager.getWinner().getName();
            broadcast(NetworkProtocol.gameOver(winner));
            if (listener != null) {
                listener.onGameOver(winner);
            }
            stop();
        }
    }

    public void broadcast(NetworkProtocol message) {
        for (ClientHandler client : clients) {
            client.send(message);
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
            for (ClientHandler client : clients) {
                client.close();
            }
            executorService.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public void setGameManager(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    /**
     * 客户端处理器
     */
    private class ClientHandler implements Runnable {
        private Socket socket;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private int playerIndex;
        private boolean connected;

        public ClientHandler(Socket socket, int playerIndex) {
            this.socket = socket;
            this.playerIndex = playerIndex;
            this.connected = true;
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());
                
                // 发送连接确认
                out.writeObject(NetworkProtocol.connectAck(true, "Welcome! You are player " + (playerIndex + 1)));
                out.flush();
                
                // 处理客户端消息
                while (connected) {
                    NetworkProtocol message = (NetworkProtocol) in.readObject();
                    handleMessage(message);
                }
            } catch (IOException | ClassNotFoundException e) {
                if (connected) {
                    e.printStackTrace();
                }
            } finally {
                close();
            }
        }

        private void handleMessage(NetworkProtocol message) {
            switch (message.getType()) {
                case CONNECT:
                    System.out.println("Player " + (playerIndex + 1) + " connected");
                    break;

                case PLAYER_ACTION:
                    processPlayerAction(message.getContent());
                    break;

                case CHAT_MESSAGE:
                    // 转发聊天消息
                    broadcast(NetworkProtocol.chat(message.getPlayerId(), message.getContent()));
                    break;

                default:
                    System.out.println("Unknown message type: " + message.getType());
            }
        }

        private void processPlayerAction(String action) {
            System.out.println("Player " + (playerIndex + 1) + " action: " + action);

            // 只处理当前回合玩家的操作
            if (gameManager.getCurrentPlayerIndex() != playerIndex) {
                send(NetworkProtocol.error("It's not your turn!"));
                return;
            }

            try {
                if ("END_TURN".equals(action)) {
                    gameManager.confirmCurrentPlayerTurnEnded();
                    if (gameManager.canAdvanceTurn()) {
                        gameManager.advanceTurn();
                    }
                    broadcastGameState();
                    return;
                }

                // 解析格式: "ACTION:cardId"
                int colonIdx = action.indexOf(':');
                if (colonIdx <= 0) {
                    send(NetworkProtocol.error("Invalid action format: " + action));
                    return;
                }

                String actionType = action.substring(0, colonIdx);
                String cardId = action.substring(colonIdx + 1);

                // 从当前玩家手牌中找到真实 Card 对象
                PlayerManagement currentPlayer = gameManager.getPlayersView().get(playerIndex);
                Card targetCard = null;
                for (Card c : currentPlayer.getHandCardsView()) {
                    if (c.getId().equals(cardId)) {
                        targetCard = c;
                        break;
                    }
                }

                if (targetCard == null) {
                    send(NetworkProtocol.error("Card not found: " + cardId));
                    return;
                }

                switch (actionType) {
                    case "DEPOSIT":
                        gameManager.depositMoneyCard(targetCard);
                        break;

                    case "PLAY_ACTION":
                        if (!(targetCard instanceof ActionCard)) {
                            send(NetworkProtocol.error("Not an action card"));
                            return;
                        }
                        gameManager.playActionCard(targetCard);
                        break;

                    case "PLACE_PROPERTY":
                        if (!(targetCard instanceof PropertyCard propertyCard)) {
                            send(NetworkProtocol.error("Not a property card"));
                            return;
                        }
                        Set<Color> colors = propertyCard.getPlayableColors();
                        if (colors == null || colors.isEmpty()) {
                            send(NetworkProtocol.error("No playable colors"));
                            return;
                        }
                        Color selectedColor = colors.iterator().next();
                        gameManager.placePropertyCard(propertyCard, currentPlayer, selectedColor);
                        break;

                    default:
                        send(NetworkProtocol.error("Unknown action: " + actionType));
                        return;
                }

                broadcastGameState();

            } catch (Exception e) {
                send(NetworkProtocol.error("Action failed: " + e.getMessage()));
                e.printStackTrace();
            }
        }

        public void send(NetworkProtocol message) {
            try {
                if (out != null) {
                    out.writeObject(message);
                    out.flush();
                }
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
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public int getPlayerIndex() {
            return playerIndex;
        }
    }
}
