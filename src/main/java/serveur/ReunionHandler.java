package serveur;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import model.ReunionManager;

import java.io.IOException;
import java.sql.SQLException;

public class ReunionHandler implements HttpHandler {

    private ReunionManager reunionManager;
    private Gson gson; // Convertisseur JSON

    public ReunionHandler() throws SQLException {
        reunionManager = new ReunionManager();
        gson = new Gson();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        switch (method) {
            case "GET":
                handleGet(exchange);
                break;
            case "POST":
                handlePost(exchange);
                break;
            case "PUT":
                handlePut(exchange);
                break;
            case "DELETE":
                handleDelete(exchange);
                break;
            default:
                exchange.sendResponseHeaders(405, -1); // Méthode non autorisée
        }
    }

    // Récupérer la liste des réunions
    private void handleGet(HttpExchange exchange) throws IOException {
        // Logique pour récupérer les réunions depuis la base
    }

    // Ajouter une réunion
    private void handlePost(HttpExchange exchange) throws IOException {
        // Logique pour ajouter une réunion dans la base
    }

    // Modifier une réunion
    private void handlePut(HttpExchange exchange) throws IOException {
        // Logique pour modifier une réunion existante
    }

    // Supprimer une réunion
    private void handleDelete(HttpExchange exchange) throws IOException {
        // Logique pour supprimer une réunion
    }

    // Récupérer une réunion spécifique
    private void handleGetOne(HttpExchange exchange) throws IOException {
        // Logique pour récupérer une réunion par son ID
    }

    // Ajouter une personne à une réunion
    private void handleAjouterParticipant(HttpExchange exchange) throws IOException {
        // Logique pour ajouter un participant
    }

    // Supprimer une personne d'une réunion
    private void handleRetirerParticipant(HttpExchange exchange) throws IOException {
        // Logique pour retirer un participant
    }

    // Lister les participants d'une réunion
    private void handleListerParticipants(HttpExchange exchange) throws IOException {
        // Logique pour récupérer la liste des participants d'une réunion
    }
}
