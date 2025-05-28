package model;

import model.Reunion;
import serveur.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID; // For generating meeting code

public class ReunionManager {
    private Connection connection;

    public ReunionManager() throws SQLException {
        this.connection = Database.getConnection();
    }

    private static String genererMeetingCode() {
        // Generates a code like "abc-def-ghi"
        String uuid = UUID.randomUUID().toString();
        return uuid.substring(0, 3) + "-" + uuid.substring(4, 7) + "-" + uuid.substring(8, 11);
    }

    public Reunion planifierReunion(String nom, String sujet, String agenda, LocalDateTime debut, int duree, Reunion.Type type, int organisateurId, Integer animateurId) throws SQLException {
        String meetingCode = genererMeetingCode();
        String sql = "INSERT INTO reunion (nom, sujet, agenda, debut, duree, type, organisateur_id, animateur_id, meeting_code) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, nom);
            pstmt.setString(2, sujet);
            pstmt.setString(3, agenda);
            pstmt.setTimestamp(4, Timestamp.valueOf(debut));
            pstmt.setInt(5, duree);
            pstmt.setString(6, type.toString());
            pstmt.setInt(7, organisateurId);
            if (animateurId != null && animateurId != 0) { // Assuming 0 means no animator from model
                pstmt.setInt(8, animateurId);
            } else {
                pstmt.setNull(8, java.sql.Types.INTEGER);
            }
            pstmt.setString(9, meetingCode); // Set the meeting code
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int newId = generatedKeys.getInt(1);
                        return consulterDetailsReunion(newId);
                    } else {
                        throw new SQLException("Creating meeting failed, no ID obtained.");
                    }
                }
            } else {
                throw new SQLException("Creating meeting failed, no rows affected.");
            }
        }
        // Removed duplicated return block from previous merge error
    }

    public Reunion consulterDetailsReunion(int reunionId) throws SQLException {
        String sql = "SELECT id, nom, sujet, agenda, debut, duree, type, organisateur_id, animateur_id, meeting_code FROM reunion WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, reunionId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Reunion(
                        rs.getInt("id"),
                        rs.getString("nom"),
                        rs.getString("sujet"),
                        rs.getString("agenda"),
                        rs.getTimestamp("debut").toLocalDateTime(),
                        rs.getInt("duree"),
                        Reunion.Type.valueOf(rs.getString("type")),
                        rs.getInt("organisateur_id"),
                        rs.getInt("animateur_id"), // This is int from DB
                        rs.getString("meeting_code")
                );
            }
            return null;
        }
    }

    public boolean modifierReunion(int reunionId, String nom, String sujet, String agenda, LocalDateTime debut, int duree) throws SQLException {
        String sql = "UPDATE reunion SET nom = ?, sujet = ?, agenda = ?, debut = ?, duree = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, nom);
            pstmt.setString(2, sujet);
            pstmt.setString(3, agenda);
            pstmt.setTimestamp(4, Timestamp.valueOf(debut));
            pstmt.setInt(5, duree);
            pstmt.setInt(6, reunionId);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    public boolean ouvrirReunion(int reunionId, int animateurId) throws SQLException {
        // Vous pourriez ajouter une colonne 'ouverte' à la table reunion pour suivre l'état
        // Pour l'instant, on va juste vérifier si l'animateur existe et est correctement défini.
        String sql = "SELECT COUNT(*) FROM reunion WHERE id = ? AND animateur_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, reunionId);
            pstmt.setInt(2, animateurId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                // Logique pour marquer la réunion comme ouverte pourrait être ajoutée ici.
                return true;
            }
            return false;
        }
    }

    public boolean cloturerReunion(int reunionId, int animateurId) throws SQLException {
        // Similaire à ouvrirReunion, vous pourriez avoir une colonne 'ouverte'
        String sql = "SELECT COUNT(*) FROM reunion WHERE id = ? AND animateur_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, reunionId);
            pstmt.setInt(2, animateurId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                // Logique pour marquer la réunion comme fermée ici.
                return true;
            }
            return false;
        }
    }

    public List<Reunion> obtenirToutesReunions() throws SQLException {
        List<Reunion> reunions = new ArrayList<>();
        String sql = "SELECT id, nom, sujet, agenda, debut, duree, type, organisateur_id, animateur_id, meeting_code FROM reunion";
        try (PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                reunions.add(new Reunion(
                        rs.getInt("id"),
                        rs.getString("nom"),
                        rs.getString("sujet"),
                        rs.getString("agenda"),
                        rs.getTimestamp("debut").toLocalDateTime(),
                        rs.getInt("duree"),
                        Reunion.Type.valueOf(rs.getString("type")),
                        rs.getInt("organisateur_id"),
                        rs.getInt("animateur_id"), // This is int from DB
                        rs.getString("meeting_code")
                ));
            }
        }
        return reunions;
    }

    public List<Reunion> obtenirReunionsOuvertes() throws SQLException {
        // Si vous ajoutez une colonne 'ouverte', vous pouvez filtrer ici.
        // Pour l'instant, on retourne toutes les réunions.
        return obtenirToutesReunions();
    }

    public List<Reunion> obtenirReunionsOrganiseesPar(int organisateurId) throws SQLException {
        List<Reunion> reunions = new ArrayList<>();
        String sql = "SELECT id, nom, sujet, agenda, debut, duree, type, organisateur_id, animateur_id, meeting_code FROM reunion WHERE organisateur_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, organisateurId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                reunions.add(new Reunion(
                        rs.getInt("id"),
                        rs.getString("nom"),
                        rs.getString("sujet"),
                        rs.getString("agenda"),
                        rs.getTimestamp("debut").toLocalDateTime(),
                        rs.getInt("duree"),
                        Reunion.Type.valueOf(rs.getString("type")),
                        rs.getInt("organisateur_id"),
                        rs.getInt("animateur_id"), // This is int from DB
                        rs.getString("meeting_code")
                ));
            }
        }
        return reunions;
    }

    public boolean definirAnimateur(int reunionId, int animateurId) throws SQLException {
        String sql = "UPDATE reunion SET animateur_id = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, animateurId);
            pstmt.setInt(2, reunionId);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    public Reunion getReunionByCode(String code) throws SQLException {
        String sql = "SELECT id, nom, sujet, agenda, debut, duree, type, organisateur_id, animateur_id, meeting_code FROM reunion WHERE meeting_code = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, code);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Reunion(
                        rs.getInt("id"),
                        rs.getString("nom"),
                        rs.getString("sujet"),
                        rs.getString("agenda"),
                        rs.getTimestamp("debut").toLocalDateTime(),
                        rs.getInt("duree"),
                        Reunion.Type.valueOf(rs.getString("type")),
                        rs.getInt("organisateur_id"),
                        rs.getInt("animateur_id"),
                        rs.getString("meeting_code")
                );
            }
            return null;
        }
    }

    public Reunion rejoindreReunionParCode(String code, int personneId) throws SQLException {
        Reunion reunion = getReunionByCode(code);
        if (reunion == null) {
            throw new SQLException("Aucune réunion trouvée avec le code: " + code);
        }

        ParticipationManager participationManager = new ParticipationManager();
        boolean success = participationManager.entrerDansReunion(personneId, reunion.getId());
        
        if (success) {
            return reunion;
        } else {
            // This case might not be reachable if entrerDansReunion throws exception on real failure
            // or handles duplicates by returning true.
            throw new SQLException("Impossible de rejoindre la réunion avec le code: " + code + " pour la personne ID: " + personneId);
        }
    }
}
