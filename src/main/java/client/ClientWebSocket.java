package client;

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Timer;
import java.util.TimerTask;

@ClientEndpoint
public class ClientWebSocket {

    private Session session;
    private final String webSocketUrl = "ws://localhost:8080/";
    private boolean reconnecting = false;
    private MessageListener messageListener;

    public interface MessageListener {
        void onMessageReceived(String message);
        void onConnectionStatusChanged(String status, String color);
    }

    public ClientWebSocket(MessageListener listener) {
        this.messageListener = listener;
        connectToWebSocket();
    }

    /**
     * Établit la connexion WebSocket au serveur.
     */
    private void connectToWebSocket() {
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, new URI(webSocketUrl));
        } catch (DeploymentException | URISyntaxException | IOException e) {
            notifyStatus("Non connecté - Erreur: " + e.getMessage(), "red");
            scheduleReconnect();
            e.printStackTrace();
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        reconnecting = false;
        notifyStatus("Connecté au serveur WebSocket", "green");
    }

    @OnMessage
    public void onMessage(String message) {
        if (messageListener != null) {
            messageListener.onMessageReceived(message);
        }
    }

    @OnClose
    public void onClose(CloseReason reason) {
        this.session = null;
        notifyStatus("Déconnecté - Raison: " + reason.getReasonPhrase(), "red");
        scheduleReconnect();
    }

    @OnError
    public void onError(Throwable t) {
        notifyStatus("Erreur de connexion: " + t.getMessage(), "red");
        scheduleReconnect();
    }

    /**
     * Envoie un message au serveur WebSocket.
     */
    public void sendMessage(String message) {
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IOException e) {
                notifyStatus("Erreur d'envoi: " + e.getMessage(), "red");
                e.printStackTrace();
            }
        } else {
            notifyStatus("Impossible d'envoyer - Non connecté", "red");
        }
    }

    /**
     * Planifie une tentative de reconnexion automatique.
     */
    private void scheduleReconnect() {
        if (!reconnecting) {
            reconnecting = true;
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    connectToWebSocket();
                }
            }, 3000);
        }
    }

    /**
     * Notifie l'état de connexion à l'interface.
     */
    private void notifyStatus(String status, String color) {
        if (messageListener != null) {
            messageListener.onConnectionStatusChanged(status, color);
        }
    }
}
