package client;

import javax.websocket.*;
import java.io.IOException;
import java.net.*;
import java.util.Timer;
import java.util.TimerTask;

@ClientEndpoint
public class ClientWebSocket {

    private Session session;
    private boolean reconnexion = false;
    private String ipServeur;
    private boolean estConnecte = false;
    AuthentificationController controller;

    public void setController(AuthentificationController controller) {
        this.controller = controller;
    }


    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    /**
     * Établit la connexion WebSocket au serveur.
     */
    public void connectToWebSocket(String ipEntree) {
        new Thread(() -> {
            try {
                // Vérifier si une connexion est déjà en cours ou établie
                if (estConnecte) {
                    System.out.println("Connexion déjà en cours ou établie");
                    return;
                }
                WebSocketContainer container = ContainerProvider.getWebSocketContainer();
                String webSocketUrl = "ws://" + ipEntree + ":8080/";
                container.connectToServer(this, new URI(webSocketUrl));
                // Enregistrer l'adresse IP du serveur pour reconnection
                this.ipServeur = ipEntree;
                estConnecte = true;
                System.out.println("Connecté au serveur WebSocket sur " + webSocketUrl);
            } catch (DeploymentException | URISyntaxException | IOException e) {
                System.out.println("Non connecté - Erreur: " + e.getMessage());
                seReconnecter();
                e.printStackTrace();
            }
        }).start();
    }


    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        reconnexion = false;
        System.out.println("Connecté au serveur WebSocket");
    }

    @OnMessage
    public void onMessage(String message) {
        System.out.println(("Connecté au serveur WebSocket"));
        if (controller != null) {
            controller.traiterReponseConnexion(message);
        }
    }

    @OnClose
    public void onClose(CloseReason reason) {
        this.session = null;
        System.out.println("Déconnecté - Raison: " + reason.getReasonPhrase());
        seReconnecter();
    }

    @OnError
    public void onError(Throwable t) {
        System.out.println("Erreur de connexion: " + t.getMessage());
        seReconnecter();
    }

    /**
     * Reconnexion automatique après 3 secondes.
     */
    private void seReconnecter() {
        if (!reconnexion) {
            reconnexion = true;
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    connectToWebSocket(ipServeur);
                }
            }, 3000);
        }
    }

    /**
     * Envoie une requête texte au serveur WebSocket.
     *
     * @param jsonRequete La requête texte à envoyer (généralement une chaîne JSON).
     */
    public void envoyerRequete(String jsonRequete) {
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(jsonRequete);
                System.out.println("Requête envoyée au serveur : " + jsonRequete);

            } catch (IOException e) {
                System.err.println("Erreur lors de l'envoi de la requête : " + e.getMessage());
                // Gérer la déconnexion ou tenter une reconnexion si nécessaire
            }
        } else {
            System.out.println("Impossible d'envoyer la requête : la session WebSocket n'est pas ouverte.");
        }
    }

}
