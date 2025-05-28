package client;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
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
    
    // Dialogue de loading moderne
    private Dialog<Void> loadingDialog;

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
            welcomeLabel.setText("Bienvenue " + nom + " " + prenom + " 👋");
        }
    }

    public void setUserInfo(String nom, String prenom, int userId, ClientWebSocket clientWebSocket) {
        this.nom = nom;
        this.prenom = prenom;
        this.userId = userId;
        this.clientWebSocket = clientWebSocket;
        if (welcomeLabel != null) {
            welcomeLabel.setText("Bienvenue " + nom + " " + prenom + " 👋");
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
            showModernAlert("Champ requis", "Veuillez saisir le nom ou l'ID de la réunion à rejoindre.", Alert.AlertType.WARNING);
            return;
        }

        if (clientWebSocket == null || !clientWebSocket.isConnected()) {
            showModernAlert("Connexion interrompue", "Impossible de rejoindre la réunion.\nVeuillez vérifier votre connexion.", Alert.AlertType.ERROR);
            return;
        }

        // Afficher le loading
        showLoadingDialog("Connexion à la réunion...");
        rejoindreReunion(titreReunion.trim());
    }

    @FXML
    private void handleClickCreerReunion() {
        if (clientWebSocket == null || !clientWebSocket.isConnected()) {
            showModernAlert("Connexion interrompue", "Impossible de créer une réunion.\nVeuillez vérifier votre connexion.", Alert.AlertType.ERROR);
            return;
        }

        Dialog<Reunion> dialog = createModernReunionDialog();
        dialog.showAndWait().ifPresent(r -> {
            if (r != null) {
                System.out.println("Nouvelle réunion: " + r.toString());
                setReunion(r);
                showLoadingDialog("Création de la réunion...");
                creerReunion(r);
            }
        });
    }

    /**
     * 🎨 NOUVEAU: Dialogue moderne style Teams/Meet
     */
    private Dialog<Reunion> createModernReunionDialog() {
        Dialog<Reunion> dialog = new Dialog<>();
        dialog.setTitle("Nouvelle réunion");
        dialog.setHeaderText(null);
        
        // Style moderne pour le dialogue
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/styles/main.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("modern-dialog");

        // Icône personnalisée
        Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
        stage.initStyle(StageStyle.DECORATED);

        // 📋 Formulaire moderne avec sections
        VBox mainContainer = new VBox(25);
        mainContainer.getStyleClass().add("dialog-container");
        mainContainer.setPadding(new Insets(30));

        // 🏷️ Titre de section
        Label titleSection = new Label("📅 Détails de la réunion");
        titleSection.getStyleClass().add("dialog-section-title");

        // 📝 Champs de base
        VBox basicInfoSection = createFormSection();
        
        TextField nomField = createModernTextField("Nom de la réunion", "ex: Réunion équipe projet", true);
        TextField sujetField = createModernTextField("Sujet", "ex: Revue sprint", false);
        TextArea agendaField = createModernTextArea("Ordre du jour", "Points à aborder...");
        
        basicInfoSection.getChildren().addAll(
            createFieldGroup("📝 Nom *", nomField),
            createFieldGroup("💭 Sujet", sujetField),
            createFieldGroup("📋 Agenda", agendaField)
        );

        // 🕐 Section Date & Heure
        Label scheduleSection = new Label("🕐 Planification");
        scheduleSection.getStyleClass().add("dialog-section-title");

        VBox scheduleInfoSection = createFormSection();
        
        DatePicker datePicker = createModernDatePicker();
        datePicker.setValue(LocalDate.now());
        
        ComboBox<String> heureCombo = createTimeComboBox();
        Spinner<Integer> dureeSpinner = createDurationSpinner();
        
        HBox timeBox = new HBox(15);
        timeBox.setAlignment(Pos.CENTER_LEFT);
        timeBox.getChildren().addAll(heureCombo, new Label("pour"), dureeSpinner, new Label("minutes"));
        
        scheduleInfoSection.getChildren().addAll(
            createFieldGroup("📅 Date *", datePicker),
            createFieldGroup("⏰ Heure et durée *", timeBox)
        );

        // 🔒 Section Type & Sécurité
        Label securitySection = new Label("🔒 Paramètres");
        securitySection.getStyleClass().add("dialog-section-title");

        VBox securityInfoSection = createFormSection();
        
        ComboBox<Reunion.Type> typeComboBox = createTypeComboBox();
        
        securityInfoSection.getChildren().addAll(
            createFieldGroup("🎯 Type de réunion *", typeComboBox)
        );

        // 📦 Assemblage
        mainContainer.getChildren().addAll(
            titleSection, basicInfoSection,
            scheduleSection, scheduleInfoSection,
            securitySection, securityInfoSection
        );

        ScrollPane scrollPane = new ScrollPane(mainContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("dialog-scroll");
        scrollPane.setPrefSize(600, 700);

        dialog.getDialogPane().setContent(scrollPane);

        // 🎯 Boutons modernes
        ButtonType creerButtonType = new ButtonType("✨ Créer la réunion", ButtonBar.ButtonData.OK_DONE);
        ButtonType annulerButtonType = new ButtonType("❌ Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(creerButtonType, annulerButtonType);

        // Style des boutons
        Platform.runLater(() -> {
            Button creerBtn = (Button) dialog.getDialogPane().lookupButton(creerButtonType);
            Button annulerBtn = (Button) dialog.getDialogPane().lookupButton(annulerButtonType);
            
            creerBtn.getStyleClass().addAll("modern-primary-button");
            annulerBtn.getStyleClass().addAll("modern-secondary-button");
        });

        // 🔄 Logique de validation et création
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == creerButtonType) {
                return validateAndCreateReunion(nomField, sujetField, agendaField, 
                    datePicker, heureCombo, dureeSpinner, typeComboBox);
            }
            return null;
        });

        return dialog;
    }

    /**
     * 🎨 Composants UI modernes 
     */
    private TextField createModernTextField(String label, String prompt, boolean required) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.getStyleClass().add("modern-text-field");
        if (required) {
            field.getStyleClass().add("required-field");
        }
        return field;
    }

    private TextArea createModernTextArea(String label, String prompt) {
        TextArea area = new TextArea();
        area.setPromptText(prompt);
        area.setPrefRowCount(3);
        area.setMaxHeight(80);
        area.getStyleClass().add("modern-text-area");
        return area;
    }

    private DatePicker createModernDatePicker() {
        DatePicker picker = new DatePicker();
        picker.getStyleClass().add("modern-date-picker");
        return picker;
    }

    private ComboBox<String> createTimeComboBox() {
        ComboBox<String> combo = new ComboBox<>();
        combo.getStyleClass().add("modern-combo-box");
        
        // Générer les heures (8h - 22h par pas de 30min)
        for (int h = 8; h <= 22; h++) {
            combo.getItems().addAll(
                String.format("%02d:00", h),
                String.format("%02d:30", h)
            );
        }
        combo.setValue("14:00");
        combo.setEditable(false);
        return combo;
    }

    private Spinner<Integer> createDurationSpinner() {
        Spinner<Integer> spinner = new Spinner<>(15, 480, 60, 15);
        spinner.setEditable(true);
        spinner.getStyleClass().add("modern-spinner");
        spinner.setPrefWidth(100);
        return spinner;
    }

    private ComboBox<Reunion.Type> createTypeComboBox() {
        ComboBox<Reunion.Type> combo = new ComboBox<>();
        combo.getStyleClass().add("modern-combo-box");
        combo.getItems().setAll(Reunion.Type.values());
        combo.setValue(Reunion.Type.STANDARD);
        
        // Personnaliser l'affichage
        combo.setCellFactory(listView -> new ListCell<Reunion.Type>() {
            @Override
            protected void updateItem(Reunion.Type item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    switch (item) {
                        case STANDARD -> setText("🌐 Standard - Ouverte à tous");
                        case PRIVEE -> setText("🔒 Privée - Sur invitation");
                        case DEMOCRATIQUE -> setText("🗳️ Démocratique - Vote participatif");
                    }
                }
            }
        });
        
        combo.setButtonCell(combo.getCellFactory().call(null));
        return combo;
    }

    private VBox createFormSection() {
        VBox section = new VBox(20);
        section.getStyleClass().add("form-section");
        return section;
    }

    private VBox createFieldGroup(String labelText, Node field) {
        VBox group = new VBox(8);
        group.getStyleClass().add("field-group");
        
        Label label = new Label(labelText);
        label.getStyleClass().add("field-label");
        
        group.getChildren().addAll(label, field);
        return group;
    }

    /**
     * ✅ Validation moderne avec feedback visuel
     */
    private Reunion validateAndCreateReunion(TextField nomField, TextField sujetField, 
            TextArea agendaField, DatePicker datePicker, ComboBox<String> heureCombo,
            Spinner<Integer> dureeSpinner, ComboBox<Reunion.Type> typeComboBox) {
        
        try {
            // Validation nom
            String nom = nomField.getText();
            if (nom == null || nom.trim().isEmpty()) {
                highlightErrorField(nomField, "Le nom de la réunion est obligatoire");
                return null;
            }

            // Validation date
            LocalDate date = datePicker.getValue();
            if (date == null) {
                highlightErrorField(datePicker, "La date est obligatoire");
                return null;
            }

            // Validation heure
            String heureText = heureCombo.getValue();
            if (heureText == null || heureText.trim().isEmpty()) {
                highlightErrorField(heureCombo, "L'heure est obligatoire");
                return null;
            }

            LocalTime time;
            try {
                time = LocalTime.parse(heureText.trim(), DateTimeFormatter.ofPattern("HH:mm"));
            } catch (DateTimeParseException e) {
                highlightErrorField(heureCombo, "Format d'heure invalide");
                return null;
            }

            LocalDateTime debut = LocalDateTime.of(date, time);

            // Vérification futur
            if (debut.isBefore(LocalDateTime.now())) {
                highlightErrorField(datePicker, "La réunion doit être programmée dans le futur");
                return null;
            }

            // Validation durée
            Integer duree = dureeSpinner.getValue();
            if (duree == null || duree <= 0) {
                highlightErrorField(dureeSpinner, "La durée doit être positive");
                return null;
            }

            // Validation type
            Reunion.Type type = typeComboBox.getValue();
            if (type == null) {
                highlightErrorField(typeComboBox, "Le type de réunion est obligatoire");
                return null;
            }

            String sujet = sujetField.getText();
            String agenda = agendaField.getText();

            return new Reunion(
                nom.trim(),
                sujet != null ? sujet.trim() : "",
                agenda != null ? agenda.trim() : "",
                debut,
                duree,
                type,
                userId > 0 ? userId : 1,
                null
            );

        } catch (Exception e) {
            Platform.runLater(() -> showModernAlert("Erreur", "Erreur lors de la création: " + e.getMessage(), Alert.AlertType.ERROR));
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 🔴 Highlighting d'erreur moderne
     */
    private void highlightErrorField(Node field, String message) {
        field.getStyleClass().add("error-field");
        
        // Animation shake
        FadeTransition shake = new FadeTransition(Duration.millis(100), field);
        shake.setFromValue(1.0);
        shake.setToValue(0.8);
        shake.setCycleCount(4);
        shake.setAutoReverse(true);
        shake.play();
        
        // Tooltip d'erreur
        Tooltip tooltip = new Tooltip(message);
        tooltip.getStyleClass().add("error-tooltip");
        Tooltip.install(field, tooltip);
        
        Platform.runLater(() -> showModernAlert("Validation", message, Alert.AlertType.WARNING));
        
        // Retirer l'erreur après 3 secondes
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
            field.getStyleClass().remove("error-field");
            Tooltip.uninstall(field, tooltip);
        }));
        timeline.play();
    }

    /**
     * 💬 Alertes modernes style Teams
     */
    private void showModernAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        
        // Style moderne
        alert.getDialogPane().getStylesheets().add(getClass().getResource("/styles/main.css").toExternalForm());
        alert.getDialogPane().getStyleClass().add("modern-alert");
        
        // Icônes personnalisées
        String emoji = switch (type) {
            case INFORMATION -> "ℹ️";
            case WARNING -> "⚠️";
            case ERROR -> "❌";
            case CONFIRMATION -> "✅";
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
        
        alert.setTitle(emoji + " " + title);
        alert.showAndWait();
    }

    /**
     * ⏳ Dialogue de chargement moderne
     */
    private void showLoadingDialog(String message) {
        if (loadingDialog != null) {
            loadingDialog.close();
        }
        
        loadingDialog = new Dialog<>();
        loadingDialog.setTitle("En cours...");
        loadingDialog.setHeaderText(null);
        
        // Contenu du loading
        VBox content = new VBox(20);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(30));
        content.getStyleClass().add("loading-dialog");
        
        ProgressIndicator progress = new ProgressIndicator();
        progress.getStyleClass().add("modern-progress");
        
        Label messageLabel = new Label(message);
        messageLabel.getStyleClass().add("loading-message");
        
        content.getChildren().addAll(progress, messageLabel);
        
        loadingDialog.getDialogPane().setContent(content);
        loadingDialog.getDialogPane().getStylesheets().add(getClass().getResource("/styles/main.css").toExternalForm());
        loadingDialog.getDialogPane().getStyleClass().add("modern-dialog");
        
        // Pas de boutons
        loadingDialog.getDialogPane().getButtonTypes().clear();
        
        loadingDialog.show();
    }
    
    private void hideLoadingDialog() {
        if (loadingDialog != null) {
            loadingDialog.close();
            loadingDialog = null;
        }
    }

    // [Le reste des méthodes reste identique...]
    private void creerReunion(Reunion reunion) {
        try {
            String jsonRequete = creerJsonCreationReunion(reunion);
            System.out.println("JSON envoyé: " + jsonRequete);
            clientWebSocket.envoyerRequete(jsonRequete);
        } catch (Exception e) {
            hideLoadingDialog();
            System.err.println("Erreur lors de l'envoi de la création de réunion: " + e.getMessage());
            e.printStackTrace();
            showModernAlert("Erreur réseau", "Impossible de créer la réunion: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

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
            hideLoadingDialog();
            System.err.println("Erreur lors de l'envoi de la demande de participation: " + e.getMessage());
            showModernAlert("Erreur réseau", "Impossible de rejoindre la réunion: " + e.getMessage(), Alert.AlertType.ERROR);
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

    public void traiterReponseConnexion(String message) {
        if (message == null || message.trim().isEmpty()) {
            System.err.println("Erreur: message WebSocket vide reçu.");
            return;
        }

        try {
            JSONObject jsonResponse = new JSONObject(message);
            String type = jsonResponse.optString("type");

            if ("welcome".equals(type)) {
                System.out.println("Message de bienvenue reçu: " + jsonResponse.optString("message"));
                return;
            }

            String modele = jsonResponse.optString("modele");
            String action = jsonResponse.optString("action");
            String statut = jsonResponse.optString("statut");
            String msg = jsonResponse.optString("message");

            Platform.runLater(() -> {
                hideLoadingDialog(); // Cacher le loading dans tous les cas
                
                switch (modele) {
                    case "reunion":
                        handleReunionResponse(action, statut, msg, jsonResponse);
                        break;
                    case "authentification":
                        break;
                    default:
                        if ("succes".equals(statut)) {
                            showModernAlert("Succès", msg, Alert.AlertType.INFORMATION);
                        } else if ("echec".equals(statut)) {
                            showModernAlert("Échec", msg, Alert.AlertType.ERROR);
                        }
                        break;
                }
            });
        } catch (Exception e) {
            hideLoadingDialog();
            System.err.println("Erreur lors du traitement de la réponse: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleReunionResponse(String action, String statut, String message, JSONObject jsonResponse) {
        System.out.println("Réponse réunion reçue - Action: " + action + ", Statut: " + statut);

        switch (action) {
            case "reponseCreation":
                if ("succes".equals(statut)) {
                    JSONObject reunionData = jsonResponse.optJSONObject("reunion");
                    if (reunionData != null) {
                        int reunionId = reunionData.optInt("id");
                        boolean autoJoin = jsonResponse.optBoolean("autoJoin", false);

                        System.out.println("Réunion créée avec ID: " + reunionId);

                        if (autoJoin && reunionId > 0) {
                            showModernAlert("Réunion créée", "Votre réunion a été créée avec succès !\nRedirection en cours...", Alert.AlertType.INFORMATION);
                            Platform.runLater(() -> {
                                ouvrirInterfaceReunion(String.valueOf(reunionId), true);
                            });
                        } else {
                            showModernAlert("Réunion créée", message + " (ID: " + reunionId + ")", Alert.AlertType.INFORMATION);
                        }
                    } else {
                        showModernAlert("Réunion créée", message, Alert.AlertType.INFORMATION);
                    }
                } else {
                    showModernAlert("Erreur de création", message, Alert.AlertType.ERROR);
                }
                break;

            case "reponseRejoindre":
                if ("succes".equals(statut)) {
                    showModernAlert("Succès", "Vous avez rejoint la réunion !", Alert.AlertType.INFORMATION);
                    txtTitreReunion.clear();

                    int reunionId = jsonResponse.optInt("reunionId", -1);
                    if (reunionId != -1) {
                        ouvrirInterfaceReunion(String.valueOf(reunionId));
                    }
                } else {
                    showModernAlert("Impossible de rejoindre", message, Alert.AlertType.ERROR);
                }
                break;

            default:
                if ("succes".equals(statut)) {
                    showModernAlert("Information", message, Alert.AlertType.INFORMATION);
                } else {
                    showModernAlert("Erreur", message, Alert.AlertType.ERROR);
                }
                break;
        }
    }

    private void ouvrirInterfaceReunion(String reunionId, boolean isCreator) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/reunion.fxml"));
            Parent root = loader.load();

            ReunionController reunionController = loader.getController();
            int organizateurId = isCreator ? userId : -1;
            String fullName = (nom != null && prenom != null) ? nom + " " + prenom : "Utilisateur";

            reunionController.initData(reunionId, userId, organizateurId, clientWebSocket, fullName);

            Stage stage = new Stage();
            stage.setTitle("📹 Réunion - " + reunionId + (isCreator ? " (Organisateur)" : ""));
            stage.setScene(new Scene(root));

            stage.setOnCloseRequest(event -> {
                reunionController.cleanup();
            });

            stage.show();

        } catch (IOException e) {
            System.err.println("Erreur lors de l'ouverture de l'interface de réunion: " + e.getMessage());
            e.printStackTrace();
            showModernAlert("Erreur", "Impossible d'ouvrir l'interface de réunion.", Alert.AlertType.ERROR);
        }
    }

    private void ouvrirInterfaceReunion(String reunionId) {
        ouvrirInterfaceReunion(reunionId, false);
    }

    // Getters et Setters
    public Reunion getReunion() { return reunion; }
    public void setReunion(Reunion reunion) { this.reunion = reunion; }
    public String getNom() { return nom; }
    public String getPrenom() { return prenom; }
    public int getUserId() { return userId; }
}