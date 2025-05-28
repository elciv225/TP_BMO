package client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import model.Reunion;
import model.ReunionManager;
import org.json.JSONObject;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

public class EspaceUtilisateurController {

    @FXML
    public Button btnCreerReunion;
    @FXML
    public TextField txtTitreReunion;
    @FXML
    public Button btnRejoindre;
    @FXML
    private Label welcomeLabel;

    private Reunion reunion;

    public Reunion getReunion() {
        return reunion;
    }

    public void setReunion(Reunion reunion) {
        this.reunion = reunion;
    }

    private ClientWebSocket clientWebSocket;
    private String nom;
    private String prenom;

    @FXML
    public void initialize() {
        clientWebSocket = new ClientWebSocket();
        clientWebSocket.setControllerEspc(this);

    }



    public void setUserInfo(String nom, String prenom) {
        this.nom = nom;
        this.prenom = prenom;
        if (welcomeLabel != null) {
            welcomeLabel.setText("Bienvenue " + nom + " " + prenom);
        }
    }

    @FXML
    private void handleClickCreerReunion() {
        Dialog<Reunion> dialog = new Dialog<>();
        dialog.setTitle("Créer une réunion");
        dialog.setHeaderText("Remplissez les détails de la réunion");

        // Champs de saisie
        TextField nomField = new TextField();
        nomField.setPromptText("Nom de la réunion");

        TextField sujetField = new TextField();
        sujetField.setPromptText("Sujet");

        TextField agendaField = new TextField();
        agendaField.setPromptText("Agenda");

        DatePicker datePicker = new DatePicker();
        datePicker.setPromptText("Date de début");

        TextField heureField = new TextField();
        heureField.setPromptText("Heure (HH:mm)");

        TextField dureeField = new TextField();
        dureeField.setPromptText("Durée en minutes");

        ComboBox<Reunion.Type> typeComboBox = new ComboBox<>();
        typeComboBox.getItems().setAll(Reunion.Type.values());
        typeComboBox.setPromptText("Type de réunion");

        TextField organisateurIdField = new TextField();
        organisateurIdField.setPromptText("ID de l'organisateur");

        TextField animateurIdField = new TextField();
        animateurIdField.setPromptText("ID de l'animateur (optionnel)");

        // Layout pour les champs de saisie
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Nom:"), 0, 0);
        grid.add(nomField, 1, 0);
        grid.add(new Label("Sujet:"), 0, 1);
        grid.add(sujetField, 1, 1);
        grid.add(new Label("Agenda:"), 0, 2);
        grid.add(agendaField, 1, 2);
        grid.add(new Label("Date:"), 0, 3);
        grid.add(datePicker, 1, 3);
        grid.add(new Label("Heure:"), 0, 4);
        grid.add(heureField, 1, 4);
        grid.add(new Label("Durée (minutes):"), 0, 5);
        grid.add(dureeField, 1, 5);
        grid.add(new Label("Type:"), 0, 6);
        grid.add(typeComboBox, 1, 6);
        grid.add(new Label("Organisateur ID:"), 0, 7);
        grid.add(organisateurIdField, 1, 7);
        grid.add(new Label("Animateur ID:"), 0, 8);
        grid.add(animateurIdField, 1, 8);

        dialog.getDialogPane().setContent(grid);

        // Boutons
        ButtonType creerButtonType = new ButtonType("Créer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(creerButtonType, ButtonType.CANCEL);

        // Convertir les résultats en Reunion object
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == creerButtonType) {
                try {
                    LocalDate date = datePicker.getValue();
                    String heureText = heureField.getText();
                    Reunion.Type type = typeComboBox.getValue();

                    if (date == null || heureText == null || heureText.isEmpty() || type == null) {
                        showAlert(false, "Erreur", "Veuillez remplir tous les champs obligatoires.");
                        return null;
                    }

                    LocalTime time = LocalTime.parse(heureText, DateTimeFormatter.ofPattern("HH:mm"));
                    LocalDateTime debut = LocalDateTime.of(date, time);
                    int duree = Integer.parseInt(dureeField.getText());
                    int organisateurId = Integer.parseInt(organisateurIdField.getText());
                    Integer animateurId = null;
                    if (!animateurIdField.getText().isEmpty()) {
                        animateurId = Integer.parseInt(animateurIdField.getText());
                    }

                    return new Reunion(
                            nomField.getText(),
                            sujetField.getText(),
                            agendaField.getText(),
                            debut,
                            duree,
                            type,
                            organisateurId,
                            animateurId
                    );
                } catch (NumberFormatException | DateTimeParseException e) {
                    e.printStackTrace();
                    showAlert(false, "Erreur", "Veuillez saisir des valeurs valides.");
                    return null;
                }
            }
            return null;
        });

        // Gestion du résultat du dialogue
        dialog.showAndWait().ifPresent(r -> { // Changer 'r' en 'reunion' pour plus de clarté
            if (r != null) {
                System.out.println("Réunion créée: " + r.toString());
                setReunion(r); // Utiliser 'reunion' ici
                creerReunion(r); // Utiliser 'reunion' ici
            }
        });
    }

    private void creerReunion(Reunion reunion) {
        // Préparer la requête JSON pour créer une réunion
        String jsonRequete = creerJsonCreationReunion(reunion);
        // Envoyer la requête via WebSocket
        clientWebSocket.envoyerRequete(jsonRequete);
    }

    private String creerJsonCreationReunion(Reunion reunion) {
        JSONObject json = new JSONObject();
        json.put("modele", "reunion");
        json.put("action", "creation");
        json.put("nom", reunion.getNom());
        json.put("sujet", reunion.getSujet());
        json.put("agenda", reunion.getAgenda());
        json.put("date_debut", reunion.getDebut().toString()); // Format ISO 8601
        json.put("duree", reunion.getDuree());
        json.put("type", reunion.getType().toString());
        json.put("id_organisateur", reunion.getId());

        return json.toString();
    }


    private void rejoindreReunion(String codeReunion) {
        if (codeReunion == null || codeReunion.trim().isEmpty()) {
            showAlert(false, "Erreur", "Le code de réunion ne peut pas être vide.");
            return;
        }

        // Préparer la requête JSON pour rejoindre une réunion
        String jsonRequete = creerJsonRejoindreReunion(codeReunion);

        // Envoyer la requête via WebSocket
        clientWebSocket.envoyerRequete(jsonRequete);
    }

    private String creerJsonRejoindreReunion(String codeReunion) {
        return "{" + "\"modele\":\"reunion\"," + "\"action\":\"rejoindre\"," + "\"code\":\"" + codeReunion + "\"," + "\"participant\":\"" + nom + " " + prenom + "\"" + "}";
    }

    public void traiterReponseConnexion(String message) {
        if (message == null || message.trim().isEmpty()) {
            System.err.println("Erreur: message WebSocket vide reçu.");
            return;
        }

        JSONObject jsonResponse = new JSONObject(message);
        String statut = jsonResponse.optString("statut");
        String msg = jsonResponse.optString("message");

        Platform.runLater(() -> {
            if ("succes".equals(statut)) {
                showAlert(true, "Succès", msg);
                // Vous pouvez ajouter ici la logique pour charger une nouvelle page ou mettre à jour l'UI
            } else {
                showAlert(false, "Échec", msg);
            }
        });
    }

    public void showAlert(boolean success, String titre, String message) {
        Alert.AlertType alertType = success ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR;
        Alert alert = new Alert(alertType);
        alert.setTitle(titre);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}