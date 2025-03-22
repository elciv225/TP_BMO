package model;

import serveur.Database;

import java.sql.Connection;
import java.sql.SQLException;

public class ReunionManager {

    private Connection connection;

    public ReunionManager() throws SQLException {
        connection = Database.getConnection();
    }
}
