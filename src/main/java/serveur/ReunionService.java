package serveur;

import model.*;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.websocket.Session;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ReunionService implements WebSocketAction {

    @Override
    public void execute(JSONObject data, Session session) throws IOException {
        String action = data.optString("action");

        System.out.println("ReunionService - Action reçue: " + action);
        System.out.println("ReunionService - Données reçues: " + data.toString());

        String reponseStr = null;
        boolean actionEnvoieSaPropreReponse = false;

        try {
            switch (action) {
                case "creation":
                    reponseStr = creerReunion(data, session);
                    break;
                case "rejoindre":
                    reponseStr = rejoindreReunion(data, session);
                    break;
                case "details":
                    reponseStr = obtenirDetailsReunion(data);
                    break;
                case "modifier":
                    reponseStr = modifierReunion(data);
                    break;
                case "ouvrir":
                    reponseStr = ouvrirReunion(data);
                    break;
                case "cloturer":
                    reponseStr = cloturerReunion(data);
                    break;
                case "envoyerMessage":
                    envoyerMessage(data, session);
                    actionEnvoieSaPropreReponse = true;
                    break;
                case "demanderParole":
                    handleDemanderParole(data, session);
                    actionEnvoieSaPropreReponse = true;
                    break;
                case "gererDemandeParole":
                    handleGererDemandeParole(data, session);
                    actionEnvoieSaPropreReponse = true;
                    break;
                case "getDemandesParole":
                    envoyerDemandesParole(data, session);
                    actionEnvoieSaPropreReponse = true;
                    break;
                case "inviterMembre":
                    handleInviterMembre(data, session);
                    actionEnvoieSaPropreReponse = true;
                    break;
                case "quitterReunion":
                    reponseStr = quitterReunion(data, session);
                    break;
                case "getHistoriqueMessages":
                    envoyerHistoriqueMessages(data, session);
                    actionEnvoieSaPropreReponse = true;
                    break;
                case "getParticipants":
                    envoyerListeParticipants(data, session);
                    actionEnvoieSaPropreReponse = true;
                    break;
                case "getReunionsUtilisateur":
                    envoyerReunionsUtilisateur(data, session);
                    actionEnvoieSaPropreReponse = true;
                    break;
                case "getPendingInvitations":
                    envoyerInvitationsEnAttente(data, session);
                    actionEnvoieSaPropreReponse = true;
                    break;
                case "updateInvitationStatus":
                    mettreAJourStatutInvitation(data, session);
                    actionEnvoieSaPropreReponse = true;
                    break;
                case "autoriserAccesPrivee":
                    autoriserAccesReunionPrivee(data, session);
                    actionEnvoieSaPropreReponse = true;
                    break;
                case "supprimerReunion":
                    supprimerReunion(data, session);
                    actionEnvoieSaPropreReponse = true;
                default:
                    reponseStr = genererReponseErreur("Action inconnue '" + action + "' dans le modèle reunion").toString();
                    break;
            }

            if (!actionEnvoieSaPropreReponse && reponseStr != null && session.isOpen()) {
                session.getBasicRemote().sendText(reponseStr);
            }

        } catch (SQLException e) {
            System.err.println("Erreur SQL dans ReunionService pour action '" + action + "': " + e.getMessage());
            e.printStackTrace();
            if (session.isOpen()) {
                session.getBasicRemote().sendText(genererReponseErreur("Erreur serveur SQL: " + e.getMessage()).toString());
            }
        } catch (IOException e) {
            System.err.println("Erreur IO dans ReunionService pour action '" + action + "': " + e.getMessage());
            e.printStackTrace();
            if (session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(genererReponseErreur("Erreur serveur IO: " + e.getMessage()).toString());
                } catch (IOException ex) {
                    System.err.println("Impossible d'envoyer le message d'erreur IO: " + ex.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur inattendue dans ReunionService pour action '" + action + "': " + e.getMessage());
            e.printStackTrace();
            if (session.isOpen()) {
                session.getBasicRemote().sendText(genererReponseErreur("Erreur serveur inattendue: " + e.getMessage()).toString());
            }
        }
    }

    // === FONCTIONNALITÉS EXISTANTES AMÉLIORÉES ===

    private String creerReunion(JSONObject data, Session session) throws SQLException {
        JSONObject reponseJson = new JSONObject();
        reponseJson.put("modele", "reunion");
        reponseJson.put("action", "reponseCreation");

        String nom = data.optString("nom", "").trim();
        String sujet = data.optString("sujet", "").trim();
        String agenda = data.optString("agenda", "").trim();
        int idOrganisateur = data.optInt("idOrganisateur", -1);
        Integer idAnimateur = data.has("idAnimateur") && !data.isNull("idAnimateur") ? data.getInt("idAnimateur") : null;

        if (nom.isEmpty() || idOrganisateur == -1) {
            reponseJson.put("statut", "echec").put("message", "Nom et organisateur obligatoires");
            return reponseJson.toString();
        }

        String debutStr = data.optString("debut", "");
        LocalDateTime debut = debutStr.isEmpty() ? LocalDateTime.now().plusMinutes(5) : LocalDateTime.parse(debutStr);
        int duree = data.optInt("duree", 60);
        String typeStr = data.optString("type", "STANDARD").toUpperCase();
        Reunion.Type type = Reunion.Type.valueOf(typeStr);

        ReunionManager reunionManager = new ReunionManager();
        Reunion nouvelleReunion = reunionManager.planifierReunion(nom, sujet, agenda, debut, duree, type, idOrganisateur, idAnimateur);

        // Ajouter l'organisateur comme participant
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("INSERT INTO participation (personne_id, reunion_id) VALUES (?, ?)")) {
            pstmt.setInt(1, idOrganisateur);
            pstmt.setInt(2, nouvelleReunion.getId());
            pstmt.executeUpdate();
        }

        // Pour les réunions privées, autoriser l'organisateur
        if (type == Reunion.Type.PRIVEE) {
            try (Connection conn = Database.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement("INSERT INTO autorisation_reunion_privee (personne_id, reunion_id) VALUES (?, ?)")) {
                pstmt.setInt(1, idOrganisateur);
                pstmt.setInt(2, nouvelleReunion.getId());
                pstmt.executeUpdate();
            }
        }

        reponseJson.put("statut", "succes").put("message", "Réunion créée avec succès");
        JSONObject reunionData = new JSONObject();
        reunionData.put("id", nouvelleReunion.getId())
                   .put("nom", nouvelleReunion.getNom())
                   .put("sujet", nouvelleReunion.getSujet())
                   .put("agenda", nouvelleReunion.getAgenda())
                   .put("debut", nouvelleReunion.getDebut().toString())
                   .put("duree", nouvelleReunion.getDuree())
                   .put("type", nouvelleReunion.getType().toString())
                   .put("idOrganisateur", nouvelleReunion.getIdOrganisateur())
                   .put("idAnimateur", nouvelleReunion.getIdAnimateur())
                   .put("statut", "PLANIFIEE"); // Nouvelle réunion pas encore ouverte
        reponseJson.put("reunion", reunionData).put("autoJoin", true);
        return reponseJson.toString();
    }

    private String rejoindreReunion(JSONObject data, Session session) throws SQLException {
        JSONObject reponseJson = new JSONObject();
        reponseJson.put("modele", "reunion");
        reponseJson.put("action", "reponseRejoindre");

        String codeOuId = data.optString("code", "").trim();
        int userId = data.optInt("userId", -1);

        if (codeOuId.isEmpty()) {
            reponseJson.put("statut", "echec").put("message", "Code de réunion requis");
            return reponseJson.toString();
        }
        if (userId == -1) {
            reponseJson.put("statut", "echec").put("message", "ID utilisateur invalide pour rejoindre la réunion.");
            return reponseJson.toString();
        }

        int reunionId = -1;
        String nomReunionTrouve = null;
        int organisateurId = -1;
        String typeReunion = null;
        String statutReunion = null;

        try (Connection conn = Database.getConnection();
             PreparedStatement stmtFindReunion = prepareStatementForReunionLookup(conn, codeOuId)) {

            if (stmtFindReunion == null) {
                 reponseJson.put("statut", "echec").put("message", "Format de code/ID de réunion invalide.");
                 return reponseJson.toString();
            }

            try (ResultSet rs = stmtFindReunion.executeQuery()) {
                if (rs.next()) {
                    reunionId = rs.getInt("id");
                    nomReunionTrouve = rs.getString("nom");
                    organisateurId = rs.getInt("organisateur_id");
                    typeReunion = rs.getString("type");
                    statutReunion = rs.getString("statut") != null ? rs.getString("statut") : "PLANIFIEE";
                }
            }
        }

        if (reunionId == -1) {
            reponseJson.put("statut", "echec").put("message", "Réunion non trouvée: " + codeOuId);
            return reponseJson.toString();
        }

        /*
        // Vérifier les autorisations pour les réunions privées
        if ("PRIVEE".equals(typeReunion)) {
            AutorisationReunionPriveeManager autoManager = new AutorisationReunionPriveeManager();
            if (!autoManager.estAutorise(userId, reunionId)) {
                reponseJson.put("statut", "echec").put("message", "Accès refusé : réunion privée");
                return reponseJson.toString();
            }
        }
        */

        // Vérifier si la réunion est ouverte (sauf pour l'organisateur et l'animateur)
        if (!"OUVERTE".equals(statutReunion)) {
            // L'organisateur peut toujours entrer
            if (userId != organisateurId) {
                // Vérifier si c'est l'animateur
                try (Connection conn = Database.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement("SELECT animateur_id FROM reunion WHERE id = ?")) {
                    pstmt.setInt(1, reunionId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            Integer animateurId = rs.getObject("animateur_id") != null ? rs.getInt("animateur_id") : null;
                            if (animateurId == null || userId != animateurId) {
                                reponseJson.put("statut", "echec").put("message", "Réunion non ouverte. Seuls l'organisateur et l'animateur peuvent y accéder.");
                                return reponseJson.toString();
                            }
                        }
                    }
                }
            }
        }

        ParticipationManager participationManager = new ParticipationManager();
        participationManager.entrerDansReunion(userId, reunionId);

        reponseJson.put("statut", "succes");
        reponseJson.put("message", "Vous avez rejoint la réunion " + nomReunionTrouve);
        reponseJson.put("reunionId", reunionId);
        reponseJson.put("nomReunion", nomReunionTrouve);
        reponseJson.put("organisateurId", organisateurId);
        reponseJson.put("typeReunion", typeReunion);
        reponseJson.put("statutReunion", statutReunion);

        if (session != null && session.isOpen()) {
            session.getUserProperties().put("reunionId", String.valueOf(reunionId));
            session.getUserProperties().put("userId", String.valueOf(userId));
        }

        // Notifier les autres participants
        notifierParticipants(reunionId, userId, "userJoined");

        return reponseJson.toString();
    }

  private void supprimerReunion(JSONObject data, Session session) {
    try {
        String reponse = supprimerReunionLogic(data);

    } catch (Exception e) {
        e.printStackTrace();
        JSONObject erreurJson = new JSONObject();
        erreurJson.put("modele", "reunion");
        erreurJson.put("action", "reponseSuppression");
        erreurJson.put("statut", "echec");
        erreurJson.put("message", "Erreur serveur lors de la suppression");

    }
}

private String supprimerReunionLogic(JSONObject data) throws SQLException {
    JSONObject reponseJson = new JSONObject();
    reponseJson.put("modele", "reunion");
    reponseJson.put("action", "reponseSuppression");

    int reunionId = data.optInt("reunionId", -1);
    int utilisateurId = data.optInt("userId", -1);

    // Validation des données d'entrée
    if (reunionId == -1 || utilisateurId == -1) {
        reponseJson.put("statut", "echec").put("message", "Données manquantes pour la suppression");
        return reponseJson.toString();
    }

    // Vérifier que la réunion existe et que l'utilisateur est l'organisateur
    try (Connection conn = Database.getConnection();
         PreparedStatement checkStmt = conn.prepareStatement("SELECT organisateur_id, nom, statut FROM reunion WHERE id = ?")) {

        checkStmt.setInt(1, reunionId);
        try (ResultSet rs = checkStmt.executeQuery()) {
            if (!rs.next()) {
                reponseJson.put("statut", "echec").put("message", "Réunion non trouvée");
                return reponseJson.toString();
            }

            int organisateurId = rs.getInt("organisateur_id");
            String nomReunion = rs.getString("nom");
            String statutReunion = rs.getString("statut");

            // Vérifier les permissions
            if (utilisateurId != organisateurId) {
                reponseJson.put("statut", "echec").put("message", "Seul l'organisateur peut supprimer la réunion");
                return reponseJson.toString();
            }

            // Optionnel : empêcher la suppression de réunions ouvertes avec des participants actifs
            if ("OUVERTE".equals(statutReunion)) {
                // Vérifier s'il y a des participants connectés (autres que l'organisateur)
                String participantsConnectesSql =
                    "SELECT COUNT(*) FROM participation p " +
                    "INNER JOIN personne per ON p.personne_id = per.id " +
                    "WHERE p.reunion_id = ? AND per.connecte = TRUE AND per.id != ?";

                try (PreparedStatement participantsStmt = conn.prepareStatement(participantsConnectesSql)) {
                    participantsStmt.setInt(1, reunionId);
                    participantsStmt.setInt(2, utilisateurId);
                    try (ResultSet participantsRs = participantsStmt.executeQuery()) {
                        if (participantsRs.next() && participantsRs.getInt(1) > 0) {
                            reponseJson.put("statut", "echec")
                                     .put("message", "Impossible de supprimer une réunion ouverte avec des participants connectés");
                            return reponseJson.toString();
                        }
                    }
                }
            }

            // Procéder à la suppression
            String deleteSql = "DELETE FROM reunion WHERE id = ?";
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                deleteStmt.setInt(1, reunionId);
                int affectedRows = deleteStmt.executeUpdate();

                if (affectedRows > 0) {
                    reponseJson.put("statut", "succes")
                             .put("message", "La réunion '" + nomReunion + "' a été supprimée avec succès");

                    // TODO: Notifier tous les participants de la suppression si nécessaire
                    // notifierSuppression(reunionId, nomReunion);

                } else {
                    reponseJson.put("statut", "echec")
                             .put("message", "Erreur lors de la suppression de la réunion");
                }
            }
        }
    } catch (SQLException e) {
        e.printStackTrace();
        reponseJson.put("statut", "echec")
                 .put("message", "Erreur de base de données : " + e.getMessage());
    }

    return reponseJson.toString();
}



    // === NOUVELLES FONCTIONNALITÉS ===

    private String modifierReunion(JSONObject data) throws SQLException {
        JSONObject reponseJson = new JSONObject();
        reponseJson.put("modele", "reunion");
        reponseJson.put("action", "reponseModification");

        int reunionId = data.optInt("reunionId", -1);
        int utilisateurId = data.optInt("userId", -1);

        if (reunionId == -1 || utilisateurId == -1) {
            reponseJson.put("statut", "echec").put("message", "Données manquantes");
            return reponseJson.toString();
        }

        // Vérifier que l'utilisateur est l'organisateur
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("SELECT organisateur_id, statut FROM reunion WHERE id = ?")) {
            pstmt.setInt(1, reunionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    reponseJson.put("statut", "echec").put("message", "Réunion non trouvée");
                    return reponseJson.toString();
                }

                int organisateurId = rs.getInt("organisateur_id");
                String statut = rs.getString("statut");

                if (utilisateurId != organisateurId) {
                    reponseJson.put("statut", "echec").put("message", "Seul l'organisateur peut modifier la réunion");
                    return reponseJson.toString();
                }

                if ("OUVERTE".equals(statut) || "FERMEE".equals(statut)) {
                    reponseJson.put("statut", "echec").put("message", "Impossible de modifier une réunion ouverte ou fermée");
                    return reponseJson.toString();
                }
            }
        }


        // Effectuer la modification
        ReunionManager reunionManager = new ReunionManager();
        String nom = data.optString("nom");
        String sujet = data.optString("sujet");
        String agenda = data.optString("agenda");
        String debutStr = data.optString("debut");
        int duree = data.optInt("duree", -1);

        if (nom.isEmpty()) {
            reponseJson.put("statut", "echec").put("message", "Le nom de la réunion est obligatoire");
            return reponseJson.toString();
        }

        LocalDateTime debut = LocalDateTime.parse(debutStr);
        boolean modifiee = reunionManager.modifierReunion(reunionId, nom, sujet, agenda, debut, duree);

        if (modifiee) {
            reponseJson.put("statut", "succes").put("message", "Réunion modifiée avec succès");
        } else {
            reponseJson.put("statut", "echec").put("message", "Erreur lors de la modification");
        }

        return reponseJson.toString();
    }

    private String ouvrirReunion(JSONObject data) throws SQLException {
        JSONObject reponseJson = new JSONObject();
        reponseJson.put("modele", "reunion");
        reponseJson.put("action", "reponseOuverture");

        int reunionId = data.optInt("reunionId", -1);
        int utilisateurId = data.optInt("userId", -1);

        if (reunionId == -1 || utilisateurId == -1) {
            reponseJson.put("statut", "echec").put("message", "Données manquantes");
            return reponseJson.toString();
        }

        // Vérifier que l'utilisateur est l'animateur ou l'organisateur
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "SELECT organisateur_id, animateur_id, statut FROM reunion WHERE id = ?")) {
            pstmt.setInt(1, reunionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    reponseJson.put("statut", "echec").put("message", "Réunion non trouvée");
                    return reponseJson.toString();
                }

                int organisateurId = rs.getInt("organisateur_id");
                Integer animateurId = rs.getObject("animateur_id") != null ? rs.getInt("animateur_id") : null;
                String statut = rs.getString("statut");

                if (utilisateurId != organisateurId && (animateurId == null || utilisateurId != animateurId)) {
                    reponseJson.put("statut", "echec").put("message", "Seul l'organisateur ou l'animateur peut ouvrir la réunion");
                    return reponseJson.toString();
                }

                if ("OUVERTE".equals(statut)) {
                    reponseJson.put("statut", "echec").put("message", "La réunion est déjà ouverte");
                    return reponseJson.toString();
                }

                if ("FERMEE".equals(statut)) {
                    reponseJson.put("statut", "echec").put("message", "Impossible d'ouvrir une réunion fermée");
                    return reponseJson.toString();
                }
            }
        }

        // Ouvrir la réunion
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "UPDATE reunion SET statut = 'OUVERTE', heure_ouverture = NOW() WHERE id = ?")) {
            pstmt.setInt(1, reunionId);
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                reponseJson.put("statut", "succes").put("message", "Réunion ouverte avec succès");

                // Notifier tous les participants que la réunion est ouverte
                notifierTousParticipants(reunionId, "reunionOuverte", "La réunion vient d'être ouverte");
            } else {
                reponseJson.put("statut", "echec").put("message", "Erreur lors de l'ouverture");
            }
        }

        return reponseJson.toString();
    }

    private String cloturerReunion(JSONObject data) throws SQLException {
        JSONObject reponseJson = new JSONObject();
        reponseJson.put("modele", "reunion");
        reponseJson.put("action", "reponseCloture");

        int reunionId = data.optInt("reunionId", -1);
        int utilisateurId = data.optInt("userId", -1);

        if (reunionId == -1 || utilisateurId == -1) {
            reponseJson.put("statut", "echec").put("message", "Données manquantes");
            return reponseJson.toString();
        }

        // Vérifier que l'utilisateur est l'animateur ou l'organisateur
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "SELECT organisateur_id, animateur_id, statut FROM reunion WHERE id = ?")) {
            pstmt.setInt(1, reunionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    reponseJson.put("statut", "echec").put("message", "Réunion non trouvée");
                    return reponseJson.toString();
                }

                int organisateurId = rs.getInt("organisateur_id");
                Integer animateurId = rs.getObject("animateur_id") != null ? rs.getInt("animateur_id") : null;
                String statut = rs.getString("statut");

                if (utilisateurId != organisateurId && (animateurId == null || utilisateurId != animateurId)) {
                    reponseJson.put("statut", "echec").put("message", "Seul l'organisateur ou l'animateur peut clôturer la réunion");
                    return reponseJson.toString();
                }

                if (!"OUVERTE".equals(statut)) {
                    reponseJson.put("statut", "echec").put("message", "Seule une réunion ouverte peut être clôturée");
                    return reponseJson.toString();
                }
            }
        }

        // Clôturer la réunion
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "UPDATE reunion SET statut = 'FERMEE', heure_fermeture = NOW() WHERE id = ?")) {
            pstmt.setInt(1, reunionId);
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                reponseJson.put("statut", "succes").put("message", "Réunion clôturée avec succès");

                // Notifier tous les participants que la réunion est fermée
                notifierTousParticipants(reunionId, "reunionFermee", "La réunion vient d'être clôturée");
            } else {
                reponseJson.put("statut", "echec").put("message", "Erreur lors de la clôture");
            }
        }

        return reponseJson.toString();
    }

    private void handleDemanderParole(JSONObject data, Session session) throws IOException, SQLException {
        JSONObject response = new JSONObject();
        response.put("type", "demandeParoleResult");

        String reunionIdStr = data.optString("reunionId");
        int userId = data.optInt("userId", -1);

        if (reunionIdStr.isEmpty() || userId == -1) {
            response.put("success", false).put("message", "Données manquantes pour la demande de parole");
            session.getBasicRemote().sendText(response.toString());
            return;
        }

        int reunionId = Integer.parseInt(reunionIdStr);

        // Vérifier que la réunion est ouverte
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "SELECT statut, type FROM reunion WHERE id = ?")) {
            pstmt.setInt(1, reunionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    response.put("success", false).put("message", "Réunion non trouvée");
                    session.getBasicRemote().sendText(response.toString());
                    return;
                }

                String statut = rs.getString("statut");
                String type = rs.getString("type");

                if (!"OUVERTE".equals(statut)) {
                    response.put("success", false).put("message", "La réunion n'est pas ouverte");
                    session.getBasicRemote().sendText(response.toString());
                    return;
                }

                // Pour les réunions démocratiques, accorder automatiquement la parole
                if ("DEMOCRATIQUE".equals(type)) {
                    DemandeParoleManager demandeManager = new DemandeParoleManager();
                    DemandeParole demande = demandeManager.demanderParole(userId, reunionId);

                    if (demande != null) {
                        // Accorder automatiquement la parole en mode FIFO
                        demandeManager.accordParole(demande.getId(), userId); // L'utilisateur lui-même "accorde"

                        response.put("success", true).put("message", "Parole accordée automatiquement (réunion démocratique)");
                        session.getBasicRemote().sendText(response.toString());

                        // Notifier tous les participants
                        notifierParticipants(reunionId, userId, "paroleAccordee");
                        return;
                    }
                }
            }
        }

        // Pour les réunions standards et privées : demande normale
        DemandeParoleManager demandeManager = new DemandeParoleManager();
        try {
            DemandeParole demande = demandeManager.demanderParole(userId, reunionId);
            if (demande != null) {
                response.put("success", true).put("message", "Demande de parole enregistrée");

                // Notifier l'animateur de la nouvelle demande
                notifierAnimateur(reunionId, "nouvelleDemande", demande);
            } else {
                response.put("success", false).put("message", "Erreur lors de l'enregistrement de la demande");
            }
        } catch (SQLException e) {
            if (e.getMessage().contains("Duplicate entry")) {
                response.put("success", false).put("message", "Vous avez déjà une demande de parole en cours");
            } else {
                response.put("success", false).put("message", "Erreur serveur: " + e.getMessage());
            }
        }

        session.getBasicRemote().sendText(response.toString());
    }

    private void handleGererDemandeParole(JSONObject data, Session session) throws IOException, SQLException {
        JSONObject response = new JSONObject();
        response.put("type", "gestionParoleResult");

        int demandeId = data.optInt("demandeId", -1);
        String action = data.optString("actionParole"); // "accorder" ou "refuser"
        int animateurId = data.optInt("userId", -1);

        if (demandeId == -1 || action.isEmpty() || animateurId == -1) {
            response.put("success", false).put("message", "Données manquantes");
            session.getBasicRemote().sendText(response.toString());
            return;
        }

        // Vérifier que l'utilisateur est bien l'animateur
        DemandeParoleManager demandeManager = new DemandeParoleManager();
        DemandeParole demande = demandeManager.obtenirDemandeParoleParId(demandeId);

        if (demande == null) {
            response.put("success", false).put("message", "Demande de parole non trouvée");
            session.getBasicRemote().sendText(response.toString());
            return;
        }

        // Vérifier les permissions d'animateur
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "SELECT animateur_id, organisateur_id FROM reunion WHERE id = ?")) {
            pstmt.setInt(1, demande.getReunionId());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Integer animateurIdReunion = rs.getObject("animateur_id") != null ? rs.getInt("animateur_id") : null;
                    int organisateurId = rs.getInt("organisateur_id");

                    if (animateurId != organisateurId && (animateurIdReunion == null || animateurId != animateurIdReunion)) {
                        response.put("success", false).put("message", "Seul l'animateur ou l'organisateur peut gérer les demandes de parole");
                        session.getBasicRemote().sendText(response.toString());
                        return;
                    }
                }
            }
        }

        // Traiter la demande
        boolean resultat = false;
        String messageResult = "";

        if ("accorder".equals(action)) {
            resultat = demandeManager.accordParole(demandeId, animateurId);
            messageResult = resultat ? "Parole accordée" : "Erreur lors de l'accord de parole";

            if (resultat) {
                // Notifier le demandeur et les autres participants
                notifierParticipants(demande.getReunionId(), demande.getPersonneId(), "paroleAccordee");
            }
        } else if ("refuser".equals(action)) {
            resultat = demandeManager.refuserParole(demandeId, animateurId);
            messageResult = resultat ? "Parole refusée" : "Erreur lors du refus de parole";

            if (resultat) {
                // Notifier le demandeur
                notifierUtilisateur(demande.getPersonneId(), "paroleRefusee", "Votre demande de parole a été refusée");
            }
        }

        response.put("success", resultat).put("message", messageResult);
        session.getBasicRemote().sendText(response.toString());

        // Mettre à jour la liste des demandes pour l'animateur
        if (resultat) {
            envoyerDemandesParole(data, session);
        }
    }

    private void envoyerDemandesParole(JSONObject data, Session session) throws IOException, SQLException {
        JSONObject response = new JSONObject();
        response.put("type", "listeDemandesParole");

        String reunionIdStr = data.optString("reunionId");
        if (reunionIdStr.isEmpty()) {
            response.put("error", "ID réunion manquant");
            response.put("demandes", new JSONArray());
            session.getBasicRemote().sendText(response.toString());
            return;
        }

        int reunionId = Integer.parseInt(reunionIdStr);
        DemandeParoleManager demandeManager = new DemandeParoleManager();
        List<DemandeParole> demandes = demandeManager.obtenirDemandesEnAttente(reunionId);

        JSONArray demandesArray = new JSONArray();
        PersonneManager personneManager = new PersonneManager();

        for (DemandeParole demande : demandes) {
            JSONObject demandeJson = new JSONObject();
            demandeJson.put("id", demande.getId());
            demandeJson.put("personneId", demande.getPersonneId());
            demandeJson.put("heureDemande", demande.getHeureDemande().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            // Récupérer le nom du demandeur
            Personne demandeur = personneManager.obtenirPersonneParId(demande.getPersonneId());
            String nomDemandeur = (demandeur != null) ?
                (demandeur.getPrenom() + " " + demandeur.getNom()).trim() :
                "Utilisateur " + demande.getPersonneId();
            demandeJson.put("nomDemandeur", nomDemandeur);

            demandesArray.put(demandeJson);
        }

        response.put("demandes", demandesArray);
        session.getBasicRemote().sendText(response.toString());
    }

    private void autoriserAccesReunionPrivee(JSONObject data, Session session) throws IOException, SQLException {
        JSONObject response = new JSONObject();
        response.put("type", "autorisationResult");

        int reunionId = data.optInt("reunionId", -1);
        String loginAutorise = data.optString("loginAutorise");
        int organisateurId = data.optInt("userId", -1);

        if (reunionId == -1 || loginAutorise.isEmpty() || organisateurId == -1) {
            response.put("success", false).put("message", "Données manquantes");
            session.getBasicRemote().sendText(response.toString());
            return;
        }

        // Vérifier que l'utilisateur est l'organisateur et que la réunion est privée
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "SELECT organisateur_id, type FROM reunion WHERE id = ?")) {
            pstmt.setInt(1, reunionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    response.put("success", false).put("message", "Réunion non trouvée");
                    session.getBasicRemote().sendText(response.toString());
                    return;
                }

                int orgId = rs.getInt("organisateur_id");
                String type = rs.getString("type");

                if (organisateurId != orgId) {
                    response.put("success", false).put("message", "Seul l'organisateur peut autoriser l'accès");
                    session.getBasicRemote().sendText(response.toString());
                    return;
                }

                if (!"PRIVEE".equals(type)) {
                    response.put("success", false).put("message", "Cette réunion n'est pas privée");
                    session.getBasicRemote().sendText(response.toString());
                    return;
                }
            }
        }

        // Récupérer l'ID de la personne à autoriser
        PersonneManager personneManager = new PersonneManager();
        Personne personneAutorisee = personneManager.obtenirPersonneParLogin(loginAutorise);

        if (personneAutorisee == null) {
            response.put("success", false).put("message", "Utilisateur '" + loginAutorise + "' non trouvé");
            session.getBasicRemote().sendText(response.toString());
            return;
        }

        // Autoriser l'accès
        AutorisationReunionPriveeManager autoManager = new AutorisationReunionPriveeManager();
        boolean autorise = autoManager.autoriserAcces(personneAutorisee.getId(), reunionId);

        if (autorise) {
            response.put("success", true).put("message", "Accès autorisé pour " + loginAutorise);

            // Notifier l'utilisateur autorisé
            notifierUtilisateur(personneAutorisee.getId(), "autorisationPrivee",
                "Vous avez été autorisé à accéder à une réunion privée");
        } else {
            response.put("success", false).put("message", "Erreur lors de l'autorisation");
        }

        session.getBasicRemote().sendText(response.toString());
    }

    // === MÉTHODES UTILITAIRES ===

    private void notifierParticipants(int reunionId, int userId, String typeNotification) {
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "SELECT p.prenom, p.nom FROM personne p WHERE p.id = ?")) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                String nomUtilisateur = "Utilisateur";
                if (rs.next()) {
                    nomUtilisateur = (rs.getString("prenom") + " " + rs.getString("nom")).trim();
                }

                JSONObject notification = new JSONObject();
                notification.put("type", typeNotification);
                notification.put("reunionId", reunionId);
                notification.put("userId", userId);
                notification.put("username", nomUtilisateur);

                diffuserMessageReunion(reunionId, notification.toString());
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la notification des participants: " + e.getMessage());
        }
    }

    private void notifierTousParticipants(int reunionId, String type, String message) {
        JSONObject notification = new JSONObject();
        notification.put("type", type);
        notification.put("reunionId", reunionId);
        notification.put("message", message);

        diffuserMessageReunion(reunionId, notification.toString());
    }

    private void notifierAnimateur(int reunionId, String type, DemandeParole demande) {
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "SELECT animateur_id, organisateur_id FROM reunion WHERE id = ?")) {
            pstmt.setInt(1, reunionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Integer animateurId = rs.getObject("animateur_id") != null ? rs.getInt("animateur_id") : null;
                    int organisateurId = rs.getInt("organisateur_id");

                    JSONObject notification = new JSONObject();
                    notification.put("type", type);
                    notification.put("reunionId", reunionId);
                    notification.put("demandeId", demande.getId());
                    notification.put("demandeurId", demande.getPersonneId());

                    // Notifier l'animateur s'il existe, sinon l'organisateur
                    int destinataireId = (animateurId != null) ? animateurId : organisateurId;
                    notifierUtilisateur(destinataireId, type, notification.toString());
                }
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors de la notification de l'animateur: " + e.getMessage());
        }
    }

    private void notifierUtilisateur(int userId, String type, String message) {
        JSONObject notification = new JSONObject();
        notification.put("type", type);
        notification.put("message", message);

        for (Session s : ServeurWebSocket.getSessions()) {
            if (s.isOpen() && String.valueOf(userId).equals(s.getUserProperties().get("userId"))) {
                try {
                    s.getBasicRemote().sendText(notification.toString());
                } catch (IOException e) {
                    System.err.println("Erreur notification utilisateur " + userId + ": " + e.getMessage());
                }
                break;
            }
        }
    }

    private void diffuserMessageReunion(int reunionId, String message) {
        String reunionIdStr = String.valueOf(reunionId);
        for (Session s : ServeurWebSocket.getSessions()) {
            if (s.isOpen() && reunionIdStr.equals(s.getUserProperties().get("reunionId"))) {
                try {
                    s.getBasicRemote().sendText(message);
                } catch (IOException e) {
                    System.err.println("Erreur diffusion message réunion " + reunionId + ": " + e.getMessage());
                }
            }
        }
    }

    // === MÉTHODES EXISTANTES (inchangées) ===

    private PreparedStatement prepareStatementForReunionLookup(Connection conn, String codeOuId) throws SQLException {
        try {
            int idPotentiel = Integer.parseInt(codeOuId);
            PreparedStatement stmt = conn.prepareStatement("SELECT id, nom, organisateur_id, type, statut FROM reunion WHERE id = ?");
            stmt.setInt(1, idPotentiel);
            return stmt;
        } catch (NumberFormatException e) {
            PreparedStatement stmt = conn.prepareStatement("SELECT id, nom, organisateur_id, type, statut FROM reunion WHERE nom = ?");
            stmt.setString(1, codeOuId);
            return stmt;
        }
    }

    private String obtenirDetailsReunion(JSONObject data) throws SQLException {
        JSONObject reponseJson = new JSONObject();
        reponseJson.put("modele", "reunion").put("action", "reponseDetails");
        int reunionId = data.optInt("id");
        ReunionManager reunionManager = new ReunionManager();
        Reunion reunion = reunionManager.consulterDetailsReunion(reunionId);

        if (reunion != null) {
            reponseJson.put("statut", "succes");
            JSONObject rData = new JSONObject();
            rData.put("id", reunion.getId()).put("nom", reunion.getNom()).put("sujet", reunion.getSujet())
                 .put("debut", reunion.getDebut().toString()).put("duree", reunion.getDuree());
            reponseJson.put("reunion", rData);
        } else {
            reponseJson.put("statut", "echec").put("message", "Réunion non trouvée");
        }
        return reponseJson.toString();
    }

    private void envoyerMessage(JSONObject data, Session currentSession) throws IOException, SQLException {
        String reunionIdStr = data.optString("reunionId");
        String userIdStr = data.optString("userId");
        String contenu = data.optString("contenu");

        if (reunionIdStr.isEmpty() || userIdStr.isEmpty() || contenu.isEmpty()) {
            currentSession.getBasicRemote().sendText(genererReponseErreur("ID réunion/utilisateur ou contenu manquant.").toString());
            return;
        }
        int userId = Integer.parseInt(userIdStr);
        int reunionId = Integer.parseInt(reunionIdStr);
        String senderName = "Inconnu";

        try (Connection conn = Database.getConnection();
             PreparedStatement psUser = conn.prepareStatement("SELECT prenom, nom FROM personne WHERE id = ?")) {
            psUser.setInt(1, userId);
            try (ResultSet rsUser = psUser.executeQuery()) {
                if (rsUser.next()) {
                    senderName = (rsUser.getString("prenom") + " " + rsUser.getString("nom")).trim();
                    if (senderName.isEmpty()) senderName = "Utilisateur " + userId;
                } else {
                     currentSession.getBasicRemote().sendText(genererReponseErreur("Utilisateur expéditeur non trouvé.").toString());
                     return;
                }
            }
            try (PreparedStatement psMsg = conn.prepareStatement("INSERT INTO message (personne_id, reunion_id, contenu, heure_envoi) VALUES (?, ?, ?, NOW())")) {
                psMsg.setInt(1, userId);
                psMsg.setInt(2, reunionId);
                psMsg.setString(3, contenu);
                psMsg.executeUpdate();
            }
        }

        JSONObject broadcastJson = new JSONObject();
        broadcastJson.put("type", "newMessage").put("reunionId", reunionIdStr).put("sender", senderName)
                     .put("content", contenu).put("userId", userIdStr).put("timestamp", System.currentTimeMillis());

        for (Session s : ServeurWebSocket.getSessions()) {
            if (s.isOpen() && reunionIdStr.equals(s.getUserProperties().get("reunionId"))) {
                try { s.getBasicRemote().sendText(broadcastJson.toString()); }
                catch (IOException e) { System.err.println("Erreur diffusion msg à " + s.getId() + ": " + e.getMessage());}
            }
        }
    }

    private void handleInviterMembre(JSONObject data, Session session) throws IOException, SQLException {
        JSONObject response = new JSONObject();
        response.put("type", "invitationResult");
        String reunionIdStr = data.optString("reunionId");
        String usernameToInvite = data.optString("usernameToInvite");
        String inviterUserIdStr = (String) session.getUserProperties().get("userId");

        if (reunionIdStr.isEmpty() || usernameToInvite.isEmpty() || inviterUserIdStr == null) {
             response.put("success", false).put("message", "Données d'invitation manquantes.");
             session.getBasicRemote().sendText(response.toString()); return;
        }
        int reunionId = Integer.parseInt(reunionIdStr);
        int inviterUserId = Integer.parseInt(inviterUserIdStr);
        int invitedPersonId = -1;
        String reunionType = null;
        Reunion reunionDetails = null;

        try (Connection conn = Database.getConnection()) {
            ReunionManager reunionManager = new ReunionManager();
            reunionDetails = reunionManager.consulterDetailsReunion(reunionId);

            if (reunionDetails == null) {
                response.put("success", false).put("message", "Réunion non trouvée.");
                session.getBasicRemote().sendText(response.toString()); return;
            }
            reunionType = reunionDetails.getType().toString();
            if (inviterUserId != reunionDetails.getIdOrganisateur()) {
                response.put("success", false).put("message", "Seul l'organisateur peut inviter.");
                session.getBasicRemote().sendText(response.toString()); return;
            }

            PersonneManager personneManager = new PersonneManager();
            Personne personneAInviter = personneManager.obtenirPersonneParLogin(usernameToInvite);
            if (personneAInviter == null) {
                response.put("success", false).put("message", "Utilisateur '" + usernameToInvite + "' non trouvé.");
                session.getBasicRemote().sendText(response.toString()); return;
            }
            invitedPersonId = personneAInviter.getId();

            ParticipationManager participationManager = new ParticipationManager();
            if (participationManager.estParticipant(invitedPersonId, reunionId)) {
                response.put("success", false).put("message", "'" + usernameToInvite + "' participe déjà.");
                session.getBasicRemote().sendText(response.toString()); return;
            }

            participationManager.entrerDansReunion(invitedPersonId, reunionId);
            if("PRIVEE".equalsIgnoreCase(reunionType)){
                try(PreparedStatement stmtAuth = conn.prepareStatement("INSERT INTO autorisation_reunion_privee (personne_id,reunion_id) VALUES (?,?) ON DUPLICATE KEY UPDATE personne_id=personne_id")){
                    stmtAuth.setInt(1,invitedPersonId); stmtAuth.setInt(2,reunionId); stmtAuth.executeUpdate();
                }
            }

            // Insertion dans la table invitation_reunion
            String sqlInsertInvitation = "INSERT INTO invitation_reunion (reunion_id, personne_invitee_id, inviteur_id, statut) VALUES (?, ?, ?, 'EN_ATTENTE') ON DUPLICATE KEY UPDATE statut='EN_ATTENTE', date_invitation=NOW(), inviteur_id=?";
            try (PreparedStatement pstmtInvite = conn.prepareStatement(sqlInsertInvitation)) {
                pstmtInvite.setInt(1, reunionId);
                pstmtInvite.setInt(2, invitedPersonId);
                pstmtInvite.setInt(3, inviterUserId);
                pstmtInvite.setInt(4, inviterUserId);
                pstmtInvite.executeUpdate();
            }

            response.put("success", true).put("message", "'" + usernameToInvite + "' a été invité(e) avec succès.");
            session.getBasicRemote().sendText(response.toString());

            // NOTIFICATION À L'UTILISATEUR INVITÉ
            Personne inviterDetails = personneManager.obtenirPersonneParId(inviterUserId);
            String inviterNomComplet = (inviterDetails != null) ? (inviterDetails.getPrenom() + " " + inviterDetails.getNom()).trim() : "Quelqu'un";
            String nomReunionPourInvite = reunionDetails.getNom();

            JSONObject notificationInvite = new JSONObject();
            notificationInvite.put("type", "nouvelleInvitation")
                              .put("reunionId", reunionId)
                              .put("nomReunion", nomReunionPourInvite)
                              .put("invitePar", inviterNomComplet)
                              .put("dateReunion", reunionDetails.getDebut().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                              .put("message", inviterNomComplet + " vous a invité(e) à la réunion : " + nomReunionPourInvite);

            for (Session s : ServeurWebSocket.getSessions()) {
                if (s.isOpen() && String.valueOf(invitedPersonId).equals(s.getUserProperties().get("userId"))) {
                    try { s.getBasicRemote().sendText(notificationInvite.toString()); }
                    catch (IOException e) { System.err.println("Erreur notif invite à " + invitedPersonId + ": " + e.getMessage());}
                    break;
                }
            }
        }
    }

    private String quitterReunion(JSONObject data, Session session) throws SQLException {
        JSONObject reponseJson = new JSONObject().put("modele", "reunion").put("action", "reponseQuitter");
        String reunionIdStr = data.optString("reunionId");
        int userId = data.optInt("userId", -1);

        if (reunionIdStr.isEmpty() || userId == -1) {
            return reponseJson.put("statut", "echec").put("message", "Données manquantes.").toString();
        }
        if (session != null && session.isOpen()) {
            session.getUserProperties().remove("reunionId");
        }
        ParticipationManager participationManager = new ParticipationManager();
        boolean aQuitte = participationManager.sortirDeReunion(userId, Integer.parseInt(reunionIdStr));
        if (aQuitte) {
            return reponseJson.put("statut", "succes").put("message", "Vous avez quitté la réunion.").toString();
        } else {
            return reponseJson.put("statut", "echec").put("message", "Sortie de réunion échouée (participation non trouvée?).").toString();
        }
    }

    private void envoyerHistoriqueMessages(JSONObject data, Session session) throws IOException, SQLException {
        String reunionIdStr = data.optString("reunionId");
        JSONObject responseJson = new JSONObject().put("type", "historiqueMessages").put("reunionId", reunionIdStr);
        JSONArray messagesJsonArray = new JSONArray();

        if (reunionIdStr.isEmpty()) {
            responseJson.put("error", "ID réunion manquant").put("messages", messagesJsonArray);
            session.getBasicRemote().sendText(responseJson.toString()); return;
        }
        try {
            int reunionId = Integer.parseInt(reunionIdStr);
            MessageManager messageManager = new MessageManager();
            List<Message> messages = messageManager.obtenirMessagesReunion(reunionId);
            PersonneManager personneManager = new PersonneManager();

            for (Message msg : messages) {
                JSONObject msgJson = new JSONObject();
                msgJson.put("userId", msg.getIdPersonne());
                msgJson.put("content", msg.getContenu());
                Personne sender = personneManager.obtenirPersonneParId(msg.getIdPersonne());
                String senderName = (sender != null) ? (sender.getPrenom() + " " + sender.getNom()).trim() : "Inconnu";
                if (sender != null && senderName.isEmpty()) senderName = "Utilisateur " + sender.getId();
                msgJson.put("sender", senderName);
                if (msg.getHeureEnvoi() != null) {
                    msgJson.put("timestamp", msg.getHeureEnvoi().toInstant(ZoneOffset.UTC).toEpochMilli());
                }
                messagesJsonArray.put(msgJson);
            }
            responseJson.put("messages", messagesJsonArray);
        } catch (NumberFormatException e) {
            responseJson.put("error", "Format ID réunion invalide.").put("messages", messagesJsonArray);
        } catch (SQLException e) {
            System.err.println("SQL Error in envoyerHistoriqueMessages for reunion " + reunionIdStr + ": " + e.getMessage());
            responseJson.put("error", "Erreur SQL (historique).").put("messages", messagesJsonArray);
        }
        session.getBasicRemote().sendText(responseJson.toString());
    }

    private void envoyerListeParticipants(JSONObject data, Session session) throws IOException, SQLException {
        String reunionIdStr = data.optString("reunionId");
        JSONObject responseJson = new JSONObject().put("type", "listeParticipants").put("reunionId", reunionIdStr);
        JSONArray participantsJsonArray = new JSONArray();

        if (reunionIdStr.isEmpty()) {
            responseJson.put("error", "ID réunion manquant").put("participants", participantsJsonArray);
            session.getBasicRemote().sendText(responseJson.toString()); return;
        }
        try {
            int reunionId = Integer.parseInt(reunionIdStr);
            ParticipationManager participationManager = new ParticipationManager();
            List<Personne> participants = participationManager.obtenirParticipants(reunionId);
            for (Personne personne : participants) {
                JSONObject pJson = new JSONObject();
                pJson.put("id", personne.getId());
                String nomComplet = (personne.getPrenom() + " " + personne.getNom()).trim();
                if (nomComplet.isEmpty()) nomComplet = "Utilisateur " + personne.getId();
                pJson.put("nom", nomComplet);
                participantsJsonArray.put(pJson);
            }
            responseJson.put("participants", participantsJsonArray);
        } catch (NumberFormatException e) {
            responseJson.put("error", "Format ID réunion invalide.").put("participants", participantsJsonArray);
        } catch (SQLException e) {
            System.err.println("SQL Error in envoyerListeParticipants for reunion " + reunionIdStr + ": " + e.getMessage());
            responseJson.put("error", "Erreur SQL (participants).").put("participants", participantsJsonArray);
        }
        session.getBasicRemote().sendText(responseJson.toString());
    }

    private void envoyerReunionsUtilisateur(JSONObject data, Session session) throws IOException, SQLException {
        JSONObject responseJson = new JSONObject();
        responseJson.put("modele", "reunion");
        responseJson.put("action", "reponseGetReunionsUtilisateur");

        int userId = data.optInt("userId", -1);
        if (userId == -1) {
            responseJson.put("statut", "echec").put("message", "ID utilisateur manquant.");
            responseJson.put("reunions", new JSONArray());
            session.getBasicRemote().sendText(responseJson.toString());
            return;
        }
        JSONArray reunionsArray = new JSONArray();
        try (Connection conn = Database.getConnection()){
            String sql = "SELECT DISTINCT r.id, r.nom, r.sujet, r.agenda, r.debut, r.duree, r.type, r.organisateur_id, r.animateur_id, r.statut " +
                         "FROM reunion r LEFT JOIN participation p ON r.id = p.reunion_id " +
                         "WHERE r.organisateur_id = ? OR p.personne_id = ? " +
                         "ORDER BY r.debut DESC";
            try(PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, userId);
                pstmt.setInt(2, userId);
                try(ResultSet rs = pstmt.executeQuery()) {
                    while(rs.next()) {
                        JSONObject rJson = new JSONObject();
                        rJson.put("id", rs.getInt("id"));
                        rJson.put("nom", rs.getString("nom"));
                        rJson.put("sujet", rs.getString("sujet") == null ? "" : rs.getString("sujet"));
                        rJson.put("debut", rs.getTimestamp("debut").toLocalDateTime().toString());
                        rJson.put("duree", rs.getInt("duree"));
                        rJson.put("type", rs.getString("type"));
                        rJson.put("idOrganisateur", rs.getInt("organisateur_id"));
                        rJson.put("idAnimateur", rs.getObject("animateur_id") == null ? JSONObject.NULL : rs.getInt("animateur_id"));
                        rJson.put("statut", rs.getString("statut") != null ? rs.getString("statut") : "PLANIFIEE");
                        reunionsArray.put(rJson);
                    }
                }
            }
            responseJson.put("statut", "succes");
            responseJson.put("reunions", reunionsArray);
        } catch (SQLException e) {
            System.err.println("SQL Error in envoyerReunionsUtilisateur for user " + userId + ": " + e.getMessage());
            responseJson.put("statut", "echec");
            responseJson.put("message", "Erreur SQL (réunions).");
            responseJson.put("reunions", new JSONArray());
        }
        session.getBasicRemote().sendText(responseJson.toString());
    }

    private void envoyerInvitationsEnAttente(JSONObject data, Session session) throws IOException, SQLException {
        JSONObject responseJson = new JSONObject();
        responseJson.put("type", "listeInvitationsEnAttente");

        int userId = data.optInt("userId", -1);
        if (userId == -1) {
            responseJson.put("statut", "echec").put("message", "ID utilisateur manquant.");
            responseJson.put("invitations", new JSONArray());
            session.getBasicRemote().sendText(responseJson.toString());
            return;
        }

        JSONArray invitationsArray = new JSONArray();
        String sql = "SELECT i.id as invitation_id, i.reunion_id, r.nom as nom_reunion, r.debut as date_reunion, " +
                     "p_inviteur.prenom as inviteur_prenom, p_inviteur.nom as inviteur_nom " +
                     "FROM invitation_reunion i " +
                     "JOIN reunion r ON i.reunion_id = r.id " +
                     "JOIN personne p_inviteur ON i.inviteur_id = p_inviteur.id " +
                     "WHERE i.personne_invitee_id = ? AND i.statut = 'EN_ATTENTE' ORDER BY i.date_invitation DESC";

        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    JSONObject invJson = new JSONObject();
                    invJson.put("invitationId", rs.getInt("invitation_id"));
                    invJson.put("reunionId", rs.getInt("reunion_id"));
                    invJson.put("nomReunion", rs.getString("nom_reunion"));
                    Timestamp ts = rs.getTimestamp("date_reunion");
                    invJson.put("dateReunion", ts != null ? ts.toLocalDateTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "Date non spécifiée");
                    String inviteurNom = (rs.getString("inviteur_prenom") + " " + rs.getString("inviteur_nom")).trim();
                    invJson.put("invitePar", inviteurNom);
                    invitationsArray.put(invJson);
                }
            }
            responseJson.put("statut", "succes");
            responseJson.put("invitations", invitationsArray);
        } catch (SQLException e) {
            System.err.println("Erreur SQL lors de la récupération des invitations pour l'utilisateur " + userId + ": " + e.getMessage());
            e.printStackTrace();
            responseJson.put("statut", "echec");
            responseJson.put("message", "Erreur serveur SQL lors de la récupération des invitations.");
            responseJson.put("invitations", new JSONArray());
        }
        session.getBasicRemote().sendText(responseJson.toString());
    }

    private void mettreAJourStatutInvitation(JSONObject data, Session session) throws IOException, SQLException {
        JSONObject responseJson = new JSONObject();
        responseJson.put("type", "updateInvitationStatusResponse");

        int invitationId = data.optInt("invitationId", -1);
        String newStatusStr = data.optString("newStatus");
        int userId = data.optInt("userId", -1);

        if (invitationId == -1 || newStatusStr.isEmpty() || userId == -1) {
            responseJson.put("success", false).put("message", "Données manquantes pour mettre à jour l'invitation.");
            session.getBasicRemote().sendText(responseJson.toString());
            return;
        }

        // Valider le statut
        try {
             if (!("ACCEPTEE".equals(newStatusStr) || "REFUSEE".equals(newStatusStr))) {
                 throw new IllegalArgumentException("Statut invalide");
             }
        } catch (IllegalArgumentException e) {
             responseJson.put("success", false).put("message", "Statut d'invitation invalide: " + newStatusStr);
             session.getBasicRemote().sendText(responseJson.toString());
             return;
        }

        String sql = "UPDATE invitation_reunion SET statut = ? WHERE id = ? AND personne_invitee_id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newStatusStr);
            pstmt.setInt(2, invitationId);
            pstmt.setInt(3, userId);

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                responseJson.put("success", true).put("message", "Statut de l'invitation mis à jour avec succès en " + newStatusStr + ".");

                // Si acceptée, ajouter à la table participation si ce n'est pas déjà fait
                if ("ACCEPTEE".equals(newStatusStr)) {
                    int reunionId = -1;
                    try (PreparedStatement stmtGetReunionId = conn.prepareStatement("SELECT reunion_id FROM invitation_reunion WHERE id = ?")) {
                        stmtGetReunionId.setInt(1, invitationId);
                        try(ResultSet rs = stmtGetReunionId.executeQuery()){
                            if(rs.next()) reunionId = rs.getInt("reunion_id");
                        }
                    }
                    if (reunionId != -1) {
                        ParticipationManager pm = new ParticipationManager();
                        pm.entrerDansReunion(userId, reunionId);
                    }
                }
            } else {
                responseJson.put("success", false).put("message", "Impossible de mettre à jour l'invitation (non trouvée ou non autorisée).");
            }
        } catch (SQLException e) {
            System.err.println("Erreur SQL lors de la mise à jour du statut de l'invitation " + invitationId + ": " + e.getMessage());
            e.printStackTrace();
            responseJson.put("success", false).put("message", "Erreur serveur SQL lors de la mise à jour du statut.");
        }
        session.getBasicRemote().sendText(responseJson.toString());
    }

    private JSONObject genererReponseErreur(String message) {
        JSONObject errorJson = new JSONObject();
        errorJson.put("type", "error");
        errorJson.put("statut", "echec");
        errorJson.put("message", message);
        return errorJson;
    }
}