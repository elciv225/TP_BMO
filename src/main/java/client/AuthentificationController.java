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
import javafx.scene.layout.VBox; // Added for root pane
import javafx.animation.FadeTransition; // Added for animation
import javafx.util.Duration; // Added for animation
import javafx.scene.Node; // Added for applyFadeInAnimation helper

import java.io.IOException;

public class AuthentificationController {
    @FXML
    public TextField txtIpServeur;
    @FXML
    public Button connexion; // Assuming this is from authentification.fxml based on its name
    // If 'connexion' is the fx:id for the button in connexionServeur.fxml, it might conflict or be confusing.
    // For now, proceeding with the assumption that FXML injection handles this correctly.
    @FXML
    public TextField txtPassword;
    @FXML
    public TextField txtLogin;

    // Root panes for animation - these need to be public if initialize() is not from Initializable
    // or if access is needed from a non-FXML context that calls initialize().
    // However, @FXML fields are typically private. Let's assume standard FXML injection.
    @FXML private VBox authRootPane; // For authentification.fxml
    @FXML private VBox connexionServeurRootPane; // For connexionServeur.fxml


    private ClientWebSocket clientWebSocket;

    public void setClientWebSocket(ClientWebSocket clientWebSocket) {
        this.clientWebSocket = clientWebSocket;
    }

    @FXML
    public void initialize() {
        clientWebSocket = new ClientWebSocket();
        clientWebSocket.setControllerAuth(this); // Lien entre le controleur et le client WebSocket

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
            node.setOpacity(0.0); // Start fully transparent

            FadeTransition fadeIn = new FadeTransition(Duration.millis(500), node);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.setDelay(Duration.millis(100)); // Optional delay
            fadeIn.play();
        } else {
            // This might be expected if the controller instance is shared and only one FXML is active
            // System.err.println("Cannot apply fade-in: node is null.");
        }
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
                        // Pass the data to the EspaceUtilisateurController
                        espaceUtilisateurController.setUserInfo(nom, prenom);
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
