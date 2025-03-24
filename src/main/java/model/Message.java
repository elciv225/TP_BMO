package model;

import java.time.LocalDateTime;

public class Message {
    private int id;
    private int idPersonne;
    private int idReunion;
    private String contenu;
    private LocalDateTime heureEnvoi;

    public Message(int id, int idPersonne, int idReunion, String contenu) {
        this.id = id;
        this.idPersonne = idPersonne;
        this.idReunion = idReunion;
        this.contenu = contenu;
        this.heureEnvoi = LocalDateTime.now();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getIdPersonne() {
        return idPersonne;
    }

    public void setIdPersonne(int idPersonne) {
        this.idPersonne = idPersonne;
    }

    public int getIdReunion() {
        return idReunion;
    }

    public void setIdReunion(int idReunion) {
        this.idReunion = idReunion;
    }

    public String getContenu() {
        return contenu;
    }

    public void setContenu(String contenu) {
        this.contenu = contenu;
    }

    public LocalDateTime getHeureEnvoi() {
        return heureEnvoi;
    }

    public void setHeureEnvoi(LocalDateTime heureEnvoi) {
        this.heureEnvoi = heureEnvoi;
    }
}