package model;

import java.time.LocalDateTime;

public class DemandeParole {
    private int id;
    private int personneId;
    private int reunionId;
    private LocalDateTime heureDemande;
    public enum Statut {EN_ATTENTE, ACCORDEE, REFUSEE}
    private Statut statut;

    public DemandeParole(int id, int personneId, int reunionId, LocalDateTime heureDemande, Statut statut) {
        this.id = id;
        this.personneId = personneId;
        this.reunionId = reunionId;
        this.heureDemande = LocalDateTime.now();
        this.statut = statut;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getPersonneId() {
        return personneId;
    }

    public void setPersonneId(int personneId) {
        this.personneId = personneId;
    }

    public int getReunionId() {
        return reunionId;
    }

    public void setReunionId(int reunionId) {
        this.reunionId = reunionId;
    }

    public LocalDateTime getHeureDemande() {
        return heureDemande;
    }

    public void setHeureDemande(LocalDateTime heureDemande) {
        this.heureDemande = heureDemande;
    }

    public Statut getStatut() {
        return statut;
    }

    public void setStatut(Statut statut) {
        this.statut = statut;
    }
}