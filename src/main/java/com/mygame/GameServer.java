package com.mygame;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
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
                    
                    if (clients.size() >= expectedPlayerCount) {
                        ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                        out.writeObject(NetworkProtocol.connectAck(false, "Game is full"));
                        out.flush();
                        clientSocket.close();
                        continue;
                    }
                    
                    ClientHandler handler = new ClientHandler(clientSocket, clients.size());
                    clients.add(handler);
                    executorService.submit(handler);
                    
                    if (listener != null) {
                        listener.onClientConnected("Player " + (clients.size()));
                    }
                    
                    // 检查是否所有玩家都已连接
                    if (clients.size() == expectedPlayerCount) {
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
        gameManager.startRound();
        
        // 通知所有客户端游戏开始
        broadcast(NetworkProtocol.gameStart(expectedPlayerCount));
        broadcastGameState();
        
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
                    // 处理玩家操作
                    System.out.println("Player " + (playerIndex + 1) + " action: " + message.getContent());
                    // 这里需要解析并执行操作
                    break;
                    
                case CHAT_MESSAGE:
                    // 转发聊天消息
                    broadcast(NetworkProtocol.chat(message.getPlayerId(), message.getContent()));
                    break;
                    
                default:
                    System.out.println("Unknown message type: " + message.getType());
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
