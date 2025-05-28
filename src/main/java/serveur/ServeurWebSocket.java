package serveur;

// import java.io.IOException; // Will be removed if no longer needed
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import javax.websocket.Session;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.server.Server;

public class ServeurWebSocket {

    // Stocker toutes les sessions connectées
    private static Set<Session> sessions = Collections.synchronizedSet(new HashSet<>());

    public static Set<Session> getSessions() {
        return sessions;
    }

    public static void main(String[] args) {
        // Démarrez le serveur WebSocket sur le port 8080
        Server server = new Server("localhost", 8080, "", null, EndpointServeur.class);
        try {
            server.start();
            System.out.println("Serveur WebSocket démarré sur ws://localhost:8080/websocket");
            System.out.println("Appuyez sur une touche pour arrêter le serveur...");
            System.in.read(); // Attendre l'entrée utilisateur pour arrêter le serveur
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            server.stop();
        }
    }

    // L'annotation ServerEndpoint définit le chemin d'accès à l'endpoint WebSocket
    @ServerEndpoint("/")
    public static class EndpointServeur {

        @OnOpen
        public void onOpen(Session session,  @PathParam("ipClient") String ipClient) {
            sessions.add(session);
            System.out.println("Nouvelle connexion établie avec la machine IP : " + ipClient);
            // Envoyer un message de bienvenue au client de manière asynchrone
            session.getAsyncRemote().sendText("Connexion établie avec succès au serveur WebSocket!", new SendHandler() {
                @Override
                public void onResult(SendResult result) {
                    if (!result.isOK()) {
                        System.err.println("Erreur lors de l'envoi du message de bienvenue à la session " + session.getId() + ": " + result.getException());
                        result.getException().printStackTrace();
                    } else {
                        System.out.println("Message de bienvenue envoyé avec succès à la session " + session.getId());
                    }
                }
            });
        }

        @OnMessage
        public void onMessage(String message, Session session) {
            // Assuming ActionHandler.handleAction might also send messages.
            // If ActionHandler directly uses the session to send messages,
            // those parts would need to be updated as well.
            // For now, this subtask only focuses on ServeurWebSocket.java
            // ActionHandler.handleAction no longer throws IOException directly
            ActionHandler.handleAction(message, session);
        }

        @OnClose
        public void onClose(Session session, CloseReason closeReason) {
            sessions.remove(session);
            System.out.println("Session " + session.getId() + " fermée. Raison: " + closeReason);
        }

        @OnError
        public void onError(Session session, Throwable throwable) {
            System.out.println("Erreur pour la session " + session.getId());
            throwable.printStackTrace();
        }
    }
}