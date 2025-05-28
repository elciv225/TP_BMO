package client;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.layout.GridPane;
import model.Reunion;
import org.json.JSONArray;
import org.json.JSONObject;

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
    public TextField txtTitreReunion; // Used for joining by code/titre
    @FXML
    public Button btnRejoindre;
    @FXML
    private Label welcomeLabel;
    @FXML
    private ListView<String> meetingsListView;
    @FXML
    private TextField userInputForInvite;
    @FXML
    private Button inviteUserButton;

    private Reunion currentReunionForDialog;
    private ClientWebSocket clientWebSocket;
    private String nom; // User's name from login
    private String prenom; // User's surname from login
    private int currentUserId = -1; // User's ID from login

    public Reunion getCurrentReunionForDialog() {
        return currentReunionForDialog;
    }

    public void setCurrentReunionForDialog(Reunion reunion) {
        this.currentReunionForDialog = reunion;
    }

    @FXML
    public void initialize() {
        clientWebSocket = new ClientWebSocket();
        clientWebSocket.setControllerEspc(this);
        requestMeetingList();

        // Bind the action for btnRejoindre
        if (btnRejoindre != null) {
            btnRejoindre.setOnAction(event -> handleRejoindreReunion());
        }
    }

    private void requestMeetingList() {
        JSONObject listRequest = new JSONObject();
        listRequest.put("modele", "reunion");
        listRequest.put("action", "lister");
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
            showAlert(false, "Erreur d'invitation", "Veuillez entrer l'identifiant de l'utilisateur à inviter.");
            return;
        }

        String reunionId = parseReunionIdFromString(selectedMeetingInfo);
        if (reunionId == null) {
            showAlert(false, "Erreur d'invitation", "Impossible d'identifier la réunion sélectionnée. Format attendu: 'ID: [id] - ...'");
            return;
        }

        JSONObject inviteRequest = new JSONObject();
        inviteRequest.put("modele", "reunion");
        inviteRequest.put("action", "inviter");
        inviteRequest.put("reunionId", Integer.parseInt(reunionId)); // Ensure it's an int
        try {
            inviteRequest.put("utilisateurId", Integer.parseInt(userToInvite)); // Assuming userToInvite is an ID
        } catch (NumberFormatException e) {
            showAlert(false, "Erreur d'invitation", "L'identifiant utilisateur doit être un nombre (ID).");
            return;
        }
        clientWebSocket.envoyerRequete(inviteRequest.toString());
        userInputForInvite.clear();
    }

    private String parseReunionIdFromString(String meetingInfo) {
        if (meetingInfo == null) return null;
        String prefix = "ID: ";
        int startIndex = meetingInfo.indexOf(prefix);
        if (startIndex != -1) {
            startIndex += prefix.length();
            int endIndex = meetingInfo.indexOf(" -", startIndex);
            if (endIndex != -1) {
                return meetingInfo.substring(startIndex, endIndex).trim();
            } else {
                return meetingInfo.substring(startIndex).trim();
            }
        }
        System.err.println("Could not parse Reunion ID from: " + meetingInfo);
        return null;
    }

    public void setUserInfo(String nom, String prenom, int userId) {
        this.nom = nom;
        this.prenom = prenom;
        this.currentUserId = userId;
        if (welcomeLabel != null) {
            welcomeLabel.setText("Bienvenue " + prenom + " " + nom + " (ID: " + userId + ")");
        }
    }

    @FXML
    private void handleClickCreerReunion() {
        Dialog<Reunion> dialog = new Dialog<>();
        dialog.setTitle("Créer une Réunion");
        dialog.setHeaderText("Veuillez entrer les détails de la nouvelle réunion.");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField nomField = new TextField();
        nomField.setPromptText("Nom de la réunion");
        TextField sujetField = new TextField();
        sujetField.setPromptText("Sujet");
        TextArea agendaField = new TextArea();
        agendaField.setPromptText("Agenda");
        agendaField.setPrefRowCount(3);
        DatePicker datePicker = new DatePicker(LocalDate.now());
        TextField heureField = new TextField(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        heureField.setPromptText("Heure (HH:mm)");
        TextField dureeField = new TextField();
        dureeField.setPromptText("Durée en minutes (ex: 60)");
        ComboBox<Reunion.Type> typeComboBox = new ComboBox<>(FXCollections.observableArrayList(Reunion.Type.values()));
        typeComboBox.setPromptText("Type de réunion");
        TextField animateurIdField = new TextField();
        animateurIdField.setPromptText("ID de l'animateur (optionnel)");

        grid.add(new Label("Nom:"), 0, 0); grid.add(nomField, 1, 0);
        grid.add(new Label("Sujet:"), 0, 1); grid.add(sujetField, 1, 1);
        grid.add(new Label("Agenda:"), 0, 2); grid.add(agendaField, 1, 2);
        grid.add(new Label("Date:"), 0, 3); grid.add(datePicker, 1, 3);
        grid.add(new Label("Heure:"), 0, 4); grid.add(heureField, 1, 4);
        grid.add(new Label("Durée (min):"), 0, 5); grid.add(dureeField, 1, 5);
        grid.add(new Label("Type:"), 0, 6); grid.add(typeComboBox, 1, 6);
        grid.add(new Label("Animateur ID (Opt.):"), 0, 7); grid.add(animateurIdField, 1, 7);

        dialog.getDialogPane().setContent(grid);
        ButtonType creerButtonType = new ButtonType("Créer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(creerButtonType, ButtonType.CANCEL);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == creerButtonType) {
                try {
                    String nomReunion = nomField.getText().trim();
                    String sujetReunion = sujetField.getText().trim();
                    String agendaReunion = agendaField.getText().trim();
                    LocalDate date = datePicker.getValue();
                    String heureText = heureField.getText().trim();
                    Reunion.Type type = typeComboBox.getValue();
                    String dureeText = dureeField.getText().trim();

                    if (nomReunion.isEmpty() || date == null || heureText.isEmpty() || type == null || dureeText.isEmpty()) {
                        showAlert(false, "Validation", "Nom, date, heure, type et durée sont obligatoires.");
                        return null;
                    }

                    LocalTime time = LocalTime.parse(heureText, DateTimeFormatter.ofPattern("HH:mm"));
                    LocalDateTime debut = LocalDateTime.of(date, time);
                    int duree = Integer.parseInt(dureeText);

                    if (this.currentUserId == -1) {
                        showAlert(false, "Erreur Utilisateur", "ID Utilisateur non défini. Impossible de créer la réunion.");
                        return null;
                    }
                    int organisateurId = this.currentUserId;

                    Integer animateurId = null;
                    if (!animateurIdField.getText().isEmpty()) {
                        animateurId = Integer.parseInt(animateurIdField.getText().trim());
                    }
                    return new Reunion(nomReunion, sujetReunion, agendaReunion, debut, duree, type, organisateurId, animateurId);
                } catch (NumberFormatException e) {
                    showAlert(false, "Erreur de Format", "Durée et ID Animateur (si fourni) doivent être des nombres.");
                    return null;
                } catch (DateTimeParseException e) {
                    showAlert(false, "Erreur de Format", "Veuillez vérifier le format de l'heure (HH:mm).");
                    return null;
                }
            }
            return null;
        });

        Optional<Reunion> result = dialog.showAndWait();
        result.ifPresent(this::envoyerCreationReunion);
    }

    private void envoyerCreationReunion(Reunion reunionToCreate) {
        String jsonRequete = creerJsonCreationReunion(reunionToCreate);
        clientWebSocket.envoyerRequete(jsonRequete);
    }

    private String creerJsonCreationReunion(Reunion reunionToCreate) {
        JSONObject json = new JSONObject();
        json.put("modele", "reunion");
        json.put("action", "creation");
        json.put("titre", reunionToCreate.getNom());
        json.put("sujet", reunionToCreate.getSujet());
        json.put("agenda", reunionToCreate.getAgenda());
        json.put("debut", reunionToCreate.getDebut().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        json.put("duree", reunionToCreate.getDuree());
        json.put("type", reunionToCreate.getType().toString());
        json.put("idOrganisateur", reunionToCreate.getIdOrganisateur());
        // Corrected condition: getIdAnimateur() returns int, so != 0 is sufficient (assuming 0 means no animator)
        if (reunionToCreate.getIdAnimateur() != 0) { 
            json.put("idAnimateur", reunionToCreate.getIdAnimateur());
        }
        return json.toString();
    }

    private void handleRejoindreReunion() {
        String codeOuTitre = txtTitreReunion.getText().trim();
        if (codeOuTitre.isEmpty()) {
            showAlert(false, "Erreur", "Le code ou titre de réunion ne peut pas être vide.");
            return;
        }
        String jsonRequete = creerJsonRejoindreReunion(codeOuTitre);
        clientWebSocket.envoyerRequete(jsonRequete);
    }

    private String creerJsonRejoindreReunion(String codeReunion) { // Parameter name is 'codeReunion'
        JSONObject json = new JSONObject();
        json.put("modele", "reunion");
        json.put("action", "rejoindre");
        json.put("code", codeReunion); // Use the parameter value
        json.put("participant", this.prenom + " " + this.nom); // Use instance fields for participant name
        return json.toString();
    }
    
    // Renamed from traiterReponseConnexion to handle various server responses
    public void handleServerResponse(String message) {
        if (message == null || message.trim().isEmpty()) {
            System.err.println("Erreur: message WebSocket vide reçu.");
            return;
        }

        JSONObject jsonResponse = new JSONObject(message);
        String statut = jsonResponse.optString("statut");
        String msg = jsonResponse.optString("message", "Aucun message du serveur.");
        String actionOriginale = jsonResponse.optString("actionOriginale", "inconnue");

        Platform.runLater(() -> {
            if ("succes".equals(statut)) {
                showAlert(true, "Succès (" + actionOriginale + ")", msg);
                if ("lister".equals(actionOriginale)) {
                    JSONArray meetingsArray = jsonResponse.optJSONArray("reunions");
                    if (meetingsArray != null) {
                        ObservableList<String> items = FXCollections.observableArrayList();
                        for (int i = 0; i < meetingsArray.length(); i++) {
                            JSONObject meetingJson = meetingsArray.getJSONObject(i);
                            String displayString = String.format("ID: %d - Titre: %s (Début: %s)",
                                    meetingJson.optInt("id"),
                                    meetingJson.optString("titre", "Sans titre"),
                                    meetingJson.optString("debut", "N/A"));
                            items.add(displayString);
                        }
                        meetingsListView.setItems(items);
                    }
                } else if ("creation".equals(actionOriginale)) {
                    requestMeetingList(); // Refresh list after creation
                }
            } else { // "echec" or other
                showAlert(false, "Échec (" + actionOriginale + ")", msg);
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