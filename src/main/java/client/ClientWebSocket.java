package client;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import org.json.JSONObject;

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Timer;
import java.util.TimerTask;

@ClientEndpoint
public class ClientWebSocket {

    private Session session;
    private boolean reconnexionEnCours = false;
    private String ipServeurStockee;
    private boolean estConnecteAuServeur = false;

    private AuthentificationController controllerAuth;
    private EspaceUtilisateurController controllerEspc;
    private ReunionController controllerReunion;
    private EspaceAdministrateurController controllerAdmin;

    public synchronized void setControllerAuth(AuthentificationController controller) {
        this.controllerAuth = controller;
    }

    public synchronized void setControllerEspc(EspaceUtilisateurController controller) {
        this.controllerEspc = controller;
    }

    public synchronized void setControllerReunion(ReunionController controller) {
        this.controllerReunion = controller;
    }

    public synchronized void setControllerAdmin(EspaceAdministrateurController controller) {
        this.controllerAdmin = controller;
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        this.reconnexionEnCours = false;
        this.estConnecteAuServeur = true;
        System.out.println("Connecté au serveur WebSocket - Session ID: " + session.getId());

        if (this.controllerAuth != null) {
            Platform.runLater(controllerAuth::onWebSocketConnectionSuccess); //
        }
    }

    private boolean isSpecificReunionMessageType(String type) {
        if (type == null || type.isEmpty()) return false;
        switch (type) {
            case "newMessage":
            case "userJoined":
            case "userLeft":
            case "listeParticipants":
            case "historiqueMessages":
            case "invitationResult": // Réponse à celui qui invite
                return true;
            default:
                return false;
        }
    }

    // Nouveaux types de messages pour les invitations (reçus par EspaceUtilisateurController)
    private boolean isInvitationRelatedMessageType(String type) {
        if (type == null || type.isEmpty()) return false;
        switch (type) {
            case "nouvelleInvitation": // Notification à l'invité
            case "listeInvitationsEnAttente": // Réponse à getPendingInvitations
            case "updateInvitationStatusResponse": // Réponse à l'acceptation/refus
                return true;
            default:
                return false;
        }
    }


