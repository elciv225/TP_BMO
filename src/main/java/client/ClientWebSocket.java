package client;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.json.JSONObject; // Added for JSON parsing

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
    private ReunionController controllerReunion; // Added for active ReunionController

    public void setControllerReunion(ReunionController controller) { // Added setter for ReunionController
        this.controllerReunion = controller;
    }

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
        estConnecte = true;
        System.out.println("Connecté au serveur WebSocket - Session ID: " + session.getId());
    }

    @OnMessage
    public void onMessage(String message) {
        System.out.println("Message reçu du serveur: " + message);

        if (message != null && message.trim().startsWith("{")) {
            try {
                JSONObject jsonMessage = new JSONObject(message);

                if (jsonMessage.has("modele")) {
                    String modele = jsonMessage.optString("modele");
                    if ("authentification".equals(modele)) {
                        if (controllerAuth != null) {
                            controllerAuth.traiterReponseConnexion(message);
                        } else {
                            System.err.println("controllerAuth est null, impossible de traiter le message d'authentification.");
                        }
                    } else if ("reunion".equals(modele)) { // For EspaceUtilisateurController (initial join/create responses)
                        if (controllerEspc != null) {
                            controllerEspc.traiterReponseConnexion(message);
                        } else {
                            System.err.println("controllerEspc est null, impossible de traiter le message de réunion (modèle).");
                        }
                    } else {
                        System.out.println("Message avec modele non géré: " + modele);
                    }
                } else if (jsonMessage.has("type")) { // For active ReunionController messages (e.g., newMessage, userJoined)
                    if (controllerReunion != null && controllerReunion.isInitialized()) {
                        String messageReunionId = jsonMessage.optString("reunionId");
                        if (!messageReunionId.isEmpty() && controllerReunion.getCurrentReunionId() != null &&
                            messageReunionId.equals(controllerReunion.getCurrentReunionId())) {
                            controllerReunion.traiterMessageRecu(message);
                        } else if (messageReunionId.isEmpty()) {
                            // For messages like 'invitationResult' or general errors not tied to a specific reunionId
                            controllerReunion.traiterMessageRecu(message);
                        } else {
                             System.out.println("Message de réunion (type) reçu pour reunionId " + messageReunionId +
                                                " mais ReunionController est sur " + controllerReunion.getCurrentReunionId() +
                                                ". Message ignoré par ReunionController.");
                        }
                    } else {
                        System.out.println("Message avec type " + jsonMessage.optString("type") + " reçu mais pas de contrôleur de réunion actif/initialisé.");
                         // Potentially route to controllerEspc if it should handle some 'type' messages when no meeting is active
                        if (controllerEspc != null) {
                            // Example: controllerEspc.handleGeneralTypeMessage(message);
                            System.out.println("No active ReunionController, message type " + jsonMessage.optString("type") + " not handled further.");
                        }
                    }
                } else {
                    // Existing handling for non-JSON or other text messages
                    System.out.println("Message texte (ou JSON non structuré pour routage) du serveur: " + message);
                }
            } catch (Exception e) {
                System.err.println("Erreur lors du parsing ou du traitement du message JSON: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            // Message non-JSON (e.g. welcome message from server)
            System.out.println("Message texte (non-JSON) du serveur: " + message);
        }
    }

    @OnClose
    public void onClose(CloseReason reason) {
        this.session = null;
        estConnecte = false;
        System.out.println("Déconnecté - Raison: " + reason.getReasonPhrase());
        seReconnecter();
    }

    @OnError
    public void onError(Throwable t) {
        System.err.println("Erreur de connexion WebSocket: " + t.getMessage());
        estConnecte = false;
        seReconnecter();
    }

    /**
     * Reconnexion automatique après 3 secondes.
     */
    private void seReconnecter() {
        if (!reconnexion && ipServeur != null) {
            reconnexion = true;
            System.out.println("Tentative de reconnexion dans 3 secondes...");
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
     */
    public void envoyerRequete(String jsonRequete) {
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(jsonRequete);
                System.out.println("Requête envoyée au serveur : " + jsonRequete);
            } catch (IOException e) {
                System.err.println("Erreur lors de l'envoi de la requête : " + e.getMessage());
                estConnecte = false;
            }
        } else {
            System.err.println("Impossible d'envoyer la requête : la session WebSocket n'est pas ouverte.");
            if (controllerAuth != null) {
                Platform.runLater(() -> {
                    controllerAuth.showAlert(false, "Erreur de connexion",
                        "La connexion au serveur a été perdue. Reconnexion en cours...");
                });
            }
        }
    }

    /**
     * Établit la connexion WebSocket au serveur.
     */
    public void connectToWebSocket(String ipEntree) {
        new Thread(() -> {
            try {
                // Vérifier si une connexion est déjà en cours ou établie
                if (estConnecte && session != null && session.isOpen()) {
                    System.out.println("Connexion déjà établie et active");
                    return;
                }

                String ipClient = getAdresseIP();
                WebSocketContainer container = ContainerProvider.getWebSocketContainer();
                String webSocketUrl = "ws://" + ipEntree + ":8080/?ipClient=" + ipClient;

                System.out.println("Tentative de connexion à: " + webSocketUrl);
                container.connectToServer(this, new URI(webSocketUrl));

                // Enregistrer l'adresse IP du serveur pour reconnection
                this.ipServeur = ipEntree;

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
                            setControllerAuth(authController);
                            stage.setScene(new Scene(root));
                            stage.show();
                        } catch (IOException e) {
                            System.err.println("Erreur lors du chargement de l'interface d'authentification: " + e.getMessage());
                            e.printStackTrace();
                        }
                    } else {
                        System.err.println("Aucune fenêtre active trouvée !");
                    }
                });

                System.out.println("Connecté au serveur WebSocket sur " + webSocketUrl);

            } catch (DeploymentException | URISyntaxException | IOException e) {
                System.err.println("Échec de connexion au serveur: " + e.getMessage());
                estConnecte = false;
                if (!reconnexion) {
                    seReconnecter();
                }
            } catch (Exception e) {
                System.err.println("Erreur inattendue lors de la connexion: " + e.getMessage());
                e.printStackTrace();
                estConnecte = false;
            }
        }).start();
    }

    /**
     * Récupère l'adresse IP locale de la machine.
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
            System.err.println("Erreur lors de la récupération de l'adresse IP: " + e.getMessage());
        }
        return "127.0.0.1"; // Fallback vers localhost
    }

    // Getters pour les tests et debugging
    public boolean isConnected() {
        return estConnecte && session != null && session.isOpen();
    }

    public String getServerIP() {
        return ipServeur;
    }
}