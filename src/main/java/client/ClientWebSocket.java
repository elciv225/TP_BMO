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
    private AuthentificationController controllerAuth; // Garder privé
    private EspaceUtilisateurController controllerEspc; // Garder privé
    private ReunionController reunionController; // Garder privé

    public void setControllerAuth(AuthentificationController controller) {
        this.controllerAuth = controller;
    }

    public void setControllerEspc(EspaceUtilisateurController controller) {
        this.controllerEspc = controller;
        this.controllerAuth = null; // On est passé à l'espace utilisateur
        this.reunionController = null; // Pas encore en réunion
        System.out.println("EspaceUtilisateurController est actif.");
    }

    // Appelé par EspaceUtilisateurController lors de l'ouverture de la réunion
    public void setReunionController(ReunionController controller) {
        this.reunionController = controller;
        this.controllerEspc = null; // Relâcher la référence pour éviter les messages en double/conflit
        this.controllerAuth = null; // Idem pour la sécurité
        System.out.println("ReunionController est actif. EspaceUtilisateurController et AuthController désactivés pour la réception des messages.");
    }

    // Appelé lorsque la ReunionController est fermée ou n'est plus nécessaire
    public void clearReunionController(EspaceUtilisateurController newEspcController) {
        this.reunionController = null;
        this.controllerEspc = newEspcController; // Rétablir le contrôleur de l'espace utilisateur
        this.controllerAuth = null; // On ne retourne pas à l'auth
        System.out.println("ReunionController désactivé. EspaceUtilisateurController rétabli.");
    }

    // Surcharge pour les cas où EspaceUtilisateurController n'est pas immédiatement disponible (ex: fermeture brutale)
    // ou si on quitte la réunion pour retourner à un état où aucun contrôleur n'est "principal" pour les messages.
    public void clearReunionAndEspcControllers() {
        this.reunionController = null;
        this.controllerEspc = null; // Important si on revient à l'écran de connexion serveur par exemple
        // controllerAuth n'est généralement pas rétabli ici, il l'est lors de la connexion initiale.
        System.out.println("ReunionController et EspaceUtilisateurController désactivés.");
    }


    public Session getSession() { 
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    @OnOpen
    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        this.estConnecte = true;
        this.reconnexion = false;
        String successMessage = "Connecté au serveur WebSocket: " + this.ipServeur;
        System.out.println(successMessage + " (Session ID: " + session.getId() + ")");

        // Transition vers l'interface d'authentification après une connexion réussie (si applicable)
        Platform.runLater(() -> {
            Stage stage = (Stage) Stage.getWindows().stream()
                    .filter(Window::isShowing)
                    // Tenter d'identifier la fenêtre de connexion initiale par son ID racine.
                    .filter(w -> {
                        Scene scene = w.getScene();
                        if (scene != null) {
                            Parent rootNode = scene.getRoot();
                            return rootNode != null && "connexionServeurRootPane".equals(rootNode.getId());
                        }
                        return false;
                    })
                    .findFirst()
                    .orElse(null);

            if (stage != null) { // Si on a trouvé la fenêtre de connexion initiale
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/authentification.fxml"));
                    Parent authRoot = loader.load();
                    AuthentificationController newAuthController = loader.getController();
                    newAuthController.setClientWebSocket(this); // Ceci appellera setControllerAuth sur ClientWebSocket
                    
                    Scene scene = stage.getScene();
                    if (scene == null) { // Au cas où la scène n'existerait pas, peu probable
                        scene = new Scene(authRoot);
                        stage.setScene(scene);
                    } else {
                        scene.setRoot(authRoot);
                    }
                    stage.setTitle("Authentification");
                    newAuthController.showConnectionStatus("Connecté au serveur", true);
                    System.out.println("Transition vers l'écran d'authentification effectuée.");
                } catch (IOException e) {
                    System.err.println("CRITIQUE: Erreur lors du chargement de l'interface d'authentification: " + e.getMessage());
                    e.printStackTrace();
                    // Une alerte UI est difficile ici car le contexte UI est en transition et pourrait être instable.
                }
            } else {
                // Si la fenêtre de connexion initiale n'est pas trouvée (par ex., c'est une reconnexion),
                // mettre à jour le statut du contrôleur UI actuellement actif.
                updateUiConnectionStatus("Reconnexion au serveur réussie.", true, "Connecté");
            }
        });
    }


    @OnMessage
    public void onMessage(String message) {
        System.out.println("Message reçu du serveur: " + message);

        if (message != null && message.trim().startsWith("{")) {
            try {
                org.json.JSONObject jsonMessage = new org.json.JSONObject(message);
                String modele = jsonMessage.optString("modele");
                String action = jsonMessage.optString("action");
                String type = jsonMessage.optString("type");

                // Si ReunionController est actif, il a la priorité pour les messages de réunion
                if (this.reunionController != null && this.reunionController.isInitialized() && isReunionMessage(jsonMessage)) {
                    String finalMessage = message;
                    Platform.runLater(() -> this.reunionController.traiterMessageRecu(finalMessage));
                }
                // Sinon, si EspaceUtilisateurController est actif, il gère les messages (y compris les réponses initiales de réunion)
                else if (this.controllerEspc != null && isEspaceUtilisateurMessage(jsonMessage)) {
                    this.controllerEspc.traiterReponseConnexion(message);
                }
                // Sinon, si AuthentificationController est actif, il gère les messages d'authentification
                else if (this.controllerAuth != null && "authentification".equals(modele)) {
                    this.controllerAuth.traiterReponseConnexion(message);
                }
                // Cas où le message est une réponse de création/rejointe et EspaceUtilisateurController a été temporairement nullifié
                // Cela peut arriver si setReunionController a été appelé juste avant que la réponse arrive.
                // Il faut une logique pour que EspaceUtilisateurController (s'il est le demandeur) puisse recevoir cette réponse.
                // Pour l'instant, on logue si aucun contrôleur n'est actif ou si le message n'est pas ciblé.
                // Une solution plus robuste pourrait impliquer un mécanisme de callback ou un bus d'événements.
                // Cas des messages orphelins (réponses initiales de réunion arrivant après que ReunionController soit défini mais avant qu'il soit initialisé,
                // ou si EspaceUtilisateurController a été nullifié trop tôt).
                else if (("reunion".equals(modele) && ("reponseCreation".equals(action) || "reponseRejoindre".equals(action)))) {
                     System.err.println("AVERTISSEMENT: Réponse de création/rejointe de réunion ("+action+") reçue mais non routée car le contrôleur EspaceUtilisateur n'est pas actif ou ReunionController pas encore prêt. Message: " + message);
                     // Idéalement, ces messages devraient être mis en file d'attente ou gérés par un callback si EspaceUtilisateurController les attend.
                }
                 else {
                    System.err.println("AVERTISSEMENT: Message non routé (aucun contrôleur actif ou type/modèle non apparié): " + message + ". Modèle: " + modele + ", Action: " + action + ", Type: " + type);
                }
            } catch (Exception e) {
                System.err.println("Erreur lors du traitement du message JSON: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("Message texte (non-JSON) du serveur: " + message);
        }
    }

    // Détermine si un message est destiné à ReunionController
    private boolean isReunionMessage(org.json.JSONObject jsonMessage) {
        String modele = jsonMessage.optString("modele");
        String type = jsonMessage.optString("type"); // "newMessage", "userJoined", "userLeft"
        String action = jsonMessage.optString("action"); // "updateParticipants", "reunionClosed"

        // Messages que ReunionController doit gérer une fois qu'il est actif
        // Messages que ReunionController doit gérer une fois qu'il est actif
        if ("reunion".equals(modele)) {
            return "updateParticipants".equals(action) ||    // Liste des participants mise à jour
                   "reunionClosed".equals(action) ||       // La réunion a été terminée par le serveur
                   "broadcastMessage".equals(action);     // Message système diffusé par le serveur dans la réunion
        }
        // Les types suivants sont aussi gérés par ReunionController (souvent sans "modele":"reunion" explicite)
        return "newMessage".equals(type) ||                 // Nouveau message de chat
               "userJoined".equals(type) ||                 // Un utilisateur a rejoint
               "userLeft".equals(type) ||                   // Un utilisateur a quitté
               "errorReunion".equals(type) ||               // Erreur spécifique à la réunion en cours
               ("invitationResult".equals(type) && this.reunionController != null && this.reunionController.isInitialized()); // Résultat d'invitation pendant une réunion active
    }

    // Détermine si un message est destiné à EspaceUtilisateurController
    private boolean isEspaceUtilisateurMessage(org.json.JSONObject jsonMessage) {
        String modele = jsonMessage.optString("modele");
        String action = jsonMessage.optString("action");
        String type = jsonMessage.optString("type");

        // EspaceUtilisateurController gère les réponses initiales de création/rejointe de réunion
        if ("reunion".equals(modele) && ("reponseCreation".equals(action) || "reponseRejoindre".equals(action))) {
            return true;
        }
        // EspaceUtilisateurController peut aussi gérer les résultats d'invitation s'il n'y a pas de ReunionController actif
        // (par exemple, si l'invitation est faite depuis l'espace utilisateur avant de rejoindre la réunion)
        if ("invitationResult".equals(type) && (this.reunionController == null || !this.reunionController.isInitialized())) {
            return true;
        }
        // Gérer les erreurs générales qui ne sont pas des erreurs d'authentification ou des erreurs spécifiques à une réunion active
        if ("error".equals(type) && !"errorReunion".equals(type) && !"authentification".equals(modele)) {
             // Si ReunionController est actif, il devrait gérer ses propres erreurs via "errorReunion"
            if (this.reunionController != null && this.reunionController.isInitialized()) {
                return false; // Laisser ReunionController gérer ses erreurs
            }
            return true; // Erreur générale pour EspaceUtilisateurController
        }
        // Gérer d'autres messages d'information générale si nécessaire
        return "infoGenerale".equals(type); // Exemple pour des messages d'information non spécifiques
    }


    @OnClose
    public void onClose(CloseReason reason) {
        this.session = null;
        estConnecte = false;
        String message = "Déconnecté du serveur. Raison: " + reason.getReasonPhrase();
        System.out.println(message);
        updateUiConnectionStatus(message, false, "Déconnecté. Tentative de reconnexion...");
        seReconnecter();
    }

    @OnError
    public void onError(Throwable t) {
        String errorMessage = "Erreur de connexion WebSocket: " + t.getMessage();
        System.err.println(errorMessage);
        t.printStackTrace(); // Important pour le débogage
        estConnecte = false; // S'assurer que l'état est correctement mis à jour
        String feedbackMessage = "Erreur de connexion. Tentative de reconnexion...";
        updateUiConnectionStatus(errorMessage, false, feedbackMessage);
        seReconnecter();
    }

    /**
     * Reconnexion automatique après 3 secondes.
     */
    private void seReconnecter() {
        if (!reconnexion && ipServeur != null) { // 'reconnexion' agit comme un verrou pour éviter les tentatives multiples
            reconnexion = true;
            String reconnexionMessage = "Connexion perdue. Tentative de reconnexion dans 3 secondes...";
            System.out.println(reconnexionMessage);
            updateUiConnectionStatus(reconnexionMessage, false, reconnexionMessage);

            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> updateUiConnectionStatus("Reconnexion en cours...", false, "Reconnexion en cours..."));
                    connectToWebSocket(ipServeur); // Tente de se reconnecter
                }
            }, 3000);
        } else if (ipServeur == null) {
            System.err.println("Impossible de se reconnecter: l'adresse IP du serveur n'est pas définie.");
            updateUiConnectionStatus("Impossible de se reconnecter: IP serveur non définie.", false, "Déconnecté");
        } else {
            System.out.println("Reconnexion déjà en cours ou tentative récente.");
        }
    }


    private void updateUiConnectionStatus(String logMessage, boolean isConnected, String uiMessage) {
        Platform.runLater(() -> {
            if (reunionController != null && reunionController.isInitialized()) {
                reunionController.updateConnectionStatus(uiMessage, isConnected);
            } else if (controllerEspc != null) {
                 System.out.println("Info pour EspaceUtilisateur: " + uiMessage + " (Connecté: " + isConnected + ")");
                 // Afficher une alerte seulement si c'est une déconnexion inattendue et non une simple info.
                 if (!isConnected && (uiMessage.contains("Déconnecté") || uiMessage.contains("Erreur"))) { // Eviter alertes pour "Reconnexion en cours..."
                    controllerEspc.showAlert(false, "Problème de Connexion", uiMessage);
                 }
            } else if (controllerAuth != null) {
                controllerAuth.showConnectionStatus(uiMessage, isConnected);
            } else {
                // Si aucun contrôleur n'est actif, on ne peut pas afficher de statut UI.
                System.out.println("Aucun contrôleur UI actif pour mettre à jour le statut: " + uiMessage);
            }
        });
    }


    /**
     * Envoie une requête texte au serveur WebSocket.
     */
    public void envoyerRequete(String jsonRequete) {
        if (isConnected()) { 
            try {
                session.getBasicRemote().sendText(jsonRequete);
            } catch (IOException e) {
                System.err.println("ERREUR IO lors de l'envoi de la requête : " + e.getMessage());
                e.printStackTrace();
                estConnecte = false; 
                String uiError = "Échec de l'envoi des données. La connexion semble interrompue.";
                updateUiConnectionStatus("ERREUR IO lors de l'envoi: " + e.getMessage(), false, uiError);
                showAlertToActiveController(Alert.AlertType.ERROR, "Erreur Réseau Critique", uiError + " Veuillez vérifier votre connexion et réessayer.");
            }
        } else {
            String errorMsg = "Impossible d'envoyer la requête : connexion au serveur non établie/perdue.";
            System.err.println(errorMsg);
            updateUiConnectionStatus(errorMsg, false, "Déconnecté. Impossible d'envoyer.");
            showAlertToActiveController(Alert.AlertType.WARNING, "Connexion Perdue",
                "La connexion au serveur est interrompue. Tentative de reconnexion automatique...");
            seReconnecter(); 
        }
    }

    private void showAlertToActiveController(Alert.AlertType alertType, String title, String message) {
        Platform.runLater(() -> {
            if (reunionController != null && reunionController.isInitialized()) {
                // ReunionController a sa propre méthode showAlert, qui est déjà sur Platform.runLater.
                // Il est préférable qu'il gère ses propres alertes pour un meilleur contexte.
                System.out.println("Info/Alerte pour ReunionController (gérée par le contrôleur lui-même): " + title + " - " + message);
            } else if (controllerEspc != null) {
                controllerEspc.showAlert(alertType == Alert.AlertType.ERROR || alertType == Alert.AlertType.WARNING ? false : true, title, message);
            } else if (controllerAuth != null) {
                controllerAuth.showAlert(alertType == Alert.AlertType.ERROR || alertType == Alert.AlertType.WARNING ? false : true, title, message);
            } else {
                 System.err.println("ALERTE NON AFFICHÉE (aucun contrôleur UI actif): " + title + " - " + message);
            }
        });
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
                this.ipServeur = ipEntree; // Enregistrer pour reconnexion avant la tentative
                container.connectToServer(this, new URI(webSocketUrl));
                // onOpen mettra estConnecte à true et reconnexion à false

                // Si la connexion réussit, onOpen sera appelé.
                // La transition vers l'écran d'authentification se fait maintenant dans onOpen
                // via updateUiConnectionStatus pour assurer que la session est établie.

            } catch (DeploymentException | URISyntaxException | IOException e) {
                String detailedError = "Échec de connexion au serveur WebSocket (" + ipEntree + "): " + e.getMessage();
                System.err.println(detailedError);
                estConnecte = false; // Assurer que l'état est correct
                reconnexion = false; // Permettre une nouvelle tentative manuelle ou automatique

                Platform.runLater(() -> {
                    String userMessage = "Impossible de se connecter au serveur à l'adresse '" + ipEntree + "'. " +
                                         "Veuillez vérifier l'adresse IP du serveur, son état, et votre connexion réseau.";
                    if (controllerAuth != null) { // Le plus probable lors de la connexion initiale
                        controllerAuth.showAlert(false, "Échec de Connexion au Serveur", userMessage + "\nErreur: " + e.getClass().getSimpleName());
                        controllerAuth.showConnectionStatus("Échec de connexion.", false);
                    } else if (controllerEspc != null) { 
                         controllerEspc.showAlert(false, "Échec de Reconnexion Serveur", "La tentative de reconnexion au serveur a échoué. " + e.getMessage());
                    } else if (reunionController != null && reunionController.isInitialized()) { 
                        reunionController.updateConnectionStatus("Reconnexion au serveur échouée.", false);
                    } else {
                        // Fallback si aucun contrôleur UI actif.
                        System.err.println("ALERTE CRITIQUE (non affichée à l'utilisateur): " + userMessage + " Erreur: " + e.getClass().getSimpleName());
                    }
                });
            } catch (Exception e) { // Capturer toute autre exception pour éviter que le thread ne meure silencieusement
                String criticalErrorMsg = "Une erreur critique et inattendue est survenue lors de la tentative de connexion au serveur.";
                System.err.println(criticalErrorMsg + " " + e.getMessage());
                e.printStackTrace();
                estConnecte = false;
                reconnexion = false; // Permettre une nouvelle tentative
                Platform.runLater(() -> {
                     showAlertToActiveController(Alert.AlertType.ERROR, "Erreur Critique de Connexion", criticalErrorMsg + " Veuillez consulter les logs et redémarrer l'application si le problème persiste.");
                });
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