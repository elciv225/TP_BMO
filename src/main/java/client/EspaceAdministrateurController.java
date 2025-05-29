package client;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node; // Importation ajoutée
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import org.json.JSONArray;
import org.json.JSONObject;
// Importer d'autres classes nécessaires (ex: pour les types de données dans la ListView)

public class EspaceAdministrateurController {

    @FXML private ListView<String> adminDataListView;

    private ClientWebSocket clientWebSocket;
    private ObservableList<String> adminDataObservableList = FXCollections.observableArrayList();

    // Enum pour définir les types de données que l'admin peut charger
    private enum AdminDataType {
        USERS,
        MEETINGS,
        // Ajoutez d'autres types si nécessaire
    }

    @FXML
    public void initialize() {
        System.out.println("EspaceAdministrateurController initialisé.");
        if (adminDataListView != null) {
            adminDataListView.setItems(adminDataObservableList);
            adminDataListView.setPlaceholder(new Label("Sélectionnez une action pour charger les données."));
            // Ne pas charger de données par défaut, attendre une action de l'admin
        }
        // Des boutons ou des menus pourraient appeler loadSpecificAdminData(AdminDataType.USERS) par exemple
    }

    public void setClientWebSocket(ClientWebSocket clientWebSocket) {
        this.clientWebSocket = clientWebSocket;
        if (this.clientWebSocket != null) {
            // S'assurer que ClientWebSocket peut router les messages vers ce contrôleur
            this.clientWebSocket.setControllerAdmin(this);
        }
    }

    // Méthode générique pour charger différents types de données admin
    private void loadSpecificAdminData(AdminDataType dataType) {
        if (clientWebSocket != null && clientWebSocket.isConnected()) {
            JSONObject request = new JSONObject();
            request.put("modele", "admin"); // Modèle spécifique pour les requêtes admin

            switch (dataType) {
                case USERS:
                    request.put("action", "listerUtilisateurs");
                    adminDataListView.setPlaceholder(new Label("Chargement de la liste des utilisateurs..."));
                    break;
                case MEETINGS:
                    request.put("action", "listerToutesReunions");
                    adminDataListView.setPlaceholder(new Label("Chargement de la liste des réunions..."));
                    break;
                default:
                    System.err.println("Type de données admin non supporté: " + dataType);
                    adminDataListView.setPlaceholder(new Label("Action non reconnue."));
                    return;
            }
            adminDataObservableList.clear(); // Effacer les données précédentes
            clientWebSocket.envoyerRequete(request.toString());
        } else {
            showAlert(Alert.AlertType.ERROR, "Non Connecté", "Impossible de charger les données : client non connecté au serveur.");
            adminDataListView.setPlaceholder(new Label("Non connecté au serveur."));
        }
    }

    // Méthodes FXML pour les boutons (à ajouter dans le FXML correspondant)
    @FXML
    private void handleListerUtilisateurs() {
        loadSpecificAdminData(AdminDataType.USERS);
    }

    @FXML
    private void handleListerReunions() {
        loadSpecificAdminData(AdminDataType.MEETINGS);
    }

