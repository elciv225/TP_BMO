package client;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import model.Reunion;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class EspaceUtilisateurController {

    @FXML public Button btnCreerReunion;
    @FXML public TextField txtTitreReunion;
    @FXML public Button btnRejoindre;
    @FXML private Label welcomeLabel;
    @FXML private VBox espaceUtilisateurRootPane;

    private Reunion reunion;
    private ClientWebSocket clientWebSocket;
    private String nom;
    private String prenom;
    private int userId = -1;

    @FXML
    public void initialize() {
        applyFadeInAnimation(espaceUtilisateurRootPane);
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

    public void setUserInfo(String nom, String prenom) {
        this.nom = nom;
        this.prenom = prenom;
        if (welcomeLabel != null) {
            welcomeLabel.setText("Bienvenue " + nom + " " + prenom);
        }
    }

    public void setUserInfo(String nom, String prenom, int userId, ClientWebSocket clientWebSocket) {
        this.nom = nom;
        this.prenom = prenom;
        this.userId = userId;
        this.clientWebSocket = clientWebSocket;
        if (welcomeLabel != null) {
            welcomeLabel.setText("Bienvenue " + nom + " " + prenom);
        }
    }

    public void setClientWebSocket(ClientWebSocket clientWebSocket) {
        this.clientWebSocket = clientWebSocket;
        clientWebSocket.setControllerEspc(this);
    }

    @FXML
    private void handleClickJoinReunion() {
        String titreReunion = txtTitreReunion.getText();
        if (titreReunion == null || titreReunion.trim().isEmpty()) {
            showAlert(false, "Champ vide",
                     "Veuillez saisir le titre ou l'ID de la réunion à rejoindre.");
            return;
        }

        if (clientWebSocket == null || !clientWebSocket.isConnected()) {
            showAlert(false, "Erreur de connexion",
                     "Pas de connexion au serveur. Impossible de rejoindre la réunion.");
            return;
        }

        rejoindreReunion(titreReunion.trim());
    }

    @FXML
    private void handleClickCreerReunion() {
        if (clientWebSocket == null || !clientWebSocket.isConnected()) {
            showAlert(false, "Erreur de connexion",
                     "Pas de connexion au serveur. Impossible de créer une réunion.");
            return;
        }

        Dialog<Reunion> dialog = createReunionDialog();
        dialog.showAndWait().ifPresent(r -> {
            if (r != null) {
                System.out.println("Réunion créée: " + r.toString());
                setReunion(r);
                creerReunion(r);
            }
        });
    }

    /**
     * CORRECTION: Dialogue de création de réunion entièrement revu
     */
    private Dialog<Reunion> createReunionDialog() {
        Dialog<Reunion> dialog = new Dialog<>();
        dialog.setTitle("Créer une réunion");
        dialog.setHeaderText("Remplissez les détails de la réunion");

        // Champs de saisie
        TextField nomField = new TextField();
        nomField.setPromptText("Nom de la réunion");

        TextField sujetField = new TextField();
        sujetField.setPromptText("Sujet (optionnel)");

        TextArea agendaField = new TextArea();
        agendaField.setPromptText("Agenda (optionnel)");
        agendaField.setPrefRowCount(3);
        agendaField.setMaxHeight(80);

        DatePicker datePicker = new DatePicker();
        datePicker.setValue(LocalDate.now());

        TextField heureField = new TextField();
        heureField.setText("14:00");
        heureField.setPromptText("HH:mm");

        Spinner<Integer> dureeSpinner = new Spinner<>(15, 480, 60, 15);
        dureeSpinner.setEditable(true);

        ComboBox<Reunion.Type> typeComboBox = new ComboBox<>();
        typeComboBox.getItems().setAll(Reunion.Type.values());
        typeComboBox.setValue(Reunion.Type.STANDARD);

        // Layout
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);
        grid.setPadding(new Insets(20));

        int row = 0;
        grid.add(new Label("Nom de la réunion *:"), 0, row);
        grid.add(nomField, 1, row++);

        grid.add(new Label("Sujet:"), 0, row);
        grid.add(sujetField, 1, row++);

        grid.add(new Label("Agenda:"), 0, row);
        grid.add(agendaField, 1, row++);

        grid.add(new Label("Date *:"), 0, row);
        grid.add(datePicker, 1, row++);

        grid.add(new Label("Heure *:"), 0, row);
        grid.add(heureField, 1, row++);

        grid.add(new Label("Durée (minutes) *:"), 0, row);
        grid.add(dureeSpinner, 1, row++);

        grid.add(new Label("Type *:"), 0, row);
        grid.add(typeComboBox, 1, row++);

        dialog.getDialogPane().setContent(grid);

        // Boutons
        ButtonType creerButtonType = new ButtonType("Créer", ButtonBar.ButtonData.OK_DONE);
        ButtonType annulerButtonType = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(creerButtonType, annulerButtonType);

        // CORRECTION: Validation et création de l'objet Reunion
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == creerButtonType) {
                try {
                    // Récupération des valeurs
                    String nom = nomField.getText();
                    String sujet = sujetField.getText();
                    String agenda = agendaField.getText();
                    LocalDate date = datePicker.getValue();
                    String heureText = heureField.getText();
                    Integer duree = dureeSpinner.getValue();
                    Reunion.Type type = typeComboBox.getValue();

                    // Validation des champs obligatoires
                    if (nom == null || nom.trim().isEmpty()) {
                        Platform.runLater(() -> showAlert(false, "Erreur", "Le nom de la réunion est obligatoire."));
                        return null;
                    }
                    if (date == null) {
                        Platform.runLater(() -> showAlert(false, "Erreur", "La date est obligatoire."));
                        return null;
                    }
                    if (heureText == null || heureText.trim().isEmpty()) {
                        Platform.runLater(() -> showAlert(false, "Erreur", "L'heure est obligatoire."));
                        return null;
                    }
                    if (type == null) {
                        Platform.runLater(() -> showAlert(false, "Erreur", "Le type de réunion est obligatoire."));
                        return null;
                    }
                    if (duree == null || duree <= 0) {
                        Platform.runLater(() -> showAlert(false, "Erreur", "La durée doit être positive."));
                        return null;
                    }

                    // Parsing de l'heure
                    LocalTime time;
                    try {
                        time = LocalTime.parse(heureText.trim(), DateTimeFormatter.ofPattern("HH:mm"));
                    } catch (DateTimeParseException e) {
                        Platform.runLater(() -> showAlert(false, "Erreur", "Format d'heure invalide. Utilisez HH:mm (ex: 14:30)."));
                        return null;
                    }

                    LocalDateTime debut = LocalDateTime.of(date, time);

                    // Vérification que la date n'est pas dans le passé
                    if (debut.isBefore(LocalDateTime.now())) {
                        Platform.runLater(() -> showAlert(false, "Erreur", "La date et l'heure doivent être dans le futur."));
                        return null;
                    }

                    // CORRECTION: Créer l'objet Reunion correctement
                    return new Reunion(
                            nom.trim(),
                            sujet != null ? sujet.trim() : "",
                            agenda != null ? agenda.trim() : "",
                            debut,
                            duree,
                            type,
                            userId > 0 ? userId : 1, // Utiliser l'ID de l'utilisateur connecté
                            null // Pas d'animateur défini
                    );

                } catch (Exception e) {
                    Platform.runLater(() -> showAlert(false, "Erreur", "Erreur lors de la création: " + e.getMessage()));
                    e.printStackTrace();
                    return null;
                }
            }
            return null;
        });

        return dialog;
    }

    private void creerReunion(Reunion reunion) {
        try {
            String jsonRequete = creerJsonCreationReunion(reunion);
            System.out.println("JSON envoyé: " + jsonRequete); // Debug
            clientWebSocket.envoyerRequete(jsonRequete);
        } catch (Exception e) {
            System.err.println("Erreur lors de l'envoi de la création de réunion: " + e.getMessage());
            e.printStackTrace();
            showAlert(false, "Erreur", "Impossible de créer la réunion: " + e.getMessage());
        }
    }

    /**
     * CORRECTION: JSON pour création de réunion
     */
    private String creerJsonCreationReunion(Reunion reunion) {
        JSONObject json = new JSONObject();
        json.put("modele", "reunion");
        json.put("action", "creation");
        json.put("nom", reunion.getNom());
        json.put("sujet", reunion.getSujet() != null ? reunion.getSujet() : "");
        json.put("agenda", reunion.getAgenda() != null ? reunion.getAgenda() : "");
        json.put("debut", reunion.getDebut().toString());
        json.put("duree", reunion.getDuree());
        json.put("type", reunion.getType().toString());
        json.put("idOrganisateur", reunion.getIdOrganisateur());

        return json.toString();
    }

    private void rejoindreReunion(String codeReunion) {
        try {
            String jsonRequete = creerJsonRejoindreReunion(codeReunion);
            clientWebSocket.envoyerRequete(jsonRequete);
        } catch (Exception e) {
            System.err.println("Erreur lors de l'envoi de la demande de participation: " + e.getMessage());
            showAlert(false, "Erreur", "Impossible de rejoindre la réunion: " + e.getMessage());
        }
    }

    private String creerJsonRejoindreReunion(String codeReunion) {
        JSONObject json = new JSONObject();
        json.put("modele", "reunion");
        json.put("action", "rejoindre");
        json.put("code", codeReunion);
        json.put("userId", userId);
        json.put("participant", nom + " " + prenom);

        return json.toString();
    }

    /**
     * CORRECTION: Traitement des réponses amélioré
     */
    public void traiterReponseConnexion(String message) {
        if (message == null || message.trim().isEmpty()) {
            System.err.println("Erreur: message WebSocket vide reçu.");
            return;
        }

        try {
            JSONObject jsonResponse = new JSONObject(message);
            String type = jsonResponse.optString("type");

            // Ignorer les messages de bienvenue
            if ("welcome".equals(type)) {
                System.out.println("Message de bienvenue reçu: " + jsonResponse.optString("message"));
                return;
            }

            String modele = jsonResponse.optString("modele");
            String action = jsonResponse.optString("action");
            String statut = jsonResponse.optString("statut");
            String msg = jsonResponse.optString("message");

            Platform.runLater(() -> {
                switch (modele) {
                    case "reunion":
                        handleReunionResponse(action, statut, msg, jsonResponse);
                        break;
                    case "authentification":
                        // Ne pas traiter ici, c'est géré par AuthentificationController
                        break;
                    default:
                        // Messages génériques
                        if ("succes".equals(statut)) {
                            showAlert(true, "Succès", msg);
                        } else if ("echec".equals(statut)) {
                            showAlert(false, "Échec", msg);
                        }
                        break;
                }
            });
        } catch (Exception e) {
            System.err.println("Erreur lors du traitement de la réponse: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * CORRECTION: Gestion spécifique des réponses de réunion
     */
    private void handleReunionResponse(String action, String statut, String message, JSONObject jsonResponse) {
        System.out.println("Réponse réunion reçue - Action: " + action + ", Statut: " + statut);

        switch (action) {
            case "reponseCreation":
                if ("succes".equals(statut)) {
                    showAlert(true, "Réunion créée", message);
                    JSONObject reunionData = jsonResponse.optJSONObject("reunion");
                    if (reunionData != null) {
                        System.out.println("Réunion créée avec ID: " + reunionData.optInt("id"));
                        // Optionnel: ouvrir automatiquement l'interface de réunion
                        // ouvrirInterfaceReunion(String.valueOf(reunionData.optInt("id")));
                    }
                } else {
                    showAlert(false, "Erreur de création", message);
                }
                break;

            case "reponseRejoindre":
                if ("succes".equals(statut)) {
                    showAlert(true, "Réunion rejointe", message);
                    txtTitreReunion.clear(); // Vider le champ après succès

                    // Optionnel: ouvrir l'interface de réunion
                    int reunionId = jsonResponse.optInt("reunionId", -1);
                    if (reunionId != -1) {
                        ouvrirInterfaceReunion(String.valueOf(reunionId));
                    }
                } else {
                    showAlert(false, "Impossible de rejoindre", message);
                }
                break;

            default:
                if ("succes".equals(statut)) {
                    showAlert(true, "Information", message);
                } else {
                    showAlert(false, "Erreur", message);
                }
                break;
        }
    }

    /**
     * Ouvre l'interface de réunion
     */
    private void ouvrirInterfaceReunion(String reunionId) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/reunion.fxml"));
            Parent root = loader.load();

            ReunionController reunionController = loader.getController();
            reunionController.initData(reunionId, userId, -1, clientWebSocket);

            Stage stage = new Stage();
            stage.setTitle("Réunion - " + reunionId);
            stage.setScene(new Scene(root));
            stage.show();

        } catch (IOException e) {
            System.err.println("Erreur lors de l'ouverture de l'interface de réunion: " + e.getMessage());
            showAlert(false, "Erreur", "Impossible d'ouvrir l'interface de réunion.");
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

    // Getters et Setters
    public Reunion getReunion() {
        return reunion;
    }

    public void setReunion(Reunion reunion) {
        this.reunion = reunion;
    }

    public String getNom() {
        return nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public int getUserId() {
        return userId;
    }
}