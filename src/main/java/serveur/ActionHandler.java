package serveur;

import org.json.JSONObject;
import javax.websocket.Session;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ActionHandler {
    private static final Map<String, WebSocketAction> actions = new HashMap<>();

    static {
        actions.put("reunion", new ReunionService());
        actions.put("authentification", new AuthentificationService());
        // Tu peux ajouter d'autres services ici comme:
        // actions.put("utilisateur", new UtilisateurService());
    }

    public static void handleAction(String message, Session session) throws IOException {
        JSONObject json = new JSONObject(message);
        String modele = json.optString("modele");

        WebSocketAction action = actions.get(modele);
        if (action != null) {
            action.execute(json, session);
        } else {
            session.getBasicRemote().sendText("Erreur: Modèle inconnu '" + modele + "'");
        }
    }
}