    @OnMessage
    public void onMessage(String message) {
        System.out.println("Message reçu du serveur: " + message);
        if (message == null || message.trim().isEmpty()) return;

        Platform.runLater(() -> {
            try {
                JSONObject jsonMessage = new JSONObject(message);
                String modele = jsonMessage.optString("modele");
                String typeMessage = jsonMessage.optString("type");
                String actionReponse = jsonMessage.optString("action", jsonMessage.optString("actionReponse", typeMessage));


                // --- Route principale : Contrôleur de Réunion Actif ---
                if (controllerReunion != null && controllerReunion.isInitialized()) { //
                    boolean messagePourReunionController = false;
                    String msgReunionId = jsonMessage.optString("reunionId", ""); //
                    String currentCtrlReunionId = controllerReunion.getCurrentReunionId(); //

                    if (isSpecificReunionMessageType(typeMessage)) {
                        if (currentCtrlReunionId != null && msgReunionId.equals(currentCtrlReunionId)) {
                            messagePourReunionController = true;
                        } else if (msgReunionId.isEmpty() && "invitationResult".equals(typeMessage)) {
                            // invitationResult (à l'inviteur) n'a pas forcément de reunionId dans le message direct si c'est un type global
                            messagePourReunionController = true;
                        }
                    } else if ("reunion".equals(modele) || "error".equals(typeMessage)) {
                        if (msgReunionId.isEmpty() || (currentCtrlReunionId != null && msgReunionId.equals(currentCtrlReunionId))) {
                            messagePourReunionController = true;
                        }
                    }

                    if (messagePourReunionController) {
                        controllerReunion.traiterMessageRecu(message); //
                        return;
                    }
                }

                // --- Routes secondaires : Autres contrôleurs ou messages généraux ---
                if ("authentification".equals(modele) && "reponseConnexion".equals(actionReponse) && controllerAuth != null) {
                    controllerAuth.traiterReponseConnexion(message); //
                }
                // ROUTAGE POUR ESPACE UTILISATEUR (y compris les nouvelles actions/types d'invitation)
                else if (controllerEspc != null && (
                        ("reunion".equals(modele) &&
                                ("reponseGetReunionsUtilisateur".equals(actionReponse) ||
                                        "reponseRejoindre".equals(actionReponse) ||
                                        "reponseCreation".equals(actionReponse))) ||
                                isInvitationRelatedMessageType(typeMessage) || // Vérifier les types d'invitation
                                ("error".equals(typeMessage) && jsonMessage.optString("statut", "").equals("echec") && ("reunion".equals(modele) || isInvitationRelatedMessageType(actionReponse)))
                )) {
                    controllerEspc.traiterReponseServeur(message); //
                } else if ("admin".equals(modele) && controllerAdmin != null) {
                    controllerAdmin.traiterReponseAdmin(message); //
                } else if ("welcome".equals(typeMessage)) {
                    System.out.println("Message serveur (type: " + typeMessage + "): " + jsonMessage.optString("message"));
                } else if ("error".equals(typeMessage)) {
                    String errorMsgContent = jsonMessage.optString("message", "Erreur inconnue du serveur.");
                    System.err.println("Erreur générique du serveur (non interceptée spécifiquement): " + errorMsgContent);
                    if (controllerAuth != null) controllerAuth.showAlert(false, "Erreur Serveur", errorMsgContent);
                    else if (controllerEspc != null)
                        controllerEspc.showAlert(Alert.AlertType.ERROR, "Erreur Serveur", errorMsgContent);
                    else System.err.println("Aucun contrôleur actif pour afficher l'erreur générique: " + message);
                } else {
                    System.err.println("Message non routé (modèle: '" + modele + "', type: '" + typeMessage + "', action: '" + actionReponse + "' inconnu ou contrôleur inactif): " + message);
                }

            } catch (Exception e) {
                System.err.println("Exception dans ClientWebSocket.onMessage lors du traitement du message: " + message);
                e.printStackTrace();
            }
        });
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        // ... (code de la version précédente, Platform.runLater est déjà là)
        System.out.println("Déconnecté du serveur WebSocket - Session: " + session.getId() + ", Raison: " + reason.getReasonPhrase() + " (Code: " + reason.getCloseCode().getCode() + ")");
        this.session = null;
        this.estConnecteAuServeur = false;

        Platform.runLater(() -> {
            boolean shouldAttemptReconnect = true;
            if (reason.getCloseCode().getCode() == CloseReason.CloseCodes.NORMAL_CLOSURE.getCode() ||
                    reason.getCloseCode().getCode() == CloseReason.CloseCodes.GOING_AWAY.getCode()) {
                System.out.println("Fermeture normale ou initiée par le client, pas de tentative de reconnexion automatique.");
                shouldAttemptReconnect = false;
            }

            if (ClientApplication.getPrimaryStage() != null && ClientApplication.getPrimaryStage().isShowing()) {
                String currentView = "";
                if (controllerReunion != null && controllerReunion.isInitialized()) currentView = "Réunion";
                else if (controllerEspc != null) currentView = "Espace Utilisateur";
                else if (controllerAdmin != null) currentView = "Admin";

                if (shouldAttemptReconnect) {
                    tenterReconnexion();
                } else if (!currentView.isEmpty() && reason.getCloseCode().getCode() != CloseReason.CloseCodes.NORMAL_CLOSURE.getCode()) {
                    showAlertAndReturnToLogin("Déconnecté", "La connexion au serveur a été interrompue. (" + reason.getReasonPhrase() + ")", currentView);
                }
            }
        });
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        // ... (code de la version précédente, Platform.runLater est déjà là pour tenterReconnexion)
        System.err.println("Erreur WebSocket - Session: " + (session != null ? session.getId() : "N/A") + ", Erreur: " + throwable.getMessage());
        this.estConnecteAuServeur = false;
        if (session == null || !session.isOpen()) {
            Platform.runLater(this::tenterReconnexion);
        }
    }

