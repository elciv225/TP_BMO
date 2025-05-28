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
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.server.Server;

public class ServeurWebSocket {

    private static Set<Session> sessions = Collections.synchronizedSet(new HashSet<>());

    public static Set<Session> getSessions() {
        return sessions;
    }

    public static void main(String[] args) {
        // Test de la base de données au démarrage
        System.out.println("Test de connexion à la base de données...");
        if (!Database.testConnection()) {
            System.err.println("ERREUR: Impossible de se connecter à la base de données.");
            System.err.println("Vérifiez que:");
            System.err.println("1. MySQL est démarré");
            System.err.println("2. La base de données 'tpbmo_db' existe");
            System.err.println("3. L'utilisateur 'tpbmo' a les bonnes permissions");
            System.err.println("4. Le port 3306 est accessible");
            return;
        }

        Server server = new Server("localhost", 8080, "", null, EndpointServeur.class);
        try {
            server.start();
            System.out.println("=================================================");
            System.out.println("🚀 Serveur WebSocket démarré avec succès !");
            System.out.println("📍 URL: ws://localhost:8080/");
            System.out.println("🗄️  Base de données: MySQL sur port 3306");
            System.out.println("=================================================");
            System.out.println("Appuyez sur une touche pour arrêter le serveur...");
            System.in.read();
        } catch (Exception e) {
            System.err.println("Erreur lors du démarrage du serveur: " + e.getMessage());
            e.printStackTrace();
        } finally {
            server.stop();
            System.out.println("Serveur arrêté.");
        }
    }

    @ServerEndpoint("/")
    public static class EndpointServeur {

        @OnOpen
        public void onOpen(Session session) {
            sessions.add(session);

            String query = session.getQueryString();
            String reunionId = null;
            String userId = null;
            String ipClient = null;

            if (query != null) {
                String[] params = query.split("&");
                for (String param : params) {
                    String[] pair = param.split("=");
                    if (pair.length == 2) {
                        switch (pair[0]) {
                            case "reunionId":
                                reunionId = pair[1];
                                break;
                            case "userId":
                                userId = pair[1];
                                break;
                            case "ipClient":
                                ipClient = pair[1];
                                break;
                        }
                    }
                }
            }

            if (reunionId != null) session.getUserProperties().put("reunionId", reunionId);
            if (userId != null) session.getUserProperties().put("userId", userId);
            if (ipClient != null) session.getUserProperties().put("ipClient", ipClient);

            System.out.println("✅ Nouvelle connexion établie:");
            System.out.println("   Session ID: " + session.getId());
            System.out.println("   IP Client: " + ipClient);
            System.out.println("   Reunion ID: " + reunionId);
            System.out.println("   User ID: " + userId);
            System.out.println("   Total sessions actives: " + sessions.size());

            try {
                // CORRECTION: Envoyer un message JSON valide au lieu d'un texte brut
                String welcomeMessage = String.format(
                    "{\"type\":\"welcome\",\"message\":\"Connexion établie avec succès\",\"sessionId\":\"%s\"}",
                    session.getId()
                );
                session.getBasicRemote().sendText(welcomeMessage);
            } catch (IOException e) {
                System.err.println("Erreur lors de l'envoi du message de bienvenue: " + e.getMessage());
            }
        }

        @OnMessage
        public void onMessage(String message, Session session) {
            System.out.println("📨 Message reçu de " + session.getId() + ": " + message);
            try {
                ActionHandler.handleAction(message, session);
            } catch (IOException e) {
                System.err.println("Erreur lors du traitement du message: " + e.getMessage());
                e.printStackTrace();

                try {
                    String errorResponse = "{\"type\":\"error\",\"message\":\"Erreur lors du traitement de la requête\"}";
                    session.getBasicRemote().sendText(errorResponse);
                } catch (IOException ioException) {
                    System.err.println("Impossible d'envoyer la réponse d'erreur: " + ioException.getMessage());
                }
            }
        }

        @OnClose
        public void onClose(Session session, CloseReason closeReason) {
            sessions.remove(session);
            System.out.println("❌ Session " + session.getId() + " fermée. Raison: " + closeReason);
            System.out.println("   Sessions restantes: " + sessions.size());
        }

        @OnError
        public void onError(Session session, Throwable throwable) {
            System.err.println("💥 Erreur pour la session " + session.getId() + ": " + throwable.getMessage());
            throwable.printStackTrace();
        }
    }
}