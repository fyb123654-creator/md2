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

public class GameServer {
    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private List<ClientHandler> clients;
    private GameManager gameManager;
    private int port;
    private boolean running;
    private int expectedPlayerCount;
    private OnGameStateChangeListener listener;
    private volatile GameStateData lastBroadcastState;
    private final boolean[] readyFlags;

    // Payment flow state (single payment)
    private boolean isWaitingForJsn = false;
    private boolean isWaitingForPayment = false;
    private String waitingVictimId = null;
    private String pendingCollectorId = null;
    private int pendingPaymentAmount = 0;
    private String pendingActionName = "";

    // Batch payment queue (used by ItsMyBirthday, etc.)
    private List<String> pendingPaymentQueue = null;
    private int currentPaymentIndex = 0;
    private String batchCollectorId = null;

    // ---------------- Payment / waiting state ----------------
    private void clearSinglePaymentState() {
        isWaitingForJsn = false;
        isWaitingForPayment = false;
        waitingVictimId = null;
        pendingCollectorId = null;
        pendingPaymentAmount = 0;
        pendingActionName = "";
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
    }

    public GameServer(int port, int playerCount) {
        this.port = port;
        this.expectedPlayerCount = playerCount;
        this.clients = new ArrayList<>();
        this.executorService = Executors.newFixedThreadPool(10);
        this.gameManager = new GameManager();
        gameManager.setPlayerCount(playerCount);
        this.readyFlags = new boolean[playerCount];
        // Host is Player 1 (index 0)
        this.readyFlags[0] = false;
    }

