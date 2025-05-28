package serveur;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

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
            try {
                // Envoyer un message de bienvenue au client
                session.getBasicRemote().sendText("Connexion établie avec succès au serveur WebSocket!");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @OnMessage
        public void onMessage(String message, Session session) {
            try {
                ActionHandler.handleAction(message, session);
            } catch (IOException e) {
                e.printStackTrace();
            }
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