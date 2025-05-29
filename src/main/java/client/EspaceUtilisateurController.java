package client;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import model.Reunion;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import javafx.geometry.Insets; // Ajouté pour Dialog content padding

public class EspaceUtilisateurController {

    @FXML private Label welcomeLabel;
    @FXML
    private Button btnCreerReunion;
    @FXML
    private TextField txtTitreReunion;
    @FXML
    private Button btnRejoindre;
    @FXML
    private ListView<Reunion> listeReunionsUtilisateur;

    private ClientWebSocket clientWebSocket;
    private String nomUtilisateur;
    private String prenomUtilisateur;
    private int currentUserId = -1;

    private final ObservableList<Reunion> reunionsObservables = FXCollections.observableArrayList();
    private Dialog<Void> loadingDialog;


    @FXML
    public void initialize() {
        listeReunionsUtilisateur.setItems(reunionsObservables);
        listeReunionsUtilisateur.setCellFactory(listView -> new ReunionListCell());
        listeReunionsUtilisateur.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Reunion selectedReunion = listeReunionsUtilisateur.getSelectionModel().getSelectedItem();
                if (selectedReunion != null) {
                    rejoindreReunionParObjet(selectedReunion);
                }
            }
        });
    }

    public void setUserInfo(String nom, String prenom, int userId) {
        this.nomUtilisateur = nom;
        this.prenomUtilisateur = prenom;
        this.currentUserId = userId;
        if (welcomeLabel != null) {
            // Afficher seulement le prénom si disponible, sinon le nom, ou un fallback
            String displayName = (prenom != null && !prenom.isEmpty()) ? prenom : nom;
            if (displayName == null || displayName.isEmpty()) displayName = "Utilisateur";
            welcomeLabel.setText(displayName); // Simplifié pour juste le prénom ou nom
        }
        fetchUserMeetings();
    }

    public void setClientWebSocket(ClientWebSocket clientWebSocket) {
        this.clientWebSocket = clientWebSocket;
        // Si ce contrôleur doit écouter des messages spécifiques dès son initialisation,
        // il faut le dire à clientWebSocket
        // if (this.clientWebSocket != null) {
        // this.clientWebSocket.setControllerEspc(this);
        // }
    }

    @FXML
    private void handleDeconnexion() {
        System.out.println("Déconnexion demandée par l'utilisateur.");
        if (clientWebSocket != null) {
            clientWebSocket.deconnecter();
        }
        try {
            Stage stage = (Stage) welcomeLabel.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/connexionServeur.fxml"));
            Parent root = loader.load();
            AuthentificationController authController = loader.getController();
            // Il faut une manière d'obtenir ou de recréer clientWebSocket pour le nouvel écran de connexion
            // Pour l'instant, on suppose que ClientApplication gère une instance unique ou en crée une nouvelle.
            ClientWebSocket newSocket = ClientApplication.getWebSocketClientInstance(); // Exemple
            authController.setClientWebSocket(newSocket);


            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setTitle("Connexion au Serveur");
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Erreur", "Impossible de retourner à l'écran de connexion.");
        }
    }


    @FXML
    private void handleClickJoinReunion() {
        String codeOuNomReunion = txtTitreReunion.getText();
        if (codeOuNomReunion == null || codeOuNomReunion.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Champ Requis", "Veuillez saisir le nom, l'ID ou le code de la réunion.");
            txtTitreReunion.requestFocus();
            return;
        }
        if (clientWebSocket == null || !clientWebSocket.isConnected()) {
            showAlert(Alert.AlertType.ERROR, "Connexion Perdue", "Connexion au serveur perdue. Veuillez vous reconnecter.");
            return;
        }
        showLoadingDialog("Tentative de rejoindre la réunion...");
        JSONObject jsonRequete = new JSONObject();
        jsonRequete.put("modele", "reunion");
        jsonRequete.put("action", "rejoindre");
        jsonRequete.put("code", codeOuNomReunion.trim());
        jsonRequete.put("userId", this.currentUserId);
        clientWebSocket.envoyerRequete(jsonRequete.toString());
    }

    private void rejoindreReunionParObjet(Reunion reunion) {
        if (reunion == null) return;
        System.out.println("Tentative de rejoindre la réunion : " + reunion.getNom());
        if (clientWebSocket == null || !clientWebSocket.isConnected()) {
            showAlert(Alert.AlertType.ERROR, "Connexion Perdue", "Connexion au serveur perdue. Veuillez vous reconnecter.");
            return;
        }
        showLoadingDialog("Connexion à la réunion '" + reunion.getNom() + "'...");
        JSONObject jsonRequete = new JSONObject();
        jsonRequete.put("modele", "reunion");
        jsonRequete.put("action", "rejoindre");
        jsonRequete.put("reunionId", reunion.getId());
        jsonRequete.put("userId", this.currentUserId);
        clientWebSocket.envoyerRequete(jsonRequete.toString());
    }


    @FXML
    private void handleClickCreerReunion() {
        if (clientWebSocket == null || !clientWebSocket.isConnected()) {
            showAlert(Alert.AlertType.ERROR, "Connexion Perdue", "Impossible de créer une réunion. Veuillez vérifier votre connexion.");
            return;
        }
        Dialog<Reunion> dialog = createReunionDialog();
        dialog.showAndWait().ifPresent(nouvelleReunion -> {
            if (nouvelleReunion != null) {
                System.out.println("Nouvelle réunion à créer: " + nouvelleReunion.getNom());
                showLoadingDialog("Création de la réunion en cours...");
                envoyerCreationReunionServeur(nouvelleReunion);
            }
        });
    }

    private void envoyerCreationReunionServeur(Reunion reunion) {
        JSONObject json = new JSONObject();
        json.put("modele", "reunion");
        json.put("action", "creation");
        json.put("nom", reunion.getNom());
        json.put("sujet", reunion.getSujet() != null ? reunion.getSujet() : "");
        json.put("agenda", reunion.getAgenda() != null ? reunion.getAgenda() : "");
        json.put("debut", reunion.getDebut().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        json.put("duree", reunion.getDuree());
        json.put("type", reunion.getType().toString());
        json.put("idOrganisateur", this.currentUserId);
        clientWebSocket.envoyerRequete(json.toString());
    }

    public void traiterReponseServeur(String message) {
        Platform.runLater(() -> {
            // Toujours fermer le modal de chargement en premier
            hideLoadingDialog();

        try {
            JSONObject jsonResponse = new JSONObject(message);
            String modele = jsonResponse.optString("modele");
            String action = jsonResponse.optString("action");
            String statut = jsonResponse.optString("statut");
            String msg = jsonResponse.optString("message", "Aucun message du serveur.");

            if (!"reunion".equals(modele)) {
                System.out.println("Message non destiné à EspaceUtilisateurController (modèle): " + modele);
                return;
            }

            if ("reponseCreation".equals(action)) {
                if ("succes".equals(statut)) {
                    // Ne pas afficher d'alerte pour une création réussie si on va directement à la réunion
                    JSONObject reunionData = jsonResponse.optJSONObject("reunion");
                    if (reunionData != null) {
                        boolean autoJoin = jsonResponse.optBoolean("autoJoin", true); // Par défaut true
                        if (autoJoin) {
                            int nouvelleReunionId = reunionData.optInt("id");
                            String nomNouvelleReunion = reunionData.optString("nom");
                            int organisateurId = reunionData.optInt("idOrganisateur", this.currentUserId);
                            ouvrirInterfaceReunion(String.valueOf(nouvelleReunionId), nomNouvelleReunion, organisateurId, true);
                        } else {
                            showAlert(Alert.AlertType.INFORMATION, "Réunion Créée", msg);
                            fetchUserMeetings();
                        }
                    } else {
                        showAlert(Alert.AlertType.INFORMATION, "Réunion Créée", msg);
                        fetchUserMeetings();
                    }
                } else {
                    showAlert(Alert.AlertType.ERROR, "Erreur de Création", msg);
                }
            } else if ("reponseRejoindre".equals(action)) {
                if ("succes".equals(statut)) {
                    txtTitreReunion.clear();
                    int reunionId = jsonResponse.optInt("reunionId", -1);
                    String nomReunion = jsonResponse.optString("nomReunion", "Réunion " + reunionId);
                    int organisateurId = jsonResponse.optInt("organisateurId", -1);
                    if (reunionId != -1) {
                        ouvrirInterfaceReunion(String.valueOf(reunionId), nomReunion, organisateurId, false);
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Erreur", "ID de réunion invalide reçu du serveur.");
                    }
                } else {
                    showAlert(Alert.AlertType.ERROR, "Impossible de Rejoindre", msg);
                }
            } else if ("listeReunionsUtilisateur".equals(action) || "reponseGetReunionsUtilisateur".equals(action)) {
                if ("succes".equals(statut)) {
                    JSONArray reunionsArray = jsonResponse.optJSONArray("reunions");
                    parseAndDisplayUserMeetings(reunionsArray);
                } else {
                    showAlert(Alert.AlertType.ERROR, "Erreur de Chargement", "Impossible de charger vos réunions: " + msg);
                    listeReunionsUtilisateur.setPlaceholder(new Label("Erreur lors du chargement des réunions."));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Erreur", "Erreur lors du traitement de la réponse du serveur: " + e.getMessage());
        }
        });
    }

    private void fetchUserMeetings() {
        if (clientWebSocket != null && clientWebSocket.isConnected() && currentUserId != -1) {
            JSONObject request = new JSONObject();
            request.put("modele", "reunion");
            request.put("action", "getReunionsUtilisateur");
            request.put("userId", currentUserId);
            clientWebSocket.envoyerRequete(request.toString());
            listeReunionsUtilisateur.setPlaceholder(new Label("Chargement de vos réunions..."));
        } else {
            System.out.println("Impossible de charger les réunions : client non connecté ou ID utilisateur manquant.");
            listeReunionsUtilisateur.setPlaceholder(new Label("Non connecté ou ID utilisateur non défini."));
        }
    }

    private void parseAndDisplayUserMeetings(JSONArray reunionsArray) {
        reunionsObservables.clear();
        if (reunionsArray != null) {
            for (int i = 0; i < reunionsArray.length(); i++) {
                JSONObject reunionJson = reunionsArray.getJSONObject(i);
                try {
                    int id = reunionJson.getInt("id");
                    String nom = reunionJson.getString("nom");
                    String sujet = reunionJson.optString("sujet");
                    LocalDateTime debut = LocalDateTime.parse(reunionJson.getString("debut"), DateTimeFormatter.ISO_DATE_TIME);
                    int duree = reunionJson.getInt("duree");
                    Reunion.Type type = Reunion.Type.valueOf(reunionJson.getString("type").toUpperCase());
                    int idOrganisateur = reunionJson.getInt("idOrganisateur");
                    Integer idAnimateur = reunionJson.has("idAnimateur") && !reunionJson.isNull("idAnimateur") ? reunionJson.getInt("idAnimateur") : null;

                    Reunion reunion = new Reunion(id, nom, sujet, null, debut, duree, type, idOrganisateur, idAnimateur);
                    reunionsObservables.add(reunion);
                } catch (Exception e) {
                    System.err.println("Erreur de parsing pour la réunion JSON : " + reunionJson.toString() + " - " + e.getMessage());
                }
            }
        }
        if (reunionsObservables.isEmpty()) {
            listeReunionsUtilisateur.setPlaceholder(new Label("Vous n'avez aucune réunion planifiée."));
        }
    }


    private void ouvrirInterfaceReunion(String reunionId, String nomReunion, int organisateurId, boolean isCurrentUserOrganizer) {
        // Fermer le modal de chargement avant d'ouvrir l'interface
        hideLoadingDialog();

        try {
            Stage stage = (Stage) welcomeLabel.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/reunion.fxml"));
            Parent root = loader.load();

            ReunionController reunionController = loader.getController();
            reunionController.setClientWebSocket(this.clientWebSocket);

            // Le nom d'utilisateur complet (prénom + nom) est plus pertinent pour l'affichage dans la réunion
            String nomCompletUtilisateur = (prenomUtilisateur != null ? prenomUtilisateur : "") +
                    ((prenomUtilisateur != null && nomUtilisateur != null) ? " " : "") +
                    (nomUtilisateur != null ? nomUtilisateur : "");
            if (nomCompletUtilisateur.trim().isEmpty()) {
                nomCompletUtilisateur = "Utilisateur " + this.currentUserId;
            }

            reunionController.initData(reunionId, this.currentUserId, nomReunion, organisateurId, nomCompletUtilisateur);

            if (this.clientWebSocket != null) {
                this.clientWebSocket.setControllerReunion(reunionController);
                this.clientWebSocket.setControllerEspc(null);
            }

            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setTitle("Réunion: " + nomReunion);
            stage.setOnCloseRequest(event -> {
                reunionController.handleQuitterReunion();
                if (this.clientWebSocket != null) {
                    this.clientWebSocket.setControllerEspc(this);
                    this.clientWebSocket.setControllerReunion(null);
                    fetchUserMeetings();
                }
            });
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Erreur", "Impossible d'ouvrir l'interface de réunion: " + e.getMessage());
        }
    }

    private Dialog<Reunion> createReunionDialog() {
        Dialog<Reunion> dialog = new Dialog<>();
        dialog.setTitle("Nouvelle Réunion");
        dialog.setHeaderText("Planifiez votre nouvelle réunion");

        try {
            String cssPath = getClass().getResource("/styles/main.css").toExternalForm();
            dialog.getDialogPane().getStylesheets().add(cssPath);
            dialog.getDialogPane().getStyleClass().add("dialog-pane");
        } catch (Exception e) {
            System.err.println("CSS pour dialogue non trouvé: " + e.getMessage());
        }

        ButtonType creerButtonType = new ButtonType("Créer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(creerButtonType, ButtonType.CANCEL);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        TextField nomField = new TextField();
        nomField.setPromptText("Nom de la réunion (ex: Point d'équipe)");
        nomField.getStyleClass().add("text-input");

        TextField sujetField = new TextField();
        sujetField.setPromptText("Sujet (optionnel)");
        sujetField.getStyleClass().add("text-input");

        DatePicker datePicker = new DatePicker(LocalDate.now().plusDays(1)); // Par défaut demain
        datePicker.getStyleClass().add("date-picker");

        ComboBox<String> heureCombo = new ComboBox<>();
        LocalTime currentTime = LocalTime.now();
        int nextHour = (currentTime.getMinute() < 30) ? currentTime.getHour() : currentTime.getHour() + 1;
        if (nextHour > 23) nextHour = 8; // Default to 8 AM if next hour is past midnight

        for (int h = 8; h < 23; h++) { // Heures de bureau
            heureCombo.getItems().add(String.format("%02d:00", h));
            heureCombo.getItems().add(String.format("%02d:30", h));
        }
        heureCombo.setValue(String.format("%02d:00", nextHour));
        heureCombo.getStyleClass().add("combo-box");

        Spinner<Integer> dureeSpinner = new Spinner<>(15, 240, 60, 15);
        dureeSpinner.setEditable(true);
        dureeSpinner.getStyleClass().add("text-input"); // Utiliser text-input pour un style cohérent

        ComboBox<Reunion.Type> typeComboBox = new ComboBox<>();
        typeComboBox.setItems(FXCollections.observableArrayList(Reunion.Type.values()));
        typeComboBox.setValue(Reunion.Type.STANDARD);
        typeComboBox.getStyleClass().add("combo-box");

        content.getChildren().addAll(
                new Label("Nom de la réunion:"), nomField,
                new Label("Sujet (optionnel):"), sujetField,
                new Label("Date:"), datePicker,
                new Label("Heure de début:"), heureCombo,
                new Label("Durée (minutes):"), dureeSpinner,
                new Label("Type de réunion:"), typeComboBox
        );
        content.getChildren().filtered(node -> node instanceof Label).forEach(node -> node.getStyleClass().add("field-label"));

        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == creerButtonType) {
                try {
                    String nom = nomField.getText();
                    if (nom == null || nom.trim().isEmpty()) {
                        showAlert(Alert.AlertType.ERROR, "Validation", "Le nom de la réunion est obligatoire.");
                        return null;
                    }
                    LocalDate date = datePicker.getValue();
                    LocalTime heure = LocalTime.parse(heureCombo.getValue(), DateTimeFormatter.ofPattern("HH:mm"));
                    LocalDateTime debut = LocalDateTime.of(date, heure);

                    if (debut.isBefore(LocalDateTime.now().plusMinutes(5))) { // Au moins 5 min dans le futur
                        showAlert(Alert.AlertType.ERROR, "Validation", "La date de début doit être dans le futur (au moins 5 minutes).");
                        return null;
                    }

                    return new Reunion(0, nom.trim(), sujetField.getText().trim(), null,
                            debut, dureeSpinner.getValue(), typeComboBox.getValue(),
                            this.currentUserId, null
                    );
                } catch (Exception e) {
                    showAlert(Alert.AlertType.ERROR, "Erreur de Saisie", "Veuillez vérifier les champs: " + e.getMessage());
                    return null;
                }
            }
            return null;
        });
        return dialog;
    }

    static class ReunionListCell extends ListCell<Reunion> {
        private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");

        @Override
        protected void updateItem(Reunion reunion, boolean empty) {
            super.updateItem(reunion, empty);
            if (empty || reunion == null) {
                setText(null);
                setGraphic(null);
            } else {
                VBox vbox = new VBox(5);
                Label nomLabel = new Label(reunion.getNom());
                nomLabel.getStyleClass().add("body-text"); // Utiliser les classes CSS
                nomLabel.setStyle("-fx-font-weight: bold;");

                Label dateLabel = new Label("Le " + reunion.getDebut().format(formatter) + " (" + reunion.getDuree() + " min)");
                dateLabel.getStyleClass().add("secondary-text");

                Label typeLabel = new Label("Type: " + reunion.getType().toString());
                typeLabel.getStyleClass().add("secondary-text");

                vbox.getChildren().addAll(nomLabel, dateLabel, typeLabel);
                setGraphic(vbox);
            }
        }
    }

    private void showLoadingDialog(String message) {
        Platform.runLater(() -> {
            if (loadingDialog == null) {
                loadingDialog = new Dialog<>();
                loadingDialog.initModality(Modality.APPLICATION_MODAL);
                loadingDialog.setResizable(false);
                loadingDialog.setHeaderText(null);
                loadingDialog.setTitle("Chargement");

                VBox content = new VBox(20);
                content.setPadding(new Insets(30));
                content.setAlignment(javafx.geometry.Pos.CENTER);
                ProgressIndicator pi = new ProgressIndicator();
                pi.setMaxSize(50, 50);
                Label messageLabel = new Label(message);
                messageLabel.getStyleClass().add("body-text");
                content.getChildren().addAll(pi, messageLabel);

                loadingDialog.getDialogPane().setContent(content);

                // Supprimer les boutons par défaut
                loadingDialog.getDialogPane().getButtonTypes().clear();

                try {
                    String cssPath = getClass().getResource("/styles/main.css").toExternalForm();
                    loadingDialog.getDialogPane().getStylesheets().add(cssPath);
                    loadingDialog.getDialogPane().getStyleClass().add("dialog-pane");
                } catch (Exception e) {
                    System.err.println("CSS pour dialogue non trouvé: " + e.getMessage());
                }
            } else {
                // Mettre à jour le message si le dialog existe déjà
                VBox content = (VBox) loadingDialog.getDialogPane().getContent();
                Label messageLabel = (Label) content.getChildren().get(1);
                messageLabel.setText(message);
            }

            if (!loadingDialog.isShowing()) {
                loadingDialog.show();
            }
        });
    }

    // 4. Améliorer la méthode hideLoadingDialog
    private void hideLoadingDialog() {
        Platform.runLater(() -> {
            if (loadingDialog != null && loadingDialog.isShowing()) {
                loadingDialog.hide(); // Utiliser hide() au lieu de close() pour réutiliser le dialog
            }
        });
    }

    // Changé en public pour être accessible par ClientWebSocket
    public void showAlert(Alert.AlertType alertType, String title, String message) {
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
            System.err.println("CSS pour alerte non trouvé: " + e.getMessage());
        }
        alert.showAndWait();
    }
}
