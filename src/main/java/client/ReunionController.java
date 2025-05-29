package client;

import javafx.animation.FadeTransition;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
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
import java.util.concurrent.atomic.AtomicInteger;

public class ReunionController {

    // FXML Components modernes
    @FXML private Label connectionStatus;
    @FXML private Label statusIndicator;
    @FXML private Label participantCount;
    @FXML private Label meetingDuration;
    @FXML private VBox messageArea;
    @FXML private TextField messageInput;
    @FXML private Button sendButton;
    @FXML private HBox invitationArea;
    @FXML private TextField inviteUserField;
    @FXML private Button inviteButton;
    @FXML private VBox reunionContainer;
    @FXML private VBox sidePanel;
    @FXML private HBox toolBar;
    @FXML private VBox participantsList;

    // Instance variables
    private ClientWebSocket clientWebSocket;
    private String currentReunionId;
    private int currentUserId = -1;
    private int organizerId = -1;
    private boolean isInitialized = false;
    private String currentUserName = "";
    private Timeline durationTimer;
    private LocalDateTime startTime;
    private AtomicInteger participantCounter = new AtomicInteger(1);

    @FXML
    public void initialize() {
        setupEventHandlers();
        setupAnimations();
        applyFadeInAnimation(reunionContainer);
        startTime = LocalDateTime.now();
        startDurationTimer();
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

            // Animation du bouton d'envoi basée sur le texte
            messageInput.textProperty().addListener((obs, oldText, newText) -> {
                updateSendButtonState(newText != null && !newText.trim().isEmpty());
            });
        }
    }

    private void setupAnimations() {
        // Animation du status indicator
        if (statusIndicator != null) {
            Timeline blinkTimeline = new Timeline(
                new KeyFrame(Duration.ZERO, e -> statusIndicator.setText("🔴")),
                new KeyFrame(Duration.seconds(1), e -> statusIndicator.setText("🟢"))
            );
            blinkTimeline.setCycleCount(Timeline.INDEFINITE);
            blinkTimeline.play();
        }
    }

    private void startDurationTimer() {
        if (meetingDuration != null) {
            durationTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateDuration()));
            durationTimer.setCycleCount(Timeline.INDEFINITE);
            durationTimer.play();
        }
    }

    private void updateDuration() {
        if (startTime != null && meetingDuration != null) {
            long seconds = java.time.Duration.between(startTime, LocalDateTime.now()).getSeconds();
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            long secs = seconds % 60;

            Platform.runLater(() -> {
                meetingDuration.setText(String.format("⏱️ %02d:%02d:%02d", hours, minutes, secs));
            });
        }
    }

    private void updateSendButtonState(boolean hasText) {
        if (sendButton != null) {
            Platform.runLater(() -> {
                if (hasText) {
                    sendButton.setStyle(sendButton.getStyle() + "; -fx-scale-x: 1.1; -fx-scale-y: 1.1;");
                } else {
                    sendButton.setStyle(sendButton.getStyle().replace("; -fx-scale-x: 1.1; -fx-scale-y: 1.1;", ""));
                }
            });
        }
    }

    /**
     * 🆕 Initialisation moderne avec feedback visuel
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

        // Configuration WebSocket
        if (this.clientWebSocket != null) {
            this.clientWebSocket.setControllerReunion(this);
        }
        // Removed:
        // if (webSocket != null && webSocket.getSessionAuth() != null) {
        //     webSocket.getSessionAuth().getUserProperties().put("reunionId", reunionId);
        //     webSocket.getSessionAuth().getUserProperties().put("userId", String.valueOf(userId));
        // }

        configureInvitationVisibility();
        updateConnectionStatus("🎥 Réunion " + reunionId + " en cours", true);

        // Message de bienvenue personnalisé
        addWelcomeMessage();

        System.out.println("ReunionController initialisé - Réunion: " + reunionId + ", Utilisateur: " + userId + " (" + userName + ")");
    }

    public void initData(String reunionId, int userId, int organizerId, ClientWebSocket webSocket) {
        initData(reunionId, userId, organizerId, webSocket, "Utilisateur");
    }

    private void configureInvitationVisibility() {
        if (invitationArea != null) {
            boolean isOrganizer = (this.currentUserId == this.organizerId && this.currentUserId != -1);
            invitationArea.setVisible(isOrganizer);
            invitationArea.setManaged(isOrganizer);

            if (isOrganizer) {
                // Animation d'apparition pour l'organisateur
                FadeTransition fadeIn = new FadeTransition(Duration.millis(500), invitationArea);
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(1.0);
                fadeIn.play();
            }
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
     * 🎨 Message de bienvenue moderne
     */
    private void addWelcomeMessage() {
        Platform.runLater(() -> {
            VBox welcomeContainer = new VBox();
            welcomeContainer.setAlignment(Pos.CENTER);
            welcomeContainer.setSpacing(10);
            welcomeContainer.getStyleClass().add("welcome-message-container");
            welcomeContainer.setPadding(new Insets(20));

            // Style moderne pour le conteneur
            welcomeContainer.setStyle(
                "-fx-background-color: linear-gradient(135deg, rgba(91, 95, 199, 0.1) 0%, rgba(102, 126, 234, 0.1) 100%);" +
                "-fx-border-radius: 15px; -fx-background-radius: 15px;" +
                "-fx-border-color: rgba(91, 95, 199, 0.2); -fx-border-width: 1px;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 8, 0.3, 0, 2);"
            );

            Label welcomeIcon = new Label("🎉");
            welcomeIcon.setStyle("-fx-font-size: 2.5em;");

            Label welcomeTitle = new Label("Bienvenue " + currentUserName + " !");
            welcomeTitle.setStyle("-fx-font-size: 1.4em; -fx-font-weight: 600; -fx-text-fill: #5b5fc7;");

            Label welcomeSubtitle = new Label("Réunion " + currentReunionId + " • Commencez à discuter");
            welcomeSubtitle.setStyle("-fx-font-size: 1em; -fx-text-fill: #6c757d;");

            welcomeContainer.getChildren().addAll(welcomeIcon, welcomeTitle, welcomeSubtitle);

            if (messageArea != null) {
                messageArea.getChildren().add(0, welcomeContainer);

                // Animation d'apparition
                welcomeContainer.setOpacity(0);
                FadeTransition fadeIn = new FadeTransition(Duration.millis(800), welcomeContainer);
                fadeIn.setFromValue(0);
                fadeIn.setToValue(1);
                fadeIn.setDelay(Duration.millis(300));
                fadeIn.play();
            }
        });
    }

    /**
     * 🎨 Traitement moderne des messages avec animations
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
                        handleNewMessageModern(json);
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
     * 🎨 Messages style moderne avec animations fluides
     */
    private void handleNewMessageModern(JSONObject json) {
        String sender = json.optString("sender", "Inconnu");
        String content = json.optString("content", "");
        String userIdStr = json.optString("userId", "");
        long timestamp = json.optLong("timestamp", System.currentTimeMillis());

        if (content.isEmpty()) return;

        // Créer le conteneur principal du message
        VBox messageContainer = new VBox();
        messageContainer.setSpacing(5);
        messageContainer.setPadding(new Insets(8, 0, 8, 0));

        // Vérifier si c'est notre message
        boolean isOwnMessage = userIdStr.equals(String.valueOf(currentUserId));

        // Créer la bulle de message moderne
        VBox bubbleContainer = new VBox();
        bubbleContainer.setSpacing(3);
        bubbleContainer.setMaxWidth(600);

        // Nom de l'expéditeur (seulement pour les messages reçus)
        if (!isOwnMessage) {
            Label senderLabel = new Label(sender);
            senderLabel.setStyle(
                "-fx-font-size: 0.85em; -fx-font-weight: 600; -fx-text-fill: #5b5fc7; " +
                "-fx-padding: 0 0 3 15;"
            );
            bubbleContainer.getChildren().add(senderLabel);
        }

        // Bulle de message
        Label messageLabel = new Label(content);
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(500);

        String bubbleStyle;
        if (isOwnMessage) {
            bubbleStyle =
                "-fx-background-color: linear-gradient(135deg, #5b5fc7 0%, #667eea 100%);" +
                "-fx-text-fill: white;" +
                "-fx-background-radius: 18px 18px 4px 18px;" +
                "-fx-border-radius: 18px 18px 4px 18px;" +
                "-fx-padding: 12px 16px;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 3, 0.3, 0, 1);" +
                "-fx-font-size: 1em;";
            messageContainer.setAlignment(Pos.CENTER_RIGHT);
        } else {
            bubbleStyle =
                "-fx-background-color: white;" +
                "-fx-text-fill: #212529;" +
                "-fx-background-radius: 18px 18px 18px 4px;" +
                "-fx-border-radius: 18px 18px 18px 4px;" +
                "-fx-padding: 12px 16px;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 3, 0.3, 0, 1);" +
                "-fx-border-color: rgba(0,0,0,0.05); -fx-border-width: 1px;" +
                "-fx-font-size: 1em;";
            messageContainer.setAlignment(Pos.CENTER_LEFT);
        }

        messageLabel.setStyle(bubbleStyle);
        bubbleContainer.getChildren().add(messageLabel);

        // Heure du message
        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        Label timeLabel = new Label(timeStr);
        timeLabel.setStyle(
            "-fx-font-size: 0.8em; -fx-text-fill: #9ca3af; " +
            "-fx-padding: 2 0 0 " + (isOwnMessage ? "0" : "15") + ";"
        );

        if (isOwnMessage) {
            timeLabel.setAlignment(Pos.CENTER_RIGHT);
        }

        bubbleContainer.getChildren().add(timeLabel);
        messageContainer.getChildren().add(bubbleContainer);

        // Ajouter à la zone de messages
        if (messageArea != null) {
            messageArea.getChildren().add(messageContainer);

            // Animation d'apparition moderne
            messageContainer.setOpacity(0);
            messageContainer.setTranslateY(20);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(400), messageContainer);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);

            // Animation de glissement
            Timeline slideIn = new Timeline(
                new KeyFrame(Duration.ZERO,
                    e -> messageContainer.setTranslateY(20)),
                new KeyFrame(Duration.millis(300),
                    e -> messageContainer.setTranslateY(0))
            );

            fadeIn.play();
            slideIn.play();

            // Scroll automatique vers le bas
            Platform.runLater(() -> scrollToBottom());
        }
    }

    private void scrollToBottom() {
        if (messageArea != null && messageArea.getParent() instanceof javafx.scene.control.ScrollPane) {
            javafx.scene.control.ScrollPane scrollPane = (javafx.scene.control.ScrollPane) messageArea.getParent();
            Platform.runLater(() -> scrollPane.setVvalue(1.0));
        }
    }

    private void handleInvitationResult(JSONObject json) {
        boolean success = json.optBoolean("success", false);
        String message = json.optString("message", "Aucun message du serveur");

        showModernAlert(
            success ? "✅ Invitation envoyée" : "❌ Erreur d'invitation",
            message,
            success ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR
        );

        if (success && inviteUserField != null) {
            inviteUserField.clear();
        }
    }

    private void handleUserJoined(JSONObject json) {
        String username = json.optString("username", "Utilisateur inconnu");
        addSystemMessage("👋 " + username + " a rejoint la réunion", "#28a745");
        updateParticipantCount(participantCounter.incrementAndGet());
    }

    private void handleUserLeft(JSONObject json) {
        String username = json.optString("username", "Utilisateur inconnu");
        addSystemMessage("👋 " + username + " a quitté la réunion", "#dc3545");
        updateParticipantCount(participantCounter.decrementAndGet());
    }

    private void handleError(JSONObject json) {
        String errorMessage = json.optString("message", "Erreur inconnue");
        showModernAlert("❌ Erreur", errorMessage, Alert.AlertType.ERROR);
    }

    private void updateParticipantCount(int count) {
        if (participantCount != null) {
            Platform.runLater(() -> {
                participantCount.setText("👥 Participants: " + count);
            });
        }
    }

    /**
     * 🎨 Messages système modernes
     */
    private void addSystemMessage(String message, String color) {
        Platform.runLater(() -> {
            VBox systemContainer = new VBox();
            systemContainer.setAlignment(Pos.CENTER);
            systemContainer.setPadding(new Insets(10, 0, 10, 0));

            Label systemLabel = new Label(message);
            systemLabel.setStyle(
                "-fx-background-color: rgba(248, 249, 250, 0.9);" +
                "-fx-text-fill: " + color + ";" +
                "-fx-background-radius: 20px;" +
                "-fx-border-radius: 20px;" +
                "-fx-padding: 8px 16px;" +
                "-fx-font-size: 0.9em;" +
                "-fx-font-weight: 500;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 2, 0.2, 0, 1);" +
                "-fx-border-color: " + color + ";" +
                "-fx-border-width: 1px;"
            );

            systemContainer.getChildren().add(systemLabel);

            if (messageArea != null) {
                messageArea.getChildren().add(systemContainer);

                // Animation
                systemContainer.setOpacity(0);
                FadeTransition fadeIn = new FadeTransition(Duration.millis(500), systemContainer);
                fadeIn.setFromValue(0);
                fadeIn.setToValue(1);
                fadeIn.play();

                Platform.runLater(() -> scrollToBottom());
            }
        });
    }

    @FXML
    private void envoyerMessage() {
        if (!isInitialized) {
            showModernAlert("⚠️ Non initialisé", "Le contrôleur n'est pas encore prêt.", Alert.AlertType.WARNING);
            return;
        }

        String messageText = messageInput.getText();
        if (messageText == null || messageText.trim().isEmpty()) {
            return;
        }

        if (clientWebSocket == null) {
            showModernAlert("❌ Connexion perdue", "Impossible d'envoyer le message.\nVeuillez vérifier votre connexion.", Alert.AlertType.ERROR);
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

            // Animation du bouton après envoi
            if (sendButton != null) {
                Timeline pulseAnimation = new Timeline(
                    new KeyFrame(Duration.ZERO, e -> sendButton.setStyle(sendButton.getStyle() + "; -fx-scale-x: 0.9; -fx-scale-y: 0.9;")),
                    new KeyFrame(Duration.millis(100), e -> sendButton.setStyle(sendButton.getStyle().replace("; -fx-scale-x: 0.9; -fx-scale-y: 0.9;", "")))
                );
                pulseAnimation.play();
            }

        } catch (Exception e) {
            System.err.println("Erreur lors de l'envoi du message: " + e.getMessage());
            e.printStackTrace();
            showModernAlert("❌ Erreur d'envoi", "Impossible d'envoyer le message: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    private void handleInviteUser() {
        if (!isInitialized) {
            showModernAlert("⚠️ Non initialisé", "Le contrôleur n'est pas encore prêt.", Alert.AlertType.WARNING);
            return;
        }

        String usernameToInvite = inviteUserField.getText();
        if (usernameToInvite == null || usernameToInvite.trim().isEmpty()) {
            showModernAlert("⚠️ Champ requis", "Veuillez saisir le nom d'utilisateur à inviter.", Alert.AlertType.WARNING);
            return;
        }

        if (currentUserId != organizerId) {
            showModernAlert("🚫 Permission refusée", "Seul l'organisateur peut inviter des membres.", Alert.AlertType.ERROR);
            return;
        }

        if (clientWebSocket == null) {
            showModernAlert("❌ Connexion perdue", "Impossible d'envoyer l'invitation.\nVeuillez vérifier votre connexion.", Alert.AlertType.ERROR);
            return;
        }

        try {
            JSONObject inviteJson = new JSONObject();
            inviteJson.put("modele", "reunion");
            inviteJson.put("action", "inviterMembre");
            inviteJson.put("reunionId", currentReunionId);
            inviteJson.put("usernameToInvite", usernameToInvite.trim());

            clientWebSocket.envoyerRequete(inviteJson.toString());

            // Animation du bouton d'invitation
            if (inviteButton != null) {
                Timeline pulseAnimation = new Timeline(
                    new KeyFrame(Duration.ZERO, e -> inviteButton.setStyle(inviteButton.getStyle() + "; -fx-scale-x: 0.95; -fx-scale-y: 0.95;")),
                    new KeyFrame(Duration.millis(150), e -> inviteButton.setStyle(inviteButton.getStyle().replace("; -fx-scale-x: 0.95; -fx-scale-y: 0.95;", "")))
                );
                pulseAnimation.play();
            }

        } catch (Exception e) {
            System.err.println("Erreur lors de l'envoi de l'invitation: " + e.getMessage());
            showModernAlert("❌ Erreur d'envoi", "Impossible d'envoyer l'invitation: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void applyFadeInAnimation(Node node) {
        if (node != null) {
            node.setOpacity(0.0);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(600), node);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.setDelay(Duration.millis(150));
            fadeIn.play();
        }
    }

    /**
     * 🎨 Alertes modernes style Teams
     */
    private void showModernAlert(String title, String message, Alert.AlertType type) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);

            // Style moderne
            alert.getDialogPane().getStylesheets().add(getClass().getResource("/styles/main.css").toExternalForm());
            alert.getDialogPane().getStyleClass().add("modern-alert");

            alert.showAndWait();
        });
    }

    public void cleanup() {
        if (durationTimer != null) {
            durationTimer.stop();
        }

        if (this.clientWebSocket != null) { // Deregister from ClientWebSocket
            this.clientWebSocket.setControllerReunion(null);
        }

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
    public String getCurrentReunionId() { return currentReunionId; }
    public int getCurrentUserId() { return currentUserId; }
    public boolean isInitialized() { return isInitialized; }
}