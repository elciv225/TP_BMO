package serveur;

import org.json.JSONObject;
import org.json.JSONException;
import javax.websocket.Session;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import java.io.IOException; // May not be needed if only JSONException is thrown by new JSONObject
import java.util.HashMap;
import java.util.Map;

public class ActionHandler {
    private static final Map<String, WebSocketAction> actions = new HashMap<>();

    static {
        actions.put("reunion", new ReunionService());
        actions.put("authentification", new AuthentificationService());
        actions.put("chat", new ChatService()); // Added ChatService
        // Tu peux ajouter d'autres services ici comme:
        // actions.put("utilisateur", new UtilisateurService());
    }

    public static void handleAction(String message, Session session) { // Removed throws IOException
        try {
            JSONObject json = new JSONObject(message);
            String modele = json.optString("modele");

            WebSocketAction actionService = actions.get(modele);
            if (actionService != null) {
                actionService.execute(json, session); // WebSocketAction.execute is now async and handles its own responses/errors
            } else {
                String errorMessage = "Erreur: Modèle inconnu '" + modele + "'";
                System.err.println(errorMessage); // Log it server-side too
                session.getAsyncRemote().sendText(new JSONObject().put("statut", "echec").put("message", errorMessage).toString(), new SendHandler() {
                    @Override
                    public void onResult(SendResult result) {
                        if (!result.isOK()) {
                            System.err.println("Erreur envoi message 'Modèle inconnu' async dans ActionHandler: " + result.getException());
                            if(result.getException() != null) {
                                result.getException().printStackTrace();
                            }
                        }
                    }
                });
            }
        } catch (JSONException e) {
            System.err.println("Erreur parsing JSON dans ActionHandler: " + e.getMessage());
            e.printStackTrace();
            String errorResponse = new JSONObject().put("statut", "echec").put("message", "Erreur format message: " + e.getMessage()).toString();
            session.getAsyncRemote().sendText(errorResponse, new SendHandler() {
                @Override
                public void onResult(SendResult result) {
                    if (!result.isOK()) {
                        System.err.println("Erreur envoi message 'Erreur JSON' async dans ActionHandler: " + result.getException());
                        if(result.getException() != null) {
                           result.getException().printStackTrace();
                        }
                    }
                }
            });
        }
        // Other potential IOExceptions are now handled by the async send handlers or within the specific actionService.execute methods
    }
}
