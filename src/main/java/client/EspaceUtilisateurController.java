package client;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class EspaceUtilisateurController {

    @FXML
    private Label welcomeLabel;

    public void setUserInfo(String nom, String prenom) {
        if (welcomeLabel != null) {
            welcomeLabel.setText("Bienvenue " + nom + " " + prenom);
        }
    }

    @FXML
    public void initialize() {
        // Any initialization logic for this controller can go here
    }
}