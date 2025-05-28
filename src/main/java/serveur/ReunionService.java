package serveur;

import model.Personne;
import model.PersonneManager;
import model.Reunion;
import model.ReunionManager;
import org.json.JSONObject;

import javax.websocket.Session;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class ReunionService implements WebSocketAction {
    @Override
    public void execute(JSONObject data, Session session) throws IOException {
        String action = data.optString("action");
        String reponse;

        switch (action) {
            case "creation":
                reponse = creerReunion(data);
                break;
            case "rejoindre":
                reponse = rejoindreReunion(data);
                break;
            case "details":
                reponse = obtenirDetailsReunion(data);
                break;
            default:
                reponse = genererReponseErreur("Action inconnue '" + action + "'");
        }

        // Envoie de la réponse au client
        session.getBasicRemote().sendText(reponse);
    }

    private String creerReunion(JSONObject data) {
        JSONObject reponseJson = new JSONObject();
        reponseJson.put("modele", "reunion");
        reponseJson.put("action", "reponseCreation");

        try {
            // Vérifier les paramètres obligatoires
            String titre = data.optString("titre", "").trim();
            String sujet = data.optString("sujet", "").trim();
            String agenda = data.optString("agenda", "").trim();
            int idOrganisateur = data.optInt("idOrganisateur", -1);

            if (titre.isEmpty() || idOrganisateur == -1) {
                return genererReponseErreur("Titre et organisateur obligatoires");
            }

            // Récupération et parsing de la date de début
            String debutStr = data.optString("debut", "");
            LocalDateTime debut = debutStr.isEmpty() ? LocalDateTime.now() : LocalDateTime.parse(debutStr);

            // Récupération de la durée (valeur par défaut 60 min)
            int duree = data.optInt("duree", 60);

            // Récupération du type de réunion
            String typeStr = data.optString("type", "STANDARD");
            Reunion.Type type;
            try {
                type = Reunion.Type.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return genererReponseErreur("Type de réunion invalide");
            }

            ReunionManager reunionManager = new ReunionManager();
            Reunion nouvelleReunion = reunionManager.planifierReunion(
                    titre, sujet, agenda, debut, duree, type, idOrganisateur, null
            );

            // Préparation de la réponse de succès
            reponseJson.put("statut", "succes");
            reponseJson.put("message", "Réunion créée avec succès");
            reponseJson.put("reunion", new JSONObject()
                    .put("id", nouvelleReunion.getId())
                    .put("titre", nouvelleReunion.getNom())
                    .put("sujet", nouvelleReunion.getSujet())
                    .put("agenda", nouvelleReunion.getAgenda())
                    .put("debut", nouvelleReunion.getDebut().toString())
                    .put("duree", nouvelleReunion.getDuree())
                    .put("type", nouvelleReunion.getType().toString())
                    .put("idOrganisateur", nouvelleReunion.getIdOrganisateur())
            );

        } catch (SQLException e) {
            System.err.println("Erreur SQL lors de la création de réunion : " + e.getMessage());
            reponseJson.put("statut", "echec");
            reponseJson.put("message", "Erreur interne lors de la création de réunion");
        } catch (Exception e) {
            System.err.println("Erreur lors de la création de réunion : " + e.getMessage());
            reponseJson.put("statut", "echec");
            reponseJson.put("message", "Erreur inattendue lors de la création de réunion");
        }

        return reponseJson.toString();
    }


    private String rejoindreReunion(JSONObject data) {
        JSONObject reponseJson = new JSONObject();
        reponseJson.put("modele", "reunion");
        reponseJson.put("action", "reponseRejoindre");

        try {
            // Récupérer le code de la réunion
            String codeReunion = data.optString("code");
            String participant = data.optString("participant");

            // Logique à implémenter pour rejoindre une réunion
            // Pour l'instant, une réponse de succès simulée
            reponseJson.put("statut", "succes");
            reponseJson.put("message", "Vous avez rejoint la réunion " + codeReunion);

        } catch (Exception e) {
            System.err.println("Erreur lors de la jonction de réunion : " + e.getMessage());
            return genererReponseErreur("Erreur lors de la jonction de réunion");
        }

        return reponseJson.toString();
    }

    private String obtenirDetailsReunion(JSONObject data) {
        JSONObject reponseJson = new JSONObject();
        reponseJson.put("modele", "reunion");
        reponseJson.put("action", "reponseDetails");

        try {
            int reunionId = data.optInt("id");
            ReunionManager reunionManager = new ReunionManager();
            Reunion reunion = reunionManager.consulterDetailsReunion(reunionId);

            if (reunion != null) {
                reponseJson.put("statut", "succes");
                reponseJson.put("reunion", new JSONObject()
                        .put("id", reunion.getId())
                        .put("titre", reunion.getNom())
                        .put("sujet", reunion.getSujet())
                        .put("debut", reunion.getDebut().toString())
                        .put("duree", reunion.getDuree())
                );
            } else {
                return genererReponseErreur("Réunion non trouvée");
            }

        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des détails de réunion : " + e.getMessage());
            return genererReponseErreur("Erreur interne lors de la récupération des détails");
        }

        return reponseJson.toString();
    }

    private String genererReponseErreur(String message) {
        JSONObject reponseJson = new JSONObject();
        reponseJson.put("statut", "echec");
        reponseJson.put("message", message);
        return reponseJson.toString();
    }
}