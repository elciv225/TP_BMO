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
import javafx.stage.Window;
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

    public void setClientWebSocket(ClientWebSocket clientWebSocket) {
        this.clientWebSocket = clientWebSocket;
    }

    @FXML
    public void initialize() {
        clientWebSocket = new ClientWebSocket();
        clientWebSocket.setControllerAuth(this); // Lien entre le controleur et le client WebSocket
    }

    @FXML
    private void handleClickConnexionServeur(){
        clientWebSocket.connectToWebSocket(txtIpServeur.getText());
    }


    @FXML
    private void handleClickConnexionAuthenfication() {
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
                    showAlert( true,"Connexion réussie", msg);
                    Stage stage = (Stage) Stage.getWindows().stream()
                            .filter(Window::isShowing)
                            .findFirst()
                            .orElse(null);
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/espaceUtilisateur.fxml"));
                    Parent root = loader.load();
                    EspaceUtilisateurController espcController = loader.getController();
                    stage.setScene(new Scene(root));
                    stage.show();

                    // Get the controller for the loaded FXML
                    EspaceUtilisateurController espaceUtilisateurController = loader.getController();

                    // Extract Nom and Prenom from the personne JSON
                    JSONObject personneJson = jsonResponse.optJSONObject("personne");
                    if (personneJson != null) {
                        String nom = personneJson.optString("nom");
                        String prenom = personneJson.optString("prenom");
                        int id = personneJson.optInt("id", -1); // Assuming 'id' is the field name for user ID
                        // Pass the data to the EspaceUtilisateurController
                        espaceUtilisateurController.setUserInfo(nom, prenom, id);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                    showAlert(false,"Erreur", "Impossible de charger la page principale.");
                }
            } else {
                showAlert( false,"Échec de connexion", msg);
            }
        });
    }

    public void showAlert(boolean success,String titre, String message) {
        Alert.AlertType alertType = success ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR;
        Alert alert = new Alert(alertType);
        alert.setTitle(titre);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public Stage getCurrentScene() {
        return (Stage) txtLogin.getScene().getWindow();
    }


}
