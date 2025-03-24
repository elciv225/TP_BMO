package model;

public class Participation {
    private int personneId;
    private int reunionId;

    public Participation(int personneId, int reunionId) {
        this.personneId = personneId;
        this.reunionId = reunionId;
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
}