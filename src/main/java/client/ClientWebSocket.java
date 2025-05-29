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
// Retrait de java.net.NetworkInterface et java.net.InetAddress car getAdresseIP() n'est plus utilisé ici
// Il est préférable de gérer l'IP client côté serveur ou de la passer explicitement si nécessaire.
import java.util.Timer;
import java.util.TimerTask;

@ClientEndpoint
public class ClientWebSocket {

    private Session session;
    private boolean reconnexionEnCours = false;
    private String ipServeurStockee; // Pour les tentatives de reconnexion
    private boolean estConnecteAuServeur = false;

    // Références aux contrôleurs actifs
    private AuthentificationController controllerAuth;
    private EspaceUtilisateurController controllerEspc;
    private ReunionController controllerReunion;
    private EspaceAdministrateurController controllerAdmin; // Ajout du contrôleur admin

    // Setters pour les contrôleurs
    public synchronized void setControllerAuth(AuthentificationController controller) {
        this.controllerAuth = controller;
    }
    public synchronized void setControllerEspc(EspaceUtilisateurController controller) {
        this.controllerEspc = controller;
    }
    public synchronized void setControllerReunion(ReunionController controller) {
        this.controllerReunion = controller;
    }
    public synchronized void setControllerAdmin(EspaceAdministrateurController controller) { // Setter pour admin
        this.controllerAdmin = controller;
    }


    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        this.reconnexionEnCours = false;
        this.estConnecteAuServeur = true;
        System.out.println("Connecté au serveur WebSocket - Session ID: " + session.getId());

