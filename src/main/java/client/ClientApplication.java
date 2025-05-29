package client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class ClientApplication extends Application {

    private static Stage primaryStage; // Référence statique à la fenêtre principale
    private static ClientWebSocket webSocketClientInstance; // Instance unique du client WebSocket

    // Méthode statique pour obtenir la fenêtre principale
    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    // Méthode statique pour obtenir/créer l'instance du client WebSocket
    public static ClientWebSocket getWebSocketClientInstance() {
        if (webSocketClientInstance == null) {
            webSocketClientInstance = new ClientWebSocket();
        }
        return webSocketClientInstance;
    }


    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage; // Assigner la fenêtre principale
        webSocketClientInstance = getWebSocketClientInstance(); // S'assurer que l'instance est créée

        // Charger l'écran de connexion au serveur en premier
        FXMLLoader fxmlLoader = new FXMLLoader(ClientApplication.class.getResource("/connexionServeur.fxml"));
        Scene scene = new Scene(fxmlLoader.load());

        // Injecter le client WebSocket dans le premier contrôleur
        AuthentificationController controller = fxmlLoader.getController();
        controller.setClientWebSocket(webSocketClientInstance);
        // Le ClientWebSocket a besoin d'une référence au premier contrôleur pour gérer les réponses de connexion au serveur
        webSocketClientInstance.setControllerAuth(controller);


        stage.setTitle("Connexion au Serveur de Réunion BMO");
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            System.out.println("Fermeture de l'application demandée.");
            if (webSocketClientInstance != null && webSocketClientInstance.isConnected()) {
                webSocketClientInstance.deconnecter(); // Déconnexion propre du WebSocket
            }
            // Permettre à l'application de se fermer normalement
        });
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
