package serveur;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Database {
    private static final String URL = "jdbc:mysql://localhost:3306/tpbmo_db";
    private static final String USER = "tpbmo";
    private static final String PASSWORD = "tpbmo";

    private static HikariDataSource ds;
    private static final ExecutorService DB_EXECUTOR = Executors.newFixedThreadPool(10); // Default to 10 threads

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down database executor service...");
            DB_EXECUTOR.shutdown();
            try {
                if (!DB_EXECUTOR.awaitTermination(60, TimeUnit.SECONDS)) {
                    DB_EXECUTOR.shutdownNow();
                    if (!DB_EXECUTOR.awaitTermination(60, TimeUnit.SECONDS)) {
                        System.err.println("Database executor service did not terminate.");
                    }
                }
            } catch (InterruptedException ie) {
                DB_EXECUTOR.shutdownNow();
                Thread.currentThread().interrupt();
            }
            if (ds != null) {
                System.out.println("Closing HikariDataSource...");
                ds.close();
            }
        }));

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(URL);
        config.setUsername(USER);
        config.setPassword(PASSWORD);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        // Recommended settings for MySQL
        config.addDataSourceProperty("serverTimezone", "UTC");
        config.addDataSourceProperty("useSSL", "false");
        config.addDataSourceProperty("allowPublicKeyRetrieval","true");


        ds = new HikariDataSource(config);
    }

    public static Connection getConnection() throws SQLException {
        return ds.getConnection();
    }

    public static ExecutorService getDbExecutor() {
        return DB_EXECUTOR;
    }

    // Explicit shutdown method if needed elsewhere, though shutdown hook is primary
    public static void shutdownExecutor() {
        System.out.println("Explicitly shutting down database executor service...");
        DB_EXECUTOR.shutdown();
        try {
            if (!DB_EXECUTOR.awaitTermination(60, TimeUnit.SECONDS)) {
                DB_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            DB_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
        if (ds != null) {
            System.out.println("Closing HikariDataSource via explicit shutdown...");
            ds.close();
        }
    }
}
