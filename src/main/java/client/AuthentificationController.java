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
import javafx.scene.layout.VBox;
import javafx.animation.FadeTransition;
import javafx.util.Duration;
import javafx.scene.Node;

import java.io.IOException;

public class AuthentificationController {
    @FXML public TextField txtIpServeur;
    @FXML public Button connexion;
    @FXML public TextField txtPassword;
    @FXML public TextField txtLogin;
    @FXML private VBox authRootPane;
    @FXML private VBox connexionServeurRootPane;

    private ClientWebSocket clientWebSocket;

    public void setClientWebSocket(ClientWebSocket clientWebSocket) {
        this.clientWebSocket = clientWebSocket;
    }

    @FXML
    public void initialize() {
        if (clientWebSocket == null) {
            clientWebSocket = new ClientWebSocket();
            clientWebSocket.setControllerAuth(this);
        }

        // Apply animations
        if (authRootPane != null) {
            applyFadeInAnimation(authRootPane);
        }
        if (connexionServeurRootPane != null) {
            applyFadeInAnimation(connexionServeurRootPane);
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

    @FXML
    private void handleClickConnexionServeur(){
        String ipServeur = txtIpServeur.getText();
        if (ipServeur == null || ipServeur.trim().isEmpty()) {
            showAlert(false, "Erreur", "Veuillez saisir l'adresse IP du serveur.");
            return;
        }
        clientWebSocket.connectToWebSocket(ipServeur.trim());
    }

    @FXML
    private void handleClickConnexionAuthenfication() {
        String login = txtLogin.getText();
        String password = txtPassword.getText();

        if (login == null || login.trim().isEmpty()) {
            showAlert(false, "Erreur", "Veuillez saisir votre nom d'utilisateur.");
            return;
        }

        if (password == null || password.trim().isEmpty()) {
            showAlert(false, "Erreur", "Veuillez saisir votre mot de passe.");
            return;
        }

        if (clientWebSocket == null || !clientWebSocket.isConnected()) {
            showAlert(false, "Erreur de connexion", "Pas de connexion au serveur. Veuillez d'abord vous connecter au serveur.");
            return;
        }

        clientWebSocket.envoyerRequete(jsonAuthentification());
    }

    public void envoyerRequete() {
        clientWebSocket.envoyerRequete(jsonAuthentification());
    }

    private String jsonAuthentification() {
        return "{"
                + "\"modele\":\"authentification\","
                + "\"action\":\"connexion\","
                + "\"login\":\"" + txtLogin.getText().trim() + "\","
                + "\"password\":\"" + txtPassword.getText() + "\""
                + "}";
    }

    public void traiterReponseConnexion(String message) {
        // Vérifie si le message est vide ou null
        if (message == null || message.trim().isEmpty()) {
            System.err.println("Erreur: message WebSocket vide reçu.");
            return;
        }

        try {
            // Maintenant, on peut parser en JSON en toute sécurité
            JSONObject jsonResponse = new JSONObject(message);
            String type = jsonResponse.optString("type");

            // Ignorer les messages de bienvenue
            if ("welcome".equals(type)) {
                System.out.println("Message de bienvenue reçu: " + jsonResponse.optString("message"));
                return;
            }

            String statut = jsonResponse.optString("statut");
            String msg = jsonResponse.optString("message");

            Platform.runLater(() -> {
                if ("succes".equals(statut)) {
                    try {
                        // CORRECTION: Ne pas afficher d'alerte pour la connexion réussie
                        // L'utilisateur verra le changement d'interface

                        Stage stage = getCurrentScene();
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/espaceUtilisateur.fxml"));
                        Parent root = loader.load();

                        // Get the controller for the loaded FXML
                        EspaceUtilisateurController espaceUtilisateurController = loader.getController();

                        if (clientWebSocket != null) {
                            clientWebSocket.setControllerEspc(espaceUtilisateurController);
                        }

                        // Extract user information from the response
                        JSONObject personneJson = jsonResponse.optJSONObject("personne");
                        if (personneJson != null) {
                            String nom = personneJson.optString("nom");
                            String prenom = personneJson.optString("prenom");
                            int userId = personneJson.optInt("id", -1);

                            // CORRECTION: Passer toutes les informations nécessaires
                            espaceUtilisateurController.setUserInfo(nom, prenom, userId, clientWebSocket);
                        } else {
                            // Fallback si pas d'info utilisateur
                            espaceUtilisateurController.setClientWebSocket(clientWebSocket);
                        }

                        stage.setScene(new Scene(root));
                        stage.show();

                        if (clientWebSocket != null) {
                            clientWebSocket.setControllerAuth(null);
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                        showAlert(false, "Erreur", "Impossible de charger la page principale: " + e.getMessage());
                    }
                } else if ("echec".equals(statut)) {
                    showAlert(false, "Échec de connexion", msg);
                } else {
                    // Message non géré
                    System.out.println("Message non traité: " + message);
                }
            });
        } catch (Exception e) {
            System.err.println("Erreur lors du traitement de la réponse d'authentification: " + e.getMessage());
            e.printStackTrace();
            Platform.runLater(() -> {
                showAlert(false, "Erreur", "Erreur lors du traitement de la réponse du serveur.");
            });
        }
    }

    public void showAlert(boolean success, String titre, String message) {
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