    public void setListener(OnGameStateChangeListener listener) {
        this.listener = listener;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println("Server started on port " + port);
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
                    ClientHandler handler = new ClientHandler(clientSocket, clients.size() + 1);
                    clients.add(handler);
                    executorService.submit(handler);
                    if (listener != null) {
                        listener.onClientConnected("Player " + (clients.size() + 1));
                    }
                    broadcastRoomUpdate();
                } catch (IOException e) {
                    if (running) e.printStackTrace();
                }
            }
        });
    }

    private void startGameInternal() {
        System.out.println("All players connected, starting game...");
        // Server-side start: start round, broadcast GAME_START and initial state
        try {
            // Ensure startRound was called at least once
            if (gameManager.getCardManager() == null) {
                gameManager.startRound();
            }
        } catch (Exception e) {
            e.printStackTrace();
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
        if (clients.size() != expectedPlayerCount - 1) {
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

    public GameStateData getLastBroadcastState() {
        return lastBroadcastState;
    }

    public void broadcast(NetworkProtocol message) {
        for (ClientHandler client : clients) client.send(message);
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
            for (ClientHandler client : clients) client.close();
            executorService.shutdown();
        } catch (IOException e) { e.printStackTrace(); }
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
        waitingVictimId = victim.getPlayerId();
        pendingCollectorId = collector.getPlayerId();
        pendingPaymentAmount = amount;
        pendingActionName = actionName;
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
        for (ClientHandler client : clients) {
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
        for (Map.Entry<Color, PropertyZone> entry : player.getPropertyZonesView().entrySet()) {
            if (entry.getValue().getPropertiesView().contains(card)) return entry.getKey();
        }
        return null;
    }

    private Card findJustSayNoCard(PlayerManagement player) {
        for (Card c : player.getHandCardsView()) {
            if (c instanceof JustSayNoCard) return c;
        }
        return null;
    }

    private class ClientHandler implements Runnable {
        private Socket socket;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private int playerIndex;
        private boolean connected;

        // Action-card Just Say No waiting
        private boolean isWaitingForJsnAction = false;
        private PlayerManagement pendingJsnVictim = null;
        private PlayerManagement pendingJsnSource = null;
        private String pendingJsnActionName = "";
        private Runnable pendingJsnSuccessCallback = null;
        private Runnable pendingJsnCancelCallback = null;

        public ClientHandler(Socket socket, int playerIndex) {
            this.socket = socket;
            this.playerIndex = playerIndex;
            this.connected = true;

            // Initialize streams in constructor to avoid early message loss
            if (socket != null) {
                try {
                    out = new ObjectOutputStream(socket.getOutputStream());
                    in = new ObjectInputStream(socket.getInputStream());
                    out.writeObject(NetworkProtocol.connectAck(true, "Welcome! You are player " + (playerIndex + 1)));
                    out.flush();
                } catch (IOException e) {
                    connected = false;
                    e.printStackTrace();
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
                if (connected) System.out.println("Player " + (playerIndex + 1) + " disconnected");
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
                    broadcast(NetworkProtocol.chat(message.getPlayerId(), message.getContent()));
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
                    System.out.println("Unknown message type: " + message.getType());
            }
        }

        private void askForJustSayNo(PlayerManagement victim, PlayerManagement sourcePlayer, String actionName,
                                     Runnable successCallback, Runnable cancelCallback) {
            Card justSayNoCard = findJustSayNoCard(victim);
            if (justSayNoCard == null) {
                successCallback.run();
                return;
            }
            NetworkProtocol req = new NetworkProtocol();
            req.setType(NetworkProtocol.MessageType.ASK_JUST_SAY_NO);
            req.setContent(sourcePlayer.getName() + ":" + actionName);
            sendToPlayer(victim.getPlayerId(), req);
            isWaitingForJsnAction = true;
            pendingJsnVictim = victim;
            pendingJsnSource = sourcePlayer;
            pendingJsnActionName = actionName;
            pendingJsnSuccessCallback = successCallback;
            pendingJsnCancelCallback = cancelCallback;
        }

        private void handleJustSayNoResponse(String content, PlayerManagement responder) {
            String[] parts = content.split(":");
            String answer = parts[0];
            String cardId = parts.length > 1 ? parts[1] : null;
            if ("YES".equals(answer) && cardId != null) {
                Card card = findCardInHand(responder, cardId);
                if (card != null) {
                    responder.removeFromHand(card);
                    gameManager.getCardManager().playCard(card);
                    if (pendingJsnCancelCallback != null) pendingJsnCancelCallback.run();
                } else {
                    if (pendingJsnSuccessCallback != null) pendingJsnSuccessCallback.run();
                }
            } else {
                if (pendingJsnSuccessCallback != null) pendingJsnSuccessCallback.run();
            }
            isWaitingForJsnAction = false;
            pendingJsnVictim = null;
            pendingJsnSource = null;
            pendingJsnActionName = "";
            pendingJsnSuccessCallback = null;
            pendingJsnCancelCallback = null;
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
                transferAllAssetsToCollectorBank(collector, victim);
            } else {
                String[] ids = cardIds.split(",");
                for (String id : ids) {
                    Card c = findAssetCardById(victim, id);
                    if (c != null) {
                        if (victim.removeFromBank(c) || victim.removeFromPropertyZones(c)) {
                            // Rule: received payment cards go to collector's bank
                            collector.depositToBank(c);
                        }
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

        private void transferAllAssetsToCollectorBank(PlayerManagement collector, PlayerManagement victim) {
            // Bank
            List<Card> bankCards = new ArrayList<>(victim.getBankCardsView());
            for (Card c : bankCards) {
                if (victim.removeFromBank(c)) {
                    collector.depositToBank(c);
                }
            }
            // Properties + house/hotel
            List<Card> props = new ArrayList<>();
            for (PropertyZone zone : victim.getPropertyZonesView().values()) {
                props.addAll(zone.getPropertiesView());
                if (zone.getHouse() != null) props.add(zone.getHouse());
                if (zone.getHotel() != null) props.add(zone.getHotel());
            }
            for (Card c : props) {
                if (victim.removeFromPropertyZones(c)) {
                    collector.depositToBank(c);
                }
            }
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
            System.out.println("Player " + (playerIndex + 1) + " action: " + action);
            PlayerManagement currentPlayer = gameManager.getPlayersView().get(playerIndex);

            // 1) Handle Just Say No waiting
            if (isWaitingForJsnAction) {
                if (!currentPlayer.getPlayerId().equals(pendingJsnVictim.getPlayerId())) {
                    send(NetworkProtocol.error("Waiting for another player's response"));
                    return;
                }
                if (action.startsWith("JUST_SAY_NO_RESPONSE:")) {
                    handleJustSayNoResponse(action.substring(21), currentPlayer);
                } else {
                    send(NetworkProtocol.error("Invalid response format"));
                }
                return;
            }

            // 2) Handle payment waiting (includes JSN and payment)
            if (isWaitingForJsn || isWaitingForPayment) {
                if (!currentPlayer.getPlayerId().equals(waitingVictimId)) {
                    send(NetworkProtocol.error("Waiting for another player's response"));
                    return;
                }
                if (isWaitingForJsn && action.startsWith("JUST_SAY_NO_RESPONSE:")) {
                    String[] parts = action.split(":");
                    if ("YES".equals(parts[1])) {
                        Card jsnCard = findCardInHand(currentPlayer, parts[2]);
                        if (jsnCard != null) {
                            currentPlayer.removeFromHand(jsnCard);
                            gameManager.getCardManager().playCard(jsnCard);
                        }
                        // Payment rejected: end single wait; continue batch if applicable
                        clearSinglePaymentState();
                        if (pendingPaymentQueue != null) {
                            currentPaymentIndex++;
                            processNextPaymentInBatch();
                        } else {
                            broadcastGameState();
                        }
                    } else {
                        isWaitingForJsn = false;
                        isWaitingForPayment = true;
                        NetworkProtocol req = new NetworkProtocol();
                        req.setType(NetworkProtocol.MessageType.REQUIRE_PAYMENT);
                        PlayerManagement collector = findPlayerById(pendingCollectorId);
                        req.setContent(pendingPaymentAmount + ":" + (collector != null ? collector.getName() : "Bank"));
                        sendToPlayer(waitingVictimId, req);
                    }
                    return;
                } else if (isWaitingForPayment && action.startsWith("PAYMENT_RESPONSE:")) {
                    handlePaymentResponse(action.substring(17), currentPlayer);
                    return;
                } else {
                    send(NetworkProtocol.error("You must respond to the payment request"));
                    return;
                }
            }

            // 3) Normal turn operations
            if (gameManager.getCurrentPlayerIndex() != playerIndex) {
                send(NetworkProtocol.error("It's not your turn!"));
                return;
            }

            try {
                if ("END_TURN".equals(action)) {
                    gameManager.confirmCurrentPlayerTurnEnded();
                    if (gameManager.canAdvanceTurn()) gameManager.advanceTurn();
                    broadcastGameState();
                    return;
                }

                String[] parts = action.split(":");
                if (parts.length < 2) {
                    send(NetworkProtocol.error("Invalid action format"));
                    return;
                }

                String actionType = parts[0];
                String cardId = parts[1];

                // Except DISCARD/END_TURN, playing cards is limited to 3 per turn
                if (!"DISCARD".equals(actionType) && !"END_TURN".equals(actionType) && !gameManager.canCurrentPlayerPlayCard()) {
                    send(NetworkProtocol.error("You have already played the maximum number of cards this turn"));
                    return;
                }

                Card targetCard = null;
                for (Card c : currentPlayer.getHandCardsView()) {
                    if (c.getId().equals(cardId)) { targetCard = c; break; }
                }
                if (targetCard == null) {
                    send(NetworkProtocol.error("Card not found"));
                    return;
                }

                switch (actionType) {
                    case "DEPOSIT":
                        gameManager.depositMoneyCard(targetCard);
                        broadcastGameState();
                        break;
                    case "DISCARD":
                        gameManager.removeFromCurrentPlayerHand(targetCard);
                        gameManager.getCardManager().playCard(targetCard);
                        if (currentPlayer.getHandCardCount() <= PlayerManagement.MAX_HAND_SIZE) {
                            gameManager.confirmCurrentPlayerTurnEnded();
                            if (gameManager.canAdvanceTurn()) gameManager.advanceTurn();
                        }
                        broadcastGameState();
                        break;
                    case "PLACE_PROPERTY":
                    {
                        if (!(targetCard instanceof PropertyCard)) return;
                        PropertyCard pc = (PropertyCard) targetCard;
                        if (pc.getPlayableColors().isEmpty()) return;
                        Color placeSelectedColor = pc.getPlayableColors().iterator().next();
                        gameManager.placePropertyCard(pc, currentPlayer, placeSelectedColor);
                        broadcastGameState();
                        break;
                    }
                    case "PLAY_ACTION":
                    {
                        if (!(targetCard instanceof ActionCard)) {
                            send(NetworkProtocol.error("Not an action card"));
                            return;
                        }

                        // Rent card: client already selected color/target/double flag
                        if (parts.length >= 4 && ("BI_RENT".equals(parts[2]) || "WILD_RENT".equals(parts[2]))) {
                            String rentMode = parts[2]; // BI_RENT / WILD_RENT
                            Color rentSelectedColor = Color.valueOf(parts[3]);

                            String targetPlayerId = null;
                            String doubleCardId = "NONE";
                            if ("WILD_RENT".equals(rentMode)) {
                                if (parts.length < 6) {
                                    send(NetworkProtocol.error("Invalid WILD_RENT format"));
                                    return;
                                }
                                targetPlayerId = parts[4];
                                doubleCardId = parts[5];
                            } else { // BI_RENT
                                if (parts.length < 5) {
                                    send(NetworkProtocol.error("Invalid BI_RENT format"));
                                    return;
                                }
                                doubleCardId = parts[4];
                            }

                            executeRentDirectly(currentPlayer, targetCard, rentMode, rentSelectedColor, targetPlayerId, doubleCardId);
                            return;
                        }

                        // House/Hotel: client already selected target color set
                        if (parts.length >= 4 && "BUILDING".equals(parts[2])) {
                            Color buildingSelectedColor = Color.valueOf(parts[3]);
                            executeBuildingDirectly(currentPlayer, targetCard, buildingSelectedColor);
                            broadcastGameState();
                            return;
                        }

                        if (targetCard instanceof SlyDealCard && parts.length == 4) {
                            executeSlyDealDirectly(currentPlayer, targetCard, parts[2], parts[3]);
                        } else if (targetCard instanceof ForcedDealCard && parts.length == 5) {
                            executeForcedDealDirectly(currentPlayer, targetCard, parts[2], parts[3], parts[4]);
                        } else if (targetCard instanceof DealBreakerCard && parts.length == 4) {
                            executeDealBreakerDirectly(currentPlayer, targetCard, parts[2], parts[3]);
                        } else if (targetCard instanceof DebtCollectorCard && parts.length == 3) {
                            PlayerManagement victim = findPlayerById(parts[2]);
                            if (victim != null) {
                                gameManager.removeFromCurrentPlayerHand(targetCard);
                                gameManager.getCardManager().playCard(targetCard);
                                gameManager.recordPlayedCardAfterExternalResolution();
                                initiatePaymentAgainstVictim(currentPlayer, victim, 5, "Debt Collector");
                            }
                        } else if (targetCard instanceof ItsMyBirthdayCard) {
                            gameManager.removeFromCurrentPlayerHand(targetCard);
                            gameManager.getCardManager().playCard(targetCard);
                            gameManager.recordPlayedCardAfterExternalResolution();
                            List<PlayerManagement> victims = new ArrayList<>();
                            for (PlayerManagement p : gameManager.getPlayersView()) {
                                if (p != currentPlayer) victims.add(p);
                            }
                            if (!victims.isEmpty()) {
                                initiateBatchPayment(currentPlayer, victims, 2, "It's My Birthday");
                            } else {
                                broadcastGameState();
                            }
                        } else if (targetCard instanceof PassGoCard) {
                            gameManager.playActionCard(targetCard);
                            broadcastGameState();
                        } else {
                            gameManager.playActionCard(targetCard);
                            broadcastGameState();
                        }
                        break;
                    }
                    default:
                        send(NetworkProtocol.error("Unknown action type"));
                }
            } catch (Exception e) {
                e.printStackTrace();
                send(NetworkProtocol.error("Action failed: " + e.getMessage()));
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
            int baseRent = currentPlayer.getRent(selectedColor);
            if (baseRent <= 0) {
                send(NetworkProtocol.error("No rent available for this color"));
                return;
            }

            int rentAmount = baseRent;

            // Apply double rent (optional)
            if (doubleCardId != null && !"NONE".equalsIgnoreCase(doubleCardId)) {
                Card doubleCard = findCardInHand(currentPlayer, doubleCardId);
                if (doubleCard instanceof DoubleTheRentCard) {
                    currentPlayer.removeFromHand(doubleCard);
                    gameManager.getCardManager().playCard(doubleCard);
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

            if (buildingActionCard instanceof HouseCard) {
                if (selectedColor == Color.RAILROAD || selectedColor == Color.UTILITY) {
                    send(NetworkProtocol.error("House cannot be placed on railroad or utility sets"));
                    return;
                }
            }
            if (!currentPlayer.isSetComplete(selectedColor)) {
                send(NetworkProtocol.error("Building can only be placed on a complete set"));
                return;
            }

            int addedRent = (buildingActionCard instanceof HotelCard) ? 4 : 3;
            BuildingCard placed = new BuildingCard(
                    buildingActionCard.getId(),
                    buildingActionCard.getName(),
                    buildingActionCard.getValue(),
                    addedRent
            );

            gameManager.removeFromCurrentPlayerHand(buildingActionCard);
            gameManager.getCardManager().playCard(buildingActionCard);
            currentPlayer.addBuilding(selectedColor, placed);
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
                // Once played, an action card goes to discard pile and counts as played this turn
                gameManager.removeFromCurrentPlayerHand(actionCard);
                gameManager.getCardManager().playCard(actionCard);
                gameManager.recordPlayedCardAfterExternalResolution();

                Runnable success = () -> {
                    Color myColor = findColorOfProperty(currentPlayer, myCard);
                    Color targetColor = findColorOfProperty(targetPlayer, cardToSteal);
                    if (myColor != null && targetColor != null) {
                        if (currentPlayer.removeFromPropertyZones(myCard) && targetPlayer.removeFromPropertyZones(cardToSteal)) {
                            currentPlayer.addProperty(targetColor, (PropertyCard) cardToSteal);
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
                    out.flush();
                }
            } catch (IOException e) { e.printStackTrace(); }
        }

        public void close() {
            connected = false;
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null) socket.close();
            } catch (IOException e) {}
        }

        public int getPlayerIndex() { return playerIndex; }
    }
}
