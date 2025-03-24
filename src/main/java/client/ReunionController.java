package client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class ReunionController implements ClientWebSocket.MessageListener {

    @FXML private Label connectionStatus;
    private ClientWebSocket clientWebSocket;

    @FXML
    public void initialize() {
        clientWebSocket = new ClientWebSocket(this);
    }

    /**
     * Gère la réception des messages du serveur WebSocket.
     */
    @Override
    public void onMessageReceived(String message) {
        Platform.runLater(() -> {
            System.out.println("Message reçu: " + message);
            // Ajouter ici le traitement UI si nécessaire
        });
    }

    /**
     * Met à jour le label d'état de connexion.
     */
    @Override
    public void onConnectionStatusChanged(String status, String color) {
        Platform.runLater(() -> {
            connectionStatus.setText("État de la connexion: " + status);
            connectionStatus.setStyle("-fx-text-fill: " + color + ";");
        });
    }

    /**
     * Permet d'envoyer un message via WebSocket.
     */
    public void sendMessageToServer(String message) {
        clientWebSocket.sendMessage(message);
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
