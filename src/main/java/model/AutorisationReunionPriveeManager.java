package model;


import serveur.Database;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AutorisationReunionPriveeManager {
    private Connection connection;

    public AutorisationReunionPriveeManager() throws SQLException {
        this.connection = Database.getConnection();
    }

    public boolean autoriserAcces(int personneId, int reunionId) throws SQLException {
        String sql = "INSERT INTO autorisation_reunion_privee (personne_id, reunion_id) VALUES (?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, personneId);
            pstmt.setInt(2, reunionId);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            if (e.getSQLState().equals("23000") && e.getMessage().contains("Duplicate entry")) {
                return true; // Considérer comme succès si déjà autorisé
            }
            throw e;
        }
    }

    public boolean retirerAutorisation(int personneId, int reunionId) throws SQLException {
        String sql = "DELETE FROM autorisation_reunion_privee WHERE personne_id = ? AND reunion_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, personneId);
            pstmt.setInt(2, reunionId);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    public List<Personne> obtenirPersonnesAutorisees(int reunionId) throws SQLException {
        List<Personne> autorisees = new ArrayList<>();
        String sql = "SELECT p.id, p.nom, p.prenom, p.login, p.password, p.connecte FROM personne p JOIN autorisation_reunion_privee arp ON p.id = arp.personne_id WHERE arp.reunion_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, reunionId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                autorisees.add(new Personne(
                        rs.getInt("id"),
                        rs.getString("nom"),
                        rs.getString("prenom"),
                        rs.getString("login"),
                        rs.getString("password"),
                        rs.getBoolean("connecte")
                ));
            }
        }
        return autorisees;
    }

    public boolean estAutorise(int personneId, int reunionId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM autorisation_reunion_privee WHERE personne_id = ? AND reunion_id = ?";
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