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
        String reponse = null;

        System.out.println("ReunionService - Action reçue: " + action);
        System.out.println("ReunionService - Données reçues: " + data.toString());

        switch (action) {
            case "creation":
                reponse = creerReunion(data, session);
                break;
            case "rejoindre":
                reponse = rejoindreReunion(data, session); // Pass session
                break;
            case "details":
                reponse = obtenirDetailsReunion(data);
                break;
            case "envoyerMessage":
                envoyerMessage(data, session);
                return;
            case "inviterMembre":
                handleInviterMembre(data, session);
                return;
            case "quitterReunion":
                reponse = quitterReunion(data, session); // Pass session
                break;
            default:
                reponse = genererReponseErreur("Action inconnue '" + action + "' dans le modèle reunion");
                break;
        }

        if (reponse != null) {
            session.getBasicRemote().sendText(reponse);
        }
    }

    /**
     * CORRECTION: Crée une réunion et ajoute automatiquement l'organisateur comme participant
     */
    private String creerReunion(JSONObject data, Session session) {
        JSONObject reponseJson = new JSONObject();
        reponseJson.put("modele", "reunion");
        reponseJson.put("action", "reponseCreation");

        try {
            String nom = data.optString("nom", "").trim();
            String sujet = data.optString("sujet", "").trim();
            String agenda = data.optString("agenda", "").trim();
            int idOrganisateur = data.optInt("idOrganisateur", -1);

            if (nom.isEmpty() || idOrganisateur == -1) {
                return genererReponseErreur("Nom et organisateur obligatoires");
            }

            String debutStr = data.optString("debut", "");
            LocalDateTime debut = debutStr.isEmpty() ? LocalDateTime.now() : LocalDateTime.parse(debutStr);
            int duree = data.optInt("duree", 60);
            String typeStr = data.optString("type", "STANDARD");

            Reunion.Type type;
            try {
                type = Reunion.Type.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return genererReponseErreur("Type de réunion invalide");
            }

            ReunionManager reunionManager = new ReunionManager();
            Reunion nouvelleReunion = reunionManager.planifierReunion(
                    nom, sujet, agenda, debut, duree, type, idOrganisateur, null
            );

            // NOUVEAU: Ajouter automatiquement l'organisateur comme participant
            try (Connection conn = Database.getConnection()) {
                String insertParticipationSql = "INSERT INTO participation (personne_id, reunion_id) VALUES (?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(insertParticipationSql)) {
                    pstmt.setInt(1, idOrganisateur);
                    pstmt.setInt(2, nouvelleReunion.getId());
                    pstmt.executeUpdate();
                    System.out.println("Organisateur ajouté comme participant à la réunion ID: " + nouvelleReunion.getId());
                }
            }

            reponseJson.put("statut", "succes");
            reponseJson.put("message", "Réunion créée avec succès");

            // NOUVEAU: Inclure l'ID de la réunion pour redirection automatique
            JSONObject reunionData = new JSONObject();
            reunionData.put("id", nouvelleReunion.getId());
            reunionData.put("nom", nouvelleReunion.getNom());
            reunionData.put("sujet", nouvelleReunion.getSujet());
            reunionData.put("agenda", nouvelleReunion.getAgenda());
            reunionData.put("debut", nouvelleReunion.getDebut().toString());
            reunionData.put("duree", nouvelleReunion.getDuree());
            reunionData.put("type", nouvelleReunion.getType().toString());
            reunionData.put("idOrganisateur", nouvelleReunion.getIdOrganisateur());

            reponseJson.put("reunion", reunionData);
            reponseJson.put("autoJoin", true); // Signal pour rejoindre automatiquement

        } catch (SQLException e) {
            System.err.println("Erreur SQL lors de la création de réunion: " + e.getMessage());
            e.printStackTrace();
            reponseJson.put("statut", "echec");
            reponseJson.put("message", "Erreur interne lors de la création de réunion");
        } catch (Exception e) {
            System.err.println("Erreur lors de la création de réunion: " + e.getMessage());
            e.printStackTrace();
            reponseJson.put("statut", "echec");
            reponseJson.put("message", "Erreur inattendue lors de la création de réunion");
        }

        return reponseJson.toString();
    }

    /**
     * CORRECTION: Amélioration de la fonction d'envoi de message avec validation FK
     */
    private void envoyerMessage(JSONObject data, Session currentSession) throws IOException {
        String reunionId = data.optString("reunionId");
        String userIdStr = data.optString("userId");
        String contenu = data.optString("contenu");

        System.out.println("Tentative d'envoi de message - ReunionID: " + reunionId + ", UserID: " + userIdStr + ", Contenu: " + contenu);

        if (reunionId.isEmpty() || userIdStr.isEmpty() || contenu.isEmpty()) {
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("type", "error");
            errorResponse.put("message", "ID de réunion, ID utilisateur et contenu requis.");
            currentSession.getBasicRemote().sendText(errorResponse.toString());
            return;
        }

        int userId;
        int reunionIdInt;
        try {
            userId = Integer.parseInt(userIdStr);
            reunionIdInt = Integer.parseInt(reunionId);
        } catch (NumberFormatException e) {
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("type", "error");
            errorResponse.put("message", "Format d'ID invalide.");
            currentSession.getBasicRemote().sendText(errorResponse.toString());
            return;
        }

        String senderName = "Inconnu";

        try (Connection conn = Database.getConnection()) {
            // CORRECTION: Vérifier d'abord que l'utilisateur existe
            String checkUserSql = "SELECT CONCAT(nom, ' ', prenom) as nom_complet FROM personne WHERE id = ?";
            boolean userExists = false;

            try (PreparedStatement checkStmt = conn.prepareStatement(checkUserSql)) {
                checkStmt.setInt(1, userId);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    senderName = rs.getString("nom_complet");
                    userExists = true;
                } else {
                    System.err.println("Utilisateur avec ID " + userId + " n'existe pas dans la base de données");
                }
            }

            if (!userExists) {
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("type", "error");
                errorResponse.put("message", "Utilisateur non trouvé.");
                currentSession.getBasicRemote().sendText(errorResponse.toString());
                return;
            }

            // CORRECTION: Vérifier que la réunion existe
            String checkReunionSql = "SELECT COUNT(*) FROM reunion WHERE id = ?";
            boolean reunionExists = false;

            try (PreparedStatement checkStmt = conn.prepareStatement(checkReunionSql)) {
                checkStmt.setInt(1, reunionIdInt);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) {
                    reunionExists = true;
                } else {
                    System.err.println("Réunion avec ID " + reunionIdInt + " n'existe pas");
                }
            }

            if (!reunionExists) {
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("type", "error");
                errorResponse.put("message", "Réunion non trouvée.");
                currentSession.getBasicRemote().sendText(errorResponse.toString());
                return;
            }

            // CORRECTION: Vérifier que l'utilisateur participe à la réunion
            String checkParticipationSql = "SELECT COUNT(*) FROM participation WHERE personne_id = ? AND reunion_id = ?";
            boolean isParticipant = false;

            try (PreparedStatement checkStmt = conn.prepareStatement(checkParticipationSql)) {
                checkStmt.setInt(1, userId);
                checkStmt.setInt(2, reunionIdInt);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) {
                    isParticipant = true;
                }
            }

            if (!isParticipant) {
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("type", "error");
                errorResponse.put("message", "Vous ne participez pas à cette réunion.");
                currentSession.getBasicRemote().sendText(errorResponse.toString());
                return;
            }

            // Maintenant on peut sauvegarder le message en toute sécurité
            String sqlInsert = "INSERT INTO message (personne_id, reunion_id, contenu, heure_envoi) VALUES (?, ?, ?, NOW())";
            try (PreparedStatement pstmtInsert = conn.prepareStatement(sqlInsert)) {
                pstmtInsert.setInt(1, userId);
                pstmtInsert.setInt(2, reunionIdInt);
                pstmtInsert.setString(3, contenu);
                int rowsAffected = pstmtInsert.executeUpdate();

                if (rowsAffected > 0) {
                    System.out.println("Message sauvegardé avec succès pour l'utilisateur " + userId + " dans la réunion " + reunionIdInt);
                } else {
                    System.err.println("Aucune ligne affectée lors de l'insertion du message");
                }
            }

        } catch (SQLException e) {
            System.err.println("Erreur SQL lors de l'envoi du message: " + e.getMessage());
            e.printStackTrace();
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("type", "error");
            errorResponse.put("message", "Erreur serveur lors de la sauvegarde du message.");
            currentSession.getBasicRemote().sendText(errorResponse.toString());
            return;
        }

        // Diffuser le message à toutes les sessions de la même réunion
        JSONObject broadcastJson = new JSONObject();
        broadcastJson.put("type", "newMessage");
        broadcastJson.put("reunionId", reunionId);
        broadcastJson.put("sender", senderName);
        broadcastJson.put("content", contenu);
        broadcastJson.put("userId", userIdStr);
        broadcastJson.put("timestamp", System.currentTimeMillis()); // NOUVEAU: Timestamp pour l'affichage

        Set<Session> allSessions = ServeurWebSocket.getSessions();
        int messagesSent = 0;
        for (Session s : allSessions) {
            if (s.isOpen()) {
                String sessionReunionId = (String) s.getUserProperties().get("reunionId");
                if (reunionId.equals(sessionReunionId)) {
                    try {
                        s.getBasicRemote().sendText(broadcastJson.toString());
                        messagesSent++;
                    } catch (IOException e) {
                        System.err.println("Erreur lors de la diffusion du message à la session " + s.getId() + ": " + e.getMessage());
                    }
                }
            }
        }
        System.out.println("Message diffusé à " + messagesSent + " sessions");
    }

    /**
     * Gère l'invitation d'un membre à une réunion
     */
    private void handleInviterMembre(JSONObject json, Session session) throws IOException {
        JSONObject response = new JSONObject();
        response.put("type", "invitationResult");

        String reunionIdStr = json.optString("reunionId");
        String usernameToInvite = json.optString("usernameToInvite");
        String inviterUserIdStr = (String) session.getUserProperties().get("userId");

        if (reunionIdStr.isEmpty() || usernameToInvite.isEmpty()) {
            response.put("success", false);
            response.put("message", "ID de réunion et nom d'utilisateur requis.");
            session.getBasicRemote().sendText(response.toString());
            return;
        }

        int reunionId;
        try {
            reunionId = Integer.parseInt(reunionIdStr);
        } catch (NumberFormatException e) {
            response.put("success", false);
            response.put("message", "Format d'ID de réunion invalide.");
            session.getBasicRemote().sendText(response.toString());
            return;
        }

        try (Connection conn = Database.getConnection()) {
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
                    response.put("message", "Réunion non trouvée.");
                    session.getBasicRemote().sendText(response.toString());
                    return;
                }
            }

            int inviterUserId = -1;
            if (inviterUserIdStr != null) {
                try {
                    inviterUserId = Integer.parseInt(inviterUserIdStr);
                } catch (NumberFormatException e) {
                    // Ignorer
                }
            }

            if (inviterUserId != organisateurId) {
                response.put("success", false);
                response.put("message", "Seul l'organisateur peut inviter des membres à cette réunion.");
                session.getBasicRemote().sendText(response.toString());
                return;
            }

            int invitedPersonId;
            try (PreparedStatement stmt = conn.prepareStatement("SELECT id FROM personne WHERE login = ?")) {
                stmt.setString(1, usernameToInvite);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    invitedPersonId = rs.getInt("id");
                } else {
                    response.put("success", false);
                    response.put("message", "Utilisateur '" + usernameToInvite + "' non trouvé.");
                    session.getBasicRemote().sendText(response.toString());
                    return;
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM participation WHERE personne_id = ? AND reunion_id = ?")) {
                stmt.setInt(1, invitedPersonId);
                stmt.setInt(2, reunionId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    response.put("success", false);
                    response.put("message", "'" + usernameToInvite + "' participe déjà à cette réunion.");
                    session.getBasicRemote().sendText(response.toString());
                    return;
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO participation (personne_id, reunion_id) VALUES (?, ?)")) {
                stmt.setInt(1, invitedPersonId);
                stmt.setInt(2, reunionId);
                stmt.executeUpdate();
            }

            if ("PRIVEE".equalsIgnoreCase(reunionType)) {
                try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO autorisation_reunion_privee (personne_id, reunion_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE personne_id=personne_id")) {
                    stmt.setInt(1, invitedPersonId);
                    stmt.setInt(2, reunionId);
                    stmt.executeUpdate();
                }
            }

            response.put("success", true);
            response.put("message", "'" + usernameToInvite + "' a été invité avec succès à la réunion.");
            session.getBasicRemote().sendText(response.toString());

        } catch (SQLException e) {
            System.err.println("Erreur SQL lors de l'invitation: " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Erreur serveur lors du traitement de l'invitation.");
            session.getBasicRemote().sendText(response.toString());
        }
    }

    private String rejoindreReunion(JSONObject data, Session session) {
        JSONObject reponseJson = new JSONObject();
        reponseJson.put("modele", "reunion");
        reponseJson.put("action", "reponseRejoindre");

        try {
            String codeReunion = data.optString("code");
            String participant = data.optString("participant");
            int userId = data.optInt("userId", -1);

            if (codeReunion.isEmpty()) {
                return genererReponseErreur("Code de réunion requis");
            }

            try (Connection conn = Database.getConnection()) {
                int reunionId = -1;

                try {
                    reunionId = Integer.parseInt(codeReunion);
                    try (PreparedStatement stmt = conn.prepareStatement("SELECT id FROM reunion WHERE id = ?")) {
                        stmt.setInt(1, reunionId);
                        ResultSet rs = stmt.executeQuery();
                        if (!rs.next()) {
                            reunionId = -1;
                        }
                    }
                } catch (NumberFormatException e) {
                    try (PreparedStatement stmt = conn.prepareStatement("SELECT id FROM reunion WHERE nom = ?")) {
                        stmt.setString(1, codeReunion);
                        ResultSet rs = stmt.executeQuery();
                        if (rs.next()) {
                            reunionId = rs.getInt("id");
                        }
                    }
                }

                if (reunionId == -1) {
                    return genererReponseErreur("Réunion non trouvée");
                }

                if (userId != -1) {
                    try (PreparedStatement stmt = conn.prepareStatement("INSERT IGNORE INTO participation (personne_id, reunion_id) VALUES (?, ?)")) {
                        stmt.setInt(1, userId);
                        stmt.setInt(2, reunionId);
                        stmt.executeUpdate();
                    }
                }

                reponseJson.put("statut", "succes");
                reponseJson.put("message", "Vous avez rejoint la réunion " + codeReunion);
                reponseJson.put("reunionId", reunionId);

                if (session != null && session.isOpen()) {
                    session.getUserProperties().put("reunionId", String.valueOf(reunionId));
                    // Also good to ensure userId is stored if available and consistent
                    if (data.has("userId")) {
                        session.getUserProperties().put("userId", String.valueOf(data.optInt("userId")));
                    }
                    System.out.println("Session " + session.getId() + " associated with reunionId: " + reunionId);
                }

            } catch (SQLException e) {
                System.err.println("Erreur SQL lors de la participation: " + e.getMessage());
                return genererReponseErreur("Erreur lors de la participation à la réunion");
            }

        } catch (Exception e) {
            System.err.println("Erreur lors de la participation: " + e.getMessage());
            return genererReponseErreur("Erreur lors de la participation à la réunion");
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
                        .put("nom", reunion.getNom())
                        .put("sujet", reunion.getSujet())
                        .put("debut", reunion.getDebut().toString())
                        .put("duree", reunion.getDuree())
                );
            } else {
                return genererReponseErreur("Réunion non trouvée");
            }

        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération des détails: " + e.getMessage());
            return genererReponseErreur("Erreur interne lors de la récupération des détails");
        }

        return reponseJson.toString();
    }

    private String quitterReunion(JSONObject data, Session session) {
        JSONObject reponseJson = new JSONObject();
        reponseJson.put("modele", "reunion");
        reponseJson.put("action", "reponseQuitter");

        try {
            String reunionIdStr = data.optString("reunionId"); // Renamed to avoid confusion
            int userId = data.optInt("userId", -1);

            if (reunionIdStr.isEmpty() || userId == -1) {
                return genererReponseErreur("ID de réunion et utilisateur requis");
            }

            // Remove from session first
            if (session != null && session.isOpen()) {
                session.getUserProperties().remove("reunionId");
                System.out.println("Session " + session.getId() + " disassociated from reunionId upon quit.");
            }

            try (Connection conn = Database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("DELETE FROM participation WHERE personne_id = ? AND reunion_id = ?")) {
                stmt.setInt(1, userId);
                stmt.setInt(2, Integer.parseInt(reunionIdStr)); // Use the string version for parsing
                stmt.executeUpdate();
            }

            reponseJson.put("statut", "succes");
            reponseJson.put("message", "Vous avez quitté la réunion");

        } catch (NumberFormatException e) {
            System.err.println("Erreur lors de la conversion de l'ID de réunion: " + e.getMessage());
            return genererReponseErreur("Format d'ID de réunion invalide");
        } catch (Exception e) {
            System.err.println("Erreur lors de la sortie de réunion: " + e.getMessage());
            return genererReponseErreur("Erreur lors de la sortie de réunion");
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