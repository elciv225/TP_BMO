package client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.json.JSONObject;

import java.io.IOException;

public class AuthentificationController {
    @FXML
    public TextField txtIpServeur;
    @FXML
    public Button connexion;
    @FXML
    public TextField txtPassword;
    @FXML
    public TextField txtLogin;

    private ClientWebSocket clientWebSocket;

    @FXML
    public void initialize() {
        clientWebSocket = new ClientWebSocket();
        clientWebSocket.setController(this); // Lien entre le controleur et le client WebSocket
    }


    @FXML
    private void handleClickConnexion() {
        // Connexion au serveur
        clientWebSocket.connectToWebSocket(txtIpServeur.getText());

        clientWebSocket.envoyerRequete(jsonAuthentification());
    }

    public void envoyerRequete() {
        clientWebSocket.envoyerRequete(jsonAuthentification());
    }

    private String jsonAuthentification() {
        return "{"
                + "\"modele\":\"authentification\","
                + "\"action\":\"connexion\","
                + "\"login\":\"" + txtLogin.getText() + "\","
                + "\"password\":\"" + txtPassword.getText() + "\""
                + "}";
    }

    public void traiterReponseConnexion(String message) {
        // Vérifie si le message est vide ou null
        if (message == null || message.trim().isEmpty()) {
            System.err.println("Erreur: message WebSocket vide reçu.");
            return;
        }

        // Maintenant, on peut parser en JSON en toute sécurité
        JSONObject jsonResponse = new JSONObject(message);
        String statut = jsonResponse.optString("statut");
        String msg = jsonResponse.optString("message");

        Platform.runLater(() -> {
            if ("succes".equals(statut)) {
                try {
                    showAlert("Connexion réussie", msg);
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/espaceUtilisateur.fxml"));
                    Parent root = loader.load();

                    // Get the controller for the loaded FXML
                    EspaceUtilisateurController espaceUtilisateurController = loader.getController();

                    // Extract Nom and Prenom from the personne JSON
                    JSONObject personneJson = jsonResponse.optJSONObject("personne");
                    if (personneJson != null) {
                        String nom = personneJson.optString("nom");
                        String prenom = personneJson.optString("prenom");
                        // Pass the data to the EspaceUtilisateurController
                        espaceUtilisateurController.setUserInfo(nom, prenom);
                    }

                    Stage stage = (Stage) txtLogin.getScene().getWindow();
                    stage.setScene(new Scene(root));
                    stage.show();
                } catch (IOException e) {
                    e.printStackTrace();
                    showAlert("Erreur", "Impossible de charger la page principale.");
                }
            } else {
                showAlert("Échec de connexion", msg);
            }
        });
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

}
