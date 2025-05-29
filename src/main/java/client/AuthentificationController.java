package client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.json.JSONObject;

import java.io.IOException;

public class AuthentificationController {

    @FXML private TextField txtIpServeur;
    @FXML private Button btnConnexionServeur;
    @FXML private TextField txtLogin;
    @FXML private PasswordField txtPassword;
    @FXML private Button connexion;

    private ClientWebSocket clientWebSocket;

    public void setClientWebSocket(ClientWebSocket clientWebSocket) {
        this.clientWebSocket = clientWebSocket;
    }

    @FXML
    public void initialize() {
        // L'instance de clientWebSocket est maintenant injectée via setClientWebSocket
    }

    @FXML
    private void handleClickConnexionServeur() {
        String ipServeurStr = txtIpServeur.getText();
        if (ipServeurStr == null || ipServeurStr.trim().isEmpty()) {
            showAlert(false, "Erreur de Saisie", "Veuillez saisir l'adresse IP du serveur.");
            txtIpServeur.requestFocus();
            return;
        }

        if (clientWebSocket == null) {
            // Tenter de récupérer une instance globale si elle existe ou en créer une nouvelle
            // Pour cet exemple, nous supposons qu'elle est gérée par ClientApplication
            // et devrait être injectée avant que cette vue ne soit affichée,
            // ou que ClientApplication l'initialise et la passe.
            // Si elle est nulle ici, c'est un problème de logique d'initialisation de l'application.
            clientWebSocket = ClientApplication.getWebSocketClientInstance(); // Exemple d'appel
             if (clientWebSocket == null) {
                 System.err.println("ClientWebSocket non initialisé et non récupérable via ClientApplication.");
                 showAlert(false, "Erreur Critique", "Le module de connexion n'est pas disponible.");
                 return;
             }
        }

        clientWebSocket.setControllerAuth(this);
        clientWebSocket.connectToWebSocket(ipServeurStr.trim());
    }

    @FXML
    private void handleClickConnexionAuthenfication() {
        String loginStr = txtLogin.getText();
        String passwordStr = txtPassword.getText();

        if (loginStr == null || loginStr.trim().isEmpty()) {
            showAlert(false, "Champ Requis", "Veuillez saisir votre nom d'utilisateur.");
            txtLogin.requestFocus();
            return;
        }
        if (passwordStr == null || passwordStr.isEmpty()) {
            showAlert(false, "Champ Requis", "Veuillez saisir votre mot de passe.");
            txtPassword.requestFocus();
            return;
        }
        if (clientWebSocket == null || !clientWebSocket.isConnected()) {
            showAlert(false, "Erreur de Connexion", "Pas de connexion au serveur. Veuillez d'abord vous connecter au serveur.");
            return;
        }
        clientWebSocket.envoyerRequete(jsonAuthentification());
    }

    private String jsonAuthentification() {
        JSONObject json = new JSONObject();
        json.put("modele", "authentification");
        json.put("action", "connexion");
        json.put("login", txtLogin.getText().trim());
        json.put("password", txtPassword.getText());
        return json.toString();
    }

    // Appelé par ClientWebSocket après une connexion réussie au serveur (onOpen)
    public void onWebSocketConnectionSuccess() {
        Platform.runLater(() -> {
            try {
                Stage stage = getCurrentStage();
                if (stage == null) {
                    System.err.println("Impossible de récupérer la scène actuelle pour passer à l'authentification.");
                    showAlert(false, "Erreur Interne", "Impossible de changer de vue après connexion au serveur.");
                    return;
                }

                FXMLLoader loader = new FXMLLoader(getClass().getResource("/authentification.fxml"));
                Parent root = loader.load();
                AuthentificationController authControllerInstance = loader.getController();

                // Transmettre l'instance de clientWebSocket déjà connectée
                authControllerInstance.setClientWebSocket(this.clientWebSocket);
                if (this.clientWebSocket != null) {
                    this.clientWebSocket.setControllerAuth(authControllerInstance); // Mettre à jour le contrôleur actif
                }

                Scene scene = new Scene(root);
                stage.setScene(scene);
                stage.setTitle("Authentification Utilisateur");
                stage.centerOnScreen();

            } catch (IOException e) {
                e.printStackTrace();
                showAlert(false, "Erreur de Chargement", "Impossible de charger l'écran d'authentification: " + e.getMessage());
            }
        });
    }

