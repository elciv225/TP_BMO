package serveur;

import model.Message;
import model.MessageManager;
import model.ParticipationManager;
import model.Personne;
import model.PersonneManager;
import model.Reunion;
import model.ReunionManager;
import model.DemandeParole;
import model.DemandeParoleManager;
import model.AutorisationReunionPriveeManager; // Import manquait
import org.json.JSONArray;
import org.json.JSONObject;

import javax.websocket.Session;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp; // Nécessaire pour rs.getTimestamp
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
                case "envoyerMessage":
                    envoyerMessage(data, session);
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
                case "ouvrirReunion":
                    reponseStr = ouvrirReunionAction(data, session);
                    // actionEnvoieSaPropreReponse = true; // ouvrirReunionAction will send its own response and broadcast
                    break;
                case "cloturerReunion":
                    reponseStr = cloturerReunionAction(data, session);
                    // actionEnvoieSaPropreReponse = true; // cloturerReunionAction will send its own response and broadcast
                    break;
                case "modifierReunion":
                    // modifierReunionAction will send its own response and broadcast
                    modifierReunionAction(data, session);
                    actionEnvoieSaPropreReponse = true;
                    break;
                case "definirAnimateur":
                    definirAnimateurAction(data, session);
                    actionEnvoieSaPropreReponse = true;
                    break;
                case "demanderParole":
                    demanderParoleAction(data, session);
                    actionEnvoieSaPropreReponse = true;
                    break;
                case "accorderParole":
                    accorderParoleAction(data, session);
                    actionEnvoieSaPropreReponse = true;
                    break;
                case "refuserParole":
                    refuserParoleAction(data, session);
                    actionEnvoieSaPropreReponse = true;
                    break;
                case "cederParole":
                    cederParoleAction(data, session);
                    actionEnvoieSaPropreReponse = true;
                    break;
                case "getDemandesParole":
                    getDemandesParoleAction(data, session);
                    actionEnvoieSaPropreReponse = true;
                    break;
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

    private String creerReunion(JSONObject data, Session session) throws SQLException {
        JSONObject reponseJson = new JSONObject();
        reponseJson.put("modele", "reunion");
        reponseJson.put("action", "reponseCreation");

        String nom = data.optString("nom", "").trim();
        String sujet = data.optString("sujet", "").trim();
        String agenda = data.optString("agenda", "").trim();
        int idOrganisateur = data.optInt("idOrganisateur", -1);

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
        Reunion nouvelleReunion = reunionManager.planifierReunion(nom, sujet, agenda, debut, duree, type, idOrganisateur, null); //

        try (Connection conn = Database.getConnection(); //
             PreparedStatement pstmt = conn.prepareStatement("INSERT INTO participation (personne_id, reunion_id) VALUES (?, ?)")) {
            pstmt.setInt(1, idOrganisateur);
            pstmt.setInt(2, nouvelleReunion.getId());
            pstmt.executeUpdate();
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
                   .put("statutReunion", nouvelleReunion.getStatutReunion().toString()); // Ajout du statut
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
            reponseJson.put("statut", "echec").put("message", "Code de réunion requis"); //
            return reponseJson.toString();
        }
        if (userId == -1) {
            reponseJson.put("statut", "echec").put("message", "ID utilisateur invalide pour rejoindre la réunion.");
            return reponseJson.toString();
        }

        ReunionManager reunionManager = new ReunionManager();
        Reunion reunion = null;
        int reunionId = -1;

        try {
            reunionId = Integer.parseInt(codeOuId);
            reunion = reunionManager.consulterDetailsReunion(reunionId);
        } catch (NumberFormatException e) {
            reunion = reunionManager.rechercherReunionParNom(codeOuId);
            if (reunion != null) {
                reunionId = reunion.getId();
            }
        }

        if (reunion == null) {
            reponseJson.put("statut", "echec").put("message", "Réunion non trouvée: " + codeOuId);
            return reponseJson.toString();
        }

        // --- Contrôle d'accès pour les réunions PRIVEE ---
        if (reunion.getType() == Reunion.Type.PRIVEE) {
            boolean isUserOrganizer = (userId == reunion.getIdOrganisateur());
            boolean isUserAnimator = (reunion.getIdAnimateur() != null && userId == reunion.getIdAnimateur());

            if (!isUserOrganizer && !isUserAnimator) { // Si l'utilisateur n'est ni organisateur ni animateur
                AutorisationReunionPriveeManager autorisationManager = new AutorisationReunionPriveeManager();
                if (!autorisationManager.estAutorise(userId, reunion.getId())) {
                    reponseJson.put("statut", "echec").put("message", "Accès refusé. Cette réunion est privée et vous n'êtes pas sur la liste des participants autorisés.");
                    return reponseJson.toString();
                }
            }
        }

        // Vérifier le statut de la réunion (si elle est ouverte)
        // Cette vérification doit permettre à l'organisateur/animateur de rejoindre même si pas encore OUVERTE pour pouvoir l'ouvrir
        if (reunion.getStatutReunion() != Reunion.StatutReunion.OUVERTE) {
            boolean isOrganizer = (userId == reunion.getIdOrganisateur()); // Répétition, mais OK pour clarté ici
            boolean isAnimator = (reunion.getIdAnimateur() != null && userId == reunion.getIdAnimateur()); // Répétition
            if (!isOrganizer && !isAnimator) {
                reponseJson.put("statut", "echec").put("message", "La réunion n'est pas encore ouverte.");
                return reponseJson.toString();
            }
            // Si c'est l'organisateur ou l'animateur, on le laisse rejoindre même si pas OUVERTE (pour qu'il puisse l'ouvrir)
        }

        ParticipationManager participationManager = new ParticipationManager();
        participationManager.entrerDansReunion(userId, reunion.getId());

        reponseJson.put("statut", "succes");
        reponseJson.put("message", "Vous avez rejoint la réunion " + reunion.getNom());
        reponseJson.put("reunionId", reunion.getId());
        reponseJson.put("nomReunion", reunion.getNom());
        reponseJson.put("organisateurId", reunion.getIdOrganisateur());
        reponseJson.put("statutReunion", reunion.getStatutReunion().toString()); // Ajout du statut dans la réponse

        if (session != null && session.isOpen()) {
            session.getUserProperties().put("reunionId", String.valueOf(reunion.getId()));
            session.getUserProperties().put("userId", String.valueOf(userId));
        }
        return reponseJson.toString();
    }

    // prepareStatementForReunionLookup n'est plus nécessaire car on utilise ReunionManager
    /* 
    private PreparedStatement prepareStatementForReunionLookup(Connection conn, String codeOuId) throws SQLException {
        try {
            int idPotentiel = Integer.parseInt(codeOuId);
            PreparedStatement stmt = conn.prepareStatement("SELECT id, nom, organisateur_id, statut_reunion FROM reunion WHERE id = ?");
            stmt.setInt(1, idPotentiel);
            return stmt;
        } catch (NumberFormatException e) {
            PreparedStatement stmt = conn.prepareStatement("SELECT id, nom, organisateur_id, statut_reunion FROM reunion WHERE nom = ?");
            stmt.setString(1, codeOuId);
            return stmt;
        }
    } */

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
                 .put("debut", reunion.getDebut().toString()).put("duree", reunion.getDuree())
                 .put("statutReunion", reunion.getStatutReunion().toString()); // Ajout du statut
            reponseJson.put("reunion", rData);
        } else {
            reponseJson.put("statut", "echec").put("message", "Réunion non trouvée");
        }
        return reponseJson.toString();
    }

    // REMOVING DUPLICATE METHOD - The first one is more up-to-date.
    // private String obtenirDetailsReunion(JSONObject data) throws SQLException {
    //     JSONObject reponseJson = new JSONObject();
    //     reponseJson.put("modele", "reunion").put("action", "reponseDetails");
    //     int reunionId = data.optInt("id");
    //     ReunionManager reunionManager = new ReunionManager();
    //     Reunion reunion = reunionManager.consulterDetailsReunion(reunionId); //
    //
    //     if (reunion != null) {
    //         reponseJson.put("statut", "succes");
    //         JSONObject rData = new JSONObject();
    //         rData.put("id", reunion.getId()).put("nom", reunion.getNom()).put("sujet", reunion.getSujet())
    //              .put("debut", reunion.getDebut().toString()).put("duree", reunion.getDuree());
    //         reponseJson.put("reunion", rData);
    //     } else {
    //         reponseJson.put("statut", "echec").put("message", "Réunion non trouvée");
    //     }
    //     return reponseJson.toString();
    // }

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

        // --- Modification pour Demande de Parole (restriction d'envoi) ---
        ReunionManager reunionManager = new ReunionManager(); // Gardé pour vérifier type de réunion
        Reunion reunion = reunionManager.consulterDetailsReunion(reunionId);
        if (reunion == null) {
            currentSession.getBasicRemote().sendText(genererReponseErreur("Réunion non trouvée pour l'envoi de message.").toString());
            return;
        }

        DemandeParoleManager demandeParoleManager = new DemandeParoleManager();
        DemandeParole demandeActuelle = demandeParoleManager.obtenirDemandeActuelle(reunionId);

        if (demandeActuelle != null && demandeActuelle.getPersonneId() != userId) {
            if (reunion.getType() == Reunion.Type.STANDARD || reunion.getType() == Reunion.Type.PRIVEE) {
                 currentSession.getBasicRemote().sendText(genererReponseErreur("Vous n'avez pas la parole actuellement.").toString());
                 return;
            }
        }
        // --- Fin de la modification pour Demande de Parole ---

        // Utilisation de PersonneManager pour obtenir les détails de l'expéditeur
        PersonneManager personneManager = new PersonneManager();
        Personne sender = personneManager.obtenirPersonneParId(userId);
        String senderName;
        if (sender != null) {
            senderName = (sender.getPrenom() + " " + sender.getNom()).trim();
            if (senderName.isEmpty()) {
                senderName = "Utilisateur " + userId;
            }
        } else {
            currentSession.getBasicRemote().sendText(genererReponseErreur("Utilisateur expéditeur non trouvé.").toString());
            return;
        }

        // Utilisation de MessageManager pour créer le message
        MessageManager messageManager = new MessageManager();
        Message nouveauMessage = messageManager.creerMessage(userId, reunionId, contenu);

        if (nouveauMessage == null) {
            currentSession.getBasicRemote().sendText(genererReponseErreur("Échec de l'enregistrement du message.").toString());
            return;
        }

        // Préparation du message pour le broadcast
        JSONObject broadcastJson = new JSONObject();
        broadcastJson.put("type", "newMessage");
        broadcastJson.put("reunionId", reunionIdStr); // ou String.valueOf(reunionId)
        broadcastJson.put("sender", senderName);
        broadcastJson.put("content", nouveauMessage.getContenu());
        broadcastJson.put("userId", userIdStr); // ou String.valueOf(userId)
        // Utiliser le timestamp de la base de données pour la cohérence
        broadcastJson.put("timestamp", nouveauMessage.getHeureEnvoi().toInstant(ZoneOffset.UTC).toEpochMilli());

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

        try (Connection conn = Database.getConnection()) { //
            ReunionManager reunionManager = new ReunionManager();
            reunionDetails = reunionManager.consulterDetailsReunion(reunionId); //

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
            Personne personneAInviter = personneManager.obtenirPersonneParLogin(usernameToInvite); //
            if (personneAInviter == null) {
                response.put("success", false).put("message", "Utilisateur '" + usernameToInvite + "' non trouvé.");
                session.getBasicRemote().sendText(response.toString()); return;
            }
            invitedPersonId = personneAInviter.getId();

            ParticipationManager participationManager = new ParticipationManager();
            if (participationManager.estParticipant(invitedPersonId, reunionId)) { //
                response.put("success", false).put("message", "'" + usernameToInvite + "' participe déjà.");
                session.getBasicRemote().sendText(response.toString()); return;
            }

            participationManager.entrerDansReunion(invitedPersonId, reunionId); //
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
            Personne inviterDetails = personneManager.obtenirPersonneParId(inviterUserId); //
            String inviterNomComplet = (inviterDetails != null) ? (inviterDetails.getPrenom() + " " + inviterDetails.getNom()).trim() : "Quelqu'un";
            String nomReunionPourInvite = reunionDetails.getNom();

            JSONObject notificationInvite = new JSONObject();
            notificationInvite.put("type", "nouvelleInvitation")
                              .put("reunionId", reunionId)
                              .put("nomReunion", nomReunionPourInvite)
                              .put("invitePar", inviterNomComplet)
                              .put("dateReunion", reunionDetails.getDebut().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)) // Ajout de la date pour affichage
                              .put("message", inviterNomComplet + " vous a invité(e) à la réunion : " + nomReunionPourInvite);

            for (Session s : ServeurWebSocket.getSessions()) { //
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
        boolean aQuitte = participationManager.sortirDeReunion(userId, Integer.parseInt(reunionIdStr)); //
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
            List<Message> messages = messageManager.obtenirMessagesReunion(reunionId); //
            PersonneManager personneManager = new PersonneManager();

            for (Message msg : messages) {
                JSONObject msgJson = new JSONObject();
                msgJson.put("userId", msg.getIdPersonne());
                msgJson.put("content", msg.getContenu());
                Personne sender = personneManager.obtenirPersonneParId(msg.getIdPersonne()); //
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
        JSONObject responseJson = new JSONObject().put("type", "listeParticipants").put("reunionId", reunionIdStr); //
        JSONArray participantsJsonArray = new JSONArray();

        if (reunionIdStr.isEmpty()) {
            responseJson.put("error", "ID réunion manquant").put("participants", participantsJsonArray);
            session.getBasicRemote().sendText(responseJson.toString()); return;
        }
        try {
            int reunionId = Integer.parseInt(reunionIdStr);
            ParticipationManager participationManager = new ParticipationManager();
            List<Personne> participants = participationManager.obtenirParticipants(reunionId); //
            for (Personne personne : participants) {
                JSONObject pJson = new JSONObject();
                pJson.put("id", personne.getId());
                String nomComplet = (personne.getPrenom() + " " + personne.getNom()).trim();
                if (nomComplet.isEmpty()) nomComplet = "Utilisateur " + personne.getId();
                pJson.put("nom", nomComplet);
                participantsJsonArray.put(pJson);
            }
            responseJson.put("participants", participantsJsonArray); //
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
        try (Connection conn = Database.getConnection()){ //
            String sql = "SELECT DISTINCT r.id, r.nom, r.sujet, r.agenda, r.debut, r.duree, r.type, r.organisateur_id, r.animateur_id, r.statut_reunion " +
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
                        rJson.put("statutReunion", rs.getString("statut_reunion")); // Ajout du statut
                        reunionsArray.put(rJson);
                    }
                }
            }
            responseJson.put("statut", "succes");
            responseJson.put("reunions", reunionsArray); //
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
        responseJson.put("type", "listeInvitationsEnAttente"); // Client attend ce type
        // Si EspaceUtilisateurController s'attend à "modele" et "action" pour ce type de réponse :
        // responseJson.put("modele", "reunion"); // ou "invitation"
        // responseJson.put("action", "reponseGetPendingInvitations");


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

        try (Connection conn = Database.getConnection(); //
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
            responseJson.put("statut", "succes"); // Important pour le client
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
        responseJson.put("type", "updateInvitationStatusResponse"); // Pour que le client sache quelle action a généré cette réponse

        int invitationId = data.optInt("invitationId", -1);
        String newStatusStr = data.optString("newStatus");
        int userId = data.optInt("userId", -1);

        if (invitationId == -1 || newStatusStr.isEmpty() || userId == -1) {
            responseJson.put("success", false).put("message", "Données manquantes pour mettre à jour l'invitation.");
            session.getBasicRemote().sendText(responseJson.toString());
            return;
        }

        // Valider le statut
        Reunion.Type statutEnum; // Utiliser un type existant juste pour la validation du String, ou créer un Enum Invitation.Statut
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
        try (Connection conn = Database.getConnection(); //
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
                        pm.entrerDansReunion(userId, reunionId); // entrerDansReunion gère les doublons
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

    private String ouvrirReunionAction(JSONObject data, Session session) throws SQLException, IOException {
        JSONObject responseJson = new JSONObject();
        responseJson.put("type", "reunionOuverte"); // Le client s'attend à "type" pour les réponses d'action directe

        int reunionId = data.optInt("reunionId", -1);
        int userId = data.optInt("userId", -1); // ou récupérer depuis session.getUserProperties() si fiable

        if (reunionId == -1 || userId == -1) {
            responseJson.put("statut", "echec").put("message", "ID de réunion ou d'utilisateur manquant.");
            session.getBasicRemote().sendText(responseJson.toString());
            return responseJson.toString(); // Retourner pour éviter le traitement ultérieur
        }

        ReunionManager reunionManager = new ReunionManager();
        boolean success = reunionManager.ouvrirReunion(reunionId, userId);

        if (success) {
            responseJson.put("statut", "succes").put("reunionId", reunionId).put("message", "Réunion ouverte avec succès.");
            session.getBasicRemote().sendText(responseJson.toString());

            // Broadcast aux participants
            JSONObject broadcastJson = new JSONObject();
            broadcastJson.put("type", "meetingStatusUpdate");
            broadcastJson.put("reunionId", reunionId);
            broadcastJson.put("status", "OUVERTE");
            broadcastToParticipants(reunionId, broadcastJson.toString());
        } else {
            responseJson.put("statut", "echec").put("reunionId", reunionId).put("message", "Impossible d'ouvrir la réunion (non autorisé ou erreur).");
            session.getBasicRemote().sendText(responseJson.toString());
        }
        return responseJson.toString(); // Nécessaire car la méthode doit retourner une String
    }

    private String cloturerReunionAction(JSONObject data, Session session) throws SQLException, IOException {
        JSONObject responseJson = new JSONObject();
        responseJson.put("type", "reunionCloturee");

        int reunionId = data.optInt("reunionId", -1);
        int userId = data.optInt("userId", -1); // ou récupérer depuis session.getUserProperties()

        if (reunionId == -1 || userId == -1) {
            responseJson.put("statut", "echec").put("message", "ID de réunion ou d'utilisateur manquant.");
            session.getBasicRemote().sendText(responseJson.toString());
            return responseJson.toString();
        }

        ReunionManager reunionManager = new ReunionManager();
        boolean success = reunionManager.cloturerReunion(reunionId, userId);

        if (success) {
            responseJson.put("statut", "succes").put("reunionId", reunionId).put("message", "Réunion clôturée avec succès.");
            session.getBasicRemote().sendText(responseJson.toString());

            // Broadcast aux participants
            JSONObject broadcastJson = new JSONObject();
            broadcastJson.put("type", "meetingStatusUpdate");
            broadcastJson.put("reunionId", reunionId);
            broadcastJson.put("status", "CLOTUREE");
            broadcastToParticipants(reunionId, broadcastJson.toString());
        } else {
            responseJson.put("statut", "echec").put("reunionId", reunionId).put("message", "Impossible de clôturer la réunion (non autorisé ou erreur).");
            session.getBasicRemote().sendText(responseJson.toString());
        }
        return responseJson.toString(); // Nécessaire
    }

    private void broadcastToParticipants(int reunionId, String message) {
        String reunionIdStr = String.valueOf(reunionId);
        for (Session s : ServeurWebSocket.getSessions()) {
            if (s.isOpen() && reunionIdStr.equals(s.getUserProperties().get("reunionId"))) {
                try {
                    s.getBasicRemote().sendText(message);
                } catch (IOException e) {
                    System.err.println("Erreur lors du broadcast aux participants de la réunion " + reunionId + ": " + e.getMessage());
                }
            }
        }
    }

    private void modifierReunionAction(JSONObject data, Session session) throws SQLException, IOException {
        JSONObject responseJson = new JSONObject();
        responseJson.put("type", "reponseModifierReunion"); // Consistent with how other direct actions send responses

        int reunionId = data.optInt("reunionId", -1);
        String userIdStr = (String) session.getUserProperties().get("userId");

        if (reunionId == -1 || userIdStr == null) {
            responseJson.put("statut", "echec").put("message", "ID de réunion ou d'utilisateur manquant pour la modification.");
            session.getBasicRemote().sendText(responseJson.toString());
            return;
        }
        int userId = Integer.parseInt(userIdStr);

        String nom = data.optString("nom");
        String sujet = data.optString("sujet");
        String agenda = data.optString("agenda");
        String debutStr = data.optString("debut");
        int duree = data.optInt("duree", -1);

        // Basic validation for required fields for modification
        if (nom.isEmpty() || debutStr.isEmpty() || duree == -1) {
            responseJson.put("statut", "echec").put("message", "Les champs nom, debut et duree sont obligatoires pour la modification.");
            session.getBasicRemote().sendText(responseJson.toString());
            return;
        }

        LocalDateTime debutLocalDateTime;
        try {
            debutLocalDateTime = LocalDateTime.parse(debutStr);
        } catch (Exception e) {
            responseJson.put("statut", "echec").put("message", "Format de date 'debut' invalide. Utilisez le format ISO (YYYY-MM-DDTHH:MM:SS).");
            session.getBasicRemote().sendText(responseJson.toString());
            return;
        }

        ReunionManager reunionManager = new ReunionManager();
        Reunion reunionExistante = reunionManager.consulterDetailsReunion(reunionId);

        if (reunionExistante == null) {
            responseJson.put("statut", "echec").put("message", "Réunion non trouvée.");
            session.getBasicRemote().sendText(responseJson.toString());
            return;
        }

        if (reunionExistante.getIdOrganisateur() != userId) {
            responseJson.put("statut", "echec").put("message", "Non autorisé. Seul l'organisateur peut modifier la réunion.");
            session.getBasicRemote().sendText(responseJson.toString());
            return;
        }

        boolean success = reunionManager.modifierReunion(reunionId, nom, sujet, agenda, debutLocalDateTime, duree);

        if (success) {
            responseJson.put("statut", "succes").put("message", "Réunion modifiée avec succès.");
            session.getBasicRemote().sendText(responseJson.toString());

            Reunion reunionModifiee = reunionManager.consulterDetailsReunion(reunionId);
            if (reunionModifiee != null) {
                JSONObject detailsReunionJson = new JSONObject();
                detailsReunionJson.put("id", reunionModifiee.getId());
                detailsReunionJson.put("nom", reunionModifiee.getNom());
                detailsReunionJson.put("sujet", reunionModifiee.getSujet());
                detailsReunionJson.put("agenda", reunionModifiee.getAgenda());
                detailsReunionJson.put("debut", reunionModifiee.getDebut().toString());
                detailsReunionJson.put("duree", reunionModifiee.getDuree());
                detailsReunionJson.put("type", reunionModifiee.getType().toString());
                detailsReunionJson.put("idOrganisateur", reunionModifiee.getIdOrganisateur());
                if (reunionModifiee.getIdAnimateur() != null) {
                    detailsReunionJson.put("idAnimateur", reunionModifiee.getIdAnimateur());
                } else {
                    detailsReunionJson.put("idAnimateur", JSONObject.NULL);
                }
                detailsReunionJson.put("statutReunion", reunionModifiee.getStatutReunion().toString());

                JSONObject broadcastJson = new JSONObject();
                broadcastJson.put("type", "meetingDetailsUpdated");
                broadcastJson.put("reunionId", reunionId);
                broadcastJson.put("details", detailsReunionJson);
                broadcastToParticipants(reunionId, broadcastJson.toString());
            }
        } else {
            responseJson.put("statut", "echec").put("message", "Échec de la modification de la réunion.");
            session.getBasicRemote().sendText(responseJson.toString());
        }
    }

    private void definirAnimateurAction(JSONObject data, Session session) throws SQLException, IOException {
        JSONObject responseJson = new JSONObject();
        responseJson.put("type", "reponseDefinirAnimateur");

        int reunionId = data.optInt("reunionId", -1);
        int animateurIdToSet = data.optInt("animateurIdToSet", -1);
        String userIdStr = (String) session.getUserProperties().get("userId"); // Organizer's ID

        if (reunionId == -1 || animateurIdToSet == -1 || userIdStr == null) {
            responseJson.put("statut", "echec").put("message", "ID de réunion, ID animateur ou ID utilisateur (organisateur) manquant.");
            session.getBasicRemote().sendText(responseJson.toString());
            return;
        }
        int organizerId = Integer.parseInt(userIdStr);

        ReunionManager reunionManager = new ReunionManager();
        PersonneManager personneManager = new PersonneManager();

        Reunion reunion = reunionManager.consulterDetailsReunion(reunionId);
        if (reunion == null) {
            responseJson.put("statut", "echec").put("message", "Réunion non trouvée.");
            session.getBasicRemote().sendText(responseJson.toString());
            return;
        }

        if (reunion.getIdOrganisateur() != organizerId) {
            responseJson.put("statut", "echec").put("message", "Non autorisé. Seul l'organisateur peut désigner un animateur.");
            session.getBasicRemote().sendText(responseJson.toString());
            return;
        }

        Personne animateur = personneManager.obtenirPersonneParId(animateurIdToSet);
        if (animateur == null) {
            responseJson.put("statut", "echec").put("message", "Utilisateur animateur non trouvé.");
            session.getBasicRemote().sendText(responseJson.toString());
            return;
        }

        boolean success = reunionManager.definirAnimateur(reunionId, animateurIdToSet);

        if (success) {
            responseJson.put("statut", "succes").put("message", "Animateur désigné avec succès.");
            session.getBasicRemote().sendText(responseJson.toString());

            // Broadcast update to all participants
            JSONObject broadcastJson = new JSONObject();
            broadcastJson.put("type", "animateurUpdated");
            broadcastJson.put("reunionId", reunionId);
            broadcastJson.put("animateurId", animateurIdToSet);
            broadcastToParticipants(reunionId, broadcastJson.toString());

            // Optional: Notify the new animator specifically if they are connected
            for (Session s : ServeurWebSocket.getSessions()) {
                if (s.isOpen() && String.valueOf(animateurIdToSet).equals(s.getUserProperties().get("userId"))) {
                    JSONObject notificationAnimateur = new JSONObject();
                    notificationAnimateur.put("type", "notificationNouveauRole");
                    notificationAnimateur.put("reunionId", reunionId);
                    notificationAnimateur.put("nomReunion", reunion.getNom());
                    notificationAnimateur.put("role", "animateur");
                    notificationAnimateur.put("message", "Vous avez été désigné(e) comme animateur pour la réunion : " + reunion.getNom());
                    try {
                        s.getBasicRemote().sendText(notificationAnimateur.toString());
                    } catch (IOException e) {
                        System.err.println("Erreur lors de l'envoi de la notification de nouveau rôle à l'animateur " + animateurIdToSet + ": " + e.getMessage());
                    }
                    break; 
                }
            }

        } else {
            responseJson.put("statut", "echec").put("message", "Échec de la désignation de l'animateur.");
            session.getBasicRemote().sendText(responseJson.toString());
        }
    }

    // --- Début des méthodes pour Demande de Parole ---

    private JSONObject demandToJSON(DemandeParole demande) throws SQLException {
        if (demande == null) return null;
        JSONObject json = new JSONObject();
        json.put("id", demande.getId());
        json.put("personneId", demande.getPersonneId());
        json.put("reunionId", demande.getReunionId());
        json.put("heureDemande", demande.getHeureDemande() != null ? demande.getHeureDemande().toString() : JSONObject.NULL);
        json.put("statut", demande.getStatutDemande().toString());

        PersonneManager personneManager = new PersonneManager();
        Personne personne = personneManager.obtenirPersonneParId(demande.getPersonneId());
        json.put("nomPersonne", personne != null ? (personne.getPrenom() + " " + personne.getNom()).trim() : "Inconnu");
        return json;
    }
    
    private JSONObject getCurrentSpeakerInfo(int reunionId) throws SQLException {
        DemandeParoleManager demandeParoleManager = new DemandeParoleManager();
        DemandeParole demandeActuelle = demandeParoleManager.obtenirDemandeActuelle(reunionId);
        if (demandeActuelle != null) {
            PersonneManager personneManager = new PersonneManager();
            Personne speaker = personneManager.obtenirPersonneParId(demandeActuelle.getPersonneId());
            if (speaker != null) {
                JSONObject speakerInfo = new JSONObject();
                speakerInfo.put("userId", speaker.getId());
                speakerInfo.put("nomUser", (speaker.getPrenom() + " " + speaker.getNom()).trim());
                speakerInfo.put("demandeId", demandeActuelle.getId());
                return speakerInfo;
            }
        }
        return null;
    }

    private void accorderProchaineParoleAutomatiquement(int reunionId, Session sessionForContext) throws SQLException, IOException {
        DemandeParoleManager demandeParoleManager = new DemandeParoleManager();
        DemandeParole prochaineDemande = demandeParoleManager.obtenirProchaineDemandeAutomatique(reunionId);

        if (prochaineDemande != null) {
            demandeParoleManager.changerStatutDemande(prochaineDemande.getId(), DemandeParole.StatutDemande.ACCORDEE);
            
            PersonneManager personneManager = new PersonneManager();
            Personne personneAccordo = personneManager.obtenirPersonneParId(prochaineDemande.getPersonneId());
            String nomPersonneAccordo = "Inconnu";
            if (personneAccordo != null) {
                nomPersonneAccordo = (personneAccordo.getPrenom() + " " + personneAccordo.getNom()).trim();
            }

            JSONObject broadcastMsg = new JSONObject();
            broadcastMsg.put("type", "paroleAccordee");
            broadcastMsg.put("reunionId", reunionId);
            broadcastMsg.put("userId", prochaineDemande.getPersonneId());
            broadcastMsg.put("nomUser", nomPersonneAccordo);
            broadcastMsg.put("demandeId", prochaineDemande.getId());
            broadcastToParticipants(reunionId, broadcastMsg.toString());
        }
    }

    private void demanderParoleAction(JSONObject data, Session session) throws SQLException, IOException {
        JSONObject responseJson = new JSONObject().put("type", "reponseDemanderParole");
        int reunionId = data.optInt("reunionId", -1);
        String userIdStr = (String) session.getUserProperties().get("userId");

        if (reunionId == -1 && session.getUserProperties().containsKey("reunionId")) {
            reunionId = Integer.parseInt((String) session.getUserProperties().get("reunionId"));
        }
        
        if (reunionId == -1 || userIdStr == null) {
            responseJson.put("statut", "echec").put("message", "ID Réunion ou utilisateur manquant.");
            session.getBasicRemote().sendText(responseJson.toString());
            return;
        }
        int userId = Integer.parseInt(userIdStr);

        DemandeParoleManager demandeMgr = new DemandeParoleManager();
        // Vérifier si l'utilisateur a déjà une demande EN_ATTENTE ou ACCORDEE
        if (demandeMgr.obtenirDemandeParPersonneEtReunion(userId, reunionId, DemandeParole.StatutDemande.EN_ATTENTE) != null ||
            demandeMgr.obtenirDemandeParPersonneEtReunion(userId, reunionId, DemandeParole.StatutDemande.ACCORDEE) != null) {
            responseJson.put("statut", "echec").put("message", "Vous avez déjà une demande de parole en attente ou la parole vous est déjà accordée.");
            session.getBasicRemote().sendText(responseJson.toString());
            return;
        }

        try {
            DemandeParole nouvelleDemande = demandeMgr.creerDemande(userId, reunionId);
            responseJson.put("statut", "succes").put("message", "Demande de parole enregistrée.").put("demande", demandToJSON(nouvelleDemande));
            session.getBasicRemote().sendText(responseJson.toString());

            ReunionManager reunionMgr = new ReunionManager();
            Reunion reunion = reunionMgr.consulterDetailsReunion(reunionId);

            if (reunion != null) {
                if (reunion.getType() == Reunion.Type.DEMOCRATIQUE) {
                    if (demandeMgr.obtenirDemandeActuelle(reunionId) == null) { // Si personne ne parle
                        accorderProchaineParoleAutomatiquement(reunionId, session);
                    }
                } else { // STANDARD ou PRIVEE
                    Integer animateurId = reunion.getIdAnimateur();
                    if (animateurId == null) animateurId = reunion.getIdOrganisateur(); // Fallback sur l'organisateur si pas d'animateur

                    JSONObject notifAnimateur = new JSONObject();
                    notifAnimateur.put("type", "nouvelleDemandeParole");
                    notifAnimateur.put("reunionId", reunionId);
                    notifAnimateur.put("demande", demandToJSON(nouvelleDemande));
                    
                    for (Session s : ServeurWebSocket.getSessions()) {
                        if (s.isOpen() && String.valueOf(animateurId).equals(s.getUserProperties().get("userId"))) {
                            s.getBasicRemote().sendText(notifAnimateur.toString());
                            break; 
                        }
                    }
                }
            }
        } catch (SQLException e) {
             // Gérer la violation de contrainte unique (si creerDemande ne la gère pas en amont par suppression)
            if (e.getSQLState().equals("23000")) { // Code d'erreur SQL pour violation de contrainte d'unicité
                 responseJson.put("statut", "echec").put("message", "Impossible de créer la demande. Avez-vous déjà une demande active?");
            } else {
                responseJson.put("statut", "echec").put("message", "Erreur SQL: " + e.getMessage());
            }
            session.getBasicRemote().sendText(responseJson.toString());
        }
    }

    private void accorderParoleAction(JSONObject data, Session session) throws SQLException, IOException {
        JSONObject responseJson = new JSONObject().put("type", "reponseAccorderParole");
        int demandeId = data.optInt("demandeId", -1);
        int reunionId = data.optInt("reunionId", -1); // Utile pour le broadcast et la logique démocratique
        String userIdStr = (String) session.getUserProperties().get("userId"); // ID de l'animateur/organisateur

        if (demandeId == -1 || reunionId == -1 || userIdStr == null) {
            responseJson.put("statut", "echec").put("message", "ID Demande, Réunion ou Animateur manquant.");
            session.getBasicRemote().sendText(responseJson.toString());
            return;
        }
        int animateurId = Integer.parseInt(userIdStr);

        ReunionManager reunionMgr = new ReunionManager();
        Reunion reunion = reunionMgr.consulterDetailsReunion(reunionId);
        if (reunion == null) {
             responseJson.put("statut", "echec").put("message", "Réunion non trouvée.");
             session.getBasicRemote().sendText(responseJson.toString()); return;
        }
        // Vérification des permissions (seul l'animateur ou l'organisateur peut accorder)
        if (!((reunion.getIdAnimateur() != null && reunion.getIdAnimateur() == animateurId) || reunion.getIdOrganisateur() == animateurId)) {
            responseJson.put("statut", "echec").put("message", "Non autorisé à accorder la parole.");
            session.getBasicRemote().sendText(responseJson.toString()); return;
        }
        
        DemandeParoleManager demandeMgr = new DemandeParoleManager();
        // Mettre la demande actuelle (si elle existe) à TERMINEE
        DemandeParole demandeActuelle = demandeMgr.obtenirDemandeActuelle(reunionId);
        if (demandeActuelle != null && demandeActuelle.getId() != demandeId) {
            demandeMgr.changerStatutDemande(demandeActuelle.getId(), DemandeParole.StatutDemande.TERMINEE);
             // Informer l'ancien speaker que sa parole est terminée (optionnel, ou géré par le client via paroleAccordee général)
        }

        boolean success = demandeMgr.changerStatutDemande(demandeId, DemandeParole.StatutDemande.ACCORDEE);
        if (success) {
            responseJson.put("statut", "succes").put("message", "Parole accordée.");
            session.getBasicRemote().sendText(responseJson.toString());

            DemandeParole demandeAccordee = demandeMgr.obtenirDemandeParoleParId(demandeId);
            PersonneManager personneMgr = new PersonneManager();
            Personne speaker = personneMgr.obtenirPersonneParId(demandeAccordee.getPersonneId());
            String nomSpeaker = (speaker != null) ? (speaker.getPrenom() + " " + speaker.getNom()).trim() : "Inconnu";

            JSONObject broadcastMsg = new JSONObject();
            broadcastMsg.put("type", "paroleAccordee");
            broadcastMsg.put("reunionId", reunionId);
            broadcastMsg.put("userId", demandeAccordee.getPersonneId());
            broadcastMsg.put("nomUser", nomSpeaker);
            broadcastMsg.put("demandeId", demandeId);
            broadcastToParticipants(reunionId, broadcastMsg.toString());
        } else {
            responseJson.put("statut", "echec").put("message", "Échec de l'accord de la parole.");
            session.getBasicRemote().sendText(responseJson.toString());
        }
    }

    private void refuserParoleAction(JSONObject data, Session session) throws SQLException, IOException {
        JSONObject responseJson = new JSONObject().put("type", "reponseRefuserParole");
        int demandeId = data.optInt("demandeId", -1);
        String userIdStr = (String) session.getUserProperties().get("userId"); // ID de l'animateur/organisateur

        if (demandeId == -1 || userIdStr == null) {
            responseJson.put("statut", "echec").put("message", "ID Demande ou Animateur manquant.");
            session.getBasicRemote().sendText(responseJson.toString()); return;
        }
        int animateurId = Integer.parseInt(userIdStr);
        
        DemandeParoleManager demandeMgr = new DemandeParoleManager();
        DemandeParole demande = demandeMgr.obtenirDemandeParoleParId(demandeId);
        if (demande == null) {
            responseJson.put("statut", "echec").put("message", "Demande non trouvée.");
            session.getBasicRemote().sendText(responseJson.toString()); return;
        }

        ReunionManager reunionMgr = new ReunionManager();
        Reunion reunion = reunionMgr.consulterDetailsReunion(demande.getReunionId());
         if (reunion == null) {
             responseJson.put("statut", "echec").put("message", "Réunion associée à la demande non trouvée.");
             session.getBasicRemote().sendText(responseJson.toString()); return;
        }
        if (!((reunion.getIdAnimateur() != null && reunion.getIdAnimateur() == animateurId) || reunion.getIdOrganisateur() == animateurId)) {
            responseJson.put("statut", "echec").put("message", "Non autorisé à refuser la parole.");
            session.getBasicRemote().sendText(responseJson.toString()); return;
        }

        boolean success = demandeMgr.changerStatutDemande(demandeId, DemandeParole.StatutDemande.REFUSEE);
        if (success) {
            responseJson.put("statut", "succes").put("message", "Demande de parole refusée.");
            session.getBasicRemote().sendText(responseJson.toString());

            // Notifier l'utilisateur spécifique
            JSONObject notifUser = new JSONObject();
            notifUser.put("type", "demandeParoleRefusee");
            notifUser.put("reunionId", demande.getReunionId());
            notifUser.put("demandeId", demandeId);
            for (Session s : ServeurWebSocket.getSessions()) {
                if (s.isOpen() && String.valueOf(demande.getPersonneId()).equals(s.getUserProperties().get("userId"))) {
                    s.getBasicRemote().sendText(notifUser.toString());
                    break;
                }
            }
            // L'animateur pourrait aussi vouloir voir la liste des demandes mises à jour.
            // On pourrait broadcaster la liste mise à jour des demandes en attente à l'animateur.
            // Ou le client animateur rafraîchit la liste après cette action.
        } else {
            responseJson.put("statut", "echec").put("message", "Échec du refus de la parole.");
            session.getBasicRemote().sendText(responseJson.toString());
        }
    }

    private void cederParoleAction(JSONObject data, Session session) throws SQLException, IOException {
        JSONObject responseJson = new JSONObject().put("type", "reponseCederParole");
        int reunionId = data.optInt("reunionId", -1);
        String userIdStr = (String) session.getUserProperties().get("userId"); // ID du speaker actuel

        if (reunionId == -1 && session.getUserProperties().containsKey("reunionId")) {
            reunionId = Integer.parseInt((String) session.getUserProperties().get("reunionId"));
        }

        if (reunionId == -1 || userIdStr == null) {
            responseJson.put("statut", "echec").put("message", "ID Réunion ou Utilisateur manquant.");
            session.getBasicRemote().sendText(responseJson.toString()); return;
        }
        int speakerId = Integer.parseInt(userIdStr);

        DemandeParoleManager demandeMgr = new DemandeParoleManager();
        DemandeParole demandeActuelle = demandeMgr.obtenirDemandeActuelle(reunionId);

        if (demandeActuelle == null || demandeActuelle.getPersonneId() != speakerId) {
            responseJson.put("statut", "echec").put("message", "Vous n'avez pas la parole actuellement ou demande non trouvée.");
            session.getBasicRemote().sendText(responseJson.toString()); return;
        }

        boolean success = demandeMgr.changerStatutDemande(demandeActuelle.getId(), DemandeParole.StatutDemande.TERMINEE);
        if (success) {
            responseJson.put("statut", "succes").put("message", "Parole cédée.");
            session.getBasicRemote().sendText(responseJson.toString());
            
            PersonneManager personneMgr = new PersonneManager();
            Personne speaker = personneMgr.obtenirPersonneParId(speakerId);
            String nomSpeaker = (speaker != null) ? (speaker.getPrenom() + " " + speaker.getNom()).trim() : "Inconnu";

            JSONObject broadcastMsg = new JSONObject();
            broadcastMsg.put("type", "paroleCedee");
            broadcastMsg.put("reunionId", reunionId);
            broadcastMsg.put("userId", speakerId);
            broadcastMsg.put("nomUser", nomSpeaker);
            broadcastMsg.put("demandeId", demandeActuelle.getId());
            broadcastToParticipants(reunionId, broadcastMsg.toString());

            ReunionManager reunionMgr = new ReunionManager();
            Reunion reunion = reunionMgr.consulterDetailsReunion(reunionId);
            if (reunion != null) {
                if (reunion.getType() == Reunion.Type.DEMOCRATIQUE) {
                    accorderProchaineParoleAutomatiquement(reunionId, session);
                } else { // STANDARD ou PRIVEE
                    Integer animateurId = reunion.getIdAnimateur() != null ? reunion.getIdAnimateur() : reunion.getIdOrganisateur();
                    JSONObject notifAnimateur = new JSONObject();
                    notifAnimateur.put("type", "paroleEstLibre");
                    notifAnimateur.put("reunionId", reunionId);
                     for (Session s : ServeurWebSocket.getSessions()) {
                        if (s.isOpen() && String.valueOf(animateurId).equals(s.getUserProperties().get("userId"))) {
                            s.getBasicRemote().sendText(notifAnimateur.toString());
                            break; 
                        }
                    }
                }
            }
        } else {
            responseJson.put("statut", "echec").put("message", "Échec de la cession de la parole.");
            session.getBasicRemote().sendText(responseJson.toString());
        }
    }

    private void getDemandesParoleAction(JSONObject data, Session session) throws SQLException, IOException {
        JSONObject responseJson = new JSONObject().put("type", "listeDemandesParole");
        int reunionId = data.optInt("reunionId", -1);
        String userIdStr = (String) session.getUserProperties().get("userId"); // ID de l'animateur/organisateur

         if (reunionId == -1 && session.getUserProperties().containsKey("reunionId")) {
            reunionId = Integer.parseInt((String) session.getUserProperties().get("reunionId"));
        }

        if (reunionId == -1 || userIdStr == null) {
            responseJson.put("statut", "echec").put("message", "ID Réunion ou Animateur manquant.");
            session.getBasicRemote().sendText(responseJson.toString()); return;
        }
        int animateurId = Integer.parseInt(userIdStr);

        ReunionManager reunionMgr = new ReunionManager();
        Reunion reunion = reunionMgr.consulterDetailsReunion(reunionId);
         if (reunion == null) {
             responseJson.put("statut", "echec").put("message", "Réunion non trouvée.");
             session.getBasicRemote().sendText(responseJson.toString()); return;
        }
        if (!((reunion.getIdAnimateur() != null && reunion.getIdAnimateur() == animateurId) || reunion.getIdOrganisateur() == animateurId)) {
            responseJson.put("statut", "echec").put("message", "Non autorisé.");
            session.getBasicRemote().sendText(responseJson.toString()); return;
        }
        
        DemandeParoleManager demandeMgr = new DemandeParoleManager();
        List<DemandeParole> demandes = demandeMgr.obtenirDemandesEnAttente(reunionId);
        JSONArray demandesJson = new JSONArray();
        for (DemandeParole demande : demandes) {
            demandesJson.put(demandToJSON(demande));
        }
        responseJson.put("statut", "succes").put("reunionId", reunionId).put("demandes", demandesJson);
        session.getBasicRemote().sendText(responseJson.toString());
    }

    // --- Fin des méthodes pour Demande de Parole ---

}