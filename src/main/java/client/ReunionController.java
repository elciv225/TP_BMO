package client;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
// import javafx.scene.paint.Color; // Non utilisé directement pour le style du point
// import javafx.scene.shape.Circle; // Non utilisé directement pour le style du point
import javafx.stage.Stage;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.IOException;
import java.time.Duration; // Remplacé java.time.Duration par son équivalent pour le calcul
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Timer;
import java.util.TimerTask;

public class ReunionController {

    @FXML private Label statusIndicator;
    @FXML private Label connectionStatus;
    @FXML private Label meetingDuration;
    @FXML private Button btnToggleParticipants;
    @FXML private VBox messageArea;
    @FXML private ScrollPane messageScrollPane;
    @FXML private TextField messageInput;
    @FXML private Button sendButton;
    @FXML private HBox invitationArea;
    @FXML private TextField inviteUserField;
    @FXML private Button inviteButton;
    @FXML private VBox participantsPane;
    @FXML private Label participantCountLabel;
    @FXML private ListView<String> participantsListView;

    private ClientWebSocket clientWebSocket;
    private String currentReunionId;
    private String currentReunionNom;
    private int currentUserId = -1;
    private int organizerId = -1;
    private String currentUserName = "Moi"; // Initialisé par défaut

    private Timer durationTimerObj;
    private LocalDateTime startTime;
    private ObservableList<String> participantsObservableList = FXCollections.observableArrayList();
    private boolean isInitialized = false; // Ajout du flag d'initialisation


    @FXML
    public void initialize() {
        participantsListView.setItems(participantsObservableList);
        participantsPane.setVisible(false);
        participantsPane.setManaged(false);
        updateStatusIndicatorStyle(false, "Connexion...");
        messageInput.setOnAction(event -> envoyerMessage());
    }

    public void setClientWebSocket(ClientWebSocket clientWebSocket) {
        this.clientWebSocket = clientWebSocket;
    }

    // Méthode pour vérifier si le contrôleur a été initialisé avec les données de la réunion
    public boolean isInitialized() {
        return isInitialized;
    }

    // Getter pour currentReunionId
    public String getCurrentReunionId() {
        return currentReunionId;
    }


    public void initData(String reunionId, int userId, String reunionNom, int organizerIdFromCaller, String userName) {
        this.currentReunionId = reunionId;
        this.currentUserId = userId;
        this.currentReunionNom = (reunionNom != null && !reunionNom.isEmpty()) ? reunionNom : "Réunion " + reunionId;
        this.organizerId = organizerIdFromCaller; // Utiliser l'ID de l'organisateur passé
        this.currentUserName = (userName != null && !userName.isEmpty()) ? userName : "Utilisateur " + userId;


        if (connectionStatus != null) {
            connectionStatus.setText(this.currentReunionNom);
        }

        if (invitationArea != null) {
            boolean isUserTheOrganizer = (this.currentUserId == this.organizerId && this.currentUserId != -1);
            invitationArea.setVisible(isUserTheOrganizer);
            invitationArea.setManaged(isUserTheOrganizer);
        }

        startTime = LocalDateTime.now();
        startDurationTimer();
        updateStatusIndicatorStyle(true, "Connecté");
        isInitialized = true; // Marquer comme initialisé

        fetchInitialReunionData();
    }

    // Surcharge pour compatibilité si userName n'est pas toujours fourni immédiatement
    public void initData(String reunionId, int userId, String reunionNom, int organizerIdFromCaller) {
        initData(reunionId, userId, reunionNom, organizerIdFromCaller, "Utilisateur " + userId);
    }


    private void fetchInitialReunionData() {
        if (clientWebSocket != null && clientWebSocket.isConnected() && isInitialized) {
            JSONObject participantsRequest = new JSONObject();
            participantsRequest.put("modele", "reunion");
            participantsRequest.put("action", "getParticipants");
            participantsRequest.put("reunionId", currentReunionId);
            clientWebSocket.envoyerRequete(participantsRequest.toString());

            JSONObject messagesRequest = new JSONObject();
            messagesRequest.put("modele", "reunion");
            messagesRequest.put("action", "getHistoriqueMessages");
            messagesRequest.put("reunionId", currentReunionId);
            clientWebSocket.envoyerRequete(messagesRequest.toString());
        }
    }


    private void updateStatusIndicatorStyle(boolean isConnected, String statusText) {
        Platform.runLater(() -> {
            if (statusIndicator == null) return;
            statusIndicator.getStyleClass().removeAll("connected", "disconnected", "connecting");
            if (isConnected) {
                statusIndicator.getStyleClass().add("connected");
            } else if ("Connexion...".equals(statusText) || "Reconnexion...".equals(statusText)) {
                statusIndicator.getStyleClass().add("connecting");
            } else {
                statusIndicator.getStyleClass().add("disconnected");
            }
            // Le texte du statut (connectionStatus) est géré par le nom de la réunion.
        });
    }


