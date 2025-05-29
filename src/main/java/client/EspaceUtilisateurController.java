package client;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
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

import javafx.geometry.Insets;


// Classe simple pour représenter une invitation dans la ListView
class Invitation {
    private int invitationId;
    private int reunionId;
    private String nomReunion;
    private String dateReunion;
    private String invitePar;

    public Invitation(int invitationId, int reunionId, String nomReunion, String dateReunion, String invitePar) {
        this.invitationId = invitationId;
        this.reunionId = reunionId;
        this.nomReunion = nomReunion;
        this.dateReunion = dateReunion;
        this.invitePar = invitePar;
    }

    public int getInvitationId() { return invitationId; }
    public int getReunionId() { return reunionId; }
    public String getNomReunion() { return nomReunion; }
    public String getDateReunion() { return dateReunion; }
    public String getInvitePar() { return invitePar; }

    @Override
    public String toString() {
        return nomReunion + " (par " + invitePar + ") - Le " + dateReunion;
    }
}


public class EspaceUtilisateurController {

    @FXML private Label welcomeLabel;
    @FXML private Button btnCreerReunion;
    @FXML private TextField txtTitreReunion;
    @FXML private Button btnRejoindre;
    @FXML private ListView<Reunion> listeReunionsUtilisateur;

    @FXML private ListView<Invitation> listeInvitations;

    private ClientWebSocket clientWebSocket;
    private String nomUtilisateur;
    private String prenomUtilisateur;
    private int currentUserId = -1;

    private final ObservableList<Reunion> reunionsObservables = FXCollections.observableArrayList();
    private final ObservableList<Invitation> invitationsObservables = FXCollections.observableArrayList();