    private synchronized void tenterReconnexion() {
        // ... (code de la version précédente, les showAlert sont déjà dans Platform.runLater)
        if (!reconnexionEnCours && ipServeurStockee != null && !ipServeurStockee.trim().isEmpty()) {
            reconnexionEnCours = true;
            System.out.println("Tentative de reconnexion au serveur " + ipServeurStockee + " dans 5 secondes...");

            Platform.runLater(() -> {
                String reconnexionMsg = "Tentative de reconnexion au serveur...";
                if (controllerAuth != null) controllerAuth.showAlert(false, "Reconnexion", reconnexionMsg);
                else if (controllerEspc != null)
                    controllerEspc.showAlert(Alert.AlertType.WARNING, "Reconnexion", reconnexionMsg);
                else if (controllerReunion != null)
                    controllerReunion.showAlert(Alert.AlertType.WARNING, "Reconnexion", reconnexionMsg);
            });

            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    System.out.println("Reconnexion...");
                    connectToWebSocket(ipServeurStockee);
                }
            }, 5000);
        } else if (reconnexionEnCours) {
            System.out.println("Tentative de reconnexion déjà en cours.");
        } else {
            System.err.println("Impossible de tenter la reconnexion : IP du serveur non disponible.");
            Platform.runLater(() -> showAlertAndReturnToLogin("Erreur Critique", "Impossible de se reconnecter, IP du serveur inconnue.", "N/A"));
        }
    }

    private void showAlertAndReturnToLogin(String title, String message, String currentViewName) {
        // ... (code de la version précédente, Platform.runLater est déjà autour de l'appel à cette méthode)
        System.out.println("Alerte et retour à la connexion: " + title + " - " + message + " (Vue actuelle: " + currentViewName + ")");

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message + "\nVous allez être redirigé vers l'écran de connexion au serveur.");
         try {
             String cssPath = getClass().getResource("/styles/main.css").toExternalForm(); //
            if (cssPath != null) {
                alert.getDialogPane().getStylesheets().add(cssPath);
                alert.getDialogPane().getStyleClass().add("dialog-pane"); //
            }
         } catch (Exception e) { /* ignorer */ }
        alert.showAndWait();

        Stage primaryStage = ClientApplication.getPrimaryStage(); //
        if (primaryStage != null && primaryStage.isShowing()) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/connexionServeur.fxml")); //
                Parent root = loader.load();
                AuthentificationController authController = loader.getController(); //
                authController.setClientWebSocket(this); //
                this.setControllerAuth(authController); //
                this.setControllerEspc(null); //
                this.setControllerReunion(null); //
                this.setControllerAdmin(null); //

                Scene scene = new Scene(root);
                primaryStage.setScene(scene);
                primaryStage.setTitle("Connexion au Serveur");
                primaryStage.centerOnScreen();
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Erreur critique : Impossible de charger l'écran de connexion au serveur.");
            }
        } else {
            System.err.println("Impossible de récupérer la Stage principale ou elle n'est plus affichée.");
        }
    }

    public void envoyerRequete(String jsonRequete) {
        // ... (code de la version précédente, les Platform.runLater sont déjà là pour les alertes)
        if (session != null && session.isOpen() && estConnecteAuServeur) {
            try {
                session.getBasicRemote().sendText(jsonRequete);
                System.out.println("Requête envoyée au serveur : " + jsonRequete);
            } catch (IOException e) {
                System.err.println("Erreur lors de l'envoi de la requête : " + e.getMessage());
                this.estConnecteAuServeur = false;
                Platform.runLater(this::tenterReconnexion);
            }
        } else {
            System.err.println("Impossible d'envoyer la requête : session WebSocket non ouverte ou non connectée.");
             Platform.runLater(() -> {
                 String errorMsg = "La connexion au serveur a été perdue. Tentative de reconnexion...";
                 if (controllerAuth != null) controllerAuth.showAlert(false, "Erreur de Connexion", errorMsg);
                 else if (controllerEspc != null)
                     controllerEspc.showAlert(Alert.AlertType.ERROR, "Erreur de Connexion", errorMsg);
                 else if (controllerReunion != null)
                     controllerReunion.showAlert(Alert.AlertType.ERROR, "Erreur de Connexion", errorMsg);
            });
            if (!reconnexionEnCours) {
                Platform.runLater(this::tenterReconnexion);
            }
        }
    }

    public void connectToWebSocket(String ipEntree) {
        // ... (code de la version précédente, les Platform.runLater sont déjà là pour les alertes)
        if (estConnecteAuServeur && session != null && session.isOpen()) {
            if (!ipEntree.equals(ipServeurStockee)) {
                System.out.println("Changement d'IP serveur demandée. Fermeture de la connexion actuelle.");
                deconnecter();
            } else {
                System.out.println("Connexion WebSocket déjà active avec " + ipServeurStockee);
                if (controllerAuth != null) {
                    Platform.runLater(controllerAuth::onWebSocketConnectionSuccess);
                }
                return;
            }
        }

        if (!(reconnexionEnCours && ipEntree.equals(ipServeurStockee))) {
            reconnexionEnCours = false;
        }

        this.ipServeurStockee = ipEntree.trim();

        new Thread(() -> {
            try {
                WebSocketContainer container = ContainerProvider.getWebSocketContainer();
                String webSocketUrl = "ws://" + ipServeurStockee + ":8080/";
                System.out.println("Tentative de connexion à: " + webSocketUrl);
                container.connectToServer(this, new URI(webSocketUrl));
            } catch (DeploymentException | IOException | URISyntaxException e) {
                System.err.println("Échec de la connexion WebSocket à " + ipServeurStockee + ": " + e.getMessage());
                final String errorTitle;
                final String errorMessage;
                if (e instanceof DeploymentException) {
                    errorTitle = "Échec de Connexion";
                    errorMessage = "Impossible de se connecter au serveur : " + e.getLocalizedMessage() +
                            ".\nVérifiez l'adresse IP et que le serveur est démarré.";
                } else if (e instanceof URISyntaxException) {
                    errorTitle = "Erreur d'URL";
                    errorMessage = "L'adresse du serveur WebSocket est malformée.";
                } else {
                    errorTitle = "Erreur Réseau";
                    errorMessage = "Une erreur réseau est survenue lors de la connexion.";
                }
                Platform.runLater(() -> {
                    if (controllerAuth != null) {
                        controllerAuth.showAlert(false, errorTitle, errorMessage);
                    }
                });
                reconnexionEnCours = false;
                estConnecteAuServeur = false;
            }
        }).start();
    }

    public boolean isConnected() {
        return estConnecteAuServeur && session != null && session.isOpen();
    }

    public void deconnecter() {
        // ... (code de la version précédente)
        System.out.println("Demande de déconnexion manuelle du client WebSocket.");
        reconnexionEnCours = false;
        if (session != null && session.isOpen()) {
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "Client déconnecté manuellement"));
            } catch (IOException e) {
                System.err.println("Erreur lors de la fermeture de la session WebSocket: " + e.getMessage());
            }
        }
        session = null;
        estConnecteAuServeur = false;
    }
}