package client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.layout.GridPane;
import model.Reunion;
// import model.ReunionManager; // ReunionManager is likely server-side or for local storage, not directly used for WS calls here
import org.json.JSONArray;
import org.json.JSONObject;

// import java.sql.SQLException; // Client unlikely to use SQLException directly
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
    @FXML
    private ListView<String> meetingsListView; // To display meeting strings
    @FXML
    private TextField userInputForInvite;
    @FXML
    private Button inviteUserButton;


    private Reunion currentReunionForDialog; // To store the reunion object from the dialog

    public Reunion getCurrentReunionForDialog() {
        return currentReunionForDialog;
    }

    public void setCurrentReunionForDialog(Reunion reunion) {
        this.currentReunionForDialog = reunion;
    }

    private ClientWebSocket clientWebSocket;
    private String nom; // User's name
    private String prenom; // User's surname
    private int currentUserId = -1; // Placeholder for the current user's ID

    @FXML
    public void initialize() {
        clientWebSocket = new ClientWebSocket();
        clientWebSocket.setControllerEspc(this); // Link controller to WebSocket client

        // Request meeting list on initialization
        requestMeetingList();
    }

    private void requestMeetingList() {
        JSONObject listRequest = new JSONObject();
        listRequest.put("modele", "reunion");
        listRequest.put("action", "lister"); // Assuming "lister" is the server action
        clientWebSocket.envoyerRequete(listRequest.toString());
    }

    @FXML
    private void handleInviteUser() {
        String selectedMeetingInfo = meetingsListView.getSelectionModel().getSelectedItem();
        String userToInvite = userInputForInvite.getText();

        if (selectedMeetingInfo == null || selectedMeetingInfo.isEmpty()) {
            showAlert(false, "Erreur d'invitation", "Veuillez sélectionner une réunion dans la liste.");
            return;
        }
        if (userToInvite == null || userToInvite.trim().isEmpty()) {
            showAlert(false, "Erreur d'invitation", "Veuillez entrer l'identifiant (ID, email, etc.) de l'utilisateur à inviter.");
            return;
        }

        String reunionId = parseReunionIdFromString(selectedMeetingInfo);
        if (reunionId == null) {
            showAlert(false, "Erreur d'invitation", "Impossible d'identifier la réunion sélectionnée. Format attendu: 'ID: [id] - ...'");
            return;
        }

        JSONObject inviteRequest = new JSONObject();
        inviteRequest.put("modele", "reunion");
        inviteRequest.put("action", "inviter"); // Server should handle "inviter" or "ajouterParticipant"
        inviteRequest.put("reunionId", reunionId);
        inviteRequest.put("utilisateurId", userToInvite); // Or "email", "participantId" - depends on server implementation

        clientWebSocket.envoyerRequete(inviteRequest.toString());
        userInputForInvite.clear(); // Clear input after sending
    }

    private String parseReunionIdFromString(String meetingInfo) {
        if (meetingInfo == null) return null;
        // Expecting format like "ID: 123 - Titre: My Meeting"
        // Or simply the ID if the ListView stores only IDs or Reunion objects with ID.
        // For now, parsing "ID: xxx -"
        String prefix = "ID: ";
        int startIndex = meetingInfo.indexOf(prefix);
        if (startIndex != -1) {
            startIndex += prefix.length();
            int endIndex = meetingInfo.indexOf(" -", startIndex);
            if (endIndex != -1) {
                return meetingInfo.substring(startIndex, endIndex).trim();
            } else { // If no " -" found after ID:, maybe the ID is the rest of the string
                return meetingInfo.substring(startIndex).trim();
            }
        }
        // Fallback or alternative parsing if the format is different.
        // This part is crucial and depends on how items are added to meetingsListView.
        System.err.println("Could not parse Reunion ID from: " + meetingInfo);
        return null; // Or throw an exception
    }



    public void setUserInfo(String nom, String prenom, int userId) { // Added userId
        this.nom = nom;
        this.prenom = prenom;
        this.currentUserId = userId; // Store the user's ID
        if (welcomeLabel != null) {
            welcomeLabel.setText("Bienvenue " + prenom + " " + nom + " (ID: " + userId + ")");
        }
    }

    @FXML
    private void handleClickCreerReunion() {
        Dialog<Reunion> dialog = new Dialog<>();
        dialog.setTitle("Créer une Réunion");
        dialog.setHeaderText("Veuillez entrer les détails de la nouvelle réunion.");

        // Content for the dialog
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField nomField = new TextField();
        nomField.setPromptText("Nom de la réunion");
        TextField sujetField = new TextField();
        sujetField.setPromptText("Sujet");
        TextArea agendaField = new TextArea(); // Changed to TextArea for more space
        agendaField.setPromptText("Agenda");
        agendaField.setPrefRowCount(3);
        DatePicker datePicker = new DatePicker();
        datePicker.setPromptText("Date de début");
        datePicker.setValue(LocalDate.now()); // Default to today
        TextField heureField = new TextField();
        heureField.setPromptText("Heure (HH:mm)");
        heureField.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))); // Default to now
        TextField dureeField = new TextField();
        dureeField.setPromptText("Durée en minutes (ex: 60)");
        ComboBox<Reunion.Type> typeComboBox = new ComboBox<>();
        typeComboBox.getItems().setAll(Reunion.Type.values());
        typeComboBox.setPromptText("Type de réunion");
        // Organisateur ID should be the current user, not manually entered generally
        // TextField organisateurIdField = new TextField(String.valueOf(currentUserId));
        // organisateurIdField.setPromptText("ID de l'organisateur");
        // organisateurIdField.setDisable(true); // Pre-fill with current user's ID

        TextField animateurIdField = new TextField();
        animateurIdField.setPromptText("ID de l'animateur (optionnel, si différent de l'organisateur)");


        grid.add(new Label("Nom:"), 0, 0); grid.add(nomField, 1, 0);
        grid.add(new Label("Sujet:"), 0, 1); grid.add(sujetField, 1, 1);
        grid.add(new Label("Agenda:"), 0, 2); grid.add(agendaField, 1, 2);
        grid.add(new Label("Date:"), 0, 3); grid.add(datePicker, 1, 3);
        grid.add(new Label("Heure:"), 0, 4); grid.add(heureField, 1, 4);
        grid.add(new Label("Durée (min):"), 0, 5); grid.add(dureeField, 1, 5);
        grid.add(new Label("Type:"), 0, 6); grid.add(typeComboBox, 1, 6);
        // grid.add(new Label("Organisateur ID:"), 0, 7); grid.add(organisateurIdField, 1, 7); // Removed for auto-fill
        grid.add(new Label("Animateur ID (Opt.):"), 0, 7); grid.add(animateurIdField, 1, 7);


        dialog.getDialogPane().setContent(grid);
        ButtonType creerButtonType = new ButtonType("Créer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(creerButtonType, ButtonType.CANCEL);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == creerButtonType) {
                try {
                    String nomReunion = nomField.getText();
                    String sujetReunion = sujetField.getText();
                    String agendaReunion = agendaField.getText();
                    LocalDate date = datePicker.getValue();
                    String heureText = heureField.getText();
                    Reunion.Type type = typeComboBox.getValue();
                    String dureeText = dureeField.getText();

                    if (nomReunion.isEmpty() || date == null || heureText.isEmpty() || type == null || dureeText.isEmpty()) {
                        showAlert(false, "Validation", "Nom, date, heure, type et durée sont obligatoires.");
                        return null;
                    }

                    LocalTime time = LocalTime.parse(heureText, DateTimeFormatter.ofPattern("HH:mm"));
                    LocalDateTime debut = LocalDateTime.of(date, time);
                    int duree = Integer.parseInt(dureeText);
                    // int organisateurId = Integer.parseInt(organisateurIdField.getText()); // Now using currentUserId
                    int organisateurId = this.currentUserId; // Use the logged-in user's ID
                     if (organisateurId == -1) {
                        showAlert(false, "Erreur Utilisateur", "ID Utilisateur non défini. Impossible de créer la réunion.");
                        return null;
                    }


                    Integer animateurId = null;
                    if (!animateurIdField.getText().isEmpty()) {
                        animateurId = Integer.parseInt(animateurIdField.getText());
                    }

                    return new Reunion(nomReunion, sujetReunion, agendaReunion, debut, duree, type, organisateurId, animateurId);
                } catch (NumberFormatException | DateTimeParseException e) {
                    showAlert(false, "Erreur de Format", "Veuillez vérifier le format des nombres (durée, IDs) et de l'heure (HH:mm).");
                    return null;
                }
            }
            return null;
        });

        Optional<Reunion> result = dialog.showAndWait();
        result.ifPresent(nouvelleReunion -> {
            setCurrentReunionForDialog(nouvelleReunion); // Store it
            System.out.println("Tentative de création de réunion: " + nouvelleReunion.getNom());
            envoyerCreationReunion(nouvelleReunion);
        });
    }

    // Renamed to avoid conflict and clarify purpose
    private void envoyerCreationReunion(Reunion reunionToCreate) {
        String jsonRequete = creerJsonCreationReunion(reunionToCreate);
        clientWebSocket.envoyerRequete(jsonRequete);
    }

    // Corrected id_organisateur to use reunionToCreate.getIdOrganisateur()
    private String creerJsonCreationReunion(Reunion reunionToCreate) {
        JSONObject json = new JSONObject();
        json.put("modele", "reunion");
        json.put("action", "creation");
        // Use "titre" as per server expectation in ReunionService if that's the case
        // Based on ReunionService, it expects "titre", not "nom" from client for creation.
        json.put("titre", reunionToCreate.getNom()); // Assuming server's "titre" maps to client's "nom"
        json.put("sujet", reunionToCreate.getSujet());
        json.put("agenda", reunionToCreate.getAgenda());
        json.put("debut", reunionToCreate.getDebut().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        json.put("duree", reunionToCreate.getDuree());
        json.put("type", reunionToCreate.getType().toString());
        json.put("idOrganisateur", reunionToCreate.getIdOrganisateur()); // Corrected field
        if (reunionToCreate.getIdAnimateur() != null) {
            json.put("idAnimateur", reunionToCreate.getIdAnimateur());
        }
        return json.toString();
    }

    // Method to handle joining a meeting, seems pre-existing, ensure it's okay
    private void handleRejoindreReunion() { // Assuming this is triggered by btnRejoindre
        String codeOuTitre = txtTitreReunion.getText();
        if (codeOuTitre == null || codeOuTitre.trim().isEmpty()) {
            showAlert(false, "Erreur", "Le code ou titre de réunion ne peut pas être vide.");
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