    public void traiterReponseConnexion(String message) {
        if (message == null || message.trim().isEmpty()) {
            System.err.println("Erreur: message WebSocket vide reçu.");
            Platform.runLater(() -> showAlert(false, "Erreur Réseau", "Réponse invalide reçue du serveur."));
            return;
        }
        try {
            JSONObject jsonResponse = new JSONObject(message);
            String type = jsonResponse.optString("type");
            if ("welcome".equals(type)) {
                System.out.println("Message de bienvenue du serveur: " + jsonResponse.optString("message"));
                return;
            }

            String modele = jsonResponse.optString("modele");
            String action = jsonResponse.optString("action");

            if (!"authentification".equals(modele) || !"reponseConnexion".equals(action)) {
                System.out.println("Message non pertinent pour AuthentificationController.traiterReponseConnexion: " + message);
                return;
            }

            String statut = jsonResponse.optString("statut");
            String msg = jsonResponse.optString("message");

            Platform.runLater(() -> {
                if ("succes".equals(statut)) {
                    try {
                        Stage stage = getCurrentStage();
                        if (stage == null) {
                            System.err.println("Impossible de récupérer la scène actuelle pour la transition vers l'espace utilisateur.");
                            showAlert(false, "Erreur Interne", "Impossible de changer de vue après authentification.");
                            return;
                        }

                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/espaceUtilisateur.fxml"));
                        Parent root = loader.load();
                        EspaceUtilisateurController espaceUtilisateurController = loader.getController();

                        if (clientWebSocket != null) {
                            espaceUtilisateurController.setClientWebSocket(clientWebSocket);
                            clientWebSocket.setControllerEspc(espaceUtilisateurController);
                        }

                        JSONObject personneJson = jsonResponse.optJSONObject("personne");
                        if (personneJson != null) {
                            String nom = personneJson.optString("nom", "Utilisateur");
                            String prenom = personneJson.optString("prenom", "");
                            int userId = personneJson.optInt("id", -1);
                            espaceUtilisateurController.setUserInfo(nom, prenom, userId);
                        } else {
                             System.err.println("Données utilisateur non trouvées dans la réponse JSON d'authentification.");
                             showAlert(true, "Connexion Réussie", msg + "\nAttention : Données utilisateur partielles reçues.");
                        }

                        Scene scene = new Scene(root);
                        stage.setScene(scene);
                        stage.setTitle("Espace Utilisateur");
                        stage.centerOnScreen();

                        if (clientWebSocket != null) {
                            clientWebSocket.setControllerAuth(null);
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                        showAlert(false, "Erreur de Chargement", "Impossible de charger l'espace utilisateur: " + e.getMessage());
                    }
                } else if ("echec".equals(statut)) {
                    showAlert(false, "Échec de l'Authentification", msg);
                } else {
                    System.out.println("Réponse du serveur non traitée (Authentification - statut inconnu): " + message);
                    showAlert(false, "Réponse Inattendue du Serveur", msg);
                }
            });
        } catch (Exception e) {
            System.err.println("Erreur lors du traitement de la réponse d'authentification: " + e.getMessage());
            e.printStackTrace();
            Platform.runLater(() -> showAlert(false, "Erreur Critique", "Erreur interne lors du traitement de la réponse du serveur."));
        }
    }

    public void showAlert(boolean success, String titre, String message) {
        Alert.AlertType alertType = success ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR;
        Alert alert = new Alert(alertType);
        alert.setTitle(titre);
        alert.setHeaderText(null);
        alert.setContentText(message);
        try {
            String cssPath = getClass().getResource("/styles/main.css").toExternalForm();
            if (cssPath != null) {
                alert.getDialogPane().getStylesheets().add(cssPath);
                alert.getDialogPane().getStyleClass().add("dialog-pane");
            }
        } catch (Exception e) {
            System.err.println("Erreur CSS pour alerte: " + e.getMessage());
        }
        alert.showAndWait();
    }

    private Stage getCurrentStage() {
        if (txtLogin != null && txtLogin.getScene() != null) return (Stage) txtLogin.getScene().getWindow();
        if (connexion != null && connexion.getScene() != null) return (Stage) connexion.getScene().getWindow();
        if (txtIpServeur != null && txtIpServeur.getScene() != null) return (Stage) txtIpServeur.getScene().getWindow();
        if (btnConnexionServeur != null && btnConnexionServeur.getScene() != null) return (Stage) btnConnexionServeur.getScene().getWindow();
        System.err.println("Impossible de déterminer la scène/stage actuelle.");
        return null;
    }
}
