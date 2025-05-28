package client;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.json.JSONObject;

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

    @FXML
    public void initialize() {
        setupEventHandlers();
        applyFadeInAnimation(reunionContainer);
    }

    /**
     * Configure les gestionnaires d'événements pour les composants FXML
     */
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
     * Initialise le contrôleur avec les données nécessaires
     */
    public void initData(String reunionId, int userId, int organizerId, ClientWebSocket webSocket) {
        if (isInitialized) {
            System.out.println("ReunionController déjà initialisé, ignoré.");
            return;
        }

        this.currentReunionId = reunionId;
        this.currentUserId = userId;
        this.organizerId = organizerId;
        this.clientWebSocket = webSocket;
        this.isInitialized = true;

        // Configuration de la visibilité des invitations
        configureInvitationVisibility();

        // Configuration du statut de connexion
        updateConnectionStatus("Connecté à la réunion: " + reunionId, true);

        System.out.println("ReunionController initialisé - Réunion: " + reunionId + ", Utilisateur: " + userId);
    }

    /**
     * Configure la visibilité de la zone d'invitation selon les permissions
     */
    private void configureInvitationVisibility() {
        if (invitationArea != null) {
            boolean isOrganizer = (this.currentUserId == this.organizerId && this.currentUserId != -1);
            invitationArea.setVisible(isOrganizer);
            invitationArea.setManaged(isOrganizer);
        }
    }

    /**
     * Met à jour le statut de connexion
     */
    private void updateConnectionStatus(String message, boolean isConnected) {
        if (connectionStatus != null) {
            Platform.runLater(() -> {
                connectionStatus.setText(message);
                if (isConnected) {
                    connectionStatus.getStyleClass().remove("disconnected");
                    connectionStatus.getStyleClass().add("connected");
                } else {
                    connectionStatus.getStyleClass().remove("connected");
                    connectionStatus.getStyleClass().add("disconnected");
                }
            });
        }
    }

    /**
     * Traite les messages reçus du serveur
     */
    public void traiterMessageRecu(String message) {
        if (message == null || message.trim().isEmpty()) {
            System.err.println("Message vide reçu du serveur");
            return;
        }

        try {
            JSONObject json = new JSONObject(message);
            String messageType = json.optString("type");

            Platform.runLater(() -> {
                switch (messageType) {
                    case "newMessage":
                        handleNewMessage(json);
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
                        System.out.println("Type de message non géré: " + messageType);
                }
            });
        } catch (Exception e) {
            System.err.println("Erreur lors du traitement du message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Gère les nouveaux messages de chat
     */
    private void handleNewMessage(JSONObject json) {
        String sender = json.optString("sender", "Inconnu");
        String content = json.optString("content", "");

        if (!content.isEmpty()) {
            Label messageLabel = new Label(sender + ": " + content);
            messageLabel.getStyleClass().add("message-bubble");

            if (messageArea != null) {
                messageArea.getChildren().add(messageLabel);
            }
        }
    }

    /**
     * Gère les résultats d'invitation
     */
    private void handleInvitationResult(JSONObject json) {
        boolean success = json.optBoolean("success", false);
        String message = json.optString("message", "Aucun message du serveur");

        Alert alert = new Alert(success ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
        alert.setTitle(success ? "Invitation envoyée" : "Erreur d'invitation");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Gère l'arrivée d'un nouvel utilisateur
     */
    private void handleUserJoined(JSONObject json) {
        String username = json.optString("username", "Utilisateur inconnu");
        Label joinLabel = new Label("📥 " + username + " a rejoint la réunion");
        joinLabel.getStyleClass().add("system-message");

        if (messageArea != null) {
            messageArea.getChildren().add(joinLabel);
        }
    }

    /**
     * Gère le départ d'un utilisateur
     */
    private void handleUserLeft(JSONObject json) {
        String username = json.optString("username", "Utilisateur inconnu");
        Label leaveLabel = new Label("📤 " + username + " a quitté la réunion");
        leaveLabel.getStyleClass().add("system-message");

        if (messageArea != null) {
            messageArea.getChildren().add(leaveLabel);
        }
    }

    /**
     * Gère les messages d'erreur du serveur
     */
    private void handleError(JSONObject json) {
        String errorMessage = json.optString("message", "Erreur inconnue");
        showAlert(Alert.AlertType.ERROR, "Erreur", errorMessage);
    }

    /**
     * Envoie un message de chat
     */
    @FXML
    private void envoyerMessage() {
        if (!isInitialized) {
            showAlert(Alert.AlertType.WARNING, "Non initialisé",
                     "Le contrôleur n'est pas encore initialisé.");
            return;
        }

        String messageText = messageInput.getText();
        if (messageText == null || messageText.trim().isEmpty()) {
            return;
        }

        if (clientWebSocket == null) {
            showAlert(Alert.AlertType.ERROR, "Erreur de connexion",
                     "Pas de connexion au serveur. Impossible d'envoyer le message.");
            return;
        }

        try {
            JSONObject messageJson = new JSONObject();
            messageJson.put("modele", "reunion");
            messageJson.put("action", "envoyerMessage");
            messageJson.put("reunionId", String.valueOf(currentReunionId));
            messageJson.put("userId", String.valueOf(currentUserId));
            messageJson.put("contenu", messageText.trim());

            clientWebSocket.envoyerRequete(messageJson.toString());
            messageInput.clear();

        } catch (Exception e) {
            System.err.println("Erreur lors de l'envoi du message: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Erreur",
                     "Impossible d'envoyer le message: " + e.getMessage());
        }
    }

    /**
     * Gère l'invitation d'un utilisateur
     */
    @FXML
    private void handleInviteUser() {
        if (!isInitialized) {
            showAlert(Alert.AlertType.WARNING, "Non initialisé",
                     "Le contrôleur n'est pas encore initialisé.");
            return;
        }

        String usernameToInvite = inviteUserField.getText();
        if (usernameToInvite == null || usernameToInvite.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Champ vide",
                     "Veuillez saisir le nom d'utilisateur à inviter.");
            return;
        }

        // Vérifier les permissions
        if (currentUserId != organizerId) {
            showAlert(Alert.AlertType.ERROR, "Permission refusée",
                     "Seul l'organisateur peut inviter des membres.");
            return;
        }

        if (clientWebSocket == null) {
            showAlert(Alert.AlertType.ERROR, "Erreur de connexion",
                     "Pas de connexion au serveur. Impossible d'envoyer l'invitation.");
            return;
        }

        try {
            JSONObject inviteJson = new JSONObject();
            inviteJson.put("modele", "reunion");
            inviteJson.put("action", "inviterMembre");
            inviteJson.put("reunionId", String.valueOf(currentReunionId));
            inviteJson.put("usernameToInvite", usernameToInvite.trim());

            clientWebSocket.envoyerRequete(inviteJson.toString());
            inviteUserField.clear();

        } catch (Exception e) {
            System.err.println("Erreur lors de l'envoi de l'invitation: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Erreur",
                     "Impossible d'envoyer l'invitation: " + e.getMessage());
        }
    }

    /**
     * Applique une animation de fondu à l'ouverture
     */
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

    /**
     * Affiche une alerte avec le type, titre et message spécifiés
     */
    private void showAlert(Alert.AlertType type, String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * Nettoie les ressources avant la fermeture
     */
    public void cleanup() {
        if (clientWebSocket != null) {
            try {
                // Envoyer une notification de déconnexion
                JSONObject leaveJson = new JSONObject();
                leaveJson.put("modele", "reunion");
                leaveJson.put("action", "quitterReunion");
                leaveJson.put("reunionId", String.valueOf(currentReunionId));
                leaveJson.put("userId", String.valueOf(currentUserId));

                clientWebSocket.envoyerRequete(leaveJson.toString());
            } catch (Exception e) {
                System.err.println("Erreur lors de la fermeture: " + e.getMessage());
            }
        }

        // Réinitialiser les variables
        isInitialized = false;
        clientWebSocket = null;
        currentReunionId = null;
        currentUserId = -1;
        organizerId = -1;
    }

    // Getters pour les tests et le debugging
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