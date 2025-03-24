package model;

import serveur.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DemandeParoleManager {
    private Connection connection;

    public DemandeParoleManager() throws SQLException {
        this.connection = Database.getConnection();
    }

    public DemandeParole demanderParole(int personneId, int reunionId) throws SQLException {
        String sql = "INSERT INTO demande_parole (personne_id, reunion_id) VALUES (?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, personneId);
            pstmt.setInt(2, reunionId);
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int newId = generatedKeys.getInt(1);
                        return obtenirDemandeParoleParId(newId);
                    } else {
                        throw new SQLException("Creating request failed, no ID obtained.");
                    }
                }
            } else {
                throw new SQLException("Creating request failed, no rows affected.");
            }
        } catch (SQLException e) {
            if (e.getSQLState().equals("23000") && e.getMessage().contains("Duplicate entry")) {
                // Gérer le cas où la personne a déjà une demande en attente dans cette réunion
                String sqlSelect = "SELECT id FROM demande_parole WHERE personne_id = ? AND reunion_id = ? AND statut = 'en_attente'";
                try (PreparedStatement pstmtSelect = connection.prepareStatement(sqlSelect)) {
                    pstmtSelect.setInt(1, personneId);
                    pstmtSelect.setInt(2, reunionId);
                    ResultSet rs = pstmtSelect.executeQuery();
                    if (rs.next()) {
                        return obtenirDemandeParoleParId(rs.getInt("id"));
                    }
                }
            }
            throw e;
        }
    }

    public boolean accordParole(int demandeParoleId, int animateurId) throws SQLException {
        // Vous pourriez ajouter une vérification pour s'assurer que l'animateur est bien l'animateur de la réunion
        String sql = "UPDATE demande_parole SET statut = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, DemandeParole.Statut.ACCORDEE.toString());
            pstmt.setInt(2, demandeParoleId);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    public boolean refuserParole(int demandeParoleId, int animateurId) throws SQLException {
        // Similaire à accordParole, vérifiez l'animateur si nécessaire
        String sql = "UPDATE demande_parole SET statut = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, DemandeParole.Statut.REFUSEE.toString());
            pstmt.setInt(2, demandeParoleId);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    public DemandeParole obtenirProchaineDemandeParole(int reunionId) throws SQLException {
        String sql = "SELECT id, personne_id, reunion_id, heure_demande, statut FROM demande_parole WHERE reunion_id = ? AND statut = 'en_attente' ORDER BY heure_demande ASC LIMIT 1";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, reunionId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new DemandeParole(
                        rs.getInt("id"),
                        rs.getInt("personne_id"),
                        rs.getInt("reunion_id"),
                        rs.getTimestamp("heure_demande").toLocalDateTime(),
                        DemandeParole.Statut.valueOf(rs.getString("statut"))
                );
            }
            return null;
        }
    }

    public List<DemandeParole> obtenirDemandesEnAttente(int reunionId) throws SQLException {
        List<DemandeParole> demandes = new ArrayList<>();
        String sql = "SELECT id, personne_id, reunion_id, heure_demande, statut FROM demande_parole WHERE reunion_id = ? AND statut = 'en_attente'";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, reunionId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                demandes.add(new DemandeParole(
                        rs.getInt("id"),
                        rs.getInt("personne_id"),
                        rs.getInt("reunion_id"),
                        rs.getTimestamp("heure_demande").toLocalDateTime(),
                        DemandeParole.Statut.valueOf(rs.getString("statut"))
                ));
            }
        }
        return demandes;
    }

    public DemandeParole obtenirDemandesParPersonneEtReunion(int personneId, int reunionId) throws SQLException {
        String sql = "SELECT id, personne_id, reunion_id, heure_demande, statut FROM demande_parole WHERE personne_id = ? AND reunion_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, personneId);
            pstmt.setInt(2, reunionId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new DemandeParole(
                        rs.getInt("id"),
                        rs.getInt("personne_id"),
                        rs.getInt("reunion_id"),
                        rs.getTimestamp("heure_demande").toLocalDateTime(),
                        DemandeParole.Statut.valueOf(rs.getString("statut"))
                );
            }
            return null;
        }
    }

    public List<DemandeParole> obtenirDemandesPourReunion(int reunionId) throws SQLException {
        List<DemandeParole> demandes = new ArrayList<>();
        String sql = "SELECT id, personne_id, reunion_id, heure_demande, statut FROM demande_parole WHERE reunion_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, reunionId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                demandes.add(new DemandeParole(
                        rs.getInt("id"),
                        rs.getInt("personne_id"),
                        rs.getInt("reunion_id"),
                        rs.getTimestamp("heure_demande").toLocalDateTime(),
                        DemandeParole.Statut.valueOf(rs.getString("statut"))
                ));
            }
        }
        return demandes;
    }

    public boolean changerStatutDemandeParole(int demandeParoleId, DemandeParole.Statut statut) throws SQLException {
        String sql = "UPDATE demande_parole SET statut = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, statut.toString());
            pstmt.setInt(2, demandeParoleId);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    private DemandeParole obtenirDemandeParoleParId(int id) throws SQLException {
        String sql = "SELECT id, personne_id, reunion_id, heure_demande, statut FROM demande_parole WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new DemandeParole(
                        rs.getInt("id"),
                        rs.getInt("personne_id"),
                        rs.getInt("reunion_id"),
                        rs.getTimestamp("heure_demande").toLocalDateTime(),
                        DemandeParole.Statut.valueOf(rs.getString("statut"))
                );
            }
            return null;
        }
    }
}