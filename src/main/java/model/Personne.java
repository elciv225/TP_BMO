package model;

import org.json.JSONObject;

public class Personne {
    private int id;
    private String nom;
    private String prenom;
    private String login;
    private String password;
    private boolean connecte;

    public Personne(int id, String nom, String prenom, String login, String password, boolean connecte) {
        this.id = id;
        this.nom = nom;
        this.prenom = prenom;
        this.login = login;
        this.password = password;
        this.connecte = connecte;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isConnecte() {
        return connecte;
    }

    public void setConnecte(boolean connecte) {
        this.connecte = connecte;
    }

    public JSONObject toJsonObject() {
        JSONObject json = new JSONObject();
        json.put("id", this.id);
        json.put("nom", this.nom);
        json.put("prenom", this.prenom);
        json.put("login", this.login);
        json.put("password", this.password);
        json.put("connecte", this.connecte);
        return json;
    }
}