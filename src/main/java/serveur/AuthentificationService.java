package serveur;

import model.Personne;
import model.PersonneManager;
import org.json.JSONObject;

import javax.websocket.Session;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
// import java.io.IOException; // No longer thrown by execute
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

public class AuthentificationService implements WebSocketAction {
    @Override
    public void execute(JSONObject data, Session session) { // Removed throws IOException
        String action = data.optString("action");
        final String actionOriginale = action; // For context in async blocks
        CompletableFuture<String> futureReponse;

        switch (action) {
            case "connexion":
                futureReponse = connexionAsync(data, session, actionOriginale); // Pass session
                break;
            default:
                JSONObject errorJson = new JSONObject();
                errorJson.put("modele", "authentification");
                errorJson.put("actionOriginale", actionOriginale);
                errorJson.put("statut", "echec");
                errorJson.put("message", "Action d'authentification inconnue '" + action + "'");
                futureReponse = CompletableFuture.completedFuture(errorJson.toString());
        }

        futureReponse.thenAcceptAsync(reponseString -> {
            session.getAsyncRemote().sendText(reponseString, new SendHandler() {
                @Override
                public void onResult(SendResult result) {
                    if (!result.isOK()) {
                        System.err.println("Erreur envoi message async dans AuthentificationService (" + actionOriginale + "): " + result.getException());
                        if (result.getException() != null) {
                            result.getException().printStackTrace();
                        }
                    }
                }
            });
        }).exceptionally(ex -> {
            System.err.println("Exception dans la chaine CompletableFuture de AuthentificationService ("+actionOriginale+"): " + ex);
            ex.printStackTrace();
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("modele", "authentification");
            errorResponse.put("actionOriginale", actionOriginale);
            errorResponse.put("statut", "echec");
            errorResponse.put("message", "Erreur interne du serveur: " + ex.getMessage());
            session.getAsyncRemote().sendText(errorResponse.toString(), new SendHandler() {
                 @Override
                 public void onResult(SendResult result) {
                     if (!result.isOK()) {
                         System.err.println("Erreur envoi message d'erreur (exceptionally) async dans AuthentificationService: " + result.getException());
                         if(result.getException() != null) result.getException().printStackTrace();
                     }
                 }
            });
            return null;
        });
    }

    private CompletableFuture<String> connexionAsync(JSONObject data, Session session, String actionOriginale) { // Added session parameter
        return CompletableFuture.supplyAsync(() -> {
            String login = data.optString("login");
            String password = data.optString("password");
            JSONObject reponseJson = new JSONObject();

            reponseJson.put("modele", "authentification");
            reponseJson.put("actionOriginale", actionOriginale); 

            try {
                PersonneManager personneManager = new PersonneManager();
                Personne personne = personneManager.connecter(login, password);

                if (personne != null) {
                    // Store user info in session properties upon successful login
                    session.getUserProperties().put("userId", personne.getId());
                    session.getUserProperties().put("userName", personne.getLogin()); // Or getNom(), getPrenom()

                    reponseJson.put("statut", "succes");
                    reponseJson.put("message", "Connexion réussie");
                    // Ensure personne.toJsonObject() exists and works as expected
                    reponseJson.put("personne", personne.toJsonObject()); 
                } else {
                    reponseJson.put("statut", "echec");
                    reponseJson.put("message", "Identifiants incorrects");
                }
            } catch (SQLException e) {
                System.err.println("Erreur SQL (connexion): " + e.getMessage());
                reponseJson.put("statut", "echec"); // Changed from "status":"error" for consistency
                reponseJson.put("message", "Erreur interne du serveur lors de l'authentification.");
            } catch (Exception e) {
                System.err.println("Erreur inattendue (connexion): " + e.getMessage());
                e.printStackTrace();
                reponseJson.put("statut", "echec");
                reponseJson.put("message", "Erreur serveur inattendue: " + e.getMessage());
            }
            return reponseJson.toString();
        }, Database.getDbExecutor()); // Assuming Database.getDbExecutor() exists
    }
}
