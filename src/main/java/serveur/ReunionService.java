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
import java.util.Set;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;


public class ReunionService implements WebSocketAction {
    @Override
    public void execute(JSONObject data, Session session) throws IOException {
        String action = data.optString("action");
        String reponse = null; // Initialize reponse to null

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
            case "envoyerMessage":
                // No direct response to sender for "envoyerMessage", it broadcasts
                envoyerMessage(data, session);
                return; // Return directly as broadcasting is handled within
            default:
                reponse = genererReponseErreur("Action inconnue '" + action + "' dans le modèle reunion");
                break; // Added break statement
        }

        // Envoie de la réponse au client, if reponse is not null
        if (reponse != null) {
            session.getBasicRemote().sendText(reponse);
        }
    }


    private void handleInviterMembre(JSONObject json, Session session) throws IOException {
        JSONObject response = new JSONObject();
        response.put("type", "invitationResult"); // Type of response for the client

        String reunionIdStr = json.optString("reunionId");
        String usernameToInvite = json.optString("usernameToInvite");
        String inviterUserIdStr = (String) session.getUserProperties().get("userId");

        if (reunionIdStr.isEmpty() || usernameToInvite.isEmpty() || inviterUserIdStr == null || inviterUserIdStr.isEmpty()) {
            response.put("success", false);
            response.put("message", "Reunion ID, username to invite, and inviter ID are required.");
            session.getBasicRemote().sendText(response.toString());
            return;
        }

        int reunionId;
        int inviterUserId;
        try {
            reunionId = Integer.parseInt(reunionIdStr);
            inviterUserId = Integer.parseInt(inviterUserIdStr);
        } catch (NumberFormatException e) {
            response.put("success", false);
            response.put("message", "Invalid Reunion ID or Inviter ID format.");
            session.getBasicRemote().sendText(response.toString());
            return;
        }

        try (Connection conn = Database.getConnection()) {
            // Fetch Reunion Details (especially type and organizer_id)
            String reunionType;
            int organisateurId;
            try (PreparedStatement stmt = conn.prepareStatement("SELECT type, organisateur_id FROM reunion WHERE id = ?")) {
                stmt.setInt(1, reunionId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    reunionType = rs.getString("type");
                    organisateurId = rs.getInt("organisateur_id");
                } else {
                    response.put("success", false);
                    response.put("message", "Reunion not found.");
                    session.getBasicRemote().sendText(response.toString());
                    return;
                }
            }

            // Authorization Check (Basic): Only organizer can invite
            if (inviterUserId != organisateurId) {
                response.put("success", false);
                response.put("message", "Only the organizer can invite members to this reunion.");
                session.getBasicRemote().sendText(response.toString());
                return;
            }

            // Fetch personne_id of the user to be invited
            int invitedPersonId;
            try (PreparedStatement stmt = conn.prepareStatement("SELECT id FROM personne WHERE login = ?")) {
                stmt.setString(1, usernameToInvite);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    invitedPersonId = rs.getInt("id");
                } else {
                    response.put("success", false);
                    response.put("message", "User '" + usernameToInvite + "' not found.");
                    session.getBasicRemote().sendText(response.toString());
                    return;
                }
            }

            // Check if already participating
            try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM participation WHERE personne_id = ? AND reunion_id = ?")) {
                stmt.setInt(1, invitedPersonId);
                stmt.setInt(2, reunionId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    response.put("success", false); // Not necessarily an error, but not a new invitation
                    response.put("message", "'" + usernameToInvite + "' is already a participant in this reunion.");
                    session.getBasicRemote().sendText(response.toString());
                    return;
                }
            }

            // Add to participation table
            try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO participation (personne_id, reunion_id) VALUES (?, ?)")) {
                stmt.setInt(1, invitedPersonId);
                stmt.setInt(2, reunionId);
                stmt.executeUpdate();
            }

            // If reunion is 'PRIVEE', add to autorisation_reunion_privee table
            if ("PRIVEE".equalsIgnoreCase(reunionType)) {
                try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO autorisation_reunion_privee (personne_id, reunion_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE personne_id=personne_id")) {
                    stmt.setInt(1, invitedPersonId);
                    stmt.setInt(2, reunionId);
                    stmt.executeUpdate();
                }
            }

            response.put("success", true);
            response.put("message", "'" + usernameToInvite + "' has been successfully invited to the reunion.");
            session.getBasicRemote().sendText(response.toString());

        } catch (SQLException e) {
            System.err.println("SQL Error during invitation process: " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "An error occurred on the server while processing the invitation.");
            session.getBasicRemote().sendText(response.toString());
        } catch (Exception e) { // Catch any other unexpected errors
            System.err.println("Unexpected error during invitation process: " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "An unexpected server error occurred.");
            session.getBasicRemote().sendText(response.toString());
        }
    }


    private void envoyerMessage(JSONObject data, Session currentSession) throws IOException {
        String reunionId = data.optString("reunionId");
        String userIdStr = data.optString("userId"); // Assuming userId is passed as String
        String contenu = data.optString("contenu");

        if (reunionId.isEmpty() || userIdStr.isEmpty() || contenu.isEmpty()) {
            // Optionally send an error back to the sender if data is invalid
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("type", "error");
            errorResponse.put("message", "Reunion ID, User ID, and content are required.");
            currentSession.getBasicRemote().sendText(errorResponse.toString());
            return;
        }

        int userId;
        try {
            userId = Integer.parseInt(userIdStr);
        } catch (NumberFormatException e) {
            System.err.println("Invalid User ID format: " + userIdStr);
            // Optionally send an error back to the sender
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("type", "error");
            errorResponse.put("message", "Invalid User ID format.");
            currentSession.getBasicRemote().sendText(errorResponse.toString());
            return;
        }

        String senderName = "Unknown"; // Default sender name

        try (Connection conn = Database.getConnection()) {
            // Save message to database
            String sqlInsert = "INSERT INTO message (personne_id, reunion_id, contenu, heure_envoi) VALUES (?, ?, ?, NOW())";
            try (PreparedStatement pstmtInsert = conn.prepareStatement(sqlInsert)) {
                pstmtInsert.setInt(1, userId);
                // Assuming reunion_id in database is also integer. If it's string, adjust accordingly.
                // For now, let's assume it's an integer and needs parsing, or the client sends it as int.
                // If reunionId from JSON is a string like "reunion123", it needs to be an INT for the DB.
                // This part needs clarification based on DB schema for message.reunion_id
                // For now, attempting to parse, but this might fail if reunionId is not numeric.
                try {
                    pstmtInsert.setInt(2, Integer.parseInt(reunionId));
                } catch (NumberFormatException e) {
                    // If reunionId is not an integer, we might need to fetch the actual integer ID
                    // based on a string code, or change the DB schema.
                    // For this example, let's assume client will send an integer reunionId for messages.
                    // Or, we can send an error back if it's not an int.
                    System.err.println("Invalid Reunion ID format for database: " + reunionId + ". Assuming it should be an int.");
                     JSONObject errorResponse = new JSONObject();
                    errorResponse.put("type", "error");
                    errorResponse.put("message", "Invalid Reunion ID format for saving message.");
                    currentSession.getBasicRemote().sendText(errorResponse.toString());
                    return;
                }
                pstmtInsert.setString(3, contenu);
                pstmtInsert.executeUpdate();
            }

            // Fetch sender's name
            String sqlSelectSender = "SELECT nom FROM personne WHERE id = ?";
            try (PreparedStatement pstmtSelect = conn.prepareStatement(sqlSelectSender)) {
                pstmtSelect.setInt(1, userId);
                ResultSet rs = pstmtSelect.executeQuery();
                if (rs.next()) {
                    senderName = rs.getString("nom");
                }
            }

        } catch (SQLException e) {
            System.err.println("Erreur SQL lors de l'envoi/sauvegarde du message : " + e.getMessage());
            e.printStackTrace();
            // Optionally send an error back to the sender
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("type", "error");
            errorResponse.put("message", "Database error while sending message.");
            currentSession.getBasicRemote().sendText(errorResponse.toString());
            return;
        }

        // Construct broadcast JSON
        JSONObject broadcastJson = new JSONObject();
        broadcastJson.put("type", "newMessage"); // Type identifier for the client
        broadcastJson.put("reunionId", reunionId); // Include reunionId
        broadcastJson.put("sender", senderName); // Key "sender" as expected by client
        broadcastJson.put("content", contenu);    // Key "content" as expected by client
        broadcastJson.put("userId", userIdStr); // Include sender's user ID (client might use this)

        // Broadcast message
        Set<Session> allSessions = ServeurWebSocket.getSessions();
        for (Session s : allSessions) {
            if (s.isOpen()) {
                String sessionReunionId = (String) s.getUserProperties().get("reunionId");
                // Ensure we only send to sessions in the same reunion
                if (reunionId.equals(sessionReunionId)) {
                    try {
                        s.getBasicRemote().sendText(broadcastJson.toString());
                    } catch (IOException e) {
                        System.err.println("Erreur lors de la diffusion du message à la session " + s.getId() + ": " + e.getMessage());
                    }
                }
            }
        }
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