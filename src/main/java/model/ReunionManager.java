package model;

import serveur.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ReunionManager {
    private Connection connection;

    public ReunionManager() throws SQLException {
        this.connection = Database.getConnection();
    }

    /**
     * Planifie une nouvelle réunion
     */
    public Reunion planifierReunion(String nom, String sujet, String agenda, LocalDateTime debut, int duree, Reunion.Type type, int organisateurId, Integer animateurId) throws SQLException {
        // statut_reunion will default to 'PLANIFIEE' in the database
        String sql = "INSERT INTO reunion (nom, sujet, agenda, debut, duree, type, organisateur_id, animateur_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, nom);
            pstmt.setString(2, sujet);
            pstmt.setString(3, agenda);
            pstmt.setTimestamp(4, Timestamp.valueOf(debut));
            pstmt.setInt(5, duree);
            pstmt.setString(6, type.toString());
            pstmt.setInt(7, organisateurId);

            if (animateurId != null) {
                pstmt.setInt(8, animateurId);
            } else {
                pstmt.setNull(8, java.sql.Types.INTEGER);
            }

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int newId = generatedKeys.getInt(1);
                        System.out.println("Nouvelle réunion créée avec ID: " + newId);
                        return consulterDetailsReunion(newId);
                    } else {
                        throw new SQLException("Creating meeting failed, no ID obtained.");
                    }
                }
            } else {
                throw new SQLException("Creating meeting failed, no rows affected.");
            }
        }
    }

    /**
     * Consulte les détails d'une réunion par son ID
     */
    public Reunion consulterDetailsReunion(int reunionId) throws SQLException {
        String sql = "SELECT id, nom, sujet, agenda, debut, duree, type, organisateur_id, animateur_id, statut_reunion FROM reunion WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, reunionId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                Integer animateurId = rs.getObject("animateur_id") != null ? rs.getInt("animateur_id") : null;
                Reunion.StatutReunion statut = Reunion.StatutReunion.valueOf(rs.getString("statut_reunion"));

                return new Reunion(
                        rs.getInt("id"),
                        rs.getString("nom"),
                        rs.getString("sujet"),
                        rs.getString("agenda"),
                        rs.getTimestamp("debut").toLocalDateTime(),
                        rs.getInt("duree"),
                        Reunion.Type.valueOf(rs.getString("type")),
                        rs.getInt("organisateur_id"),
                        animateurId,
                        statut
                );
            }
            return null;
        }
    }

    /**
     * Modifie les détails d'une réunion existante
     */
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

    /**
     * Ouvre une réunion (vérifie que l'utilisateur est animateur ou organisateur)
     */
    public boolean ouvrirReunion(int reunionId, int userId) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM reunion WHERE id = ? AND (animateur_id = ? OR organisateur_id = ?)";
        try (PreparedStatement pstmtCheck = connection.prepareStatement(checkSql)) {
            pstmtCheck.setInt(1, reunionId);
            pstmtCheck.setInt(2, userId);
            pstmtCheck.setInt(3, userId);
            ResultSet rs = pstmtCheck.executeQuery();

            if (rs.next() && rs.getInt(1) > 0) {
                // User is authorized, now update status
                String updateSql = "UPDATE reunion SET statut_reunion = 'OUVERTE' WHERE id = ?";
                try (PreparedStatement pstmtUpdate = connection.prepareStatement(updateSql)) {
                    pstmtUpdate.setInt(1, reunionId);
                    int affectedRows = pstmtUpdate.executeUpdate();
                    return affectedRows > 0;
                }
            }
            return false; // Not authorized or update failed
        }
    }

    /**
     * Clôture une réunion (vérifie que l'utilisateur est animateur ou organisateur)
     */
    public boolean cloturerReunion(int reunionId, int userId) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM reunion WHERE id = ? AND (animateur_id = ? OR organisateur_id = ?)";
        try (PreparedStatement pstmtCheck = connection.prepareStatement(checkSql)) {
            pstmtCheck.setInt(1, reunionId);
            pstmtCheck.setInt(2, userId);
            pstmtCheck.setInt(3, userId);
            ResultSet rs = pstmtCheck.executeQuery();

            if (rs.next() && rs.getInt(1) > 0) {
                // User is authorized, now update status
                String updateSql = "UPDATE reunion SET statut_reunion = 'CLOTUREE' WHERE id = ?";
                try (PreparedStatement pstmtUpdate = connection.prepareStatement(updateSql)) {
                    pstmtUpdate.setInt(1, reunionId);
                    int affectedRows = pstmtUpdate.executeUpdate();
                    return affectedRows > 0;
                }
            }
            return false; // Not authorized or update failed
        }
    }

    /**
     * Récupère toutes les réunions
     */
    public List<Reunion> obtenirToutesReunions() throws SQLException {
        List<Reunion> reunions = new ArrayList<>();
        String sql = "SELECT id, nom, sujet, agenda, debut, duree, type, organisateur_id, animateur_id, statut_reunion FROM reunion ORDER BY debut DESC";

        try (PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Integer animateurId = rs.getObject("animateur_id") != null ? rs.getInt("animateur_id") : null;
                Reunion.StatutReunion statut = Reunion.StatutReunion.valueOf(rs.getString("statut_reunion"));

                reunions.add(new Reunion(
                        rs.getInt("id"),
                        rs.getString("nom"),
                        rs.getString("sujet"),
                        rs.getString("agenda"),
                        rs.getTimestamp("debut").toLocalDateTime(),
                        rs.getInt("duree"),
                        Reunion.Type.valueOf(rs.getString("type")),
                        rs.getInt("organisateur_id"),
                        animateurId,
                        statut
                ));
            }
        }
        return reunions;
    }

    /**
     * Récupère les réunions ouvertes (actuellement toutes les réunions)
     */
    public List<Reunion> obtenirReunionsOuvertes() throws SQLException {
        List<Reunion> reunions = new ArrayList<>();
        String sql = "SELECT id, nom, sujet, agenda, debut, duree, type, organisateur_id, animateur_id, statut_reunion FROM reunion WHERE statut_reunion = 'OUVERTE' ORDER BY debut ASC";

        try (PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Integer animateurId = rs.getObject("animateur_id") != null ? rs.getInt("animateur_id") : null;
                Reunion.StatutReunion statut = Reunion.StatutReunion.valueOf(rs.getString("statut_reunion"));

                reunions.add(new Reunion(
                        rs.getInt("id"),
                        rs.getString("nom"),
                        rs.getString("sujet"),
                        rs.getString("agenda"),
                        rs.getTimestamp("debut").toLocalDateTime(),
                        rs.getInt("duree"),
                        Reunion.Type.valueOf(rs.getString("type")),
                        rs.getInt("organisateur_id"),
                        animateurId,
                        statut
                ));
            }
        }
        return reunions;
    }

    /**
     * Récupère les réunions organisées par un utilisateur spécifique
     */
    public List<Reunion> obtenirReunionsOrganiseesPar(int organisateurId) throws SQLException {
        List<Reunion> reunions = new ArrayList<>();
        String sql = "SELECT id, nom, sujet, agenda, debut, duree, type, organisateur_id, animateur_id, statut_reunion FROM reunion WHERE organisateur_id = ? ORDER BY debut DESC";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, organisateurId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Integer animateurId = rs.getObject("animateur_id") != null ? rs.getInt("animateur_id") : null;
                Reunion.StatutReunion statut = Reunion.StatutReunion.valueOf(rs.getString("statut_reunion"));

                reunions.add(new Reunion(
                        rs.getInt("id"),
                        rs.getString("nom"),
                        rs.getString("sujet"),
                        rs.getString("agenda"),
                        rs.getTimestamp("debut").toLocalDateTime(),
                        rs.getInt("duree"),
                        Reunion.Type.valueOf(rs.getString("type")),
                        rs.getInt("organisateur_id"),
                        animateurId,
                        statut
                ));
            }
        }
        return reunions;
    }

    /**
     * Définit l'animateur d'une réunion
     */
    public boolean definirAnimateur(int reunionId, int animateurId) throws SQLException {
        String sql = "UPDATE reunion SET animateur_id = ? WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, animateurId);
            pstmt.setInt(2, reunionId);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    /**
     * Recherche des réunions par nom (pour la fonctionnalité de recherche/rejoindre)
     */
    public Reunion rechercherReunionParNom(String nom) throws SQLException {
        String sql = "SELECT id, nom, sujet, agenda, debut, duree, type, organisateur_id, animateur_id, statut_reunion FROM reunion WHERE nom = ? LIMIT 1";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, nom);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                Integer animateurId = rs.getObject("animateur_id") != null ? rs.getInt("animateur_id") : null;
                Reunion.StatutReunion statut = Reunion.StatutReunion.valueOf(rs.getString("statut_reunion"));

                return new Reunion(
                        rs.getInt("id"),
                        rs.getString("nom"),
                        rs.getString("sujet"),
                        rs.getString("agenda"),
                        rs.getTimestamp("debut").toLocalDateTime(),
                        rs.getInt("duree"),
                        Reunion.Type.valueOf(rs.getString("type")),
                        rs.getInt("organisateur_id"),
                        animateurId,
                        statut
                );
            }
            return null;
        }
    }

    /**
     * Vérifie si une réunion existe
     */
    public boolean reunionExiste(int reunionId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM reunion WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, reunionId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            return false;
        }
    }

    /**
     * Supprime une réunion (avec toutes ses dépendances)
     */
    public boolean supprimerReunion(int reunionId, int utilisateurId) throws SQLException {
        // Vérifier que l'utilisateur est l'organisateur
        String checkSql = "SELECT COUNT(*) FROM reunion WHERE id = ? AND organisateur_id = ?";

        try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
            checkStmt.setInt(1, reunionId);
            checkStmt.setInt(2, utilisateurId);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next() && rs.getInt(1) > 0) {
                // L'utilisateur est bien l'organisateur, on peut supprimer
                // Les suppressions en cascade sont gérées par les contraintes FK
                String deleteSql = "DELETE FROM reunion WHERE id = ?";

                try (PreparedStatement deleteStmt = connection.prepareStatement(deleteSql)) {
                    deleteStmt.setInt(1, reunionId);
                    int affectedRows = deleteStmt.executeUpdate();
                    return affectedRows > 0;
                }
            }
            return false; // L'utilisateur n'est pas autorisé à supprimer cette réunion
        }
    }

    /**
     * Ferme la connexion à la base de données
     */
    public void fermerConnexion() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}