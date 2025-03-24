package model;

import serveur.Database;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ParticipationManager {
    private Connection connection;

    public ParticipationManager() throws SQLException {
        this.connection = Database.getConnection();
    }

    public boolean entrerDansReunion(int personneId, int reunionId) throws SQLException {
        String sql = "INSERT INTO participation (personne_id, reunion_id) VALUES (?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, personneId);
            pstmt.setInt(2, reunionId);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            // Gérer les doublons (si la personne participe déjà)
            if (e.getSQLState().equals("23000") && e.getMessage().contains("Duplicate entry")) {
                return true; // Considérer comme succès si déjà participant
            }
            throw e;
        }
    }

    public boolean sortirDeReunion(int personneId, int reunionId) throws SQLException {
        String sql = "DELETE FROM participation WHERE personne_id = ? AND reunion_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, personneId);
            pstmt.setInt(2, reunionId);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    public List<Personne> obtenirParticipants(int reunionId) throws SQLException {
        List<Personne> participants = new ArrayList<>();
        String sql = "SELECT p.id, p.nom, p.prenom, p.login, p.password, p.connecte FROM personne p JOIN participation pa ON p.id = pa.personne_id WHERE pa.reunion_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, reunionId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                participants.add(new Personne(
                        rs.getInt("id"),
                        rs.getString("nom"),
                        rs.getString("prenom"),
                        rs.getString("login"),
                        rs.getString("password"),
                        rs.getBoolean("connecte")
                ));
            }
        }
        return participants;
    }

    public boolean estParticipant(int personneId, int reunionId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM participation WHERE personne_id = ? AND reunion_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, personneId);
            pstmt.setInt(2, reunionId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            return false;
        }
    }
}