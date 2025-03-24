package serveur;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Database {
    private static final String URL = "jdbc:mysql://localhost:3306/tpbmo_db";
    private static final String USER = "tpbmo";
    private static final String PASSWORD = "tpbmo";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static  void closeConnection(Connection connection) throws SQLException {
        connection.close();
    }
}
