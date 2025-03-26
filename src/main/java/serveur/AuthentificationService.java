package serveur;

import model.Personne;
import model.PersonneManager;
import org.json.JSONObject;

import javax.websocket.Session;
import java.io.IOException;
import java.sql.SQLException;

public class AuthentificationService implements WebSocketAction{
    @Override
    public void execute(JSONObject data, Session session) throws IOException {
        String action = data.optString("action");
        String reponse;
        switch (action){
            case "connexion":
                reponse = connexion(data);
                break;
            default:
                reponse = "Erreur: Action inconnue '" + action + "'";
        }
        // Envoie de la reponse au client
        session.getBasicRemote().sendText(reponse);
    }

    private String connexion(JSONObject data){
        String login = data.optString("login");
        String password = data.optString("password");
        JSONObject reponseJson = new JSONObject();

        reponseJson.put("modele", "authentification"); // Indique que ce message concerne l'authentification.
        reponseJson.put("action", "reponseConnexion"); // Indique que c'est une réponse à une tentative de connexion
        // Inte=éractionn avec la base de données pour vérifier les informations de connexion
        try {
            PersonneManager personneManager = new PersonneManager();
            Personne personne = personneManager.connecter(login, password);

            // Si la connexion est réussie, on envoie un message de succès
            if (personne != null) {
                reponseJson.put("statut", "succes");
                reponseJson.put("message", "Connexion réussie");
                reponseJson.put("personne", personne.toJsonObject());
            } else {
                reponseJson.put("statut", "echec");
                reponseJson.put("message", "Identifiants incorrects");
            }

        } catch (SQLException e) {
            System.err.println("Erreur lors de la connexion à la base de données : " + e.getMessage()); // Affichage de l'erreur dans la console du serveur pour le débogage.
            reponseJson.put("status", "error"); // Ajout du statut "error" à la réponse.
            reponseJson.put("message", "Erreur interne du serveur lors de l'authentification.");
        }

        return reponseJson.toString();
    }
}
