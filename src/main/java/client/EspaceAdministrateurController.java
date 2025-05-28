package client;

import javafx.fxml.FXML;
import javafx.scene.layout.VBox;
import javafx.animation.FadeTransition; // Added for animation
import javafx.util.Duration; // Added for animation
import javafx.scene.Node; // Added for applyFadeInAnimation helper

public class EspaceAdministrateurController {

    @FXML
    private VBox adminContainer; // This is the root pane, fx:id="adminContainer"

    // Add other FXML fields and methods as needed in the future

    @FXML
    public void initialize() {
        // Initialization logic for the admin space controller
        System.out.println("EspaceAdministrateurController initialized.");

        // Apply animations
        if (adminContainer != null) {
            applyFadeInAnimation(adminContainer);
        } else {
            System.err.println("EspaceAdministrateurController: adminContainer is null. FXML might not be loaded correctly or fx:id is missing.");
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
             System.err.println("Cannot apply fade-in: node is null for EspaceAdministrateurController.");
        }
    }
}
