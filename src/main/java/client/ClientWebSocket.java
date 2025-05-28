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
import org.json.JSONObject; // Added import
import org.json.JSONArray; // Added import

@ClientEndpoint
public class ClientWebSocket {

    private Session session;
    private boolean reconnexion = false;
    private String ipServeur;
    private boolean estConnecte = false;
    private AuthentificationController controllerAuth;
    private EspaceUtilisateurController controllerEspc;
    private ReunionController reunionController; // Added

    public void setControllerAuth(AuthentificationController controller) {
        this.controllerAuth = controller;
        this.controllerEspc = null; // Clear other controllers
        this.reunionController = null;
    }

    public void setControllerEspc(EspaceUtilisateurController controller) {
        this.controllerEspc = controller;
        this.controllerAuth = null; // Clear other controllers
        this.reunionController = null;
    }

    public void setReunionController(ReunionController controller) {
        this.reunionController = controller;
        this.controllerAuth = null; // Clear other controllers
        this.controllerEspc = null;
    }

    public void clearReunionController() {
        this.reunionController = null;
        // Typically, after exiting a meeting, you might go back to EspaceUtilisateur.
        // The EspaceUtilisateurController should re-register itself if needed.
    }

    // Consider renaming getSessionAuth to something more generic if it's used by other controllers
    public Session getSession() { 
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
        System.out.println("Message reçu du serveur: " + message); // Log received message
        JSONObject jsonResponse = new JSONObject(message); // Use imported JSONObject
        String modele = jsonResponse.optString("modele");
        String action = jsonResponse.optString("actionOriginale", jsonResponse.optString("action")); // Prefer actionOriginale

        if ("chat".equals(modele) && reunionController != null) {
            if ("nouveauMessage".equals(action) || "messageRecu".equals(action)) { // "messageRecu" if server echoes back sent message
                String auteur = jsonResponse.optString("auteur", "Inconnu");
                String contenu = jsonResponse.optString("contenu");
                String timestamp = jsonResponse.optString("timestamp", java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
                reunionController.displayChatMessage(auteur, contenu, timestamp);
            } else if ("reponseHistoriqueMessages".equals(action) || "historiqueMessages".equals(action)) { // Match action from setMeetingData
                JSONArray messagesArray = jsonResponse.optJSONArray("messages"); // Use imported JSONArray
                if (messagesArray != null) {
                    for (int i = 0; i < messagesArray.length(); i++) {
                        JSONObject msgJson = messagesArray.getJSONObject(i); // Use imported JSONObject
                        String auteur = msgJson.optString("auteur", "Inconnu");
                        String contenu = msgJson.optString("contenu");
                        String timestamp = msgJson.optString("timestamp", java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
                        // Display in chronological order (assuming server sends them that way)
                        reunionController.displayChatMessage(auteur, contenu, timestamp);
                    }
                }
            }
        } else if (controllerAuth != null && "authentification".equals(modele)) {
            // Route to AuthentificationController if it's an auth response
             controllerAuth.traiterReponseConnexion(message); // This method needs to exist in AuthentificationController
        } else if (controllerEspc != null) {
            // Generic response for EspaceUtilisateurController (e.g. list meetings, create meeting response)
            // The EspaceUtilisateurController's handleServerResponse method should parse based on "actionOriginale"
            controllerEspc.handleServerResponse(message); 
        } else {
            System.err.println("Aucun contrôleur actif pour gérer le message du modèle: " + modele);
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
