package serveur;

import model.Personne;
import model.PersonneManager;
import model.Reunion;
import model.ReunionManager;
import org.json.JSONObject;

import javax.websocket.Session;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

public class ReunionService implements WebSocketAction {
    @Override
    public void execute(JSONObject data, Session session) {
        String action = data.optString("action");
        final String actionOriginale = action; // Capture for use in lambdas
        CompletableFuture<String> futureReponse;

        switch (action) {
            case "creation":
                futureReponse = creerReunionAsync(data, actionOriginale);
                break;
            case "rejoindre":
                futureReponse = rejoindreReunionAsync(data, actionOriginale);
                break;
            case "details":
                futureReponse = obtenirDetailsReunionAsync(data, actionOriginale);
                break;
            case "lister": // New action
                futureReponse = listerReunionsAsync(data, actionOriginale); // Shell implementation for now
                break;
            case "inviter": // New action
                futureReponse = inviterUtilisateurAsync(data, actionOriginale); // Shell implementation for now
                break;
            default:
                futureReponse = CompletableFuture.completedFuture(genererReponseErreur("Action inconnue '" + action + "'", actionOriginale));
        }

        futureReponse.thenAcceptAsync(reponseString -> {
            session.getAsyncRemote().sendText(reponseString, new SendHandler() {
                @Override
                public void onResult(SendResult result) {
                    if (!result.isOK()) {
                        System.err.println("Erreur envoi message async dans ReunionService (" + actionOriginale + "): " + result.getException());
                        result.getException().printStackTrace();
                    }
                }
            });
        }).exceptionally(ex -> {
            System.err.println("Exception dans la chaine CompletableFuture de ReunionService: " + ex);
            ex.printStackTrace();
            String errorResponse = genererReponseErreur("Erreur interne du serveur: " + ex.getMessage(), actionOriginale);
            session.getAsyncRemote().sendText(errorResponse, new SendHandler() {
                @Override
                public void onResult(SendResult result) {
                    if (!result.isOK()) {
                        System.err.println("Erreur envoi message d'erreur async dans ReunionService (exceptionally, " + actionOriginale + "): " + result.getException());
                        result.getException().printStackTrace();
                    }
                }
            });
            return null;
        });
    }

    // Signature already updated in previous (failed) attempt, just ensuring it's correct.
    // And that actionOriginale is used in responses.
    private CompletableFuture<String> creerReunionAsync(JSONObject data, String actionOriginale) {
        return CompletableFuture.supplyAsync(() -> {
            JSONObject reponseJson = new JSONObject();
            reponseJson.put("modele", "reunion");
            reponseJson.put("actionOriginale", actionOriginale); // Corrected

            try {
                // Vérifier les paramètres obligatoires
                String titre = data.optString("titre", "").trim();
                String sujet = data.optString("sujet", "").trim();
                String agenda = data.optString("agenda", "").trim();
                int idOrganisateur = data.optInt("idOrganisateur", -1);

                if (titre.isEmpty() || idOrganisateur == -1) {
                    return genererReponseErreur("Titre et organisateur obligatoires.", actionOriginale);
                }

                String debutStr = data.optString("debut", "");
                LocalDateTime debut = debutStr.isEmpty() ? LocalDateTime.now() : LocalDateTime.parse(debutStr); // Expect ISO format
                int duree = data.optInt("duree", 60);
                String typeStr = data.optString("type", "STANDARD");
                Reunion.Type type;
                try {
                    type = Reunion.Type.valueOf(typeStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return genererReponseErreur("Type de réunion invalide: " + typeStr, actionOriginale);
                }
                Integer idAnimateur = data.has("idAnimateur") ? data.optInt("idAnimateur") : null;


                ReunionManager reunionManager = new ReunionManager();
                Reunion nouvelleReunion = reunionManager.planifierReunion(
                        titre, sujet, agenda, debut, duree, type, idOrganisateur, idAnimateur
                );

                reponseJson.put("statut", "succes");
                reponseJson.put("message", "Réunion '" + nouvelleReunion.getNom() + "' créée avec succès.");
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

                        .putOpt("idAnimateur", nouvelleReunion.getIdAnimateur())
                );

            } catch (SQLException e) {
                System.err.println("Erreur SQL (création réunion): " + e.getMessage());
                return genererReponseErreur("Erreur base de données lors de la création: " + e.getMessage(), actionOriginale);
            } catch (Exception e) {
                System.err.println("Erreur (création réunion): " + e.getMessage());
                e.printStackTrace();
                return genererReponseErreur("Erreur inattendue lors de la création: " + e.getMessage(), actionOriginale);
            }
            return reponseJson.toString();
        }, Database.getDbExecutor());
    }

