package client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox; // Added for invitationArea
import javafx.scene.control.Alert; // Added for error popups
// import javafx.scene.control.ListView; // If participantsList is added
import org.json.JSONObject;

public class ReunionController {

    @FXML private Label connectionStatus;
    @FXML private VBox messageArea;
    @FXML private TextField messageInput;
    @FXML private Button sendButton;

    // FXML fields for Invitation Feature
    @FXML private HBox invitationArea;
    @FXML private TextField inviteUserField;
    @FXML private Button inviteButton;
    // @FXML private ListView<String> participantsList; // If you add it

    private ClientWebSocket clientWebSocket;
    // Instance variables for IDs - to be set by initData
    private String currentReunionId = "defaultReunionId"; // Default value
    private int currentUserId = -1; // Default value, indicating not set
    private int organizerId = -1;   // Default value, indicating not set


    @FXML
    public void initialize() {
        // WebSocket initialization is deferred until initData provides necessary IDs.
        // Setup for Invitation Feature - event handler only
        if (inviteButton != null) {
            inviteButton.setOnAction(event -> handleInviteUser());
        }
        // Visibility of invitationArea will be handled by initData
    }

    // Method to initialize controller with necessary data
    public void initData(String reunionId, int userId, int organizerId) {
        this.currentReunionId = reunionId;
        this.currentUserId = userId;
        this.organizerId = organizerId;

        // Initialize ClientWebSocket with actual IDs
        try {
            String wsUri = String.format("ws://localhost:8080/?reunionId=%s&userId=%d", this.currentReunionId, this.currentUserId);
            clientWebSocket = new ClientWebSocket(wsUri);
            System.out.println("ClientWebSocket initialized with URI: " + wsUri);

            // Set onMessage handler
            clientWebSocket.setOnMessageHandler(message -> {
                Platform.runLater(() -> { // Ensure UI updates are on JavaFX Application Thread
                    JSONObject json = new JSONObject(message);
                    String messageType = json.optString("type");

                    if ("newMessage".equals(messageType)) {
                        String sender = json.optString("sender", "Unknown");
                        String content = json.getString("content");
                        // String msgReunionId = json.optString("reunionId"); // Optional: use if needed
                        // String msgUserId = json.optString("userId"); // Optional: use if needed
                        Label newMessageLabel = new Label(sender + ": " + content);
                        // Apply styles or add user-specific markers based on msgUserId if needed
                        messageArea.getChildren().add(newMessageLabel);
                    } else if ("invitationResult".equals(messageType)) {
                        boolean success = json.optBoolean("success", false);
                        String responseMessage = json.optString("message", "No message from server.");

                        Alert alert;
                        if (success) {
                            alert = new Alert(Alert.AlertType.INFORMATION, responseMessage);
                            alert.setTitle("Invitation Status");
                        } else {
                            alert = new Alert(Alert.AlertType.ERROR, responseMessage);
                            alert.setTitle("Invitation Failed");
                        }
                        alert.setHeaderText(null);
                        alert.showAndWait();
                    } else {
                        System.out.println("Received unknown message type: " + messageType + " | Full message: " + message);
                        // Handle other message types or log if necessary
                    }
                });
            });

            // Set onAction handler for the sendButton
            sendButton.setOnAction(event -> envoyerMessage());

                    // ... (existing onMessage handler logic from previous step)
                    // Ensure this part is correctly merged from your previous working code
                    Platform.runLater(() -> { // Ensure UI updates are on JavaFX Application Thread
                        JSONObject json = new JSONObject(message);
                        String messageType = json.optString("type");

                        if ("newMessage".equals(messageType)) {
                            String sender = json.optString("sender", "Unknown");
                            String content = json.getString("content");
                            Label newMessageLabel = new Label(sender + ": " + content);
                            messageArea.getChildren().add(newMessageLabel);
                        } else if ("invitationResult".equals(messageType)) {
                            boolean success = json.optBoolean("success", false);
                            String responseMessage = json.optString("message", "No message from server.");
                            Alert alert;
                            if (success) {
                                alert = new Alert(Alert.AlertType.INFORMATION, responseMessage);
                                alert.setTitle("Invitation Status");
                            } else {
                                alert = new Alert(Alert.AlertType.ERROR, responseMessage);
                                alert.setTitle("Invitation Failed");
                            }
                            alert.setHeaderText(null);
                            alert.showAndWait();
                        } else {
                            System.out.println("Received unknown message type: " + messageType + " | Full message: " + message);
                        }
                    });
                });

