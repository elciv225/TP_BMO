package model;

import java.time.LocalDateTime;

public class Reunion {
    private int id;
    private String nom;
    private String sujet;
    private String agenda;
    private LocalDateTime debut; // CORRECTION: utiliser LocalDateTime au lieu de LocalDate
    private int duree;
    private Type type;
    private int idOrganisateur;
    private Integer idAnimateur; // Nullable
    private StatutReunion statutReunion;

    public enum Type {
        STANDARD, PRIVEE, DEMOCRATIQUE
    }

    public enum StatutReunion {
        PLANIFIEE, OUVERTE, CLOTUREE
    }

    // CORRECTION: Constructeur pour création (sans ID)
    public Reunion(String nom, String sujet, String agenda, LocalDateTime debut, int duree, Type type, int idOrganisateur, Integer idAnimateur) {
        this.nom = nom;
        this.sujet = sujet;
        this.agenda = agenda;
        this.debut = debut;
        this.duree = duree;
        this.type = type;
        this.idOrganisateur = idOrganisateur;
        this.idAnimateur = idAnimateur;
        this.statutReunion = StatutReunion.PLANIFIEE; // Default status
    }

    // CORRECTION: Constructeur complet (avec ID, pour les données de la DB)
    public Reunion(int id, String nom, String sujet, String agenda, LocalDateTime debut, int duree, Type type, int idOrganisateur, Integer idAnimateur, StatutReunion statutReunion) {
        this.id = id;
        this.nom = nom;
        this.sujet = sujet;
        this.agenda = agenda;
        this.debut = debut;
        this.duree = duree;
        this.type = type;
        this.idOrganisateur = idOrganisateur;
        this.idAnimateur = idAnimateur;
        this.statutReunion = statutReunion;
    }

    // Getters et Setters
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

    public LocalDateTime getDebut() {
        return debut;
    }

    public void setDebut(LocalDateTime debut) {
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

    public Integer getIdAnimateur() {
        return idAnimateur;
    }

    public void setIdAnimateur(Integer idAnimateur) {
        this.idAnimateur = idAnimateur;
    }

    public StatutReunion getStatutReunion() {
        return statutReunion;
    }

    public void setStatutReunion(StatutReunion statutReunion) {
        this.statutReunion = statutReunion;
    }

    @Override
    public String toString() {
        return "Reunion{" +
                "id=" + id +
                ", nom='" + nom + '\'' +
                ", sujet='" + sujet + '\'' +
                ", agenda='" + agenda + '\'' +
                ", debut=" + debut +
                ", duree=" + duree +
                ", type=" + type +
                ", idOrganisateur=" + idOrganisateur +
                ", idAnimateur=" + idAnimateur +
                ", statutReunion=" + statutReunion +
                '}';
    }
}