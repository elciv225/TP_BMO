package client;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ReunionController {

    // FXML Components
    @FXML private Label connectionStatus;
    @FXML private VBox messageArea;
    @FXML private TextField messageInput;
    @FXML private Button sendButton;
    @FXML private HBox invitationArea;
    @FXML private TextField inviteUserField;
    @FXML private Button inviteButton;
    @FXML private VBox reunionContainer;

    // Instance variables
    private ClientWebSocket clientWebSocket;
    private String currentReunionId;
    private int currentUserId = -1;
    private int organizerId = -1;
    private boolean isInitialized = false;
    private String currentUserName = "";

    @FXML
    public void initialize() {
        setupEventHandlers();
        applyFadeInAnimation(reunionContainer);
    }

    private void setupEventHandlers() {
        if (sendButton != null) {
            sendButton.setOnAction(event -> envoyerMessage());
        }

        if (inviteButton != null) {
            inviteButton.setOnAction(event -> handleInviteUser());
        }

        if (messageInput != null) {
            messageInput.setOnKeyPressed(event -> {
                if (event.getCode().toString().equals("ENTER")) {
                    envoyerMessage();
                }
            });
        }
    }

    /**
     * Initialise les données du contrôleur pour une session de réunion.
     * Doit être appelé après le chargement de l'FXML.
     *
     * @param reunionId      L'ID de la réunion.
     * @param userId         L'ID de l'utilisateur actuel.
     * @param organizerId    L'ID de l'organisateur de la réunion.
     * @param webSocket      L'instance ClientWebSocket pour la communication.
     * @param userName       Le nom complet de l'utilisateur actuel.
     */
    public void initData(String reunionId, int userId, int organizerId, ClientWebSocket webSocket, String userName) {
        if (isInitialized) {
            System.out.println("INFO: ReunionController déjà initialisé pour la réunion " + this.currentReunionId + ". Nouvelle initialisation pour " + reunionId + " ignorée ou gérée avec précaution.");
            return;
        }

        this.currentReunionId = reunionId;
        this.currentUserId = userId;
        this.organizerId = organizerId;
        this.clientWebSocket = webSocket;
        this.currentUserName = userName != null ? userName : "Utilisateur";
        this.isInitialized = true;

        // NOUVEAU: Configurer la session WebSocket avec les IDs de réunion
        if (webSocket != null && webSocket.getSessionAuth() != null) {
            webSocket.getSessionAuth().getUserProperties().put("reunionId", reunionId);
            webSocket.getSessionAuth().getUserProperties().put("userId", String.valueOf(userId));
        }

        configureInvitationVisibility();
        updateConnectionStatus("Connecté à la réunion: " + reunionId, true);

        System.out.println("ReunionController initialisé - Réunion: " + reunionId + ", Utilisateur: " + userId + " (" + userName + ")");
    }

    /**
     * Initialise les données du contrôleur avec un nom d'utilisateur par défaut.
     * @deprecated Utiliser de préférence {@link #initData(String, int, int, ClientWebSocket, String)} pour fournir un nom d'utilisateur.
     */
    @Deprecated
    public void initData(String reunionId, int userId, int organizerId, ClientWebSocket webSocket) {
        initData(reunionId, userId, organizerId, webSocket, "Utilisateur Anonyme");
    }

    private void configureInvitationVisibility() {
        if (invitationArea != null) {
            boolean isOrganizer = (this.currentUserId == this.organizerId && this.currentUserId != -1);
            invitationArea.setVisible(isOrganizer);
            invitationArea.setManaged(isOrganizer);
        }
    }

    public void updateConnectionStatus(String message, boolean isConnected) {
        if (connectionStatus != null) {
            Platform.runLater(() -> {
                connectionStatus.setText(message);
                connectionStatus.getStyleClass().removeAll("connected", "disconnected", "connecting");
                if (message.contains("Tentative de reconnexion") || message.contains("Connexion en cours...")) {
                    connectionStatus.getStyleClass().add("connecting");
                } else {
                    connectionStatus.getStyleClass().add(isConnected ? "connected" : "disconnected");
                }
            });
        }
    }

    /**
     * Traite un message JSON entrant reçu via WebSocket pour cette réunion.
     * Dispatch les messages aux handlers appropriés en fonction de leur type.
     *
     * @param message Le message JSON brut reçu du serveur.
     */
    public void traiterMessageRecu(String message) {
        if (message == null || message.trim().isEmpty()) {
            System.err.println("ERREUR: Message vide reçu du serveur dans ReunionController.");
            return;
        }

        try {
            JSONObject json = new JSONObject(message);
            String messageType = json.optString("type");

            Platform.runLater(() -> {
                switch (messageType) {
                    case "newMessage":
                        handleNewMessageWhatsApp(json);
                        break;
                    case "invitationResult":
                        handleInvitationResult(json);
                        break;
                    case "userJoined":
                        handleUserJoined(json);
                        break;
                    case "userLeft":
                        handleUserLeft(json);
                        break;
                    case "error":
                        handleError(json);
                        break;
                    default:
                        System.err.println("AVERTISSEMENT: Type de message WebSocket non géré dans ReunionController: '" + messageType + "'. Message: " + json.toString(2));
                }
            });
        } catch (Exception e) {
            System.err.println("ERREUR: Échec du traitement du message JSON dans ReunionController: " + e.getMessage() + ". Message: " + message);
            e.printStackTrace();
        }
    }

    /**
     * Gère l'affichage d'un nouveau message de chat dans l'interface utilisateur.
     * Applique des styles différents pour les messages envoyés par l'utilisateur actuel et ceux reçus.
     *
     * @param json L'objet JSON contenant les détails du message (expéditeur, contenu, ID utilisateur).
     */
    private void handleNewMessageWhatsApp(JSONObject json) {
        String sender = json.optString("sender", "Inconnu");
        String content = json.optString("content", "");
        String userIdStr = json.optString("userId", "");
        // long timestamp = json.optLong("timestamp", System.currentTimeMillis()); // Timestamp du serveur, non utilisé actuellement pour l'affichage de l'heure locale.

        if (content.isEmpty()) return;

        // Créer le conteneur du message
        VBox messageContainer = new VBox();
        messageContainer.setSpacing(2);
        messageContainer.getStyleClass().add("message-container");

        // Vérifier si c'est notre message
        boolean isOwnMessage = userIdStr.equals(String.valueOf(currentUserId));

        // Créer la bulle de message
        Label messageLabel = new Label(content);
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(300);

        if (isOwnMessage) {
            messageLabel.getStyleClass().add("message-bubble-sent");
            messageContainer.setAlignment(Pos.CENTER_RIGHT);
        } else {
            messageLabel.getStyleClass().add("message-bubble-received");
            messageContainer.setAlignment(Pos.CENTER_LEFT);

            // Ajouter le nom de l'expéditeur pour les messages reçus
            Label senderLabel = new Label(sender);
            senderLabel.getStyleClass().add("message-sender");
            messageContainer.getChildren().add(senderLabel);
        }

        messageContainer.getChildren().add(messageLabel);

        // Ajouter l'heure
        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        Label timeLabel = new Label(timeStr);
        timeLabel.getStyleClass().add("message-time");

        if (isOwnMessage) {
            timeLabel.setAlignment(Pos.CENTER_RIGHT);
        } else {
            timeLabel.setAlignment(Pos.CENTER_LEFT);
        }

        messageContainer.getChildren().add(timeLabel);

        // Ajouter à la zone de messages
        if (messageArea != null) {
            messageArea.getChildren().add(messageContainer);

            // Animation d'apparition
            messageContainer.setOpacity(0);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(300), messageContainer);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            fadeIn.play();

            // Scroll automatique vers le bas
            Platform.runLater(() -> {
                if (messageArea.getParent() instanceof javafx.scene.control.ScrollPane) {
                    javafx.scene.control.ScrollPane scrollPane = (javafx.scene.control.ScrollPane) messageArea.getParent();
                    scrollPane.setVvalue(1.0);
                }
            });
        }
    }

    private void handleInvitationResult(JSONObject json) {
        boolean success = json.optBoolean("success", false);
        String serverMessage = json.optString("message", "Aucune réponse détaillée du serveur.");
        String title = success ? "Invitation Envoyée" : "Échec de l'Invitation";
        Alert.AlertType alertType = success ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR;

        showAlert(alertType, title, serverMessage);
    }

    private void handleUserJoined(JSONObject json) {
        String username = json.optString("username", "Un utilisateur");
        addSystemMessage("📥 " + username + " a rejoint la réunion.");
    }

    private void handleUserLeft(JSONObject json) {
        String username = json.optString("username", "Un utilisateur");
        addSystemMessage("📤 " + username + " a quitté la réunion.");
    }

    private void handleError(JSONObject json) {
        String errorMessage = json.optString("message", "Une erreur inconnue est survenue.");
        String errorTitle = json.optString("errorTitle", "Erreur de Réunion"); // Allow server to specify title

        // Check if this error is related to a specific failed action, e.g. sending a message
        String originalAction = json.optString("originalAction", "");
        if ("envoyerMessage".equals(originalAction)) {
            errorTitle = "Échec de l'Envoi du Message";
        }

        showAlert(Alert.AlertType.ERROR, errorTitle, errorMessage);
    }

    /**
     * Ajoute un message système (par exemple, utilisateur rejoint/quitte) à la zone de chat.
     *
     * @param message Le texte du message système à afficher.
     */
    private void addSystemMessage(String message) {
        Platform.runLater(() -> {
            VBox systemContainer = new VBox(); // Conteneur pour centrer le message système
            systemContainer.setAlignment(Pos.CENTER);
            systemContainer.setPadding(new Insets(5, 0, 5, 0));

            Label systemLabel = new Label(message);
            systemLabel.getStyleClass().add("system-message");

            systemContainer.getChildren().add(systemLabel);

            if (messageArea != null) {
                messageArea.getChildren().add(systemContainer);
            }
        });
    }

    @FXML
    private void envoyerMessage() {
        if (!isInitialized) {
            showAlert(Alert.AlertType.WARNING, "Fonctionnalité Indisponible",
                     "Le système de messagerie n'est pas prêt. Veuillez vérifier votre connexion.");
            return;
        }

        String messageText = messageInput.getText();
        if (messageText == null || messageText.trim().isEmpty()) {
            // Pas d'alerte pour un message vide, juste ne rien envoyer.
            return;
        }

        if (clientWebSocket == null || !clientWebSocket.isConnected()) {
            showAlert(Alert.AlertType.ERROR, "Erreur de Connexion",
                     "Connexion au serveur perdue. Impossible d'envoyer le message. Veuillez vérifier votre connexion internet.");
            updateConnectionStatus("Déconnecté. Impossible d'envoyer.", false);
            return;
        }

        try {
            JSONObject messageJson = new JSONObject();
            messageJson.put("modele", "reunion");
            messageJson.put("action", "envoyerMessage");
            messageJson.put("reunionId", currentReunionId);
            messageJson.put("userId", String.valueOf(currentUserId));
            messageJson.put("contenu", messageText.trim());

            // System.out.println("DEBUG: Envoi du message JSON: " + messageJson.toString());
            clientWebSocket.envoyerRequete(messageJson.toString());
            messageInput.clear();

        } catch (Exception e) {
            System.err.println("ERREUR: Exception lors de la construction ou l'envoi du message: " + e.getMessage());
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Erreur d'Envoi du Message",
                     "Votre message n'a pas pu être envoyé en raison d'une erreur technique interne: " + e.getClass().getSimpleName());
        }
    }

    @FXML
    private void handleInviteUser() {
        if (!isInitialized) {
            showAlert(Alert.AlertType.WARNING, "Fonctionnalité Indisponible",
                     "Le système d'invitation n'est pas prêt. Veuillez vérifier votre connexion.");
            return;
        }

        String usernameToInvite = inviteUserField.getText();
        if (usernameToInvite == null || usernameToInvite.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Champ Requis",
                     "Veuillez saisir le nom d'utilisateur de la personne à inviter.");
            return;
        }

        if (currentUserId != organizerId) {
            showAlert(Alert.AlertType.ERROR, "Action Non Autorisée",
                     "Seul l'organisateur de la réunion a le droit d'inviter des participants.");
            return;
        }

        if (clientWebSocket == null || !clientWebSocket.isConnected()) {
            showAlert(Alert.AlertType.ERROR, "Erreur de Connexion",
                     "Connexion au serveur perdue. Impossible d'envoyer l'invitation. Veuillez vérifier votre connexion internet.");
            updateConnectionStatus("Déconnecté. Impossible d'inviter.", false);
            return;
        }

        try {
            JSONObject inviteJson = new JSONObject();
            inviteJson.put("modele", "reunion");
            inviteJson.put("action", "inviterMembre");
            inviteJson.put("reunionId", currentReunionId);
            inviteJson.put("usernameToInvite", usernameToInvite.trim());

            clientWebSocket.envoyerRequete(inviteJson.toString());
            inviteUserField.clear();
            showAlert(Alert.AlertType.INFORMATION, "Invitation en Cours", "L'invitation pour '" + usernameToInvite + "' est en cours de traitement par le serveur.");


        } catch (Exception e) {
            System.err.println("ERREUR: Exception lors de l'envoi de l'invitation: " + e.getMessage());
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Erreur d'Envoi de l'Invitation",
                     "L'invitation n'a pas pu être envoyée en raison d'une erreur technique interne: " + e.getClass().getSimpleName());
        }
    }

    private void applyFadeInAnimation(Node node) {
        if (node != null) {
            node.setOpacity(0.0);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(500), node);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.setDelay(Duration.millis(100));
            fadeIn.play();
        }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    public void cleanup() {
        if (clientWebSocket != null) {
            try {
                JSONObject leaveJson = new JSONObject();
                leaveJson.put("modele", "reunion");
                leaveJson.put("action", "quitterReunion");
                leaveJson.put("reunionId", currentReunionId);
                leaveJson.put("userId", String.valueOf(currentUserId));

                clientWebSocket.envoyerRequete(leaveJson.toString());
                System.out.println("INFO: Message 'quitterReunion' envoyé pour utilisateur " + currentUserId + " de la réunion " + currentReunionId);
            } catch (Exception e) {
                System.err.println("ERREUR: Échec de l'envoi du message 'quitterReunion': " + e.getMessage());
            }
            // ClientWebSocket.clearReunionController() est appelé par EspaceUtilisateurController
            // lors de la fermeture de la fenêtre de réunion.
            // Si cleanup() est appelé pour d'autres raisons, la version sans paramètre peut être appelée ici
            // pour s'assurer que ce ReunionController n'est plus la cible des messages.
            // Cependant, pour éviter des appels multiples ou des ordres incorrects de nettoyage,
            // il est préférable de centraliser cela dans EspaceUtilisateurController.
            // Si cette instance de ReunionController doit être explicitement retirée de ClientWebSocket
            // sans que EspaceUtilisateurController ne soit immédiatement rétabli, on pourrait appeler:
            // clientWebSocket.clearReunionAndEspcControllers(); // ou une version plus spécifique
            // Pour l'instant, on se fie à EspaceUtilisateurController.
        }

        isInitialized = false; // Marquer comme non initialisé pour éviter toute action ultérieure.
        System.out.println("INFO: ReunionController nettoyé pour la réunion " + currentReunionId + " et l'utilisateur " + currentUserName);
        // Ne pas nullifier clientWebSocket ici, car il est géré par le contrôleur parent (EspaceUtilisateurController).
        currentReunionId = null; // Important pour la logique isInitialized et pour éviter la réutilisation incorrecte.
        currentUserId = -1; 
        organizerId = -1;
    }

    // Getters
    public String getCurrentReunionId() {
        return currentReunionId;
    }

    public int getCurrentUserId() {
        return currentUserId;
    }

    public boolean isInitialized() {
        return isInitialized;
    }
}