    public void traiterReponseAdmin(String message) {
        Platform.runLater(() -> {
            try {
                JSONObject jsonResponse = new JSONObject(message);
                String action = jsonResponse.optString("actionReponse"); // Ex: "reponseListerUtilisateurs"
                String statut = jsonResponse.optString("statut");

                if (!"succes".equals(statut)) {
                    String erreurMsg = jsonResponse.optString("message", "Erreur inconnue du serveur.");
                    showAlert(Alert.AlertType.ERROR, "Erreur Serveur Admin", "Erreur lors de l'action '" + action + "': " + erreurMsg);
                    adminDataListView.setPlaceholder(new Label("Erreur lors du chargement des données."));
                    return;
                }

                adminDataObservableList.clear(); // Toujours effacer avant d'ajouter de nouvelles données

                if ("reponseListerUtilisateurs".equals(action)) {
                    JSONArray utilisateurs = jsonResponse.optJSONArray("utilisateurs");
                    if (utilisateurs != null) {
                        for (int i = 0; i < utilisateurs.length(); i++) {
                            JSONObject utilisateur = utilisateurs.getJSONObject(i);
                            // Construire une chaîne descriptive pour l'affichage
                            String userInfo = String.format("ID: %d - %s %s (%s) - Connecté: %s",
                                    utilisateur.getInt("id"),
                                    utilisateur.optString("prenom", ""),
                                    utilisateur.optString("nom", ""),
                                    utilisateur.getString("login"),
                                    utilisateur.optBoolean("connecte") ? "Oui" : "Non"
                            );
                            adminDataObservableList.add(userInfo);
                        }
                        // Le placeholder est mis à jour après la boucle si la liste est vide
                    } else {
                         adminDataListView.setPlaceholder(new Label("Aucun utilisateur retourné par le serveur."));
                    }
                } else if ("reponseListerToutesReunions".equals(action)) {
                    JSONArray reunions = jsonResponse.optJSONArray("reunions");
                    if (reunions != null) {
                        for (int i = 0; i < reunions.length(); i++) {
                            JSONObject reunion = reunions.getJSONObject(i);
                            String reunionInfo = String.format("ID: %d - Nom: %s (Type: %s, Début: %s, Org: %d)",
                                    reunion.getInt("id"),
                                    reunion.getString("nom"),
                                    reunion.getString("type"),
                                    reunion.getString("debut"), // Supposant que c'est une chaîne formatée
                                    reunion.getInt("organisateur_id")
                            );
                            adminDataObservableList.add(reunionInfo);
                        }
                        // Le placeholder est mis à jour après la boucle si la liste est vide
                    } else {
                        adminDataListView.setPlaceholder(new Label("Aucune réunion retournée par le serveur."));
                    }
                } else {
                    System.out.println("Action de réponse admin non gérée: " + action);
                    adminDataObservableList.add("Données reçues pour une action non reconnue: " + action);
                }

                // Mettre à jour le placeholder si la liste est vide APRES avoir tenté de la peupler
                if (adminDataObservableList.isEmpty()) {
                    Node placeholderNode = adminDataListView.getPlaceholder();
                    String currentPlaceholderText = "";
                    if (placeholderNode instanceof Label) {
                        currentPlaceholderText = ((Label) placeholderNode).getText();
                    }

                    // Mettre à jour le placeholder seulement si c'était un message de chargement
                    // ou si aucun placeholder spécifique n'a été défini par les branches ci-dessus.
                    if (currentPlaceholderText.startsWith("Chargement") || adminDataListView.getPlaceholder() == null) {
                        if ("reponseListerUtilisateurs".equals(action)) {
                            adminDataListView.setPlaceholder(new Label("Aucun utilisateur trouvé."));
                        } else if ("reponseListerToutesReunions".equals(action)) {
                            adminDataListView.setPlaceholder(new Label("Aucune réunion trouvée."));
                        } else {
                            adminDataListView.setPlaceholder(new Label("Aucune donnée disponible pour cette action."));
                        }
                    }
                }


            } catch (Exception e) {
                e.printStackTrace();
                showAlert(Alert.AlertType.ERROR, "Erreur de Traitement", "Impossible de traiter la réponse admin: " + e.getMessage());
                adminDataListView.setPlaceholder(new Label("Erreur de traitement des données."));
            }
        });
    }

    // Rendre la méthode showAlert publique si elle doit être appelée depuis ClientWebSocket
    // ou garder private si ClientWebSocket a sa propre méthode d'alerte.
    // Pour l'instant, on la garde private car ClientWebSocket a sa propre logique d'alerte.
    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        try {
            String cssPath = getClass().getResource("/styles/main.css").toExternalForm();
            if (cssPath != null) {
                alert.getDialogPane().getStylesheets().add(cssPath);
                alert.getDialogPane().getStyleClass().add("dialog-pane");
            }
        } catch (Exception e) {
            System.err.println("Erreur CSS pour alerte admin: " + e.getMessage());
        }
        alert.showAndWait();
    }

    // Méthodes pour d'autres actions admin (bannir utilisateur, supprimer réunion, etc.)
    // Ces méthodes enverraient des requêtes spécifiques au serveur.
}
