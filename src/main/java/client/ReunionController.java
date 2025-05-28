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
     * NOUVEAU: Initialise avec les données de l'utilisateur connecté
     */
    public void initData(String reunionId, int userId, int organizerId, ClientWebSocket webSocket, String userName) {
        if (isInitialized) {
            System.out.println("ReunionController déjà initialisé, ignoré.");
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
     * Version compatible avec l'ancienne méthode
     */
    public void initData(String reunionId, int userId, int organizerId, ClientWebSocket webSocket) {
        initData(reunionId, userId, organizerId, webSocket, "Utilisateur");
    }

    private void configureInvitationVisibility() {
        if (invitationArea != null) {
            boolean isOrganizer = (this.currentUserId == this.organizerId && this.currentUserId != -1);
            invitationArea.setVisible(isOrganizer);
            invitationArea.setManaged(isOrganizer);
        }
    }

    private void updateConnectionStatus(String message, boolean isConnected) {
        if (connectionStatus != null) {
            Platform.runLater(() -> {
                connectionStatus.setText(message);
                connectionStatus.getStyleClass().removeAll("connected", "disconnected");
                connectionStatus.getStyleClass().add(isConnected ? "connected" : "disconnected");
            });
        }
    }

    /**
     * NOUVEAU: Traite les messages reçus avec style WhatsApp
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
                        System.out.println("Type de message non géré: " + messageType);
                }
            });
        } catch (Exception e) {
            System.err.println("Erreur lors du traitement du message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * NOUVEAU: Gère les messages avec style WhatsApp
     */
    private void handleNewMessageWhatsApp(JSONObject json) {
        String sender = json.optString("sender", "Inconnu");
        String content = json.optString("content", "");
        String userIdStr = json.optString("userId", "");
        long timestamp = json.optLong("timestamp", System.currentTimeMillis());

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
        String message = json.optString("message", "Aucun message du serveur");

        Alert alert = new Alert(success ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
        alert.setTitle(success ? "Invitation envoyée" : "Erreur d'invitation");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void handleUserJoined(JSONObject json) {
        String username = json.optString("username", "Utilisateur inconnu");
        addSystemMessage("📥 " + username + " a rejoint la réunion");
    }

    private void handleUserLeft(JSONObject json) {
        String username = json.optString("username", "Utilisateur inconnu");
        addSystemMessage("📤 " + username + " a quitté la réunion");
    }

    private void handleError(JSONObject json) {
        String errorMessage = json.optString("message", "Erreur inconnue");
        showAlert(Alert.AlertType.ERROR, "Erreur", errorMessage);
    }

    /**
     * NOUVEAU: Ajoute un message système style WhatsApp
     */
    private void addSystemMessage(String message) {
        Platform.runLater(() -> {
            VBox systemContainer = new VBox();
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
            messageJson.put("reunionId", currentReunionId);
            messageJson.put("userId", String.valueOf(currentUserId));
            messageJson.put("contenu", messageText.trim());

            System.out.println("Envoi du message: " + messageJson.toString());
            clientWebSocket.envoyerRequete(messageJson.toString());
            messageInput.clear();

        } catch (Exception e) {
            System.err.println("Erreur lors de l'envoi du message: " + e.getMessage());
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Erreur",
                     "Impossible d'envoyer le message: " + e.getMessage());
        }
    }

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
            inviteJson.put("reunionId", currentReunionId);
            inviteJson.put("usernameToInvite", usernameToInvite.trim());

            clientWebSocket.envoyerRequete(inviteJson.toString());
            inviteUserField.clear();

        } catch (Exception e) {
            System.err.println("Erreur lors de l'envoi de l'invitation: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Erreur",
                     "Impossible d'envoyer l'invitation: " + e.getMessage());
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
            } catch (Exception e) {
                System.err.println("Erreur lors de la fermeture: " + e.getMessage());
            }
        }

        isInitialized = false;
        clientWebSocket = null;
        currentReunionId = null;
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