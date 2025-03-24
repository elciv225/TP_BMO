package model;
import model.Personne;
import serveur.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class PersonneManager {
    private Connection connection;

    public PersonneManager() throws SQLException {
        this.connection = Database.getConnection();
    }

    public Personne connecter(String login, String password) throws SQLException {
        String sql = "SELECT id, nom, prenom, login, password, connecte FROM personne WHERE login = ? AND password = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, login);
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                Personne personne = new Personne(
                        rs.getInt("id"),
                        rs.getString("nom"),
                        rs.getString("prenom"),
                        rs.getString("login"),
                        rs.getString("password"),
                        rs.getBoolean("connecte")
                );
                mettreAJourStatutConnexion(personne.getId(), true);
                personne.setConnecte(true);
                return personne;
            }
            return null;
        }
    }

    public void deconnecter(int personneId) throws SQLException {
        mettreAJourStatutConnexion(personneId, false);
    }

    public Personne enregistrerPersonne(String nom, String prenom, String login, String password) throws SQLException {
        String sql = "INSERT INTO personne (nom, prenom, login, password) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, nom);
            pstmt.setString(2, prenom);
            pstmt.setString(3, login);
            pstmt.setString(4, password);
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int newId = generatedKeys.getInt(1);
                        return obtenirPersonneParId(newId);
                    } else {
                        throw new SQLException("Creating user failed, no ID obtained.");
                    }
                }
            } else {
                throw new SQLException("Creating user failed, no rows affected.");
            }
        }
    }

    public Personne obtenirPersonneParId(int id) throws SQLException {
        String sql = "SELECT id, nom, prenom, login, password, connecte FROM personne WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Personne(
                        rs.getInt("id"),
                        rs.getString("nom"),
                        rs.getString("prenom"),
                        rs.getString("login"),
                        rs.getString("password"),
                        rs.getBoolean("connecte")
                );
            }
            return null;
        }
    }

    public Personne obtenirPersonneParLogin(String login) throws SQLException {
        String sql = "SELECT id, nom, prenom, login, password, connecte FROM personne WHERE login = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, login);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Personne(
                        rs.getInt("id"),
                        rs.getString("nom"),
                        rs.getString("prenom"),
                        rs.getString("login"),
                        rs.getString("password"),
                        rs.getBoolean("connecte")
                );
            }
            return null;
        }
    }

    private void mettreAJourStatutConnexion(int personneId, boolean connecte) throws SQLException {
        String sql = "UPDATE personne SET connecte = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setBoolean(1, connecte);
            pstmt.setInt(2, personneId);
            pstmt.executeUpdate();
        }
    }
}