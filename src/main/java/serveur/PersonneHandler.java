package serveur;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import model.PersonneManager;

import java.io.IOException;
import java.sql.SQLException;

public class PersonneHandler implements HttpHandler {

    private PersonneManager personneManager;
    private  Gson gson; // Convertisseur Json

    public PersonneHandler() throws SQLException {
        personneManager = new PersonneManager();
        gson = new Gson();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {

    }

    // Récupérer les personnes
    private void handleGet(HttpExchange exchange) throws IOException {

    }

    // Ajouter une personne (inscrire)
    private void handlePost(HttpExchange exchange) throws IOException {

    }

    // Modifier une personne
    private void handlePut(HttpExchange exchange) throws IOException {

    }

    // Supprimer une personne
    private void handleDelete(HttpExchange exchange) throws IOException {

    }

    // Récupérer une personne
    private void handleGetOne(HttpExchange exchange) throws IOException {

    }

    // Inscrire une personne à une réunion
    private void handleInscrire(HttpExchange exchange) throws IOException {

    }

    // Désinscrire une personne d'une réunion
    private void handleDesinscrire(HttpExchange exchange) throws IOException {

    }

    // Récupérer les réunions d'une personne
    private void handleGetReunions(HttpExchange exchange) throws IOException {

    }

}