        // Notifier le contrôleur d'authentification (s'il est actif et a initié la connexion)
        // pour qu'il puisse changer de vue vers authentification.fxml
        if (this.controllerAuth != null) {
            // Cette méthode doit exister dans AuthentificationController
            // pour gérer la transition vers l'écran de login après connexion au serveur.
            this.controllerAuth.onWebSocketConnectionSuccess();
        }
    }

    @OnMessage
    public void onMessage(String message) {
        System.out.println("Message reçu du serveur: " + message);
        if (message == null || message.trim().isEmpty()) return;

        try {
            JSONObject jsonMessage = new JSONObject(message);
            String modele = jsonMessage.optString("modele");
            String typeMessage = jsonMessage.optString("type"); // Pour les messages de bienvenue ou d'erreur génériques

            // Routeur de messages basé sur le modèle ou le type
            if ("authentification".equals(modele) && controllerAuth != null) {
                controllerAuth.traiterReponseConnexion(message);
            } else if ("reunion".equals(modele)) { // Peut être pour EspaceUtilisateur ou ReunionController
                if (controllerReunion != null && controllerReunion.isInitialized()) { // isInitialized est une méthode à ajouter
                    // Vérifier si le message est pour la réunion actuelle du contrôleur
                    String msgReunionId = jsonMessage.optString("reunionId", "");
                    if (msgReunionId.equals(controllerReunion.getCurrentReunionId()) || msgReunionId.isEmpty()) {
                         // Les messages sans reunionId (ex: invitationResult) sont aussi pour le contrôleur de réunion actif
                        controllerReunion.traiterMessageRecu(message);
                    } else {
                        System.out.println("Message de réunion pour " + msgReunionId + " ignoré, réunion active: " + controllerReunion.getCurrentReunionId());
                    }
                } else if (controllerEspc != null) {
                    // Réponses à la création/rejoindre une réunion depuis l'espace utilisateur
                    controllerEspc.traiterReponseServeur(message);
                } else {
                    System.err.println("Aucun contrôleur de réunion ou d'espace utilisateur actif pour le message modèle 'reunion'.");
                }
            } else if ("admin".equals(modele) && controllerAdmin != null) { // Routage pour admin
                controllerAdmin.traiterReponseAdmin(message);
            } else if ("welcome".equals(typeMessage) || "error".equals(typeMessage)) {
                // Messages génériques de bienvenue ou d'erreur du serveur
                // Peuvent être affichés globalement ou gérés par le contrôleur actuellement actif s'il a une méthode pour cela
                System.out.println("Message serveur (type: " + typeMessage + "): " + jsonMessage.optString("message"));
                // Exemple: if (controllerAuth != null) controllerAuth.afficherMessageServeur(jsonMessage.optString("message"));
            }
            else {
                System.err.println("Message non routé (modèle/type inconnu ou contrôleur inactif): " + message);
            }

        } catch (Exception e) {
            System.err.println("Erreur lors du parsing ou du routage du message JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        System.out.println("Déconnecté du serveur WebSocket - Session: " + session.getId() + ", Raison: " + reason.getReasonPhrase() + " (Code: " + reason.getCloseCode().getCode() + ")");
        this.session = null;
        this.estConnecteAuServeur = false;
        // Gérer la notification de déconnexion à l'UI si nécessaire
        // Par exemple, afficher un message ou changer d'écran.
        // Tenter de se reconnecter uniquement si ce n'est pas une fermeture initiée par le client (ex: déconnexion manuelle)
        if (reason.getCloseCode().getCode() != CloseReason.CloseCodes.NORMAL_CLOSURE.getCode() &&
            reason.getCloseCode().getCode() != CloseReason.CloseCodes.GOING_AWAY.getCode()) { // GOING_AWAY peut être une fermeture de l'onglet/app
            tenterReconnexion();
        } else {
            System.out.println("Fermeture normale, pas de tentative de reconnexion automatique.");
            // Potentiellement rediriger vers l'écran de connexion serveur si l'utilisateur n'est plus authentifié
            Platform.runLater(() -> {
                if (controllerAuth == null && controllerEspc == null && controllerReunion == null && controllerAdmin == null) {
                    // Si aucun contrôleur n'est actif, on est probablement déjà sur l'écran de connexion serveur
                } else {
                    // Si un contrôleur est actif, la déconnexion est anormale ou inattendue
                    // Afficher une alerte et/ou rediriger
                    String currentView = "";
                    if (controllerReunion != null) currentView = "Réunion";
                    else if (controllerEspc != null) currentView = "Espace Utilisateur";
                    else if (controllerAdmin != null) currentView = "Admin";

                    final String view = currentView; // Pour le lambda
                    if (!view.isEmpty()) {
                         showAlertAndReturnToLogin("Déconnecté", "La connexion au serveur a été interrompue. (" + reason.getReasonPhrase() + ")", view);
                    }
                }
            });
        }
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        System.err.println("Erreur WebSocket - Session: " + (session != null ? session.getId() : "N/A") + ", Erreur: " + throwable.getMessage());
        // throwable.printStackTrace(); // Peut être verbeux
        this.estConnecteAuServeur = false; // Marquer comme déconnecté en cas d'erreur
        // Ne pas tenter de se reconnecter indéfiniment sur certaines erreurs (ex: serveur introuvable)
        // La logique de reconnexion dans onClose est généralement suffisante.
        // Si l'erreur se produit avant onOpen, onClose pourrait ne pas être appelé.
        if (session == null || !session.isOpen()) { // Si la session n'a jamais été ouverte ou est déjà fermée
            tenterReconnexion();
        }
    }

    private synchronized void tenterReconnexion() {
        if (!reconnexionEnCours && ipServeurStockee != null && !ipServeurStockee.trim().isEmpty()) {
            reconnexionEnCours = true;
            System.out.println("Tentative de reconnexion au serveur " + ipServeurStockee + " dans 5 secondes...");
            // Notifier l'UI de la tentative de reconnexion
            if (controllerAuth != null) controllerAuth.showAlert(false, "Reconnexion", "Tentative de reconnexion au serveur...");
            else if (controllerEspc != null) controllerEspc.showAlert(Alert.AlertType.WARNING,"Reconnexion", "Tentative de reconnexion au serveur...");
            else if (controllerReunion != null) controllerReunion.showAlert(Alert.AlertType.WARNING,"Reconnexion", "Tentative de reconnexion au serveur...");
            // etc. pour les autres contrôleurs

            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    System.out.println("Reconnexion...");
                    connectToWebSocket(ipServeurStockee); // Utilise l'IP stockée
                    // reconnexionEnCours sera remis à false dans onOpen ou si la connexion échoue à nouveau
                }
            }, 5000); // Délai avant reconnexion
        } else if (reconnexionEnCours) {
            System.out.println("Tentative de reconnexion déjà en cours.");
        } else {
            System.err.println("Impossible de tenter la reconnexion : IP du serveur non disponible.");
             Platform.runLater(() -> {
                // Si aucune IP n'est stockée, on ne peut pas se reconnecter.
                // Il faut probablement rediriger vers l'écran de saisie de l'IP.
                showAlertAndReturnToLogin("Erreur Critique", "Impossible de se reconnecter, IP du serveur inconnue.", "N/A");
            });
        }
    }

    private void showAlertAndReturnToLogin(String title, String message, String currentViewName) {
        System.out.println("Alerte: " + title + " - " + message + " (Vue actuelle: " + currentViewName + ")");
        // Fermer toutes les fenêtres sauf la principale (si possible) ou simplement la fenêtre actuelle
        // et afficher l'écran de connexion au serveur.

        // D'abord, afficher l'alerte
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message + "\nVous allez être redirigé vers l'écran de connexion au serveur.");
         try {
            String cssPath = getClass().getResource("/styles/main.css").toExternalForm();
            if (cssPath != null) {
                alert.getDialogPane().getStylesheets().add(cssPath);
                alert.getDialogPane().getStyleClass().add("dialog-pane");
            }
        } catch (Exception e) { /* ignorer si CSS non trouvé */ }
        alert.showAndWait();

        // Ensuite, tenter de naviguer vers l'écran de connexion serveur
        Stage primaryStage = ClientApplication.getPrimaryStage(); // Nécessite une méthode statique dans ClientApplication
        if (primaryStage != null) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/connexionServeur.fxml"));
                Parent root = loader.load();
                AuthentificationController authController = loader.getController();
                authController.setClientWebSocket(this); // Important de passer l'instance actuelle
                this.setControllerAuth(authController); // Mettre à jour le contrôleur actif
                this.setControllerEspc(null);
                this.setControllerReunion(null);
                this.setControllerAdmin(null);

                Scene scene = new Scene(root);
                primaryStage.setScene(scene);
                primaryStage.setTitle("Connexion au Serveur");
                primaryStage.centerOnScreen();
                if (!primaryStage.isShowing()) {
                    primaryStage.show();
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Erreur critique : Impossible de charger l'écran de connexion au serveur.");
                // En dernier recours, fermer l'application
                // Platform.exit();
            }
        } else {
            System.err.println("Impossible de récupérer la Stage principale pour la redirection.");
        }
    }


    public void envoyerRequete(String jsonRequete) {
        if (session != null && session.isOpen() && estConnecteAuServeur) {
            try {
                session.getBasicRemote().sendText(jsonRequete);
                System.out.println("Requête envoyée au serveur : " + jsonRequete);
            } catch (IOException e) {
                System.err.println("Erreur lors de l'envoi de la requête : " + e.getMessage());
                // Marquer comme déconnecté et tenter une reconnexion pourrait être une option ici
                this.estConnecteAuServeur = false;
                tenterReconnexion();
            }
        } else {
            System.err.println("Impossible d'envoyer la requête : session WebSocket non ouverte ou non connectée.");
            // Notifier l'UI que la connexion est perdue
             Platform.runLater(() -> {
                if (controllerAuth != null) controllerAuth.showAlert(false, "Erreur de Connexion", "La connexion au serveur a été perdue. Tentative de reconnexion...");
                else if (controllerEspc != null) controllerEspc.showAlert(Alert.AlertType.ERROR,"Erreur de Connexion", "La connexion au serveur a été perdue. Tentative de reconnexion...");
                else if (controllerReunion != null) controllerReunion.showAlert(Alert.AlertType.ERROR,"Erreur de Connexion", "La connexion au serveur a été perdue. Tentative de reconnexion...");
                // etc.
            });
            if (!reconnexionEnCours) { // Éviter les tentatives multiples si déjà en cours
                 tenterReconnexion();
            }
        }
    }

    public void connectToWebSocket(String ipEntree) {
        // Vérifier si une connexion est déjà en cours ou établie pour éviter les duplications
        if (estConnecteAuServeur && session != null && session.isOpen()) {
            System.out.println("Connexion WebSocket déjà active avec " + ipServeurStockee);
            // Si l'IP demandée est différente, il faut d'abord fermer l'ancienne connexion
            if (!ipEntree.equals(ipServeurStockee)) {
                System.out.println("Changement d'IP serveur demandée. Fermeture de la connexion actuelle.");
                deconnecter(); // Fermer la session existante proprement
            } else {
                 // Si c'est la même IP et qu'on est connecté, notifier le contrôleur Auth que la connexion est OK
                 // pour qu'il puisse passer à l'écran d'authentification si ce n'est pas déjà fait.
                if (controllerAuth != null) {
                    controllerAuth.onWebSocketConnectionSuccess();
                }
                return;
            }
        }

        if (reconnexionEnCours && ipEntree.equals(ipServeurStockee)) {
            System.out.println("Tentative de reconnexion à " + ipEntree + " déjà en cours.");
            // Ne rien faire de plus, laisser la tentative en cours se terminer.
            // reconnexionEnCours sera remis à false par onOpen ou si la connexion échoue.
        } else {
            // Si ce n'est pas une reconnexion à la même IP, ou si c'est une nouvelle connexion.
            reconnexionEnCours = false; // Réinitialiser pour une nouvelle tentative de connexion explicite
        }


        this.ipServeurStockee = ipEntree.trim(); // Stocker pour les reconnexions
        String ipClient = "N/A"; // L'IP client est souvent difficile à obtenir de manière fiable côté client et est généralement gérée par le serveur.
                                // Si vous avez une méthode fiable, utilisez-la. Sinon, le serveur peut la déduire.

        // Exécuter la connexion sur un nouveau thread pour ne pas bloquer l'UI JavaFX
        new Thread(() -> {
            try {
                WebSocketContainer container = ContainerProvider.getWebSocketContainer();
                // L'URL doit être valide, ex: ws://localhost:8080/monendpoint
                // Le chemin après le port (ex: /monendpoint) doit correspondre à ce qui est configuré sur le serveur Tyrus.
                // Si le serveur Tyrus est configuré avec @ServerEndpoint("/"), alors l'URL se termine après le port.
                String webSocketUrl = "ws://" + ipServeurStockee + ":8080/"; // Ajustez le chemin si nécessaire
                System.out.println("Tentative de connexion à: " + webSocketUrl);

                // La connexion elle-même. Si elle réussit, @OnOpen sera appelé.
                // Si elle échoue, une exception sera levée (souvent DeploymentException).
                container.connectToServer(this, new URI(webSocketUrl));
                // Si on arrive ici sans exception, connectToServer a initié la connexion.
                // L'état estConnecteAuServeur sera mis à true dans @OnOpen.
                // reconnexionEnCours sera mis à false dans @OnOpen.

            } catch (DeploymentException e) {
                System.err.println("Échec du déploiement/connexion au serveur WebSocket (" + ipServeurStockee + "): " + e.getMessage());
                // e.printStackTrace(); // Pour plus de détails sur l'erreur de déploiement
                final String errorMessage = "Impossible de se connecter au serveur : " + e.getLocalizedMessage() +
                                           ".\nVérifiez l'adresse IP et que le serveur est démarré.";
                Platform.runLater(() -> {
                    if (controllerAuth != null) {
                        controllerAuth.showAlert(false, "Échec de Connexion", errorMessage);
                    }
                });
                reconnexionEnCours = false; // Permettre une nouvelle tentative manuelle ou automatique
                estConnecteAuServeur = false;
            } catch (URISyntaxException e) {
                System.err.println("Erreur de syntaxe de l'URI WebSocket: " + e.getMessage());
                Platform.runLater(() -> {
                     if (controllerAuth != null) {
                        controllerAuth.showAlert(false, "Erreur d'URL", "L'adresse du serveur WebSocket est malformée.");
                    }
                });
                reconnexionEnCours = false;
                estConnecteAuServeur = false;
            } catch (IOException e) {
                System.err.println("Erreur d'entrée/sortie lors de la connexion WebSocket: " + e.getMessage());
                 Platform.runLater(() -> {
                     if (controllerAuth != null) {
                        controllerAuth.showAlert(false, "Erreur Réseau", "Une erreur réseau est survenue lors de la connexion.");
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

    // Méthode pour fermer la connexion proprement
    public void deconnecter() {
        System.out.println("Demande de déconnexion manuelle du client WebSocket.");
        if (session != null && session.isOpen()) {
            try {
                // Envoyer une raison de fermeture personnalisée si nécessaire
                // CloseReason closeReason = new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "Déconnexion manuelle du client");
                session.close(); // Cela devrait déclencher @OnClose
            } catch (IOException e) {
                System.err.println("Erreur lors de la fermeture de la session WebSocket: " + e.getMessage());
            }
        }
        // Réinitialiser les états
        session = null;
        estConnecteAuServeur = false;
        reconnexionEnCours = false; // Arrêter les tentatives de reconnexion si c'est une déconnexion manuelle
        // Ne pas effacer ipServeurStockee ici, au cas où l'utilisateur voudrait se reconnecter au même serveur plus tard.
    }
}
