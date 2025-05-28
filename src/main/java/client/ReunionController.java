package client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField; // Added
import javafx.scene.layout.VBox; // Added for messageArea
import javafx.stage.Stage;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDateTime; // For timestamp formatting (optional)
import java.time.format.DateTimeFormatter; // For timestamp formatting

public class ReunionController {

    @FXML private Label connectionStatus;
    @FXML private Label reunionTitleLabel;
    @FXML private Label reunionAgendaLabel;
    @FXML private Button exitMeetingButton;
    @FXML private TextField messageInput; // Added
    @FXML private Button sendButton; // Added
    @FXML private VBox messageArea; // Added

    private ClientWebSocket clientWebSocket;
    private String currentMeetingId; 

    @FXML
    public void initialize() {
        if (sendButton != null) {
            sendButton.setOnAction(event -> handleSendMessage());
        }
        // Ensure messageArea is cleared on initialization or when a new meeting is loaded
        if (messageArea != null) {
            messageArea.getChildren().clear();
        }
    }

    public void setClientWebSocket(ClientWebSocket clientWebSocket) {
        this.clientWebSocket = clientWebSocket;
        if (this.clientWebSocket != null) {
            this.clientWebSocket.setReunionController(this); // Register this controller
        }
    }
    
    public void setMeetingData(String meetingId, String title, String agenda) {
        this.currentMeetingId = meetingId;
        if (reunionTitleLabel != null) {
            reunionTitleLabel.setText(title);
        }
        if (reunionAgendaLabel != null) {
            reunionAgendaLabel.setText("Agenda: " + (agenda != null ? agenda : "Non défini"));
        }
        if (messageArea != null) { // Clear previous messages
            messageArea.getChildren().clear();
        }

        // Request message history
        if (this.clientWebSocket != null && this.currentMeetingId != null) {
            JSONObject historyRequest = new JSONObject();
            historyRequest.put("modele", "chat");
            historyRequest.put("action", "historiqueMessages");
            historyRequest.put("reunionId", this.currentMeetingId);
            this.clientWebSocket.envoyerRequete(historyRequest.toString());
        }
    }

    @FXML
    private void handleSendMessage() {
        if (messageInput == null || clientWebSocket == null || currentMeetingId == null) {
            System.err.println("Erreur: messageInput, clientWebSocket ou currentMeetingId non initialisé.");
            return;
        }
        String messageText = messageInput.getText().trim();
        if (!messageText.isEmpty()) {
            JSONObject messageJson = new JSONObject();
            messageJson.put("modele", "chat");
            messageJson.put("action", "envoyerMessage");
            messageJson.put("reunionId", this.currentMeetingId);
            messageJson.put("contenu", messageText);
            // Assuming currentUserId and name/prenom are available if author needs to be sent from client
            // For now, server will determine author from session
            // messageJson.put("auteurId", this.currentUserId); // Example if needed

            clientWebSocket.envoyerRequete(messageJson.toString());
            messageInput.clear();
        }
    }

    public void displayChatMessage(String author, String content, String timestamp) {
        Platform.runLater(() -> {
            if (messageArea != null) {
                Label messageLabel = new Label("[" + timestamp + "] " + author + ": " + content);
                messageLabel.setWrapText(true);
                messageArea.getChildren().add(messageLabel);
            } else {
                System.err.println("messageArea is null. Cannot display chat message.");
            }
        });
    }

    @FXML
    private void handleExitMeeting() {
        try {
            // Optional: Send a "left meeting" message to the server via WebSocket if needed
            // clientWebSocket.sendMessage(...);

            // Close WebSocket connection if it's specific to this meeting view
            if (clientWebSocket != null) {
                // clientWebSocket.close(); // Closing might be too abrupt if user wants to rejoin
                clientWebSocket.clearReunionController(); // Unregister this controller
            }

            Stage stage = (Stage) exitMeetingButton.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/espaceUtilisateur.fxml"));
            Parent root = loader.load();

            // Potentially pass back user info or re-initialize EspaceUtilisateurController if needed
            // EspaceUtilisateurController espaceController = loader.getController();
            // espaceController.setUserInfo(...); // This would require getting user info from somewhere

            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setTitle("Espace Utilisateur"); // Reset title
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            // Handle error loading the FXML (e.g., show an alert)
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Erreur de Navigation");
            alert.setHeaderText(null);
            alert.setContentText("Impossible de retourner à l'espace utilisateur: " + e.getMessage());
            alert.showAndWait();
        }
    }

    // Existing commented code - can be removed or adapted if chat functionality is added
    /*
    public void ajouterReunion(String titre, String description, String date) {
        JSONObject json = new JSONObject();
        json.put("modele", "reunion");
        json.put("action", "ajouter");
        json.put("titre", titre);
        json.put("description", description);
        json.put("date", date);
        // clientWebSocket.sendMessage(json.toString()); // Requires clientWebSocket to be initialized
    }
    */
}
