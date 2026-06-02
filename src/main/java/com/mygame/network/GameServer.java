package com.mygame.network;

import com.mygame.cards.action.*;
import com.mygame.cards.base.*;
import com.mygame.cards.money.*;
import com.mygame.cards.property.*;
import com.mygame.cards.rent.*;
import com.mygame.core.*;
import com.mygame.core.deck.*;
import com.mygame.core.pending.*;
import com.mygame.model.*;
import com.mygame.network.dto.GameStateData;
import com.mygame.network.protocol.NetworkProtocol;
import com.mygame.rules.*;
import com.mygame.ui.GameController;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GameServer {
    private static final Logger LOGGER = Logger.getLogger(GameServer.class.getName());
    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private final Map<Integer, ClientHandler> clientsByIndex;
    private final Object clientsLock = new Object();
    private GameManager gameManager;
    private int port;
    private boolean running;
    private int expectedPlayerCount;
    private OnGameStateChangeListener listener;
    private volatile GameStateData lastBroadcastState;
    private final boolean[] readyFlags;
    private final String[] playerNames;
    private final int[] playerAvatarIds;

    // Payment flow state (single payment)
    private boolean isWaitingForJsn = false;
    private boolean isWaitingForPayment = false;
    private String pendingVictimId = null;
    private String pendingCollectorId = null;
    private int pendingPaymentAmount = 0;
    private String pendingActionName = "";
    private String pendingPaymentJsnResponderId = null;
    private boolean pendingPaymentCanceledByJsn = false;

    // Batch payment queue (used by ItsMyBirthday, etc.)
    private List<String> pendingPaymentQueue = null;
    private int currentPaymentIndex = 0;
    private String batchCollectorId = null;

    // Action-card Just Say No waiting state. This must be server-wide because the
    // response is received by the target player's ClientHandler, not the action
    // player's ClientHandler that created the callbacks.
    private boolean isWaitingForJsnAction = false;
    private PlayerManagement pendingJsnVictim = null;
    private PlayerManagement pendingJsnSource = null;
    private PlayerManagement pendingJsnResponder = null;
    private String pendingJsnActionName = "";
    private boolean pendingJsnActionCanceled = false;
    private Runnable pendingJsnSuccessCallback = null;
    private Runnable pendingJsnCancelCallback = null;

    private void clearActionJustSayNoState() {
        isWaitingForJsnAction = false;
        pendingJsnVictim = null;
        pendingJsnSource = null;
        pendingJsnResponder = null;
        pendingJsnActionName = "";
        pendingJsnActionCanceled = false;
        pendingJsnSuccessCallback = null;
        pendingJsnCancelCallback = null;
    }

    // ---------------- Payment / waiting state ----------------
    private void clearSinglePaymentState() {
        isWaitingForJsn = false;
        isWaitingForPayment = false;
        pendingVictimId = null;
        pendingCollectorId = null;
        pendingPaymentAmount = 0;
        pendingActionName = "";
        pendingPaymentJsnResponderId = null;
        pendingPaymentCanceledByJsn = false;
    }

    private void clearBatchState() {
        pendingPaymentQueue = null;
        batchCollectorId = null;
        currentPaymentIndex = 0;
    }

    public interface OnGameStateChangeListener {
        void onStateChanged(GameStateData state);
        void onClientConnected(String playerName);
        void onGameStarted();
        void onGameOver(String winner);
        default void onChatMessage(String playerId, String message) {}
    }

    public GameServer(int port, int playerCount, String hostName, int hostAvatarId) {
        this.port = port;
        this.expectedPlayerCount = playerCount;
        this.clientsByIndex = new HashMap<>();
        this.executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        this.gameManager = new GameManager();
        this.readyFlags = new boolean[playerCount];
        this.playerNames = new String[playerCount];
        this.playerAvatarIds = new int[playerCount];
        // Host is Player 1 (index 0)
        this.readyFlags[0] = false;
        this.playerNames[0] = (hostName == null || hostName.isBlank()) ? "Player1" : hostName.trim();
        this.playerAvatarIds[0] = Math.max(0, hostAvatarId);
    }

    public void setListener(OnGameStateChangeListener listener) {
        this.listener = listener;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        executorService.submit(() -> {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    if (gameManager != null && gameManager.isGameStarted()) {
                        ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                        out.writeObject(NetworkProtocol.connectAck(false, "Game already started"));
                        out.flush();
                        clientSocket.close();
                        continue;
                    }
                    int assignedIndex = -1;
                    synchronized (clientsLock) {
                        if (clientsByIndex.size() < expectedPlayerCount - 1) {
                            for (int i = 1; i < expectedPlayerCount; i++) {
                                if (!clientsByIndex.containsKey(i)) {
                                    assignedIndex = i;
                                    break;
                                }
                            }
                        }
                    }
                    if (assignedIndex < 0) {
                        ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                        out.writeObject(NetworkProtocol.connectAck(false, "Game is full"));
                        out.flush();
                        clientSocket.close();
                        continue;
                    }
                    ClientHandler handler = new ClientHandler(clientSocket, assignedIndex);
                    synchronized (clientsLock) {
                        clientsByIndex.put(assignedIndex, handler);
                    }
                    executorService.submit(handler);
                    broadcastRoomUpdate();
                } catch (IOException e) {
                    if (running) LOGGER.log(Level.WARNING, "Error accepting client connection", e);
                }
            }
        });
    }

    private void startGameInternal() {
        // Server-side start: start round, broadcast GAME_START and initial state
        try {
            List<String> effectiveNames = new ArrayList<>();
            for (int i = 0; i < expectedPlayerCount; i++) {
                String name = playerNames[i];
                if (name == null || name.isBlank()) {
                    name = "Player" + (i + 1);
                }
                effectiveNames.add(name);
            }
            gameManager.setPlayerCount(expectedPlayerCount, effectiveNames);
            for (int i = 0; i < expectedPlayerCount && i < gameManager.getPlayersView().size(); i++) {
                gameManager.getPlayersView().get(i).setAvatarId(playerAvatarIds[i]);
            }
            // Ensure startRound was called at least once
            if (gameManager.getCardManager() == null) {
                gameManager.startRound();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to start game", e);
        }

        broadcast(NetworkProtocol.gameStart(expectedPlayerCount));
        broadcastGameState();
        if (listener != null) listener.onGameStarted();
    }

    public void setHostReady(boolean ready) {
        readyFlags[0] = ready;
        broadcastRoomUpdate();
    }

    public boolean canStartGame() {
        // All clients connected
        if (getConnectedClientCount() != expectedPlayerCount - 1) {
            return false;
        }
        // All players ready
        for (int i = 0; i < expectedPlayerCount; i++) {
            if (!readyFlags[i]) return false;
        }
        return true;
    }

    public boolean startGameManually() {
        if (!canStartGame()) {
            return false;
        }
        startGameInternal();
        return true;
    }

    private void broadcastRoomUpdate() {
        // Format: "P1=1,P2=0,P3=1"
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < expectedPlayerCount; i++) {
            if (i > 0) sb.append(",");
            sb.append("P").append(i + 1).append("=").append(readyFlags[i] ? "1" : "0");
        }
        broadcast(NetworkProtocol.roomUpdate(sb.toString()));
        if (listener != null) {
            // Optionally push state to host UI too
            listener.onClientConnected("RoomUpdate");
        }
    }

    public void broadcastGameState() {
        GameStateData state = GameStateData.fromGameManager(gameManager);
        lastBroadcastState = state;
        broadcast(NetworkProtocol.gameState(state));
        if (listener != null) listener.onStateChanged(state);
        if (gameManager.isGameOver()) {
            String winner = gameManager.getWinner().getName();
            broadcast(NetworkProtocol.gameOver(winner));
            if (listener != null) listener.onGameOver(winner);
            stop();
        }
    }

    public void handleHostLeaving() {
        if (gameManager == null) {
            stop();
            return;
        }
        if (gameManager.isGameOver()) {
            stop();
            return;
        }
        if (gameManager.isGameStarted()) {
            if (!gameManager.getPlayersView().isEmpty()) {
                PlayerManagement host = gameManager.getPlayersView().get(0);
                if (!host.isEliminated()) {
                    List<Card> returned = host.removeAllCards();
                    CardManager cm = gameManager.getCardManager();
                    if (cm != null) {
                        for (Card c : returned) {
                            cm.addToTopOfDrawPile(c);
                        }
                    }
                    gameManager.eliminatePlayer(0);
                    clearSinglePaymentState();
                    clearBatchState();
                    clearActionJustSayNoState();
                }
            }
            broadcastGameState();
            if (!gameManager.isGameOver()) {
                broadcast(NetworkProtocol.error("Host left the game."));
                stop();
            }
            return;
        }

        broadcast(NetworkProtocol.error("Host left the game."));
        stop();
    }

    public GameStateData getLastBroadcastState() {
        return lastBroadcastState;
    }

    public void broadcast(NetworkProtocol message) {
        for (ClientHandler client : getClientSnapshot()) {
            client.send(message);
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
            for (ClientHandler client : getClientSnapshot()) {
                client.close();
            }
            executorService.shutdown();
        } catch (IOException e) { LOGGER.log(Level.WARNING, "Error closing server resources", e); }
    }

    private int getConnectedClientCount() {
        synchronized (clientsLock) {
            return clientsByIndex.size();
        }
    }

    private List<ClientHandler> getClientSnapshot() {
        synchronized (clientsLock) {
            return new ArrayList<>(clientsByIndex.values());
        }
    }

    private void unregisterClient(int index) {
        if (index <= 0) {
            return;
        }
        boolean removed;
        synchronized (clientsLock) {
            removed = clientsByIndex.remove(index) != null;
        }
        if (removed && gameManager != null && gameManager.isGameStarted() && !gameManager.isGameOver()) {
            if (index < gameManager.getPlayersView().size()) {
                PlayerManagement leaver = gameManager.getPlayersView().get(index);
                if (!leaver.isEliminated()) {
                    boolean continueBatch = false;
                    
                    if (isWaitingForJsnAction && pendingJsnResponder != null && leaver.getPlayerId().equals(pendingJsnResponder.getPlayerId())) {
                        Runnable callback = pendingJsnActionCanceled ? pendingJsnCancelCallback : pendingJsnSuccessCallback;
                        clearActionJustSayNoState();
                        if (callback != null) callback.run();
                    } else {
                        clearActionJustSayNoState();
                    }
                    
                    if ((isWaitingForPayment || isWaitingForJsn) && pendingVictimId != null && leaver.getPlayerId().equals(pendingVictimId)) {
                        PlayerManagement collector = findPlayerById(pendingCollectorId);
                        if (collector != null && !collector.isEliminated()) {
                            leaver.transferAllAssetsTo(collector);
                        }
                        clearSinglePaymentState();
                        continueBatch = true;
                    } else {
                        clearSinglePaymentState();
                        clearBatchState();
                    }

                    List<Card> returned = leaver.removeAllCards();
                    CardManager cm = gameManager.getCardManager();
                    if (cm != null) {
                        for (Card c : returned) {
                            cm.addToTopOfDrawPile(c);
                        }
                    }
                    gameManager.eliminatePlayer(index);
                    
                    if (continueBatch && pendingPaymentQueue != null) {
                        currentPaymentIndex++;
                        processNextPaymentInBatch();
                    } else {
                        broadcastGameState();
                    }
                }
            }
        }
        if (removed && index < readyFlags.length) {
            readyFlags[index] = false;
        }
        if (removed && index < playerNames.length) {
            playerNames[index] = null;
        }
        if (removed && index < playerAvatarIds.length) {
            playerAvatarIds[index] = 0;
        }
        if (removed) {
            broadcastRoomUpdate();
        }
    }

    public GameManager getGameManager() { return gameManager; }
    public void setGameManager(GameManager gameManager) { this.gameManager = gameManager; }

    // Entry for host direct invocation
    public void processHostAction(String action) {
        ClientHandler hostHandler = new ClientHandler(null, 0) {
            @Override
            public void send(NetworkProtocol message) {
                if (message.getType() == NetworkProtocol.MessageType.ERROR) {
                    javafx.application.Platform.runLater(() ->
                            GameController.getInstance().showError(message.getErrorMessage()));
                }
            }
        };
        hostHandler.processPlayerAction(action);
    }

    // Batch payment: initialize queue
    private void initiateBatchPayment(PlayerManagement collector, List<PlayerManagement> victims, int amount, String actionName) {
        pendingPaymentQueue = new ArrayList<>();
        for (PlayerManagement v : victims) pendingPaymentQueue.add(v.getPlayerId());
        batchCollectorId = collector.getPlayerId();
        currentPaymentIndex = 0;
        pendingPaymentAmount = amount;
        pendingActionName = actionName;
        processNextPaymentInBatch();
    }

    private void processNextPaymentInBatch() {
        if (pendingPaymentQueue == null || currentPaymentIndex >= pendingPaymentQueue.size()) {
            // Batch payment complete
            clearSinglePaymentState();
            clearBatchState();
            broadcastGameState();
            return;
        }
        String victimId = pendingPaymentQueue.get(currentPaymentIndex);
        PlayerManagement victim = findPlayerById(victimId);
        PlayerManagement collector = findPlayerById(batchCollectorId);
        if (victim == null || collector == null) {
            currentPaymentIndex++;
            processNextPaymentInBatch();
            return;
        }
        // Single payment flow
        initiatePaymentAgainstVictim(collector, victim, pendingPaymentAmount, pendingActionName);
    }

    private void initiatePaymentAgainstVictim(PlayerManagement collector, PlayerManagement victim, int amount, String actionName) {
        isWaitingForJsn = true;
        isWaitingForPayment = false;
        pendingVictimId = victim.getPlayerId();
        pendingCollectorId = collector.getPlayerId();
        pendingPaymentAmount = amount;
        pendingActionName = actionName;
        pendingPaymentJsnResponderId = victim.getPlayerId();
        pendingPaymentCanceledByJsn = false;
        NetworkProtocol req = new NetworkProtocol();
        req.setType(NetworkProtocol.MessageType.ASK_JUST_SAY_NO);
        req.setContent(collector.getName() + ":" + actionName);
        sendToPlayer(victim.getPlayerId(), req);
    }

    private void sendToPlayer(String targetPlayerId, NetworkProtocol msg) {
        PlayerManagement hostPlayer = gameManager.getPlayersView().get(0);
        if (hostPlayer.getPlayerId().equals(targetPlayerId)) {
            javafx.application.Platform.runLater(() -> {
                if (msg.getType() == NetworkProtocol.MessageType.ASK_JUST_SAY_NO) {
                    String[] parts = msg.getContent().split(":");
                    GameController.getInstance().handleAskJustSayNo(parts[0], parts[1]);
                } else if (msg.getType() == NetworkProtocol.MessageType.REQUIRE_PAYMENT) {
                    String[] parts = msg.getContent().split(":");
                    GameController.getInstance().handleRequirePayment(Integer.parseInt(parts[0]), parts[1]);
                }
            });
            return;
        }
        for (ClientHandler client : getClientSnapshot()) {
            PlayerManagement p = gameManager.getPlayersView().get(client.getPlayerIndex());
            if (p.getPlayerId().equals(targetPlayerId)) {
                client.send(msg);
                break;
            }
        }
    }

    private PlayerManagement findPlayerById(String id) {
        for (PlayerManagement p : gameManager.getPlayersView()) {
            if (p.getPlayerId().equals(id)) return p;
        }
        return null;
    }

    private Card findCardInHand(PlayerManagement player, String cardId) {
        for (Card c : player.getHandCardsView()) {
            if (c.getId().equals(cardId)) return c;
        }
        return null;
    }

    private Card findPropertyCardById(PlayerManagement player, String cardId) {
        if (player == null) return null;
        for (PropertyZone zone : player.getPropertyZonesView().values()) {
            for (PropertyCard pc : zone.getPropertiesView()) {
                if (pc.getId().equals(cardId)) return pc;
            }
        }
        return null;
    }

    private Color findColorOfProperty(PlayerManagement player, Card card) {
        return player.findColorOfProperty(card);
    }

    private Card findJustSayNoCard(PlayerManagement player) {
        return player.findJustSayNoCard();
    }

    private class ClientHandler implements Runnable {
        private Socket socket;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private int playerIndex;
        private boolean connected;
        private boolean registered;

        public ClientHandler(Socket socket, int playerIndex) {
            this.socket = socket;
            this.playerIndex = playerIndex;
            this.connected = true;
            this.registered = false;

            // Initialize streams in constructor to avoid early message loss
            if (socket != null) {
                try {
                    out = new ObjectOutputStream(socket.getOutputStream());
                    in = new ObjectInputStream(socket.getInputStream());
                    out.writeObject(NetworkProtocol.connectAck(true, String.valueOf(playerIndex)));
                    out.flush();
                } catch (IOException e) {
                    connected = false;
                    LOGGER.log(Level.WARNING, "Failed to initialize streams for player " + playerIndex, e);
                }
            }
            if (playerIndex >= 0 && playerIndex < readyFlags.length) {
                readyFlags[playerIndex] = false;
            }
        }

        @Override
        public void run() {
            // hostHandler (socket == null) does not use network thread
            if (socket == null) {
                return;
            }

            try {
                while (connected) {
                    NetworkProtocol message = (NetworkProtocol) in.readObject();
                    handleMessage(message);
                }
            } catch (IOException | ClassNotFoundException e) {
                if (connected) {
                    String name = playerIndex >= 0 && playerIndex < playerNames.length ? playerNames[playerIndex] : String.valueOf(playerIndex);
                    LOGGER.log(Level.WARNING, "Connection error for player " + name, e);
                }
            } finally {
                close();
            }
        }

        private void handleMessage(NetworkProtocol message) {
            switch (message.getType()) {
                case CONNECT:
                    String payload = message.getContent();
                    String name;
                    int avatarId = playerIndex;
                    if (payload == null) {
                        name = "";
                    } else {
                        payload = payload.trim();
                        String[] parts = payload.split("\\|", 2);
                        name = parts.length > 0 ? parts[0] : "";
                        if (parts.length == 2) {
                            try {
                                avatarId = Integer.parseInt(parts[1].trim());
                            } catch (NumberFormatException ignored) {
                                avatarId = playerIndex;
                            }
                        }
                    }
                    if (name == null || name.isBlank()) {
                        name = "Player" + (playerIndex + 1);
                    } else {
                        name = name.trim();
                    }
                    avatarId = Math.max(0, avatarId);
                    if (playerIndex >= 0 && playerIndex < playerNames.length) {
                        playerNames[playerIndex] = name;
                        playerAvatarIds[playerIndex] = avatarId;
                    }
                    if (!registered) {
                        registered = true;
                        if (listener != null) {
                            listener.onClientConnected(name);
                        }
                        broadcastRoomUpdate();
                    }
                    break;
                case PLAYER_ACTION:
                    processPlayerAction(message.getContent());
                    break;
                case CHAT_MESSAGE:
                    broadcast(NetworkProtocol.chat(message.getPlayerId(), message.getContent()));
                    if (listener != null) {
                        listener.onChatMessage(message.getPlayerId(), message.getContent());
                    }
                    break;
                case TOGGLE_READY:
                    handleToggleReady(message.getContent());
                    break;
                case JUST_SAY_NO_RESPONSE:
                    handleJustSayNoResponse(message.getContent(), gameManager.getPlayersView().get(playerIndex));
                    break;
                case PAYMENT_RESPONSE:
                    handlePaymentResponse(message.getContent(), gameManager.getPlayersView().get(playerIndex));
                    break;
                default:
                    break;
            }
        }

        private void askForJustSayNo(PlayerManagement victim, PlayerManagement sourcePlayer, String actionName,
                                     Runnable successCallback, Runnable cancelCallback) {
            if (findJustSayNoCard(victim) == null) {
                successCallback.run();
                return;
            }
            isWaitingForJsnAction = true;
            pendingJsnVictim = victim;
            pendingJsnSource = sourcePlayer;
            pendingJsnResponder = victim;
            pendingJsnActionName = actionName;
            pendingJsnActionCanceled = false;
            pendingJsnSuccessCallback = successCallback;
            pendingJsnCancelCallback = cancelCallback;
            sendActionJustSayNoPrompt(sourcePlayer, actionName);
        }

        private void sendActionJustSayNoPrompt(PlayerManagement sourcePlayer, String actionName) {
            NetworkProtocol req = new NetworkProtocol();
            req.setType(NetworkProtocol.MessageType.ASK_JUST_SAY_NO);
            req.setContent(sourcePlayer.getName() + ":" + actionName);
            sendToPlayer(pendingJsnResponder.getPlayerId(), req);
        }

        private void handleJustSayNoResponse(String content, PlayerManagement responder) {
            if (!isWaitingForJsnAction || pendingJsnVictim == null || pendingJsnResponder == null) {
                return;
            }
            if (!responder.getPlayerId().equals(pendingJsnResponder.getPlayerId())) {
                send(NetworkProtocol.error("Waiting for another player's response"));
                return;
            }

            String[] parts = content.split(":");
            if (parts.length < 1) {
                send(NetworkProtocol.error("Invalid response format"));
                return;
            }
            String answer = parts[0];
            String cardId = parts.length > 1 ? parts[1] : null;
            if ("YES".equals(answer) && cardId != null) {
                Card card = findCardInHand(responder, cardId);
                if (card != null) {
                    responder.removeFromHand(card);
                    gameManager.getCardManager().playCard(card);

                    pendingJsnActionCanceled = responder.getPlayerId().equals(pendingJsnVictim.getPlayerId());
                    PlayerManagement nextResponder = pendingJsnActionCanceled ? pendingJsnSource : pendingJsnVictim;
                    if (findJustSayNoCard(nextResponder) != null) {
                        pendingJsnResponder = nextResponder;
                        sendActionJustSayNoPrompt(responder, "Just Say No");
                        broadcastGameState();
                        return;
                    }
                }
            }

            Runnable callback = pendingJsnActionCanceled ? pendingJsnCancelCallback : pendingJsnSuccessCallback;
            clearActionJustSayNoState();
            if (callback != null) {
                callback.run();
            }
        }

        private void handlePaymentResponse(String cardIds, PlayerManagement victim) {
            PlayerManagement collector = findPlayerById(pendingCollectorId);
            if (collector == null) {
                clearSinglePaymentState();
                clearBatchState();
                broadcastGameState();
                return;
            }

            // NONE: treat as unable to pay (bankrupt), transfer all assets to collector
            if ("NONE".equals(cardIds)) {
                transferAllAssetsToCollectorHand(collector, victim);
            } else {
                String[] ids = cardIds.split(",");
                int selectedValue = 0;
                List<Card> selectedCards = new ArrayList<>();
                for (String id : ids) {
                    Card c = findAssetCardById(victim, id);
                    if (c != null) {
                        selectedCards.add(c);
                        selectedValue += c.getValue();
                    }
                }
                if (selectedValue < pendingPaymentAmount) {
                    send(NetworkProtocol.error("Selected payment is less than required"));
                    NetworkProtocol req = new NetworkProtocol();
                    req.setType(NetworkProtocol.MessageType.REQUIRE_PAYMENT);
                    req.setContent(pendingPaymentAmount + ":" + collector.getName());
                    sendToPlayer(victim.getPlayerId(), req);
                    return;
                }
                for (Card c : selectedCards) {
                    if (victim.removeFromBank(c) || victim.removeFromPropertyZones(c)) {
                        collector.addToHand(c);
                    }
                }
            }

            // End single payment: clear waiting state only. Batch continuation handled below.
            clearSinglePaymentState();

            // If in batch payment, continue to next victim
            if (pendingPaymentQueue != null) {
                currentPaymentIndex++;
                processNextPaymentInBatch();
            } else {
                broadcastGameState();
            }
        }

        private void resolvePaymentAfterJustSayNoDeclined() {
            PlayerManagement collector = findPlayerById(pendingCollectorId);
            PlayerManagement victim = findPlayerById(pendingVictimId);
            if (collector == null || victim == null) {
                clearSinglePaymentState();
                continueBatchOrBroadcast();
                return;
            }

            int totalAssetValue = calculateAssetTotalValue(victim);
            if (totalAssetValue <= pendingPaymentAmount) {
                transferAllAssetsToCollectorHand(collector, victim);
                clearSinglePaymentState();
                continueBatchOrBroadcast();
                return;
            }

            isWaitingForJsn = false;
            isWaitingForPayment = true;
            NetworkProtocol req = new NetworkProtocol();
            req.setType(NetworkProtocol.MessageType.REQUIRE_PAYMENT);
            req.setContent(pendingPaymentAmount + ":" + collector.getName());
            sendToPlayer(pendingVictimId, req);
        }

        private void continueBatchOrBroadcast() {
            if (pendingPaymentQueue != null) {
                currentPaymentIndex++;
                processNextPaymentInBatch();
            } else {
                broadcastGameState();
            }
        }

        private int calculateAssetTotalValue(PlayerManagement player) {
            return player.calculateAssetTotalValue();
        }

        private void transferAllAssetsToCollectorHand(PlayerManagement collector, PlayerManagement victim) {
            victim.transferAllAssetsTo(collector);
        }

        private void handleToggleReady(String content) {
            boolean ready = "1".equals(content) || "true".equalsIgnoreCase(content);
            if (playerIndex >= 0 && playerIndex < readyFlags.length) {
                readyFlags[playerIndex] = ready;
            }
            broadcastRoomUpdate();
        }

        private Card findAssetCardById(PlayerManagement player, String cardId) {
            for (Card c : player.getBankCardsView()) {
                if (c.getId().equals(cardId)) return c;
            }
            for (PropertyZone zone : player.getPropertyZonesView().values()) {
                for (PropertyCard pc : zone.getPropertiesView()) {
                    if (pc.getId().equals(cardId)) return pc;
                }
                if (zone.getHouse() != null && zone.getHouse().getId().equals(cardId)) return zone.getHouse();
                if (zone.getHotel() != null && zone.getHotel().getId().equals(cardId)) return zone.getHotel();
            }
            return null;
        }

        // ========== Core: handle player actions ==========
        private void processPlayerAction(String action) {
            PlayerManagement currentPlayer = gameManager.getPlayersView().get(playerIndex);

            if (waitingForJsnAction(action, currentPlayer)) return;
            if (waitingForPaymentResponse(action, currentPlayer)) return;

            if (gameManager.getCurrentPlayerIndex() != playerIndex) {
                send(NetworkProtocol.error("It's not your turn!"));
                return;
            }

            try {
                if ("END_TURN".equals(action)) {
                    handleEndTurn(currentPlayer);
                    return;
                }

                String[] parts = action.split(":");
                if (parts.length < 2) {
                    send(NetworkProtocol.error("Invalid action format"));
                    return;
                }

                String actionType = parts[0];
                String cardId = parts[1];

                if ("SWITCH_PROPERTY_COLOR".equals(actionType)) {
                    handleSwitchPropertyColor(parts, currentPlayer, cardId);
                    return;
                }

                if (!"DISCARD".equals(actionType) && !gameManager.canCurrentPlayerPlayCard()) {
                    send(NetworkProtocol.error("You have already played the maximum number of cards this turn"));
                    return;
                }

                Card targetCard = findCardInHand(currentPlayer, cardId);
                if (targetCard == null) {
                    send(NetworkProtocol.error("Card not found"));
                    return;
                }

                dispatchCardAction(actionType, parts, currentPlayer, targetCard);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Action failed: " + e.getMessage(), e);
                send(NetworkProtocol.error("Action failed: " + e.getMessage()));
            }
        }

        /** Returns true if the action was consumed by JSN-action waiting. */
        private boolean waitingForJsnAction(String action, PlayerManagement currentPlayer) {
            if (!isWaitingForJsnAction) return false;

            if (pendingJsnResponder == null || !currentPlayer.getPlayerId().equals(pendingJsnResponder.getPlayerId())) {
                send(NetworkProtocol.error("Waiting for another player's response"));
                return true;
            }
            if (action.startsWith("JUST_SAY_NO_RESPONSE:")) {
                handleJustSayNoResponse(action.substring(21), currentPlayer);
            } else {
                send(NetworkProtocol.error("Invalid response format"));
            }
            return true;
        }

        /** Returns true if the action was consumed by payment-flow waiting. */
        private boolean waitingForPaymentResponse(String action, PlayerManagement currentPlayer) {
            if (!isWaitingForJsn && !isWaitingForPayment) return false;

            String expectedResponderId = isWaitingForJsn ? pendingPaymentJsnResponderId : pendingVictimId;
            if (!currentPlayer.getPlayerId().equals(expectedResponderId)) {
                send(NetworkProtocol.error("Waiting for another player's response"));
                return true;
            }
            if (isWaitingForJsn && action.startsWith("JUST_SAY_NO_RESPONSE:")) {
                handlePaymentJustSayNo(action, currentPlayer);
                return true;
            }
            if (isWaitingForPayment && action.startsWith("PAYMENT_RESPONSE:")) {
                handlePaymentResponse(action.substring(17), currentPlayer);
                return true;
            }
            send(NetworkProtocol.error("You must respond to the payment request"));
            return true;
        }

        private void handlePaymentJustSayNo(String action, PlayerManagement currentPlayer) {
            String[] parts = action.split(":");
            if (parts.length < 2) {
                send(NetworkProtocol.error("Invalid response format"));
                return;
            }
            if ("YES".equals(parts[1])) {
                if (parts.length < 3) {
                    send(NetworkProtocol.error("Missing card ID for Just Say No"));
                    return;
                }
                Card jsnCard = findCardInHand(currentPlayer, parts[2]);
                if (jsnCard != null) {
                    currentPlayer.removeFromHand(jsnCard);
                    gameManager.getCardManager().playCard(jsnCard);

                    pendingPaymentCanceledByJsn = currentPlayer.getPlayerId().equals(pendingVictimId);
                    PlayerManagement nextResponder = pendingPaymentCanceledByJsn
                            ? findPlayerById(pendingCollectorId)
                            : findPlayerById(pendingVictimId);
                    if (nextResponder != null && findJustSayNoCard(nextResponder) != null) {
                        pendingPaymentJsnResponderId = nextResponder.getPlayerId();
                        NetworkProtocol req = new NetworkProtocol();
                        req.setType(NetworkProtocol.MessageType.ASK_JUST_SAY_NO);
                        req.setContent(currentPlayer.getName() + ":Just Say No");
                        sendToPlayer(nextResponder.getPlayerId(), req);
                        broadcastGameState();
                        return;
                    }
                }
                if (pendingPaymentCanceledByJsn) {
                    clearSinglePaymentState();
                    continueBatchOrBroadcast();
                } else {
                    resolvePaymentAfterJustSayNoDeclined();
                }
            } else {
                if (pendingPaymentCanceledByJsn) {
                    clearSinglePaymentState();
                    continueBatchOrBroadcast();
                } else {
                    resolvePaymentAfterJustSayNoDeclined();
                }
            }
        }

        private void handleEndTurn(PlayerManagement currentPlayer) {
            gameManager.confirmCurrentPlayerTurnEnded();
            if (gameManager.canAdvanceTurn()) {
                gameManager.advanceTurn();
                broadcastGameState();
                return;
            }
            int handCount = currentPlayer.getHandCardCount();
            if (handCount > PlayerManagement.MAX_HAND_SIZE) {
                send(NetworkProtocol.error("Hand limit exceeded. Discard until you have 7 or fewer cards."));
            } else {
                send(NetworkProtocol.error("Cannot end turn right now."));
            }
            broadcastGameState();
        }

        private void handleSwitchPropertyColor(String[] parts, PlayerManagement currentPlayer, String cardId) {
            if (parts.length < 3) {
                send(NetworkProtocol.error("Invalid property color switch format"));
                return;
            }
            Card propertyCard = findPropertyCardById(currentPlayer, cardId);
            if (!(propertyCard instanceof PropertyCard pc)) {
                send(NetworkProtocol.error("Property card not found"));
                return;
            }
            if (!(propertyCard instanceof BiColorWildPropertyCard || propertyCard instanceof MultiColorWildPropertyCard)) {
                send(NetworkProtocol.error("Only wild property cards can switch color"));
                return;
            }
            Color targetColor = Color.valueOf(parts[2]);
            if (currentPlayer.movePropertyCardToColor(pc, targetColor)) {
                broadcastGameState();
            } else {
                send(NetworkProtocol.error("Failed to switch property color"));
            }
        }

        private void dispatchCardAction(String actionType, String[] parts, PlayerManagement currentPlayer, Card targetCard) {
            switch (actionType) {
                case "DEPOSIT":
                    gameManager.depositMoneyCard(targetCard);
                    broadcastGameState();
                    break;
                case "DISCARD":
                    handleDiscard(currentPlayer, targetCard);
                    break;
                case "PLACE_PROPERTY":
                    handlePlaceProperty(parts, currentPlayer, targetCard);
                    break;
                case "PLAY_ACTION":
                    handlePlayAction(parts, currentPlayer, targetCard);
                    break;
                default:
                    send(NetworkProtocol.error("Unknown action type"));
            }
        }

        private void handleDiscard(PlayerManagement currentPlayer, Card targetCard) {
            gameManager.removeFromCurrentPlayerHand(targetCard);
            gameManager.getCardManager().playCard(targetCard);
            if (currentPlayer.getHandCardCount() <= PlayerManagement.MAX_HAND_SIZE) {
                gameManager.confirmCurrentPlayerTurnEnded();
                if (gameManager.canAdvanceTurn()) gameManager.advanceTurn();
            }
            broadcastGameState();
        }

        private void handlePlaceProperty(String[] parts, PlayerManagement currentPlayer, Card targetCard) {
            if (!(targetCard instanceof PropertyCard pc)) return;
            if (pc.getPlayableColors().isEmpty()) return;
            Color placeSelectedColor;
            if (parts.length >= 3) {
                placeSelectedColor = Color.valueOf(parts[2]);
            } else {
                placeSelectedColor = pc.getPlayableColors().iterator().next();
            }
            if (!pc.getPlayableColors().contains(placeSelectedColor)) {
                send(NetworkProtocol.error("This property card cannot be used for " + placeSelectedColor.getDisplayName()));
                return;
            }
            gameManager.placePropertyCard(pc, currentPlayer, placeSelectedColor);
            broadcastGameState();
        }

        private void handlePlayAction(String[] parts, PlayerManagement currentPlayer, Card targetCard) {
            if (!(targetCard instanceof ActionCard)) {
                send(NetworkProtocol.error("Not an action card"));
                return;
            }

            if (tryHandleRentAction(parts, currentPlayer, targetCard)) return;
            if (tryHandleBuildingAction(parts, currentPlayer, targetCard)) return;

            if (targetCard instanceof SlyDealCard && parts.length == 4) {
                executeSlyDealDirectly(currentPlayer, targetCard, parts[2], parts[3]);
            } else if (targetCard instanceof ForcedDealCard && parts.length == 5) {
                executeForcedDealDirectly(currentPlayer, targetCard, parts[2], parts[3], parts[4]);
            } else if (targetCard instanceof DealBreakerCard && parts.length == 4) {
                executeDealBreakerDirectly(currentPlayer, targetCard, parts[2], parts[3]);
            } else if (targetCard instanceof DebtCollectorCard && parts.length == 3) {
                handleDebtCollector(currentPlayer, targetCard, parts[2]);
            } else if (targetCard instanceof ItsMyBirthdayCard) {
                handleItsMyBirthday(currentPlayer, targetCard);
            } else if (targetCard instanceof PassGoCard) {
                gameManager.playActionCard(targetCard);
                broadcastGameState();
            } else {
                gameManager.playActionCard(targetCard);
                broadcastGameState();
            }
        }

        /** Returns true if the action was handled as a rent action. */
        private boolean tryHandleRentAction(String[] parts, PlayerManagement currentPlayer, Card targetCard) {
            if (parts.length < 4) return false;
            if (!"BI_RENT".equals(parts[2]) && !"WILD_RENT".equals(parts[2])) return false;

            String rentMode = parts[2];
            Color rentSelectedColor = Color.valueOf(parts[3]);
            String targetPlayerId = null;
            String doubleCardId = "NONE";

            if ("WILD_RENT".equals(rentMode)) {
                if (parts.length < 6) {
                    send(NetworkProtocol.error("Invalid WILD_RENT format"));
                    return true;
                }
                targetPlayerId = parts[4];
                doubleCardId = parts[5];
            } else {
                if (parts.length < 5) {
                    send(NetworkProtocol.error("Invalid BI_RENT format"));
                    return true;
                }
                doubleCardId = parts[4];
            }

            executeRentDirectly(currentPlayer, targetCard, rentMode, rentSelectedColor, targetPlayerId, doubleCardId);
            return true;
        }

        /** Returns true if the action was handled as a building action. */
        private boolean tryHandleBuildingAction(String[] parts, PlayerManagement currentPlayer, Card targetCard) {
            if (parts.length < 4 || !"BUILDING".equals(parts[2])) return false;
            Color buildingSelectedColor = Color.valueOf(parts[3]);
            executeBuildingDirectly(currentPlayer, targetCard, buildingSelectedColor);
            broadcastGameState();
            return true;
        }

        private void handleDebtCollector(PlayerManagement currentPlayer, Card targetCard, String victimId) {
            PlayerManagement victim = findPlayerById(victimId);
            if (victim == null) return;
            gameManager.removeFromCurrentPlayerHand(targetCard);
            gameManager.getCardManager().playCard(targetCard);
            gameManager.recordPlayedCardAfterExternalResolution();
            initiatePaymentAgainstVictim(currentPlayer, victim, GameManager.DEBT_COLLECTOR_AMOUNT, "Debt Collector");
        }

        private void handleItsMyBirthday(PlayerManagement currentPlayer, Card targetCard) {
            gameManager.removeFromCurrentPlayerHand(targetCard);
            gameManager.getCardManager().playCard(targetCard);
            gameManager.recordPlayedCardAfterExternalResolution();
            List<PlayerManagement> victims = new ArrayList<>();
            for (PlayerManagement p : gameManager.getPlayersView()) {
                if (p != currentPlayer) victims.add(p);
            }
            if (!victims.isEmpty()) {
                initiateBatchPayment(currentPlayer, victims, GameManager.BIRTHDAY_AMOUNT, "It's My Birthday");
            } else {
                broadcastGameState();
            }
        }

        private void executeRentDirectly(
                PlayerManagement currentPlayer,
                Card rentCard,
                String rentMode,
                Color selectedColor,
                String targetPlayerId,
                String doubleCardId
        ) {
            if ("BI_RENT".equals(rentMode)) {
                if (!(rentCard instanceof BiColorRentCard biColorRentCard)) {
                    send(NetworkProtocol.error("Invalid bi-color rent card"));
                    return;
                }
                if (!biColorRentCard.getValidColors().contains(selectedColor)) {
                    send(NetworkProtocol.error("This rent card cannot be used for " + selectedColor.getDisplayName()));
                    return;
                }
                biColorRentCard.setSelectedColor(selectedColor);
            }

            int baseRent = currentPlayer.getRent(selectedColor);
            if (baseRent <= 0) {
                send(NetworkProtocol.error("No rent available for this color"));
                return;
            }

            int rentAmount = baseRent;

            // Apply double rent (optional)
            if (doubleCardId != null && !"NONE".equalsIgnoreCase(doubleCardId)) {
                if (gameManager.getRemainingPlayCountThisTurn() < 2) {
                    send(NetworkProtocol.error("Double The Rent requires one additional play"));
                    return;
                }
                Card doubleCard = findCardInHand(currentPlayer, doubleCardId);
                if (doubleCard instanceof DoubleTheRentCard) {
                    currentPlayer.removeFromHand(doubleCard);
                    gameManager.getCardManager().playCard(doubleCard);
                    gameManager.recordPlayedCardAfterExternalResolution();
                    rentAmount = rentAmount * 2;
                }
            }

            // Discard the rent card itself
            gameManager.removeFromCurrentPlayerHand(rentCard);
            gameManager.getCardManager().playCard(rentCard);
            gameManager.recordPlayedCardAfterExternalResolution();

            if ("WILD_RENT".equals(rentMode)) {
                PlayerManagement victim = findPlayerById(targetPlayerId);
                if (victim == null || victim == currentPlayer) {
                    broadcastGameState();
                    return;
                }
                initiatePaymentAgainstVictim(currentPlayer, victim, rentAmount, "Rent");
                return;
            }

            // BI_RENT: collect from all other players
            List<PlayerManagement> victims = new ArrayList<>();
            for (PlayerManagement p : gameManager.getPlayersView()) {
                if (p != currentPlayer) victims.add(p);
            }
            if (victims.isEmpty()) {
                broadcastGameState();
                return;
            }
            initiateBatchPayment(currentPlayer, victims, rentAmount, "Rent");
        }

        private void executeBuildingDirectly(PlayerManagement currentPlayer, Card buildingActionCard, Color selectedColor) {
            if (!(buildingActionCard instanceof HouseCard) && !(buildingActionCard instanceof HotelCard)) {
                send(NetworkProtocol.error("Invalid building card"));
                return;
            }

            if (buildingActionCard instanceof HouseCard || buildingActionCard instanceof HotelCard) {
                if (selectedColor == Color.RAILROAD || selectedColor == Color.UTILITY) {
                    send(NetworkProtocol.error("Building cannot be placed on railroad or utility sets"));
                    return;
                }
            }
            if (!currentPlayer.isSetComplete(selectedColor)) {
                send(NetworkProtocol.error("Building can only be placed on a complete set"));
                return;
            }
            if (buildingActionCard instanceof HotelCard) {
                PropertyZone zone = currentPlayer.getPropertyZonesView().get(selectedColor);
                if (zone == null || zone.getHouse() == null) {
                    send(NetworkProtocol.error("Hotel can only be placed on a set that already has a house"));
                    return;
                }
            }

            int addedRent = (buildingActionCard instanceof HotelCard) ? GameManager.HOTEL_ADDED_RENT : GameManager.HOUSE_ADDED_RENT;
            BuildingCard placed = new BuildingCard(
                    buildingActionCard.getId(),
                    buildingActionCard.getName(),
                    buildingActionCard.getValue(),
                    addedRent
            );

            currentPlayer.addBuilding(selectedColor, placed);
            gameManager.removeFromCurrentPlayerHand(buildingActionCard);
            gameManager.recordPlayedCardAfterExternalResolution();
        }

        private void executeSlyDealDirectly(PlayerManagement currentPlayer, Card actionCard, String targetPlayerId, String targetCardId) {
            PlayerManagement targetPlayer = findPlayerById(targetPlayerId);
            Card cardToSteal = findPropertyCardById(targetPlayer, targetCardId);
            if (targetPlayer != null && cardToSteal != null) {
                // Once played, an action card goes to discard pile and counts as played this turn
                gameManager.removeFromCurrentPlayerHand(actionCard);
                gameManager.getCardManager().playCard(actionCard);
                gameManager.recordPlayedCardAfterExternalResolution();

                Runnable success = () -> {
                    Color color = findColorOfProperty(targetPlayer, cardToSteal);
                    if (color != null && targetPlayer.removeFromPropertyZones(cardToSteal)) {
                        currentPlayer.addProperty(color, (PropertyCard) cardToSteal);
                        broadcastGameState();
                    }
                };
                Runnable cancel = () -> {
                    broadcastGameState();
                };
                askForJustSayNo(targetPlayer, currentPlayer, "Sly Deal", success, cancel);
            }
        }

        private void executeForcedDealDirectly(PlayerManagement currentPlayer, Card actionCard, String targetPlayerId, String myCardId, String targetCardId) {
            PlayerManagement targetPlayer = findPlayerById(targetPlayerId);
            Card myCard = findPropertyCardById(currentPlayer, myCardId);
            Card cardToSteal = findPropertyCardById(targetPlayer, targetCardId);
            if (targetPlayer != null && myCard != null && cardToSteal != null) {
                Color targetColor = findColorOfProperty(targetPlayer, cardToSteal);
                if (targetColor != null && targetPlayer.isSetComplete(targetColor)) {
                    send(NetworkProtocol.error("Cannot steal a property from a complete set"));
                    return;
                }
                
                // Once played, an action card goes to discard pile and counts as played this turn
                gameManager.removeFromCurrentPlayerHand(actionCard);
                gameManager.getCardManager().playCard(actionCard);
                gameManager.recordPlayedCardAfterExternalResolution();

                Runnable success = () -> {
                    Color myColor = findColorOfProperty(currentPlayer, myCard);
                    // re-check color just in case
                    Color tColor = findColorOfProperty(targetPlayer, cardToSteal);
                    if (myColor != null && tColor != null) {
                        if (currentPlayer.removeFromPropertyZones(myCard) && targetPlayer.removeFromPropertyZones(cardToSteal)) {
                            currentPlayer.addProperty(tColor, (PropertyCard) cardToSteal);
                            targetPlayer.addProperty(myColor, (PropertyCard) myCard);
                            broadcastGameState();
                        }
                    }
                };
                Runnable cancel = () -> {
                    broadcastGameState();
                };
                askForJustSayNo(targetPlayer, currentPlayer, "Forced Deal", success, cancel);
            }
        }

        private void executeDealBreakerDirectly(PlayerManagement currentPlayer, Card actionCard, String targetPlayerId, String colorName) {
            PlayerManagement targetPlayer = findPlayerById(targetPlayerId);
            if (targetPlayer == null) return;
            Color color = Color.valueOf(colorName);
            PropertyZone zone = targetPlayer.getPropertyZonesView().get(color);
            if (zone != null && targetPlayer.isSetComplete(color)) {
                // Once played, an action card goes to discard pile and counts as played this turn
                gameManager.removeFromCurrentPlayerHand(actionCard);
                gameManager.getCardManager().playCard(actionCard);
                gameManager.recordPlayedCardAfterExternalResolution();

                Runnable success = () -> {
                    List<PropertyCard> props = new ArrayList<>(zone.getPropertiesView());
                    for (PropertyCard pc : props) {
                        if (targetPlayer.removeFromPropertyZones(pc)) currentPlayer.addProperty(color, pc);
                    }
                    BuildingCard house = zone.getHouse();
                    if (house != null && targetPlayer.removeFromPropertyZones(house)) currentPlayer.addBuilding(color, house);
                    BuildingCard hotel = zone.getHotel();
                    if (hotel != null && targetPlayer.removeFromPropertyZones(hotel)) currentPlayer.addBuilding(color, hotel);
                    broadcastGameState();
                };
                Runnable cancel = () -> {
                    broadcastGameState();
                };
                askForJustSayNo(targetPlayer, currentPlayer, "Deal Breaker", success, cancel);
            }
        }

        public void send(NetworkProtocol message) {
            try {
                if (out != null) {
                    out.writeObject(message);
                    out.reset(); // Prevent ObjectOutputStream memory leak
                    out.flush();
                }
            } catch (IOException e) { LOGGER.log(Level.WARNING, "Failed to send message to player " + playerIndex, e); }
        }

        public void close() {
            connected = false;
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null) socket.close();
            } catch (IOException e) { LOGGER.log(Level.FINE, "Error closing client socket", e); }
            unregisterClient(playerIndex);
        }

        public int getPlayerIndex() { return playerIndex; }
    }
}