            // Set onAction handler for the sendButton (already in original initialize, ensure it's here)
            if (sendButton != null) { // Check if sendButton is injected
                 sendButton.setOnAction(event -> envoyerMessage());
            } else {
                System.err.println("ReunionController: sendButton is null in initData. FXML might not be loaded correctly or fx:id is missing.");
            }


        } catch (Exception e) {
            e.printStackTrace();
            if (connectionStatus != null) { // Check if connectionStatus is injected
                connectionStatus.setText("Failed to connect to WebSocket: " + e.getMessage());
            } else {
                 System.err.println("ReunionController: connectionStatus is null. FXML might not be loaded correctly or fx:id is missing.");
            }
        }

        // Visibility for Invitation UI
        if (invitationArea != null) {
            boolean isOrganizer = (this.currentUserId == this.organizerId && this.currentUserId != -1); // Ensure currentUserId is valid
            invitationArea.setVisible(isOrganizer);
            invitationArea.setManaged(isOrganizer); // Also hide from layout
        } else {
            System.err.println("ReunionController: invitationArea is null in initData. FXML might not be loaded correctly or fx:id is missing.");
        }
    }

    @FXML
    private void handleInviteUser() {
        String usernameToInvite = inviteUserField.getText();
        if (usernameToInvite == null || usernameToInvite.trim().isEmpty()) {
            // System.out.println("Username to invite cannot be empty."); // Logging replaced by alert
            Alert alert = new Alert(Alert.AlertType.WARNING, "Username to invite cannot be empty.");
            alert.setHeaderText(null);
            alert.setTitle("Input Error");
            alert.showAndWait();
            return;
        }

        if (clientWebSocket != null && clientWebSocket.isOpen()) {
            JSONObject request = new JSONObject();
            request.put("modele", "reunion");
            request.put("action", "inviterMembre");
            request.put("reunionId", this.currentReunionId); // Use instance variable
            request.put("usernameToInvite", usernameToInvite.trim());
            // inviterUserId is derived from session on server-side

            clientWebSocket.sendMessage(request.toString());
            System.out.println("Invitation request sent for: " + usernameToInvite.trim()); // Keep for logging
            inviteUserField.clear();
            // Client-side "Invitation request sent" Alert removed. Server provides feedback.

        } else {
            // System.out.println("Cannot send invitation. Not connected to server."); // Logging replaced by alert
            Alert alert = new Alert(Alert.AlertType.ERROR, "Not connected to the server. Cannot send invitation.");
            alert.setHeaderText(null);
            alert.setTitle("Connection Error");
            alert.showAndWait();
        }
    }

    public void envoyerMessage() {
        String messageText = messageInput.getText();
        if (messageText == null || messageText.trim().isEmpty()) {
            return; // Do nothing if the message is empty
        }

        if (clientWebSocket == null || !clientWebSocket.isOpen()) {
             Alert alert = new Alert(Alert.AlertType.ERROR, "Not connected to the server. Cannot send message.");
            alert.setHeaderText(null);
            alert.setTitle("Connection Error");
            alert.showAndWait();
            return;
        }

        JSONObject json = new JSONObject();
        json.put("modele", "reunion");
        json.put("action", "envoyerMessage");
        json.put("reunionId", this.currentReunionId); // Use instance variable
        json.put("userId", String.valueOf(this.currentUserId)); // Use instance variable, ensure String
        json.put("contenu", messageText);

        clientWebSocket.sendMessage(json.toString());
        messageInput.clear();
    }

    /*  Envoie dans la web
    public void ajouterReunion(String titre, String description, String date) {
        JSONObject json = new JSONObject();
        json.put("modele", "reunion");
        json.put("action", "ajouter");
        json.put("titre", titre);
        json.put("description", description);
        json.put("date", date);
        clientWebSocket.sendMessage(json.toString());
    }

     */
}
