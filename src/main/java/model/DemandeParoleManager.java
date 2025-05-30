package model;

import serveur.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DemandeParoleManager {
    private Connection connection;

    public DemandeParoleManager() throws SQLException {
        this.connection = Database.getConnection();
    }

    /**
     * Crée une demande de parole.
     * Supprime d'abord les anciennes demandes REFUSEE ou TERMINEE pour cet utilisateur et cette réunion.
     * Ensuite, tente d'insérer une nouvelle demande EN_ATTENTE.
     * Si une demande EN_ATTENTE ou ACCORDEE existe déjà (violation de contrainte unique), une SQLException sera levée.
     */
    public DemandeParole creerDemande(int personneId, int reunionId) throws SQLException {
        // D'abord, supprimer les demandes REFUSEE ou TERMINEE pour cette personne et cette réunion
        String deleteSql = "DELETE FROM demande_parole WHERE personne_id = ? AND reunion_id = ? AND (statut = ? OR statut = ?)";
        try (PreparedStatement pstmtDelete = connection.prepareStatement(deleteSql)) {
            pstmtDelete.setInt(1, personneId);
            pstmtDelete.setInt(2, reunionId);
            pstmtDelete.setString(3, DemandeParole.StatutDemande.REFUSEE.toString());
            pstmtDelete.setString(4, DemandeParole.StatutDemande.TERMINEE.toString());
            pstmtDelete.executeUpdate();
        }

        // Ensuite, insérer la nouvelle demande EN_ATTENTE
        // Le champ heure_demande utilisera la valeur par défaut CURRENT_TIMESTAMP de la DB
        String insertSql = "INSERT INTO demande_parole (personne_id, reunion_id, statut) VALUES (?, ?, ?)";
        try (PreparedStatement pstmtInsert = connection.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            pstmtInsert.setInt(1, personneId);
            pstmtInsert.setInt(2, reunionId);
            pstmtInsert.setString(3, DemandeParole.StatutDemande.EN_ATTENTE.toString());
            
            int affectedRows = pstmtInsert.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmtInsert.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int newId = generatedKeys.getInt(1);
                        return obtenirDemandeParoleParId(newId); // Récupère la demande complète avec heure_demande
                    } else {
                        throw new SQLException("La création de la demande a échoué, aucun ID obtenu.");
                    }
                }
            } else {
                throw new SQLException("La création de la demande a échoué, aucune ligne affectée.");
            }
        } 
        // La SQLException due à une contrainte unique (par exemple, si EN_ATTENTE existe déjà) sera propagée.
    }
    
    /**
     * Récupère toutes les demandes de parole en attente pour une réunion, triées par heure de demande.
     */
    public List<DemandeParole> obtenirDemandesEnAttente(int reunionId) throws SQLException {
        List<DemandeParole> demandes = new ArrayList<>();
        String sql = "SELECT id, personne_id, reunion_id, heure_demande, statut FROM demande_parole WHERE reunion_id = ? AND statut = ? ORDER BY heure_demande ASC";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, reunionId);
            pstmt.setString(2, DemandeParole.StatutDemande.EN_ATTENTE.toString());
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                demandes.add(new DemandeParole(
                        rs.getInt("id"),
                        rs.getInt("personne_id"),
                        rs.getInt("reunion_id"),
                        rs.getTimestamp("heure_demande").toLocalDateTime(),
                        DemandeParole.StatutDemande.valueOf(rs.getString("statut"))
                ));
            }
        }
        return demandes;
    }

    /**
     * Change le statut d'une demande de parole spécifique.
     */
    public boolean changerStatutDemande(int demandeId, DemandeParole.StatutDemande nouveauStatut) throws SQLException {
        String sql = "UPDATE demande_parole SET statut = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, nouveauStatut.toString());
            pstmt.setInt(2, demandeId);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    /**
     * Récupère la demande de parole actuellement accordée pour une réunion.
     * Il ne devrait y en avoir qu'une au maximum.
     */
    public DemandeParole obtenirDemandeActuelle(int reunionId) throws SQLException {
        String sql = "SELECT id, personne_id, reunion_id, heure_demande, statut FROM demande_parole WHERE reunion_id = ? AND statut = ? LIMIT 1";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, reunionId);
            pstmt.setString(2, DemandeParole.StatutDemande.ACCORDEE.toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new DemandeParole(
                        rs.getInt("id"),
                        rs.getInt("personne_id"),
                        rs.getInt("reunion_id"),
                        rs.getTimestamp("heure_demande").toLocalDateTime(),
                        DemandeParole.StatutDemande.valueOf(rs.getString("statut"))
                );
            }
            return null;
        }
    }
    
    /**
     * Récupère une demande de parole spécifique pour une personne et une réunion avec un statut donné.
     * Utile pour vérifier si un utilisateur a déjà une demande EN_ATTENTE ou ACCORDEE.
     */
    public DemandeParole obtenirDemandeParPersonneEtReunion(int personneId, int reunionId, DemandeParole.StatutDemande statut) throws SQLException {
        String sql = "SELECT id, personne_id, reunion_id, heure_demande, statut FROM demande_parole WHERE personne_id = ? AND reunion_id = ? AND statut = ? LIMIT 1";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, personneId);
            pstmt.setInt(2, reunionId);
            pstmt.setString(3, statut.toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new DemandeParole(
                        rs.getInt("id"),
                        rs.getInt("personne_id"),
                        rs.getInt("reunion_id"),
                        rs.getTimestamp("heure_demande").toLocalDateTime(),
                        DemandeParole.StatutDemande.valueOf(rs.getString("statut"))
                );
            }
            return null;
        }
    }

    /**
     * Supprime les demandes de parole terminées ou refusées pour une réunion spécifique.
     * Méthode optionnelle de nettoyage.
     */
    public int supprimerDemandesTermineesOuRefusees(int reunionId) throws SQLException {
        String sql = "DELETE FROM demande_parole WHERE reunion_id = ? AND (statut = ? OR statut = ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, reunionId);
            pstmt.setString(2, DemandeParole.StatutDemande.TERMINEE.toString());
            pstmt.setString(3, DemandeParole.StatutDemande.REFUSEE.toString());
            return pstmt.executeUpdate();
        }
    }

    /**
     * Récupère la prochaine demande de parole en attente pour une réunion (la plus ancienne).
     * Principalement utilisé pour les réunions de type démocratique.
     */
    public DemandeParole obtenirProchaineDemandeAutomatique(int reunionId) throws SQLException {
        String sql = "SELECT id, personne_id, reunion_id, heure_demande, statut FROM demande_parole WHERE reunion_id = ? AND statut = ? ORDER BY heure_demande ASC LIMIT 1";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, reunionId);
            pstmt.setString(2, DemandeParole.StatutDemande.EN_ATTENTE.toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new DemandeParole(
                        rs.getInt("id"),
                        rs.getInt("personne_id"),
                        rs.getInt("reunion_id"),
                        rs.getTimestamp("heure_demande").toLocalDateTime(),
                        DemandeParole.StatutDemande.valueOf(rs.getString("statut"))
                );
            }
            return null;
        }
    }

    // Méthodes existantes non listées dans les exigences mais potentiellement utiles ou à nettoyer :
    // - accordParole(int demandeParoleId, int animateurId) -> Remplacé par changerStatutDemande
    // - refuserParole(int demandeParoleId, int animateurId) -> Remplacé par changerStatutDemande
    // - obtenirDemandesPourReunion(int reunionId) -> Peut être utile, mais pas dans les exigences directes, à garder pour l'instant.
    // - changerStatutDemandeParole(int demandeParoleId, DemandeParole.Statut statut) -> Remplacé par changerStatutDemande
    
    // Garder cette méthode utilitaire privée
    private DemandeParole obtenirDemandeParoleParId(int id) throws SQLException {
        String sql = "SELECT id, personne_id, reunion_id, heure_demande, statut FROM demande_parole WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new DemandeParole(
                        rs.getInt("id"),
                        rs.getInt("personne_id"),
                        rs.getInt("reunion_id"),
                        rs.getTimestamp("heure_demande").toLocalDateTime(),
                        DemandeParole.StatutDemande.valueOf(rs.getString("statut")) // Utiliser StatutDemande
                );
            }
            return null;
        }
    }

    // Pour conserver une méthode qui liste toutes les demandes d'une réunion si besoin (non spécifié mais existait)
    public List<DemandeParole> obtenirToutesDemandesPourReunion(int reunionId) throws SQLException {
        List<DemandeParole> demandes = new ArrayList<>();
        String sql = "SELECT id, personne_id, reunion_id, heure_demande, statut FROM demande_parole WHERE reunion_id = ? ORDER BY heure_demande ASC";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, reunionId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                demandes.add(new DemandeParole(
                        rs.getInt("id"),
                        rs.getInt("personne_id"),
                        rs.getInt("reunion_id"),
                        rs.getTimestamp("heure_demande").toLocalDateTime(),
                        DemandeParole.StatutDemande.valueOf(rs.getString("statut"))
                ));
            }
        }
        return demandes;
    }
}