    @FXML
    public void initialize() {
        listeReunionsUtilisateur.setItems(reunionsObservables);
        listeReunionsUtilisateur.setCellFactory(listView -> new ReunionListCell());
        listeReunionsUtilisateur.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Reunion selectedReunion = listeReunionsUtilisateur.getSelectionModel().getSelectedItem();
                if (selectedReunion != null) {
                    rejoindreReunionParId(selectedReunion.getId());
                }
            }
        });

        if (listeInvitations != null) {
            listeInvitations.setItems(invitationsObservables);
            listeInvitations.setCellFactory(param -> new InvitationListCell(
                this::handleAcceptInvitation,
                this::handleDeclineInvitation
            ));
             listeInvitations.setOnMouseClicked(event -> {
                 if (event.getClickCount() == 2) {
                    Invitation selectedInvitation = listeInvitations.getSelectionModel().getSelectedItem();
                    if (selectedInvitation != null) {
                        handleAcceptInvitation(selectedInvitation);
                    }
                }
            });
        }
    }

    public void setUserInfo(String nom, String prenom, int userId) {
        this.nomUtilisateur = nom;
        this.prenomUtilisateur = prenom;
        this.currentUserId = userId;
        if (welcomeLabel != null) {
            String displayName = (prenom != null && !prenom.isEmpty()) ? prenom : nom;
            if (displayName == null || displayName.isEmpty()) displayName = "Utilisateur " + userId;
            welcomeLabel.setText(displayName);
        }
        fetchUserMeetings();
        fetchPendingInvitations();
    }

    public void setClientWebSocket(ClientWebSocket clientWebSocket) {
        this.clientWebSocket = clientWebSocket;
    }

    @FXML
    private void handleDeconnexion() {
        System.out.println("Déconnexion demandée par l'utilisateur.");
        if (clientWebSocket != null) {
            clientWebSocket.deconnecter();
        }
        try {
            Stage stage = (Stage) welcomeLabel.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/connexionServeur.fxml")); //
            Parent root = loader.load();
            AuthentificationController authController = loader.getController(); //
            ClientWebSocket newSocket = ClientApplication.getWebSocketClientInstance(); //
            authController.setClientWebSocket(newSocket); //


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
        String codeOuNomReunion = txtTitreReunion.getText(); //
        if (codeOuNomReunion == null || codeOuNomReunion.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Champ Requis", "Veuillez saisir le nom, l'ID ou le code de la réunion.");
            txtTitreReunion.requestFocus();
            return;
        }
        if (clientWebSocket == null || !clientWebSocket.isConnected()) {
            showAlert(Alert.AlertType.ERROR, "Connexion Perdue", "Connexion au serveur perdue. Veuillez vous reconnecter.");
            return;
        }
        JSONObject jsonRequete = new JSONObject();
        jsonRequete.put("modele", "reunion");
        jsonRequete.put("action", "rejoindre");
        jsonRequete.put("code", codeOuNomReunion.trim());
        jsonRequete.put("userId", this.currentUserId);
        clientWebSocket.envoyerRequete(jsonRequete.toString());
    }

    private void rejoindreReunionParId(int reunionId) {
        System.out.println("Tentative de rejoindre la réunion ID : " + reunionId);
        if (clientWebSocket == null || !clientWebSocket.isConnected()) {
            showAlert(Alert.AlertType.ERROR, "Connexion Perdue", "Connexion au serveur perdue.");
            return;
        }
        JSONObject jsonRequete = new JSONObject();
        jsonRequete.put("modele", "reunion");
        jsonRequete.put("action", "rejoindre");
        jsonRequete.put("code", String.valueOf(reunionId)); //
        jsonRequete.put("userId", this.currentUserId);
        clientWebSocket.envoyerRequete(jsonRequete.toString());
    }


    @FXML
    private void handleClickCreerReunion() {
        if (clientWebSocket == null || !clientWebSocket.isConnected()) {
            showAlert(Alert.AlertType.ERROR, "Connexion Perdue", "Impossible de créer une réunion.");
            return;
        }
        Dialog<Reunion> dialog = createReunionDialog(); //
        dialog.showAndWait().ifPresent(this::envoyerCreationReunionServeur);
    }

    private void envoyerCreationReunionServeur(Reunion reunion) {
        if (reunion == null) return;
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

    private void fetchPendingInvitations() {
        if (clientWebSocket != null && clientWebSocket.isConnected() && currentUserId != -1) {
            JSONObject request = new JSONObject();
            request.put("modele", "reunion");
            request.put("action", "getPendingInvitations");
            request.put("userId", currentUserId);
            clientWebSocket.envoyerRequete(request.toString());
            if (listeInvitations != null) {
                listeInvitations.setPlaceholder(new Label("Chargement des invitations..."));
            }
        } else {
            if (listeInvitations != null) {
                listeInvitations.setPlaceholder(new Label("Non connecté pour charger les invitations."));
            }
        }
    }

    private void handleAcceptInvitation(Invitation invitation) {
        System.out.println("Invitation acceptée pour réunion : " + invitation.getNomReunion() + " (ID Réunion: " + invitation.getReunionId() + ")");
        rejoindreReunionParId(invitation.getReunionId());

        if (clientWebSocket != null && clientWebSocket.isConnected()) {
            JSONObject request = new JSONObject();
            request.put("modele", "reunion");
            request.put("action", "updateInvitationStatus");
            request.put("invitationId", invitation.getInvitationId());
            request.put("userId", this.currentUserId);
            request.put("newStatus", "ACCEPTEE");
            clientWebSocket.envoyerRequete(request.toString());
        }
        invitationsObservables.remove(invitation);
        if (listeInvitations != null && invitationsObservables.isEmpty()) {
             listeInvitations.setPlaceholder(new Label("Aucune invitation en attente."));
        }
    }

    private void handleDeclineInvitation(Invitation invitation) {
        System.out.println("Invitation refusée pour réunion : " + invitation.getNomReunion());
        if (clientWebSocket != null && clientWebSocket.isConnected()) {
            JSONObject request = new JSONObject();
            request.put("modele", "reunion");
            request.put("action", "updateInvitationStatus");
            request.put("invitationId", invitation.getInvitationId());
            request.put("userId", this.currentUserId);
            request.put("newStatus", "REFUSEE");
            clientWebSocket.envoyerRequete(request.toString());
        }
        invitationsObservables.remove(invitation);
        if (listeInvitations != null && invitationsObservables.isEmpty()) {
             listeInvitations.setPlaceholder(new Label("Aucune invitation en attente."));
        }
    }

    public void traiterReponseServeur(String message) {
        // Platform.runLater est dans ClientWebSocket.onMessage, donc l'appel à cette méthode est déjà sur le bon thread.
        try {
            JSONObject jsonResponse = new JSONObject(message);
            String modele = jsonResponse.optString("modele");
            // 'action' peut être l'action de la requête originale, 'actionReponse' une action spécifique de la réponse,
            // ou 'type' pour les messages non sollicités comme les notifications.
            // Nous utilisons actionOuType pour couvrir ces cas.
            String actionOuType = jsonResponse.optString("action", jsonResponse.optString("actionReponse", jsonResponse.optString("type")));
            String statut = jsonResponse.optString("statut");
            String msg = jsonResponse.optString("message", "Aucun message du serveur.");

            switch (actionOuType) {
                case "reponseCreation":
                    if ("succes".equals(statut)) {
                        JSONObject rd = jsonResponse.optJSONObject("reunion");
                        if (rd != null && jsonResponse.optBoolean("autoJoin", true)) {
                            ouvrirInterfaceReunion(String.valueOf(rd.optInt("id")), rd.optString("nom"), rd.optInt("idOrganisateur",this.currentUserId), true);
                        } else { showAlert(Alert.AlertType.INFORMATION, "Réunion Créée", msg); fetchUserMeetings(); }
                    } else { showAlert(Alert.AlertType.ERROR, "Erreur Création", msg); }
                    break;
                case "reponseRejoindre":
                    if ("succes".equals(statut)) {
                        txtTitreReunion.clear();
                        ouvrirInterfaceReunion(String.valueOf(jsonResponse.optInt("reunionId",-1)), jsonResponse.optString("nomReunion"), jsonResponse.optInt("organisateurId",-1), (this.currentUserId == jsonResponse.optInt("organisateurId",-1)));
                    } else { showAlert(Alert.AlertType.ERROR, "Impossible Rejoindre", msg); }
                    break;
                case "reponseGetReunionsUtilisateur":
                    if ("succes".equals(statut)) {
                        parseAndDisplayUserMeetings(jsonResponse.optJSONArray("reunions"));
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Erreur Chargement Réunions", msg);
                        if (listeReunionsUtilisateur != null) {
                            listeReunionsUtilisateur.setPlaceholder(new Label("Erreur chargement: " + msg));
                        }
                    }
                    break;
                case "listeInvitationsEnAttente": // Réponse de getPendingInvitations du serveur
                case "reponseGetPendingInvitations": // Si le serveur utilise ce nom d'action dans la réponse
                    if ("succes".equals(statut)) {
                        parseAndDisplayPendingInvitations(jsonResponse.optJSONArray("invitations"));
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Erreur Invitations", msg);
                        if (listeInvitations != null) {
                            listeInvitations.setPlaceholder(new Label("Erreur chargement invitations: " + msg));
                        }
                    }
                    break;
                case "nouvelleInvitation": // Notification en temps réel
                    Invitation nouvelleInvite = new Invitation(
                        jsonResponse.optInt("invitationId", 0),
                        jsonResponse.getInt("reunionId"),
                        jsonResponse.getString("nomReunion"),
                        jsonResponse.optString("dateReunion", jsonResponse.optString("debut", "N/A")),
                        jsonResponse.getString("invitePar"));
                    invitationsObservables.add(0, nouvelleInvite);
                    showAlert(Alert.AlertType.INFORMATION, "Nouvelle Invitation", jsonResponse.getString("message"));
                    if (listeInvitations != null && !invitationsObservables.isEmpty()) {
                        listeInvitations.setPlaceholder(null); // Enlever le placeholder s'il y a des invitations
                    }
                    break;
                case "updateInvitationStatusResponse":
                    System.out.println("Réponse màj statut invitation: " + msg + " (Succès: " + jsonResponse.optBoolean("success", false) + ")");
                    fetchPendingInvitations();
                    fetchUserMeetings();
                    break;
                default:
                    // CORRECTION ICI : Utiliser 'msg' pour vérifier si l'erreur concerne 'getReunionsUtilisateur'
                    if ("error".equals(jsonResponse.optString("type")) && "echec".equals(statut)) {
                       showAlert(Alert.AlertType.ERROR, "Erreur Serveur", msg);
                       // Si le message d'erreur lui-même indique que c'était pour 'getReunionsUtilisateur'
                       if (msg != null && msg.contains("'getReunionsUtilisateur'")) {
                           if (listeReunionsUtilisateur != null) {
                               listeReunionsUtilisateur.setPlaceholder(new Label("Erreur serveur (réunions): " + msg));
                           }
                       }
                    } else {
                       System.out.println("Action/Type non géré par EspaceUtilisateurController: '" + actionOuType + "'. Contenu: " + message);
                    }
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Erreur Critique Client", "Erreur lors du traitement de la réponse du serveur: " + e.getMessage());
        }
    }

    private void fetchUserMeetings() {
        if (clientWebSocket != null && clientWebSocket.isConnected() && currentUserId != -1) {
            JSONObject request = new JSONObject();
            request.put("modele", "reunion");
            request.put("action", "getReunionsUtilisateur"); //
            request.put("userId", currentUserId);
            clientWebSocket.envoyerRequete(request.toString());
            if (listeReunionsUtilisateur != null) {
                 listeReunionsUtilisateur.setPlaceholder(new Label("Chargement de vos réunions..."));
            }
        }
    }

    private void parseAndDisplayUserMeetings(JSONArray reunionsArray) {
        reunionsObservables.clear();
        if (reunionsArray != null) {
            for (int i = 0; i < reunionsArray.length(); i++) {
                JSONObject rJson = reunionsArray.getJSONObject(i);
                try {
                    reunionsObservables.add(new Reunion(
                            rJson.getInt("id"), rJson.getString("nom"),
                            rJson.optString("sujet"), rJson.optString("agenda"),
                            LocalDateTime.parse(rJson.getString("debut")), rJson.getInt("duree"),
                            Reunion.Type.valueOf(rJson.getString("type").toUpperCase()),
                            rJson.getInt("idOrganisateur"),
                            rJson.isNull("idAnimateur") ? null : rJson.getInt("idAnimateur")
                    )); //
                } catch (Exception e) { System.err.println("Erreur parsing réunion: " + rJson + " - " + e.getMessage()); }
            }
        }
        if (listeReunionsUtilisateur != null) {
            listeReunionsUtilisateur.setPlaceholder(new Label(reunionsObservables.isEmpty() ? "Aucune réunion." : null));
        }
    }

    private void parseAndDisplayPendingInvitations(JSONArray invitationsArray) {
        invitationsObservables.clear();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        if (invitationsArray != null) {
            for (int i = 0; i < invitationsArray.length(); i++) {
                JSONObject invJson = invitationsArray.getJSONObject(i);
                try {
                    String dateReunionStr = invJson.getString("dateReunion");
                    // Tenter de parser comme LocalDateTime, sinon utiliser tel quel si déjà formaté
                    try {
                        LocalDateTime ldt = LocalDateTime.parse(dateReunionStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                        dateReunionStr = ldt.format(formatter);
                    } catch (DateTimeParseException e) {
                        // Laisser dateReunionStr tel quel s'il n'est pas au format ISO (serveur l'a déjà formaté)
                    }

                    invitationsObservables.add(new Invitation(
                            invJson.getInt("invitationId"),
                            invJson.getInt("reunionId"),
                            invJson.getString("nomReunion"),
                            dateReunionStr,
                            invJson.getString("invitePar")
                    ));
                } catch (Exception e) {
                    System.err.println("Erreur parsing invitation: " + invJson + " - " + e.getMessage());
                }
            }
        }
        if (listeInvitations != null) {
            listeInvitations.setPlaceholder(new Label(invitationsObservables.isEmpty() ? "Aucune invitation en attente." : null));
        }
    }

    private void ouvrirInterfaceReunion(String reunionId, String nomReunion, int organisateurId, boolean isCurrentUserOrganizer) {
        try {
            Stage stage = (Stage) welcomeLabel.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/reunion.fxml")); //
            Parent root = loader.load();

            ReunionController reunionController = loader.getController(); //
            reunionController.setClientWebSocket(this.clientWebSocket); //

            String nomCompletUtilisateur = (prenomUtilisateur != null ? prenomUtilisateur : "") +
                    ((prenomUtilisateur != null && nomUtilisateur != null && !prenomUtilisateur.isEmpty() && !nomUtilisateur.isEmpty()) ? " " : "") +
                    (nomUtilisateur != null ? nomUtilisateur : "");
            if (nomCompletUtilisateur.trim().isEmpty()) {
                nomCompletUtilisateur = "Utilisateur " + this.currentUserId;
            }

            reunionController.initData(reunionId, this.currentUserId, nomReunion, organisateurId, nomCompletUtilisateur); //

            if (this.clientWebSocket != null) {
                this.clientWebSocket.setControllerReunion(reunionController); //
                this.clientWebSocket.setControllerEspc(null);
            }

            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setTitle("Réunion: " + nomReunion);
            stage.setOnCloseRequest(event -> {
                reunionController.handleQuitterReunion(); //
                if (this.clientWebSocket != null) {
                    this.clientWebSocket.setControllerEspc(this);
                    this.clientWebSocket.setControllerReunion(null); //
                    fetchUserMeetings();
                    fetchPendingInvitations();
                }
            });
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Erreur", "Impossible d'ouvrir l'interface de réunion: " + e.getMessage());
        }
    }

    private Dialog<Reunion> createReunionDialog() {
        // ... (code existant de la réponse précédente) ...
        Dialog<Reunion> dialog = new Dialog<>();
        dialog.setTitle("Nouvelle Réunion");
        dialog.setHeaderText("Planifiez votre nouvelle réunion");

        try {
            String cssPath = getClass().getResource("/styles/main.css").toExternalForm(); //
            dialog.getDialogPane().getStylesheets().add(cssPath);
            dialog.getDialogPane().getStyleClass().add("dialog-pane"); //
        } catch (Exception e) {
            System.err.println("CSS pour dialogue non trouvé: " + e.getMessage());
        }

        ButtonType creerButtonType = new ButtonType("Créer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(creerButtonType, ButtonType.CANCEL);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        TextField nomField = new TextField();
        nomField.setPromptText("Nom de la réunion (ex: Point d'équipe)");
        nomField.getStyleClass().add("text-input"); //

        TextField sujetField = new TextField();
        sujetField.setPromptText("Sujet (optionnel)");
        sujetField.getStyleClass().add("text-input"); //

        DatePicker datePicker = new DatePicker(LocalDate.now().plusDays(1));
        datePicker.getStyleClass().add("date-picker"); //

        ComboBox<String> heureCombo = new ComboBox<>();
        LocalTime currentTime = LocalTime.now();
        int nextHour = (currentTime.getMinute() < 30) ? currentTime.getHour() : currentTime.getHour() + 1;
        if (nextHour > 23 || nextHour < 8 ) nextHour = 8;

        for (int h = 8; h < 23; h++) {
            heureCombo.getItems().add(String.format("%02d:00", h));
            heureCombo.getItems().add(String.format("%02d:30", h));
        }
        heureCombo.setValue(String.format("%02d:00", nextHour));
        heureCombo.getStyleClass().add("combo-box"); //

        Spinner<Integer> dureeSpinner = new Spinner<>(15, 240, 60, 15);
        dureeSpinner.setEditable(true);
        dureeSpinner.getStyleClass().add("text-input"); //

        ComboBox<Reunion.Type> typeComboBox = new ComboBox<>();
        typeComboBox.setItems(FXCollections.observableArrayList(Reunion.Type.values())); //
        typeComboBox.setValue(Reunion.Type.STANDARD);
        typeComboBox.getStyleClass().add("combo-box"); //

        content.getChildren().addAll(
                new Label("Nom de la réunion:"), nomField,
                new Label("Sujet (optionnel):"), sujetField,
                new Label("Date:"), datePicker,
                new Label("Heure de début:"), heureCombo,
                new Label("Durée (minutes):"), dureeSpinner,
                new Label("Type de réunion:"), typeComboBox
        );
        content.getChildren().filtered(node -> node instanceof Label).forEach(node -> node.getStyleClass().add("field-label")); //

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

                    if (debut.isBefore(LocalDateTime.now().plusMinutes(5))) {
                        showAlert(Alert.AlertType.ERROR, "Validation", "La date de début doit être dans le futur (au moins 5 minutes).");
                        return null;
                    }

                    return new Reunion(0, nom.trim(), sujetField.getText().trim(), null,
                            debut, dureeSpinner.getValue(), typeComboBox.getValue(),
                            this.currentUserId, null
                    ); //
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
        // ... (code existant de la réponse précédente) ...
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
                nomLabel.getStyleClass().add("body-text");
                nomLabel.setStyle("-fx-font-weight: bold;");

                Label dateLabel = new Label("Le " + reunion.getDebut().format(formatter) + " (" + reunion.getDuree() + " min)");
                dateLabel.getStyleClass().add("secondary-text"); //

                Label typeLabel = new Label("Type: " + reunion.getType().toString());
                typeLabel.getStyleClass().add("secondary-text"); //

                vbox.getChildren().addAll(nomLabel, dateLabel, typeLabel);
                setGraphic(vbox);
            }
        }
    }

    static class InvitationListCell extends ListCell<Invitation> {
        private final HBox hbox = new HBox(10);
        private final VBox infoContainer = new VBox(2);
        private final Label nomReunionLabel = new Label();
        private final Label detailsLabel = new Label();
        private final Button acceptButton = new Button("Rejoindre");
        private final Button declineButton = new Button("Refuser");
        private Pane spacer = new Pane();
        private Invitation currentInvitation;

        @FunctionalInterface public interface InvitationActionHandler { void accept(Invitation invitation); }
        @FunctionalInterface public interface InvitationDeclineHandler { void decline(Invitation invitation); }

        public InvitationListCell(InvitationActionHandler acceptHandler, InvitationDeclineHandler declineHandler) {
            super();
            nomReunionLabel.setStyle("-fx-font-weight: bold;");
            infoContainer.getChildren().addAll(nomReunionLabel, detailsLabel);
            acceptButton.getStyleClass().addAll("primary-action-button", "small-button");
            declineButton.getStyleClass().addAll("secondary-action-button", "small-button");
            HBox.setHgrow(spacer, Priority.ALWAYS);
            hbox.getChildren().addAll(infoContainer, spacer, acceptButton, declineButton);
            hbox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            hbox.setPadding(new Insets(5));

            acceptButton.setOnAction(event -> {
                if (currentInvitation != null && acceptHandler != null) acceptHandler.accept(currentInvitation);
            });
            declineButton.setOnAction(event -> {
                if (currentInvitation != null && declineHandler != null) declineHandler.decline(currentInvitation);
            });
        }

        @Override
        protected void updateItem(Invitation invitation, boolean empty) {
            super.updateItem(invitation, empty);
            currentInvitation = invitation;
            if (empty || invitation == null) {
                setText(null); setGraphic(null);
            } else {
                nomReunionLabel.setText(invitation.getNomReunion());
                detailsLabel.setText("Par: " + invitation.getInvitePar() + " - Le: " + invitation.getDateReunion());
                detailsLabel.getStyleClass().add("secondary-text"); //
                setGraphic(hbox);
            }
        }
    }

    public void showAlert(Alert.AlertType alertType, String title, String message) {
        // ... (code existant de la réponse précédente) ...
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        try {
            String cssPath = getClass().getResource("/styles/main.css").toExternalForm(); //
            if (cssPath != null) {
                alert.getDialogPane().getStylesheets().add(cssPath);
                alert.getDialogPane().getStyleClass().add("dialog-pane"); //
            }
        } catch (Exception e) {
            System.err.println("CSS pour alerte non trouvé: " + e.getMessage());
        }
        alert.showAndWait();
    }
}