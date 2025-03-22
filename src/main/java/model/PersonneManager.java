package model;

import serveur.Database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PersonneManager {

    private Connection connection;

    public PersonneManager() throws SQLException {
        connection = Database.getConnection();
    }

    public boolean createPersone(Personne personne) throws SQLException {
        String request = "INSERT INTO personne (nom, prenom, login, password) VALUES (?, ?, ?, ?)";
        PreparedStatement stmt = connection.prepareStatement(request);
        stmt.setString(1, personne.getNom());
        stmt.setString(2, personne.getPrenom());
        stmt.setString(3, personne.getLogin());
        stmt.setString(4, personne.getPassword());
        connection.close();
        return false;
    }

    public boolean updatePersonne(Personne personne) throws SQLException {
        String request = "UPDATE personne SET nom = ?, prenom = ?, login = ?, password = ? WHERE id = ?";
        PreparedStatement stmt = connection.prepareStatement(request);
        stmt.setString(1, personne.getNom());
        stmt.setString(2, personne.getPrenom());
        stmt.setString(3, personne.getLogin());
        stmt.setString(4, personne.getPassword());
        stmt.setInt(5, personne.getId());
        connection.close();
        return false;
    }

    public boolean deletePersonne(int id) throws SQLException {
        String request = "DELETE FROM personne WHERE id = ?";
        PreparedStatement stmt = connection.prepareStatement(request);
        stmt.setInt(1, id);
        connection.close();
        return false;
    }

    public Personne getPersonneById(int id) throws SQLException {
        String request = "SELECT * FROM personne WHERE id = ?";
        PreparedStatement stmt = connection.prepareStatement(request);
        stmt.setInt(1, id);
        connection.close();
        return null;
    }

    public List<Personne> getAllPersonnes() throws SQLException {
        List<Personne> personnes = new ArrayList<>();
        String request = "SELECT * FROM personne";
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery(request);
        while (rs.next()) {
            Personne personne = new Personne();
            personne.setId(rs.getInt("id"));
            personne.setNom(rs.getString("nom"));
            personne.setPrenom(rs.getString("prenom"));
            personne.setLogin(rs.getString("login"));
            personne.setPassword(rs.getString("password"));
            personnes.add(personne);
        }
        connection.close();
        return null;
    }


}
