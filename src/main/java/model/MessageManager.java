package model;
import serveur.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MessageManager {
    private Connection connection;

    public MessageManager() throws SQLException {
        this.connection = Database.getConnection();
    }

    public Message envoyerMessage(int personneId, int reunionId, String contenu) throws SQLException {
        String sql = "INSERT INTO message (personne_id, reunion_id, contenu) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, personneId);
            pstmt.setInt(2, reunionId);
            pstmt.setString(3, contenu);
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int newId = generatedKeys.getInt(1);
                        return obtenirMessageParId(newId);
                    } else {
                        throw new SQLException("Creating message failed, no ID obtained.");
                    }
                }
            } else {
                throw new SQLException("Creating message failed, no rows affected.");
            }
        }
    }

    public List<Message> obtenirMessagesReunion(int reunionId) throws SQLException {
        List<Message> messages = new ArrayList<>();
        String sql = "SELECT m.id, m.personne_id, m.contenu, m.heure_envoi, p.nom, p.prenom " +
                "FROM message m JOIN personne p ON m.personne_id = p.id " +
                "WHERE m.reunion_id = ? ORDER BY m.heure_envoi ASC";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, reunionId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Message message = new Message(
                        rs.getInt("id"),
                        rs.getInt("personne_id"),
                        rs.getInt("reunion_id"),
                        rs.getString("contenu")
                );
                message.setHeureEnvoi(rs.getTimestamp("heure_envoi").toLocalDateTime());
                messages.add(message);
            }
        }
        return messages;
    }

    private Message obtenirMessageParId(int id) throws SQLException {
        String sql = "SELECT id, personne_id, reunion_id, contenu, heure_envoi FROM message WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                Message message = new Message(
                        rs.getInt("id"),
                        rs.getInt("personne_id"),
                        rs.getInt("reunion_id"),
                        rs.getString("contenu")
                );
                message.setHeureEnvoi(rs.getTimestamp("heure_envoi").toLocalDateTime());
                return message;
            }
            return null;
        }
    }
}