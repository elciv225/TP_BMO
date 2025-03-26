package client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class ReunionController {

    @FXML private Label connectionStatus;
    private ClientWebSocket clientWebSocket;

    @FXML
    public void initialize() {
        clientWebSocket = new ClientWebSocket();
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
