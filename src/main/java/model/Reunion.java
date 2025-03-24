package model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Reunion {
    private int id;
    private String nom;
    private String sujet;
    private String agenda;
    private LocalDate debut;
    private int duree;
    public enum Type {STANDARD, PRIVEE, DEMOCRATIQUE}
    private Type type;
    private int idOrganisateur;
    private int idAnimateur;

    public Reunion(int id, String nom, String sujet, String agenda, LocalDateTime debut, int duree, Type type, int idOrganisateur, int idAnimateur) {
        this.id = id;
        this.nom = nom;
        this.sujet = sujet;
        this.agenda = agenda;
        this.debut = LocalDate.from(debut);
        this.duree = duree;
        this.type = type;
        this.idOrganisateur = idOrganisateur;
        this.idAnimateur = idAnimateur;
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

    public String getSujet() {
        return sujet;
    }

    public void setSujet(String sujet) {
        this.sujet = sujet;
    }

    public String getAgenda() {
        return agenda;
    }

    public void setAgenda(String agenda) {
        this.agenda = agenda;
    }

    public LocalDate getDebut() {
        return debut;
    }

    public void setDebut(LocalDate debut) {
        this.debut = debut;
    }

    public int getDuree() {
        return duree;
    }

    public void setDuree(int duree) {
        this.duree = duree;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public int getIdOrganisateur() {
        return idOrganisateur;
    }

    public void setIdOrganisateur(int idOrganisateur) {
        this.idOrganisateur = idOrganisateur;
    }

    public int getIdAnimateur() {
        return idAnimateur;
    }

    public void setIdAnimateur(int idAnimateur) {
        this.idAnimateur = idAnimateur;
    }
}