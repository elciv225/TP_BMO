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
                envoyerMessage(data, session);
                return; // Pas de réponse directe, diffusion seulement
            case "inviterMembre":
                handleInviterMembre(data, session);
                return; // Réponse envoyée dans la méthode
            case "quitterReunion":
                reponse = quitterReunion(data);
                break;
            default:
                reponse = genererReponseErreur("Action inconnue '" + action + "' dans le modèle reunion");
                break;
        }

        // Envoyer la réponse au client
        if (reponse != null) {
            session.getBasicRemote().sendText(reponse);
        }
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

        // Validation des paramètres
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
            // Récupérer les détails de la réunion
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

            // Vérifier les permissions (seul l'organisateur peut inviter)
            int inviterUserId = -1;
            if (inviterUserIdStr != null) {
                try {
                    inviterUserId = Integer.parseInt(inviterUserIdStr);
                } catch (NumberFormatException e) {
                    // Ignorer, on utilisera -1
                }
            }

            if (inviterUserId != organisateurId) {
                response.put("success", false);
                response.put("message", "Seul l'organisateur peut inviter des membres à cette réunion.");
                session.getBasicRemote().sendText(response.toString());
                return;
            }

            // Récupérer l'ID de la personne à inviter
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

            // Vérifier si déjà participant
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

            // Ajouter à la table de participation
            try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO participation (personne_id, reunion_id) VALUES (?, ?)")) {
                stmt.setInt(1, invitedPersonId);
                stmt.setInt(2, reunionId);
                stmt.executeUpdate();
            }

            // Si réunion privée, ajouter aux autorisations
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

    /**
     * Gère l'envoi de messages dans une réunion
     */
    private void envoyerMessage(JSONObject data, Session currentSession) throws IOException {
        String reunionId = data.optString("reunionId");
        String userIdStr = data.optString("userId");
        String contenu = data.optString("contenu");

        if (reunionId.isEmpty() || userIdStr.isEmpty() || contenu.isEmpty()) {
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("type", "error");
            errorResponse.put("message", "ID de réunion, ID utilisateur et contenu requis.");
            currentSession.getBasicRemote().sendText(errorResponse.toString());
            return;
        }

        int userId;
        try {
            userId = Integer.parseInt(userIdStr);
        } catch (NumberFormatException e) {
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("type", "error");
            errorResponse.put("message", "Format d'ID utilisateur invalide.");
            currentSession.getBasicRemote().sendText(errorResponse.toString());
            return;
        }

        String senderName = "Inconnu";

        try (Connection conn = Database.getConnection()) {
            // Sauvegarder le message en base
            String sqlInsert = "INSERT INTO message (personne_id, reunion_id, contenu, heure_envoi) VALUES (?, ?, ?, NOW())";
            try (PreparedStatement pstmtInsert = conn.prepareStatement(sqlInsert)) {
                pstmtInsert.setInt(1, userId);
                try {
                    pstmtInsert.setInt(2, Integer.parseInt(reunionId));
                } catch (NumberFormatException e) {
                    JSONObject errorResponse = new JSONObject();
                    errorResponse.put("type", "error");
                    errorResponse.put("message", "Format d'ID de réunion invalide.");
                    currentSession.getBasicRemote().sendText(errorResponse.toString());
                    return;
                }
                pstmtInsert.setString(3, contenu);
                pstmtInsert.executeUpdate();
            }

            // Récupérer le nom de l'expéditeur
            String sqlSelectSender = "SELECT CONCAT(nom, ' ', prenom) as nom_complet FROM personne WHERE id = ?";
            try (PreparedStatement pstmtSelect = conn.prepareStatement(sqlSelectSender)) {
                pstmtSelect.setInt(1, userId);
                ResultSet rs = pstmtSelect.executeQuery();
                if (rs.next()) {
                    senderName = rs.getString("nom_complet");
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

        Set<Session> allSessions = ServeurWebSocket.getSessions();
        for (Session s : allSessions) {
            if (s.isOpen()) {
                String sessionReunionId = (String) s.getUserProperties().get("reunionId");
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

    /**
     * Crée une nouvelle réunion
     */
    private String creerReunion(JSONObject data) {
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

            reponseJson.put("statut", "succes");
            reponseJson.put("message", "Réunion créée avec succès");
            reponseJson.put("reunion", new JSONObject()
                    .put("id", nouvelleReunion.getId())
                    .put("nom", nouvelleReunion.getNom())
                    .put("sujet", nouvelleReunion.getSujet())
                    .put("agenda", nouvelleReunion.getAgenda())
                    .put("debut", nouvelleReunion.getDebut().toString())
                    .put("duree", nouvelleReunion.getDuree())
                    .put("type", nouvelleReunion.getType().toString())
                    .put("idOrganisateur", nouvelleReunion.getIdOrganisateur())
            );

        } catch (SQLException e) {
            System.err.println("Erreur SQL lors de la création de réunion: " + e.getMessage());
            reponseJson.put("statut", "echec");
            reponseJson.put("message", "Erreur interne lors de la création de réunion");
        } catch (Exception e) {
            System.err.println("Erreur lors de la création de réunion: " + e.getMessage());
            reponseJson.put("statut", "echec");
            reponseJson.put("message", "Erreur inattendue lors de la création de réunion");
        }

        return reponseJson.toString();
    }

    /**
     * Gère la demande de participation à une réunion
     */
    private String rejoindreReunion(JSONObject data) {
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

            // Pour simplifier, on considère que le code est soit l'ID soit le nom de la réunion
            try (Connection conn = Database.getConnection()) {
                int reunionId = -1;

                // Essayer de trouver par ID d'abord
                try {
                    reunionId = Integer.parseInt(codeReunion);
                    // Vérifier que la réunion existe
                    try (PreparedStatement stmt = conn.prepareStatement("SELECT id FROM reunion WHERE id = ?")) {
                        stmt.setInt(1, reunionId);
                        ResultSet rs = stmt.executeQuery();
                        if (!rs.next()) {
                            reunionId = -1;
                        }
                    }
                } catch (NumberFormatException e) {
                    // Ce n'est pas un ID, chercher par nom
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

                // Ajouter l'utilisateur à la participation si pas déjà présent
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

    /**
     * Récupère les détails d'une réunion
     */
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

    /**
     * Gère la sortie d'une réunion
     */
    private String quitterReunion(JSONObject data) {
        JSONObject reponseJson = new JSONObject();
        reponseJson.put("modele", "reunion");
        reponseJson.put("action", "reponseQuitter");

        try {
            String reunionId = data.optString("reunionId");
            int userId = data.optInt("userId", -1);

            if (reunionId.isEmpty() || userId == -1) {
                return genererReponseErreur("ID de réunion et utilisateur requis");
            }

            // Supprimer de la participation
            try (Connection conn = Database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("DELETE FROM participation WHERE personne_id = ? AND reunion_id = ?")) {
                stmt.setInt(1, userId);
                stmt.setInt(2, Integer.parseInt(reunionId));
                stmt.executeUpdate();
            }

            reponseJson.put("statut", "succes");
            reponseJson.put("message", "Vous avez quitté la réunion");

        } catch (Exception e) {
            System.err.println("Erreur lors de la sortie de réunion: " + e.getMessage());
            return genererReponseErreur("Erreur lors de la sortie de réunion");
        }

        return reponseJson.toString();
    }

    /**
     * Génère une réponse d'erreur standard
     */
    private String genererReponseErreur(String message) {
        JSONObject reponseJson = new JSONObject();
        reponseJson.put("statut", "echec");
        reponseJson.put("message", message);
        return reponseJson.toString();
    }
}