    private CompletableFuture<String> rejoindreReunionAsync(JSONObject data, String actionOriginale) {
        return CompletableFuture.supplyAsync(() -> {
            JSONObject reponseJson = new JSONObject();
            reponseJson.put("modele", "reunion");
            reponseJson.put("actionOriginale", actionOriginale); // Updated

            try {
                String codeReunion = data.optString("code");
                String participant = data.optString("participant");

                // Logique à implémenter pour rejoindre une réunion
                // Pour l'instant, une réponse de succès simulée
                reponseJson.put("statut", "succes");
                reponseJson.put("message", "Vous avez rejoint la réunion " + codeReunion);

            } catch (Exception e) {
                System.err.println("Erreur lors de la jonction de réunion : " + e.getMessage());
                // Note: genererReponseErreur returns a String, so we must put its content into the JSON
                JSONObject errorDetails = new JSONObject(genererReponseErreur("Erreur lors de la jonction de réunion"));
                reponseJson.put("statut", errorDetails.optString("statut", "echec"));
                reponseJson.put("message", errorDetails.optString("message", "Erreur lors de la jonction de réunion"));
            }
            return reponseJson.toString();
        }, Database.getDbExecutor());
    }

    private CompletableFuture<String> obtenirDetailsReunionAsync(JSONObject data) {
        return CompletableFuture.supplyAsync(() -> {
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
                    // Return the error JSON string directly as per original logic
                    return genererReponseErreur("Réunion non trouvée");
                }

            } catch (SQLException e) {
                System.err.println("Erreur lors de la récupération des détails de réunion : " + e.getMessage());
                // Return the error JSON string directly
                return genererReponseErreur("Erreur interne lors de la récupération des détails");
            }
            return reponseJson.toString();
        }, Database.getDbExecutor());
    }

    // Fully implemented listerReunionsAsync
    private CompletableFuture<String> listerReunionsAsync(JSONObject data, String actionOriginale) {
        return CompletableFuture.supplyAsync(() -> {
            JSONObject reponseJson = new JSONObject();
            reponseJson.put("modele", "reunion");
            reponseJson.put("actionOriginale", actionOriginale);
            try {
                ReunionManager reunionManager = new ReunionManager();
                // Assuming obtenirToutesReunions() is the correct method in ReunionManager
                java.util.List<Reunion> reunions = reunionManager.obtenirToutesReunions(); 

                JSONArray reunionsArray = new JSONArray();
                for (Reunion r : reunions) {
                    JSONObject meetingJson = new JSONObject();
                    meetingJson.put("id", r.getId());
                    meetingJson.put("titre", r.getNom()); // Client expects "titre"
                    meetingJson.put("sujet", r.getSujet());
                    meetingJson.put("agenda", r.getAgenda()); // Client might need this for details view
                    meetingJson.put("debut", r.getDebut().toString()); // ISO 8601 format
                    meetingJson.put("duree", r.getDuree());
                    meetingJson.put("type", r.getType().toString());
                    meetingJson.put("idOrganisateur", r.getIdOrganisateur());
                    if (r.getIdAnimateur() != null && r.getIdAnimateur() != 0) { // Check for null or 0 if DB uses 0 for non-existent
                        meetingJson.put("idAnimateur", r.getIdAnimateur());
                    }
                    reunionsArray.put(meetingJson);
                }
                reponseJson.put("statut", "succes");
                reponseJson.put("reunions", reunionsArray);
            } catch (SQLException e) {
                System.err.println("Erreur SQL (lister réunions): " + e.getMessage());
                return genererReponseErreur("Erreur base de données en listant les réunions: " + e.getMessage(), actionOriginale);
            } catch (Exception e) {
                System.err.println("Erreur inattendue (lister réunions): " + e.getMessage());
                e.printStackTrace();
                return genererReponseErreur("Erreur serveur en listant les réunions: " + e.getMessage(), actionOriginale);
            }
            return reponseJson.toString();
        }, Database.getDbExecutor());
    }

    // Implemented inviterUtilisateurAsync
    private CompletableFuture<String> inviterUtilisateurAsync(JSONObject data, String actionOriginale) {
        return CompletableFuture.supplyAsync(() -> {
            JSONObject reponseJson = new JSONObject();
            reponseJson.put("modele", "reunion");
            reponseJson.put("actionOriginale", actionOriginale);
            try {
                int reunionId = data.getInt("reunionId");
                int utilisateurIdToInvite = data.getInt("utilisateurId"); // Assuming client sends an int ID

                if (reunionId <= 0 || utilisateurIdToInvite <= 0) {
                    return genererReponseErreur("ID de réunion ou d'utilisateur invalide.", actionOriginale);
                }
                
                // Optional: Check if meeting and user exist before attempting to add.
                // ReunionManager reunionManager = new ReunionManager();
                // if (reunionManager.consulterDetailsReunion(reunionId) == null) {
                //    return genererReponseErreur("Réunion non trouvée: ID " + reunionId, actionOriginale);
                // }
                // PersonneManager personneManager = new PersonneManager(); // Assuming this manager exists
                // if (personneManager.getPersonneById(utilisateurIdToInvite) == null) { 
                //    return genererReponseErreur("Utilisateur non trouvé: ID " + utilisateurIdToInvite, actionOriginale);
                // }

                ParticipationManager participationManager = new ParticipationManager();
                // Using entrerDansReunion as it adds to participation table.
                // If a true "INVITED" status is needed, ParticipationManager and DB schema would need changes.
                boolean success = participationManager.entrerDansReunion(utilisateurIdToInvite, reunionId);

                if (success) {
                    reponseJson.put("statut", "succes");
                    reponseJson.put("message", "Utilisateur " + utilisateurIdToInvite + " ajouté/invité à la réunion " + reunionId + ".");
                } else {
                    // entrerDansReunion handles duplicate as success, so this path might not be hit often unless other errors occur.
                    return genererReponseErreur("Impossible d'ajouter/inviter l'utilisateur à la réunion.", actionOriginale);
                }

            } catch (SQLException e) {
                System.err.println("Erreur SQL (inviter utilisateur): " + e.getMessage());
                // SQLState "23000" is a general integrity constraint violation.
                // entrerDansReunion already handles specific duplicate entry logic.
                return genererReponseErreur("Erreur base de données lors de l'invitation: " + e.getMessage(), actionOriginale);
            } catch (org.json.JSONException e) {
                System.err.println("Erreur JSON (inviter utilisateur): " + e.getMessage());
                return genererReponseErreur("Données d'invitation mal formatées: " + e.getMessage(), actionOriginale);
            } catch (Exception e) {
                System.err.println("Erreur inattendue (inviter utilisateur): " + e.getMessage());
                e.printStackTrace();
                return genererReponseErreur("Erreur serveur lors de l'invitation: " + e.getMessage(), actionOriginale);
            }
            return reponseJson.toString();
        }, Database.getDbExecutor());
    }

    private String genererReponseErreur(String message, String actionOriginale) { // Added actionOriginale
        JSONObject reponseJson = new JSONObject();
        reponseJson.put("modele", "reunion");
        reponseJson.put("actionOriginale", actionOriginale);
        reponseJson.put("statut", "echec");
        reponseJson.put("message", message);
        return reponseJson.toString();
    }
}