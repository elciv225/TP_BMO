package client;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;

import javax.websocket.*;
import java.io.IOException;
import java.net.*;
import java.util.Enumeration;
import java.util.Timer;
import java.util.TimerTask;

@ClientEndpoint
public class ClientWebSocket {

    private Session session;
    private boolean reconnexion = false;
    private String ipServeur;
    private boolean estConnecte = false;
    AuthentificationController controllerAuth;
    EspaceUtilisateurController controllerEspc;

    public void setControllerAuth(AuthentificationController controller) {
        this.controllerAuth = controller;
    }

    public void setControllerEspc(EspaceUtilisateurController controller) {
        this.controllerEspc = controller;
    }

    public Session getSessionAuth() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        reconnexion = false;
        System.out.println("Connecté au serveur WebSocket");
    }

    @OnMessage
    public void onMessage(String message) {
        System.out.println("Requête envoyé");
        if (controllerAuth != null) {
            controllerAuth.traiterReponseConnexion(message);
            System.out.println("Traitement en cours ...");
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

                String ipClient = getAdresseIP();

                WebSocketContainer container = ContainerProvider.getWebSocketContainer();
                String webSocketUrl = "ws://" + ipEntree + ":8080/?ipClient=" + ipClient;
                container.connectToServer(this, new URI(webSocketUrl));
                // Enregistrer l'adresse IP du serveur pour reconnection
                this.ipServeur = ipEntree;
                estConnecte = true;
                Platform.runLater(() -> {
                    Stage stage = (Stage) Stage.getWindows().stream()
                            .filter(Window::isShowing)
                            .findFirst()
                            .orElse(null);

                    if (stage != null) {
                        try {
                            FXMLLoader loader = new FXMLLoader(getClass().getResource("/authentification.fxml"));
                            Parent root = loader.load();
                            AuthentificationController authController = loader.getController();
                            authController.setClientWebSocket(this);
                            stage.setScene(new Scene(root));
                            stage.show();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        System.out.println("Aucune fenêtre active trouvée !");
                    }
                });

                System.out.println("Connecté au serveur WebSocket sur " + webSocketUrl);

            } catch (DeploymentException | URISyntaxException | IOException e) {
                System.out.println("Non connecté - Erreur: " + e.getMessage());
                seReconnecter();
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Récupère l'adresse IP locale de la machine.
     *
     * @return L'adresse IP locale de la machine.
     */
    public static String getAdresseIP() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();

                // Ignorer les interfaces inactives ou virtuelles
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }

                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();

                    // Vérifier si c'est bien une IPv4 et non une IPv6
                    if (inetAddress.getHostAddress().matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Adresse IP non trouvée";
    }

}
