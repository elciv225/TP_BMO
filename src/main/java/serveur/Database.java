package serveur;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Database {

    private static final String URL = "jdbc:mysql://localhost:3307/tpbmo_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String USER = "tpbmo";
    private static final String PASSWORD = "tpbmo";

    // Variable pour garder une référence au driver
    private static boolean driverLoaded = false;

    static {
        // CORRECTION: Chargement explicite du driver MySQL
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            driverLoaded = true;
            System.out.println("Driver MySQL chargé avec succès");
        } catch (ClassNotFoundException e) {
            System.err.println("ERREUR: Driver MySQL non trouvé. Assurez-vous que mysql-connector-java est dans le classpath.");
            System.err.println("Ajoutez cette dépendance dans pom.xml:");
            System.err.println("<dependency>");
            System.err.println("    <groupId>mysql</groupId>");
            System.err.println("    <artifactId>mysql-connector-java</artifactId>");
            System.err.println("    <version>8.0.33</version>");
            System.err.println("</dependency>");
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        if (!driverLoaded) {
            throw new SQLException("Driver MySQL non chargé. Vérifiez que mysql-connector-java est dans le classpath.");
        }

        try {
            Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
            System.out.println("Connexion à la base de données établie avec succès");
            return connection;
        } catch (SQLException e) {
            System.err.println("Erreur de connexion à la base de données:");
            System.err.println("URL: " + URL);
            System.err.println("User: " + USER);
            System.err.println("Erreur: " + e.getMessage());

            // Messages d'aide pour le debugging
            if (e.getMessage().contains("Access denied")) {
                System.err.println("SOLUTION: Vérifiez les identifiants de la base de données");
            } else if (e.getMessage().contains("Connection refused")) {
                System.err.println("SOLUTION: Vérifiez que MySQL est démarré et accessible sur le port 3306");
            } else if (e.getMessage().contains("Unknown database")) {
                System.err.println("SOLUTION: Créez la base de données 'tpbmo_db' ou vérifiez son nom");
            }

            throw e;
        }
    }

    public static void closeConnection(Connection connection) throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
            System.out.println("Connexion à la base de données fermée");
        }
    }

    /**
     * Teste la connexion à la base de données
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            System.err.println("Test de connexion échoué: " + e.getMessage());
            return false;
        }
    }
}