    private void startDurationTimer() {
        if (durationTimerObj != null) {
            durationTimerObj.cancel();
        }
        durationTimerObj = new Timer(true);
        durationTimerObj.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    if (startTime != null && meetingDuration != null) {
                        // Utilisation de java.time.Duration pour calculer la différence
                        long seconds = java.time.Duration.between(startTime, LocalDateTime.now()).getSeconds();
                        long hours = seconds / 3600;
                        long minutes = (seconds % 3600) / 60;
                        long secs = seconds % 60;
                        meetingDuration.setText(String.format("%02d:%02d:%02d", hours, minutes, secs));
                    }
                });
            }
        }, 0, 1000);
    }

    @FXML
    private void envoyerMessage() {
        String messageText = messageInput.getText();
        if (messageText == null || messageText.trim().isEmpty()) {
            return;
        }
        if (clientWebSocket == null || !clientWebSocket.isConnected()) {
            showAlert(Alert.AlertType.ERROR, "Erreur de Connexion", "Connexion au serveur perdue.");
            return;
        }

        JSONObject messageJson = new JSONObject();
        messageJson.put("modele", "reunion");
        messageJson.put("action", "envoyerMessage");
        messageJson.put("reunionId", currentReunionId);
        messageJson.put("userId", String.valueOf(currentUserId));
        messageJson.put("contenu", messageText.trim());
        // Le nom de l'expéditeur (currentUserName) sera ajouté par le serveur ou par le client lors de l'affichage

        clientWebSocket.envoyerRequete(messageJson.toString());
        messageInput.clear();
    }

    public void traiterMessageRecu(String messageString) {
        Platform.runLater(() -> {
            try {
                JSONObject json = new JSONObject(messageString);
                String type = json.optString("type");

                switch (type) {
                    case "newMessage":
                        displayMessage(json, false);
                        break;
                    case "userJoined":
                        String joinedUserName = json.optString("username", "Un utilisateur");
                        int joinedUserId = json.optInt("userId", -1);
                        addSystemMessage(joinedUserName + " a rejoint la réunion.");
                        if (!participantsObservableList.contains(joinedUserName) && (joinedUserId != currentUserId || !participantsObservableList.contains(currentUserName + " (Vous)"))) {
                             participantsObservableList.add(joinedUserId == currentUserId ? currentUserName + " (Vous)" : joinedUserName);
                        }
                        updateParticipantCountDisplay();
                        break;
                    case "userLeft":
                        String leftUserName = json.optString("username", "Un utilisateur");
                        // int leftUserId = json.optInt("userId", -1); // Non utilisé directement ici
                        addSystemMessage(leftUserName + " a quitté la réunion.");
                        participantsObservableList.remove(leftUserName);
                        participantsObservableList.remove(leftUserName + " (Vous)"); // Au cas où
                        updateParticipantCountDisplay();
                        break;
                    case "invitationResult":
                         handleInvitationResult(json);
                        break;
                    case "listeParticipants":
                        JSONArray participantsArray = json.optJSONArray("participants");
                        if (participantsArray != null) {
                            participantsObservableList.clear();
                            boolean selfInList = false;
                            for (int i = 0; i < participantsArray.length(); i++) {
                                JSONObject participantJson = participantsArray.optJSONObject(i);
                                String participantName = participantJson.optString("nom", "Participant inconnu");
                                int participantId = participantJson.optInt("id", -1);
                                if (participantId == currentUserId) {
                                    participantsObservableList.add(currentUserName + " (Vous)");
                                    selfInList = true;
                                } else {
                                    participantsObservableList.add(participantName);
                                }
                            }
                            if (!selfInList && currentUserName != null && !currentUserName.isEmpty()) {
                                // S'assurer que l'utilisateur actuel est dans la liste s'il n'y est pas déjà
                                // Cela peut arriver si la liste initiale du serveur ne contient pas l'utilisateur qui vient de rejoindre.
                                // participantsObservableList.add(0, currentUserName + " (Vous)"); // Ajouté en premier
                            }
                        }
                        updateParticipantCountDisplay();
                        break;
                    case "historiqueMessages":
                        JSONArray messagesArray = json.optJSONArray("messages");
                        if (messagesArray != null) {
                            boolean hadPreviousMessages = !messageArea.getChildren().isEmpty();
                            if(hadPreviousMessages && messageArea.getChildren().get(0) instanceof Label && ((Label)messageArea.getChildren().get(0)).getText().startsWith("Début de l'historique")){
                                // Ne pas re-effacer si on a déjà chargé l'historique une fois.
                            } else {
                                messageArea.getChildren().clear();
                                addSystemMessage("Début de l'historique des messages.");
                            }
                            for (int i = 0; i < messagesArray.length(); i++) {
                                displayMessage(messagesArray.getJSONObject(i), true);
                            }
                            if (!hadPreviousMessages && messagesArray.length() > 0) { // Seulement si c'est le premier chargement d'historique
                                 addSystemMessage("Fin de l'historique des messages.");
                                 Platform.runLater(() -> messageScrollPane.setVvalue(1.0)); // Scroll en bas après chargement historique
                            }
                        }
                        break;
                    case "error":
                        String errorMessage = json.optString("message", "Erreur inconnue du serveur.");
                        showAlert(Alert.AlertType.ERROR, "Erreur Serveur", errorMessage);
                        break;
                    default:
                        System.out.println("Type de message de réunion non géré: " + type + " Contenu: " + json.toString());
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Erreur de traitement du message de réunion: " + messageString);
            }
        });
    }

    private void displayMessage(JSONObject json, boolean isHistory) {
        String senderName = json.optString("sender", "Inconnu");
        String content = json.optString("content", "");
        int messageUserId = -1;
        Object userIdObj = json.opt("userId"); // Peut être String ou int
        if (userIdObj instanceof Integer) {
            messageUserId = (Integer) userIdObj;
        } else if (userIdObj instanceof String) {
            try {
                messageUserId = Integer.parseInt((String) userIdObj);
            } catch (NumberFormatException e) {
                System.err.println("Format d'ID utilisateur invalide dans le message: " + userIdObj);
            }
        }


        LocalDateTime timestamp;
        if (json.has("timestamp")) {
            Object tsObj = json.get("timestamp");
            if (tsObj instanceof Long) {
                timestamp = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli((Long) tsObj), java.time.ZoneId.systemDefault());
            } else {
                try {
                    timestamp = LocalDateTime.parse(tsObj.toString(), DateTimeFormatter.ISO_DATE_TIME); // Standard ISO
                } catch (DateTimeParseException e1) {
                    try {
                         // Essayer un autre format commun si le premier échoue
                        timestamp = LocalDateTime.parse(tsObj.toString(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    } catch (DateTimeParseException e2) {
                        timestamp = LocalDateTime.now();
                        System.err.println("Format de timestamp invalide: " + tsObj.toString() + ". Utilisation de l'heure actuelle.");
                    }
                }
            }
        } else {
            timestamp = LocalDateTime.now();
        }


        boolean isOwnMessage = (messageUserId == this.currentUserId);

        VBox messageContainer = new VBox(3);
        messageContainer.setMaxWidth(Double.MAX_VALUE);

        HBox bubbleWrapper = new HBox();
        VBox bubble = new VBox(2);
        bubble.getStyleClass().add("message-bubble");

        if (isOwnMessage) {
            bubble.getStyleClass().add("message-bubble-sent");
            bubbleWrapper.setAlignment(Pos.CENTER_RIGHT);
        } else {
            bubble.getStyleClass().add("message-bubble-received");
            bubbleWrapper.setAlignment(Pos.CENTER_LEFT);
            Label senderLabel = new Label(senderName);
            senderLabel.getStyleClass().add("message-sender-label");
            bubble.getChildren().add(senderLabel);
        }

        Label contentLabel = new Label(content);
        contentLabel.getStyleClass().add("message-content-label");
        bubble.getChildren().add(contentLabel);

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        Label timeLabel = new Label(timestamp.format(timeFormatter));
        timeLabel.getStyleClass().add("message-timestamp-label");

        HBox timeWrapper = new HBox(timeLabel);
        timeWrapper.setAlignment(Pos.CENTER_RIGHT); // Toujours à droite dans la bulle
        bubble.getChildren().add(timeWrapper);

        bubbleWrapper.getChildren().add(bubble);
        messageContainer.getChildren().add(bubbleWrapper);

        // Pour l'historique, on ajoute les messages en haut (ou dans l'ordre reçu du serveur)
        // Pour les nouveaux messages, on ajoute en bas.
        if (isHistory) {
            // Si l'historique est chargé en une fois, l'ordre est déjà géré par la boucle.
            // Si les messages d'historique arrivent un par un et doivent être insérés au début :
            // messageArea.getChildren().add(0, messageContainer); // Pour insérer en haut
            messageArea.getChildren().add(messageContainer); // Ajoute à la fin, suppose que le serveur envoie dans l'ordre chronologique
        } else {
            messageArea.getChildren().add(messageContainer);
        }

        if (!isHistory) {
             Platform.runLater(() -> messageScrollPane.setVvalue(1.0));
        }
    }

    private void addSystemMessage(String text) {
        Label systemLabel = new Label(text);
        systemLabel.getStyleClass().add("secondary-text");
        systemLabel.setMaxWidth(Double.MAX_VALUE);
        systemLabel.setAlignment(Pos.CENTER);
        systemLabel.setPadding(new Insets(5,0,5,0));
        messageArea.getChildren().add(systemLabel);
    }

    @FXML
    private void handleInviteUser() {
        String usernameToInvite = inviteUserField.getText();
        if (usernameToInvite == null || usernameToInvite.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Champ Requis", "Veuillez saisir le nom d'utilisateur à inviter.");
            return;
        }
        // La vérification de l'organisateur est faite dans initData pour afficher/cacher la zone
        // Mais une double vérification ici n'est pas mauvaise.
        if (currentUserId != organizerId && organizerId != -1) { // organizerId peut être -1 si non défini
            showAlert(Alert.AlertType.ERROR,"Permission Refusée", "Seul l'organisateur peut inviter des membres.");
            return;
        }

        JSONObject inviteJson = new JSONObject();
        inviteJson.put("modele", "reunion");
        inviteJson.put("action", "inviterMembre");
        inviteJson.put("reunionId", currentReunionId);
        inviteJson.put("usernameToInvite", usernameToInvite.trim());
        // L'ID de l'invitant (currentUserId) est implicite ou géré côté serveur

        clientWebSocket.envoyerRequete(inviteJson.toString());
    }

    private void handleInvitationResult(JSONObject json) {
        boolean success = json.optBoolean("success", false);
        String message = json.optString("message", "Aucun message du serveur.");
        if (success) {
            showAlert(Alert.AlertType.INFORMATION, "Invitation Envoyée", message);
            inviteUserField.clear();
        } else {
            showAlert(Alert.AlertType.ERROR, "Erreur d'Invitation", message);
        }
    }

    @FXML
    private void handleToggleParticipantsPane() {
        boolean isVisible = participantsPane.isVisible();
        participantsPane.setVisible(!isVisible);
        participantsPane.setManaged(!isVisible);

        if (!isVisible) { // Si on vient de le rendre visible
            // Rafraîchir la liste des participants
            JSONObject participantsRequest = new JSONObject();
            participantsRequest.put("modele", "reunion");
            participantsRequest.put("action", "getParticipants");
            participantsRequest.put("reunionId", currentReunionId);
            clientWebSocket.envoyerRequete(participantsRequest.toString());
        }
    }

    private void updateParticipantCountDisplay() {
        // Compte basé sur la liste observable qui devrait être la source de vérité
        int count = participantsObservableList.size();
        final int finalCount = count;
        Platform.runLater(() -> {
            if (participantCountLabel != null) {
                participantCountLabel.setText(finalCount + (finalCount > 1 ? " participants" : " participant"));
            }
            // Mettre à jour aussi le label dans le header principal si vous en avez un
            // btnToggleParticipants.setText("👥 (" + finalCount + ")"); // Exemple
        });
    }

    @FXML
    public void handleQuitterReunion() {
        System.out.println("L'utilisateur quitte la réunion: " + currentReunionId);
        if (durationTimerObj != null) {
            durationTimerObj.cancel();
            durationTimerObj = null;
        }
        isInitialized = false; // Marquer comme non initialisé

        if (clientWebSocket != null && clientWebSocket.isConnected()) {
            JSONObject leaveRequest = new JSONObject();
            leaveRequest.put("modele", "reunion");
            leaveRequest.put("action", "quitterReunion");
            leaveRequest.put("reunionId", currentReunionId);
            leaveRequest.put("userId", String.valueOf(currentUserId));
            clientWebSocket.envoyerRequete(leaveRequest.toString());
        }

        try {
            Stage stage = (Stage) messageArea.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/espaceUtilisateur.fxml"));
            Parent root = loader.load();

            EspaceUtilisateurController espaceController = loader.getController();
            if (clientWebSocket != null) {
                espaceController.setClientWebSocket(clientWebSocket);
                clientWebSocket.setControllerEspc(espaceController);
                clientWebSocket.setControllerReunion(null);
            }
            espaceController.setUserInfo(this.currentUserName.replace(" (Vous)",""), "", this.currentUserId); // Nettoyer "(Vous)"


            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setTitle("Espace Utilisateur");
            stage.centerOnScreen();

        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Erreur", "Impossible de retourner à l'espace utilisateur.");
        }
    }

    // Changé en public pour être accessible par ClientWebSocket
    public void showAlert(Alert.AlertType alertType, String title, String message) {
         Platform.runLater(() -> {
            Alert alert = new Alert(alertType);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            try {
                String cssPath = getClass().getResource("/styles/main.css").toExternalForm();
                if (cssPath != null) {
                    alert.getDialogPane().getStylesheets().add(cssPath);
                    alert.getDialogPane().getStyleClass().add("dialog-pane");
                }
            } catch (Exception e) {
                System.err.println("CSS pour alerte non trouvé: " + e.getMessage());
            }
            alert.showAndWait();
        });
    }
}
