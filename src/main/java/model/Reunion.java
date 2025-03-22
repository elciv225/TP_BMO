package model;

import java.time.LocalDate;

public class Reunion {
    int id;
    String nom;
    String sujet;
    LocalDate debut;
    int duree;
    enum type {standard, privee, democratique};
    int idOrganisateur;
    int idAnimateur;

    public Reunion(int id, String nom, String sujet, LocalDate debut, int duree, int idOrganisateur, int idAnimateur) {
        this.id = id;
        this.nom = nom;
        this.sujet = sujet;
        this.debut = debut;
        this.duree = duree;
        this.idOrganisateur = idOrganisateur;
        this.idAnimateur = idAnimateur;
    }
}
