package serveur;

import model.Message;
import model.MessageManager;
import model.Personne;
import model.PersonneManager;
import model.ParticipationManager;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.websocket.Session;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ChatService implements WebSocketAction {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    public void execute(JSONObject data, Session session) {
        String action = data.optString("action");
        final String actionOriginale = action;

        CompletableFuture<String> futureReponse = null;

        Integer senderId = (Integer) session.getUserProperties().get("userId");
        String senderName = (String) session.getUserProperties().get("userName");

        if (senderId == null || senderName == null) {
            futureReponse = CompletableFuture.completedFuture(
                genererReponseErreur("Utilisateur non authentifié pour l'action de chat.", actionOriginale)
            );
        } else {
            switch (action) {
                case "envoyerMessage":
                    futureReponse = envoyerMessageAsync(data, senderId, senderName, session, actionOriginale);
                    break;
                case "historiqueMessages":
                    futureReponse = historiqueMessagesAsync(data, session, actionOriginale);
                    break;
                default:
                    futureReponse = CompletableFuture.completedFuture(
                        genererReponseErreur("Action de chat inconnue: " + action, actionOriginale)
                    );
            }
        }

        if (futureReponse != null) {
            futureReponse.thenAcceptAsync(reponseString -> {
                // Only send response back to requester for history, not for new messages (they get broadcast)
                if ("historiqueMessages".equals(actionOriginale) || reponseString.contains("\"statut\":\"echec\"")) {
                    session.getAsyncRemote().sendText(reponseString, new SendHandler() {
                        @Override
                        public void onResult(SendResult result) {
                            if (!result.isOK()) {
                                System.err.println("Erreur envoi message async dans ChatService (" + actionOriginale + "): " + result.getException());
                                if (result.getException() != null) result.getException().printStackTrace();
                            }
                        }
                    });
                }
            }).exceptionally(ex -> {
                System.err.println("Exception dans CompletableFuture ChatService (" + actionOriginale + "): " + ex);
                ex.printStackTrace();
                String errorResponse = genererReponseErreur("Erreur interne du serveur: " + ex.getMessage(), actionOriginale);
                session.getAsyncRemote().sendText(errorResponse, new SendHandler() {
                    @Override
                    public void onResult(SendResult result) {
                        if (!result.isOK()) {
                            System.err.println("Erreur envoi message d'erreur (exceptionally) async dans ChatService: " + result.getException());
                            if(result.getException() != null) result.getException().printStackTrace();
                        }
                    }
                });
                return null;
            });
        }
    }

    private CompletableFuture<String> envoyerMessageAsync(JSONObject data, int senderId, String senderName, Session currentSession, String actionOriginale) {
        return CompletableFuture.supplyAsync(() -> {
            JSONObject responseJson = new JSONObject(); // This will be the broadcast message
            responseJson.put("modele", "chat");
            responseJson.put("actionOriginale", "nouveauMessage"); // Client expects "nouveauMessage" or "messageRecu"

            try {
                int reunionId = data.getInt("reunionId");
                String contenu = data.getString("contenu");

                if (contenu.trim().isEmpty()) {
                    return genererReponseErreur("Le contenu du message ne peut pas être vide.", actionOriginale);
                }

                MessageManager messageManager = new MessageManager();
                // Create a message object. Note: Message constructor sets heureEnvoi to now().
                // The enregistrerMessage method will get the DB timestamp.
                Message message = new Message(0, senderId, reunionId, contenu); 
                message.setHeureEnvoi(LocalDateTime.now()); // Set time before saving, manager might override with DB time if needed

                Message messageEnregistre = messageManager.envoyerMessage(senderId, reunionId, contenu);
                
                responseJson.put("statut", "succes"); // For broadcast, not strictly needed unless for sender's confirmation
                responseJson.put("reunionId", reunionId);
                responseJson.put("auteurId", senderId);
                responseJson.put("auteurNom", senderName); // From session
                responseJson.put("contenu", messageEnregistre.getContenu());
                responseJson.put("timestamp", messageEnregistre.getHeureEnvoi().format(TIMESTAMP_FORMATTER));
                
                // Broadcast logic
                broadcastMessageToReunion(reunionId, responseJson.toString());
                
                // For the sender, we don't send a separate response via this CompletableFuture chain,
                // as they will receive the broadcast. If a direct confirmation is needed, it could be sent here.
                return ""; // Or a specific confirmation for the sender if needed, but broadcast is typical

            } catch (SQLException e) {
                System.err.println("Erreur SQL (envoyerMessage): " + e.getMessage());
                return genererReponseErreur("Erreur base de données: " + e.getMessage(), actionOriginale);
            } catch (org.json.JSONException e) {
                System.err.println("Erreur JSON (envoyerMessage): " + e.getMessage());
                return genererReponseErreur("Données de message mal formatées: " + e.getMessage(), actionOriginale);
            } catch (Exception e) {
                System.err.println("Erreur inattendue (envoyerMessage): " + e.getMessage());
                e.printStackTrace();
                return genererReponseErreur("Erreur serveur: " + e.getMessage(), actionOriginale);
            }
        }, Database.getDbExecutor());
    }

    private void broadcastMessageToReunion(int reunionId, String messagePayload) {
        try {
            ParticipationManager participationManager = new ParticipationManager();
            List<Personne> participants = participationManager.obtenirParticipants(reunionId);
            Set<Integer> participantIds = participants.stream().map(Personne::getId).collect(Collectors.toSet());

            for (Session participantSession : ServeurWebSocket.getSessions()) { // Assumes ServeurWebSocket.getSessions() is static and accessible
                Integer userIdInSession = (Integer) participantSession.getUserProperties().get("userId");
                if (userIdInSession != null && participantIds.contains(userIdInSession) && participantSession.isOpen()) {
                    participantSession.getAsyncRemote().sendText(messagePayload);
                }
            }
        } catch (SQLException e) {
            System.err.println("Erreur SQL lors de la récupération des participants pour broadcast: " + e.getMessage());
            // Decide if/how to notify sender or log this issue more formally
        } catch (Exception e) {
            System.err.println("Erreur inattendue lors du broadcast: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private CompletableFuture<String> historiqueMessagesAsync(JSONObject data, Session session, String actionOriginale) {
        return CompletableFuture.supplyAsync(() -> {
            JSONObject responseJson = new JSONObject();
            responseJson.put("modele", "chat");
            responseJson.put("actionOriginale", actionOriginale); // Client expects "reponseHistoriqueMessages" or "historiqueMessages"

            try {
                int reunionId = data.getInt("reunionId");
                MessageManager messageManager = new MessageManager();
                PersonneManager personneManager = new PersonneManager(); // To get author names

                List<Message> messages = messageManager.obtenirMessagesReunion(reunionId);
                JSONArray messagesJsonArray = new JSONArray();

                for (Message msg : messages) {
                    JSONObject msgJson = new JSONObject();
                    msgJson.put("id", msg.getId());
                    msgJson.put("reunionId", msg.getIdReunion());
                    msgJson.put("auteurId", msg.getIdPersonne());
                    
                    Personne auteur = personneManager.obtenirPersonneParId(msg.getIdPersonne()); // Corrected method name
                    msgJson.put("auteurNom", auteur != null ? auteur.getLogin() : "Inconnu"); // Use login as name
                    
                    msgJson.put("contenu", msg.getContenu());
                    msgJson.put("timestamp", msg.getHeureEnvoi().format(TIMESTAMP_FORMATTER));
                    messagesJsonArray.put(msgJson);
                }

                responseJson.put("statut", "succes");
                responseJson.put("reunionId", reunionId);
                responseJson.put("messages", messagesJsonArray);

            } catch (SQLException e) {
                System.err.println("Erreur SQL (historiqueMessages): " + e.getMessage());
                return genererReponseErreur("Erreur base de données: " + e.getMessage(), actionOriginale);
            } catch (org.json.JSONException e) {
                System.err.println("Erreur JSON (historiqueMessages): " + e.getMessage());
                return genererReponseErreur("Données de demande d'historique mal formatées: " + e.getMessage(), actionOriginale);
            } catch (Exception e) {
                System.err.println("Erreur inattendue (historiqueMessages): " + e.getMessage());
                e.printStackTrace();
                return genererReponseErreur("Erreur serveur: " + e.getMessage(), actionOriginale);
            }
            return responseJson.toString();
        }, Database.getDbExecutor());
    }

    private String genererReponseErreur(String message, String actionOriginale) {
        JSONObject responseJson = new JSONObject();
        responseJson.put("modele", "chat");
        responseJson.put("actionOriginale", actionOriginale);
        responseJson.put("statut", "echec");
        responseJson.put("message", message);
        return responseJson.toString();
    }
}
