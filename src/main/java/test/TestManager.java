package test;

import model.*;
import serveur.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public class TestManager {

    private PersonneManager personneManager;
    private ReunionManager reunionManager;
    private ParticipationManager participationManager;
    private AutorisationReunionPriveeManager autorisationManager;
    private DemandeParoleManager demandeParoleManager;
    private MessageManager messageManager;
    private Connection connection;

    public TestManager() {
        try {
            connection = Database.getConnection();
            personneManager = new PersonneManager();
            reunionManager = new ReunionManager();
            participationManager = new ParticipationManager();
            autorisationManager = new AutorisationReunionPriveeManager();
            demandeParoleManager = new DemandeParoleManager();
            messageManager = new MessageManager();
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("Erreur lors de l'initialisation des Managers ou de la connexion à la base de données.");
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        TestManager testManager = new TestManager();
        testManager.testerPersonneManager();
        testManager.testerReunionManager();
        testManager.testerParticipationManager();
        testManager.testerAutorisationReunionPriveeManager();
        testManager.testerDemandeParoleManager();
        testManager.testerMessageManager();
        testManager.resetDatabase();
        System.out.println("\nTests terminés et base de données réinitialisée.");
    }


    public void testerPersonneManager() {
        System.out.println("\n--- Tests PersonneManager ---");

        // Créer une personne
        Personne personne1 = null;
        try {
            personne1 = personneManager.enregistrerPersonne("Doe", "John", "john.doe", "password123");
            System.out.println("Création de personne : " + (personne1 != null ? "Succès - ID: " + personne1.getId() : "Échec"));
        } catch (SQLException e) {
            System.err.println("Erreur lors de la création de personne : " + e.getMessage());
        }

        Personne personne2 = null;
        try {
            personne2 = personneManager.enregistrerPersonne("Smith", "Jane", "jane.smith", "securePass");
            System.out.println("Création de personne : " + (personne2 != null ? "Succès - ID: " + personne2.getId() : "Échec"));
        } catch (SQLException e) {
            System.err.println("Erreur lors de la création de personne : " + e.getMessage());
        }

        // Connexion
        Personne connecte1 = null;
        if (personne1 != null) {
            try {
                connecte1 = personneManager.connecter("john.doe", "password123");
                System.out.println("Connexion de john.doe : " + (connecte1 != null && connecte1.isConnecte() ? "Succès - Connecté" : "Échec"));
            } catch (SQLException e) {
                System.err.println("Erreur lors de la connexion : " + e.getMessage());
            }
        }

        // Récupérer par ID
        if (personne1 != null) {
            try {
                Personne recuperee = personneManager.obtenirPersonneParId(personne1.getId());
                System.out.println("Récupération par ID (" + personne1.getId() + ") : " + (recuperee != null && recuperee.getNom().equals("Doe") ? "Succès - Nom: " + recuperee.getNom() : "Échec"));
            } catch (SQLException e) {
                System.err.println("Erreur lors de la récupération par ID : " + e.getMessage());
            }
        }

        // Récupérer par login
        if (personne2 != null) {
            try {
                Personne recuperee = personneManager.obtenirPersonneParLogin("jane.smith");
                System.out.println("Récupération par login (jane.smith) : " + (recuperee != null && recuperee.getNom().equals("Smith") ? "Succès - Nom: " + recuperee.getNom() : "Échec"));
            } catch (SQLException e) {
                System.err.println("Erreur lors de la récupération par login : " + e.getMessage());
            }
        }

        // Déconnexion
        if (connecte1 != null) {
            try {
                personneManager.deconnecter(connecte1.getId());
                Personne verifDeconnexion = personneManager.obtenirPersonneParId(connecte1.getId());
                System.out.println("Déconnexion de " + connecte1.getLogin() + " : " + (verifDeconnexion != null && !verifDeconnexion.isConnecte() ? "Succès - Déconnecté" : "Échec"));
            } catch (SQLException e) {
                System.err.println("Erreur lors de la déconnexion : " + e.getMessage());
            }
        }
    }

    public void testerReunionManager() {
        System.out.println("\n--- Tests ReunionManager ---");

        Personne organisateur = null;
        Personne animateur = null;
        try {
            organisateur = personneManager.enregistrerPersonne("Organisateur", "Test", "organisateur", "pwd");
            animateur = personneManager.enregistrerPersonne("Animateur", "Test", "animateur", "pwd");
        } catch (SQLException e) {
            System.err.println("Erreur lors de la création des personnes pour les réunions : " + e.getMessage());
            return;
        }

        Reunion reunion1 = null;
        if (organisateur != null && animateur != null) {
            try {
                LocalDateTime debut = LocalDateTime.now().plusDays(1);
                reunion1 = reunionManager.planifierReunion("Réunion Test 1", "Sujet 1", "Agenda 1", debut, 60, Reunion.Type.STANDARD, organisateur.getId(), animateur.getId());
                System.out.println("Planification de réunion : " + (reunion1 != null ? "Succès - ID: " + reunion1.getId() : "Échec"));
            } catch (SQLException e) {
                System.err.println("Erreur lors de la planification de réunion : " + e.getMessage());
            }
        }

        if (reunion1 != null) {
            try {
                Reunion details = reunionManager.consulterDetailsReunion(reunion1.getId());
                System.out.println("Consultation des détails de la réunion : " + (details != null && details.getNom().equals("Réunion Test 1") ? "Succès - Nom: " + details.getNom() : "Échec"));
            } catch (SQLException e) {
                System.err.println("Erreur lors de la consultation des détails de la réunion : " + e.getMessage());
            }

            try {
                LocalDateTime nouveauDebut = LocalDateTime.now().plusDays(2);
                boolean modifiee = reunionManager.modifierReunion(reunion1.getId(), "Réunion Test Modifiée", "Nouveau Sujet", "Nouvel Agenda", nouveauDebut, 90);
                System.out.println("Modification de la réunion : " + (modifiee ? "Succès" : "Échec"));
                Reunion verifModification = reunionManager.consulterDetailsReunion(reunion1.getId());
                System.out.println("Vérification de la modification : " + (verifModification != null && verifModification.getNom().equals("Réunion Test Modifiée") ? "Succès - Nouveau nom: " + verifModification.getNom() : "Échec"));
            } catch (SQLException e) {
                System.err.println("Erreur lors de la modification de la réunion : " + e.getMessage());
            }

            if (animateur != null) {
                try {
                    boolean ouverte = reunionManager.ouvrirReunion(reunion1.getId(), animateur.getId());
                    System.out.println("Ouverture de la réunion : " + (ouverte ? "Succès" : "Échec"));
                    boolean fermee = reunionManager.cloturerReunion(reunion1.getId(), animateur.getId());
                    System.out.println("Fermeture de la réunion : " + (fermee ? "Succès" : "Échec"));
                } catch (SQLException e) {
                    System.err.println("Erreur lors de l'ouverture/fermeture de la réunion : " + e.getMessage());
                }
            }
        }

        try {
            List<Reunion> toutesReunions = reunionManager.obtenirToutesReunions();
            System.out.println("Récupération de toutes les réunions : " + (toutesReunions != null ? "Succès - Nombre: " + toutesReunions.size() : "Échec"));
        } catch (SQLException e) {
            System.err.println("Erreur lors de la récupération de toutes les réunions : " + e.getMessage());
        }

        if (organisateur != null) {
            try {
                List<Reunion> reunionsOrganisees = reunionManager.obtenirReunionsOrganiseesPar(organisateur.getId());
                System.out.println("Récupération des réunions organisées par " + organisateur.getLogin() + " : " + (reunionsOrganisees != null ? "Succès - Nombre: " + reunionsOrganisees.size() : "Échec"));
            } catch (SQLException e) {
                System.err.println("Erreur lors de la récupération des réunions organisées : " + e.getMessage());
            }
        }
    }

    public void testerParticipationManager() {
        System.out.println("\n--- Tests ParticipationManager ---");

        Personne participant1 = null;
        Personne participant2 = null;
        Reunion reunionTest = null;
        try {
            participant1 = personneManager.enregistrerPersonne("Participant", "Un", "part1", "pwd");
            participant2 = personneManager.enregistrerPersonne("Participant", "Deux", "part2", "pwd");
            Personne organisateur = personneManager.enregistrerPersonne("OrgaPart", "Test", "orga_part", "pwd");
            Personne animateur = personneManager.enregistrerPersonne("AnimateurPart", "Test", "anim_part", "pwd");
            LocalDateTime debut = LocalDateTime.now().plusDays(3);
            reunionTest = reunionManager.planifierReunion("Réunion Participation", "Sujet Part", "Agenda Part", debut, 45, Reunion.Type.STANDARD, organisateur.getId(), animateur.getId());
        } catch (SQLException e) {
            System.err.println("Erreur lors de la création des personnes/réunion pour la participation : " + e.getMessage());
            return;
        }

        if (participant1 != null && reunionTest != null) {
            try {
                boolean entree = participationManager.entrerDansReunion(participant1.getId(), reunionTest.getId());
                System.out.println("Entrée de " + participant1.getLogin() + " dans la réunion : " + (entree ? "Succès" : "Échec"));
                boolean estParticipant = participationManager.estParticipant(participant1.getId(), reunionTest.getId());
                System.out.println("Vérification de la participation de " + participant1.getLogin() + " : " + (estParticipant ? "Succès" : "Échec"));
            } catch (SQLException e) {
                System.err.println("Erreur lors de l'entrée dans la réunion : " + e.getMessage());
            }
        }

        if (participant2 != null && reunionTest != null) {
            try {
                boolean entree = participationManager.entrerDansReunion(participant2.getId(), reunionTest.getId());
                System.out.println("Entrée de " + participant2.getLogin() + " dans la réunion : " + (entree ? "Succès" : "Échec"));
            } catch (SQLException e) {
                System.err.println("Erreur lors de l'entrée dans la réunion : " + e.getMessage());
            }
        }

        if (reunionTest != null) {
            try {
                List<Personne> participants = participationManager.obtenirParticipants(reunionTest.getId());
                System.out.println("Récupération des participants de la réunion : " + (participants != null ? "Succès - Nombre: " + participants.size() : "Échec"));
            } catch (SQLException e) {
                System.err.println("Erreur lors de la récupération des participants : " + e.getMessage());
            }
        }

        if (participant1 != null && reunionTest != null) {
            try {
                boolean sortie = participationManager.sortirDeReunion(participant1.getId(), reunionTest.getId());
                System.out.println("Sortie de " + participant1.getLogin() + " de la réunion : " + (sortie ? "Succès" : "Échec"));
                boolean estParticipantApresSortie = participationManager.estParticipant(participant1.getId(), reunionTest.getId());
                System.out.println("Vérification de la participation de " + participant1.getLogin() + " après la sortie : " + (!estParticipantApresSortie ? "Succès" : "Échec"));
            } catch (SQLException e) {
                System.err.println("Erreur lors de la sortie de la réunion : " + e.getMessage());
            }
        }
    }

    public void testerAutorisationReunionPriveeManager() {
        System.out.println("\n--- Tests AutorisationReunionPriveeManager ---");

        Personne autorise1 = null;
        Personne nonAutorise = null;
        Reunion reunionPrivee = null;
        Personne organisateurPrivee = null;
        Personne animateurPrivee = null;
        try {
            organisateurPrivee = personneManager.enregistrerPersonne("OrgaPriv", "Test", "orga_priv", "pwd");
            animateurPrivee = personneManager.enregistrerPersonne("AnimPriv", "Test", "anim_priv", "pwd");
            autorise1 = personneManager.enregistrerPersonne("Autorise", "Un", "auto1", "pwd");
            nonAutorise = personneManager.enregistrerPersonne("NonAutorise", "Test", "nonauto", "pwd");
            LocalDateTime debut = LocalDateTime.now().plusDays(4);
            reunionPrivee = reunionManager.planifierReunion("Réunion Privée Test", "Sujet Priv", "Agenda Priv", debut, 30, Reunion.Type.PRIVEE, organisateurPrivee.getId(), animateurPrivee.getId());
        } catch (SQLException e) {
            System.err.println("Erreur lors de la création des personnes/réunion privée : " + e.getMessage());
            return;
        }

        if (autorise1 != null && reunionPrivee != null && organisateurPrivee != null) {
            try {
                boolean autorisation = autorisationManager.autoriserAcces(autorise1.getId(), reunionPrivee.getId());
                System.out.println("Autorisation de " + autorise1.getLogin() + " pour la réunion privée : " + (autorisation ? "Succès" : "Échec"));
                boolean estAutorise = autorisationManager.estAutorise(autorise1.getId(), reunionPrivee.getId());
                System.out.println("Vérification de l'autorisation de " + autorise1.getLogin() + " : " + (estAutorise ? "Succès" : "Échec"));
            } catch (SQLException e) {
                System.err.println("Erreur lors de l'autorisation : " + e.getMessage());
            }
        }

        if (reunionPrivee != null) {
            try {
                List<Personne> personnesAutorisees = autorisationManager.obtenirPersonnesAutorisees(reunionPrivee.getId());
                System.out.println("Récupération des personnes autorisées pour la réunion privée : " + (personnesAutorisees != null ? "Succès - Nombre: " + personnesAutorisees.size() : "Échec"));
            } catch (SQLException e) {
                System.err.println("Erreur lors de la récupération des personnes autorisées : " + e.getMessage());
            }
        }

        if (autorise1 != null && reunionPrivee != null) {
            try {
                boolean retrait = autorisationManager.retirerAutorisation(autorise1.getId(), reunionPrivee.getId());
                System.out.println("Retrait de l'autorisation de " + autorise1.getLogin() + " : " + (retrait ? "Succès" : "Échec"));
                boolean estAutoriseApresRetrait = autorisationManager.estAutorise(autorise1.getId(), reunionPrivee.getId());
                System.out.println("Vérification de l'autorisation après retrait : " + (!estAutoriseApresRetrait ? "Succès" : "Échec"));
            } catch (SQLException e) {
                System.err.println("Erreur lors du retrait de l'autorisation : " + e.getMessage());
            }
        }

        if (nonAutorise != null && reunionPrivee != null) {
            try {
                boolean estAutoriseNon = autorisationManager.estAutorise(nonAutorise.getId(), reunionPrivee.getId());
                System.out.println("Vérification de l'autorisation pour une personne non autorisée : " + (!estAutoriseNon ? "Succès" : "Échec"));
            } catch (SQLException e) {
                System.err.println("Erreur lors de la vérification de l'autorisation : " + e.getMessage());
            }
        }
    }

    public void testerDemandeParoleManager() {
        System.out.println("\n--- Tests DemandeParoleManager ---");

        Personne demandeur1 = null;
        Personne demandeur2 = null;
        Reunion reunionParole = null;
        Personne organisateurParole = null;
        Personne animateurParole = null;
        try {
            organisateurParole = personneManager.enregistrerPersonne("OrgaParole", "Test", "orga_parole", "pwd");
            animateurParole = personneManager.enregistrerPersonne("AnimParole", "Test", "anim_parole", "pwd");
            demandeur1 = personneManager.enregistrerPersonne("Demandeur", "Un", "dem1", "pwd");
            demandeur2 = personneManager.enregistrerPersonne("Demandeur", "Deux", "dem2", "pwd");
            LocalDateTime debut = LocalDateTime.now().plusDays(5);
            reunionParole = reunionManager.planifierReunion("Réunion Parole Test", "Sujet Parole", "Agenda Parole", debut, 20, Reunion.Type.STANDARD, organisateurParole.getId(), animateurParole.getId());
            participationManager.entrerDansReunion(demandeur1.getId(), reunionParole.getId());
            participationManager.entrerDansReunion(demandeur2.getId(), reunionParole.getId());
        } catch (SQLException e) {
            System.err.println("Erreur lors de la création des personnes/réunion pour la demande de parole : " + e.getMessage());
            return;
        }

        if (demandeur1 != null && reunionParole != null) {
            try {
                DemandeParole demande1 = demandeParoleManager.demanderParole(demandeur1.getId(), reunionParole.getId());
                System.out.println("Demande de parole de " + demandeur1.getLogin() + " : " + (demande1 != null ? "Succès - ID: " + demande1.getId() : "Échec"));
                List<DemandeParole> enAttente = demandeParoleManager.obtenirDemandesEnAttente(reunionParole.getId());
                System.out.println("Demandes en attente : " + (enAttente != null && enAttente.size() == 1 ? "Succès - Nombre: " + enAttente.size() : "Échec"));
                if (demande1 != null && animateurParole != null) {
                    boolean accord = demandeParoleManager.accordParole(demande1.getId(), animateurParole.getId());
                    System.out.println("Accord de parole à " + demandeur1.getLogin() + " : " + (accord ? "Succès" : "Échec"));
                    boolean refus = demandeParoleManager.refuserParole(demande1.getId(), animateurParole.getId());
                    System.out.println("Refus de parole à " + demandeur1.getLogin() + " (test du refus) : " + (refus ? "Succès" : "Échec"));
                    DemandeParole demande1Bis = demandeParoleManager.obtenirDemandesParPersonneEtReunion(demandeur1.getId(), reunionParole.getId());
                    System.out.println("Statut de la demande après refus : " + (demande1Bis != null && demande1Bis.getStatut() == DemandeParole.Statut.REFUSEE ? "Succès - Statut: " + demande1Bis.getStatut() : "Échec"));
                }
            } catch (SQLException e) {
                System.err.println("Erreur lors de la demande/gestion de parole : " + e.getMessage());
            }
        }

        if (demandeur2 != null && reunionParole != null && animateurParole != null) {
            try {
                DemandeParole demande2 = demandeParoleManager.demanderParole(demandeur2.getId(), reunionParole.getId());
                System.out.println("Demande de parole de " + demandeur2.getLogin() + " : " + (demande2 != null ? "Succès - ID: " + demande2.getId() : "Échec"));
                List<DemandeParole> enAttenteApresDeuxieme = demandeParoleManager.obtenirDemandesEnAttente(reunionParole.getId());
                System.out.println("Demandes en attente après deuxième demande : " + (enAttenteApresDeuxieme != null && enAttenteApresDeuxieme.size() == 1 ? "Succès - Nombre: " + enAttenteApresDeuxieme.size() : "Échec"));
                DemandeParole prochaine = demandeParoleManager.obtenirProchaineDemandeParole(reunionParole.getId());
                System.out.println("Prochaine demande de parole (PAPS) : " + (prochaine != null && prochaine.getPersonneId() == demandeur2.getId() ? "Succès - Demandeur: " + prochaine.getPersonneId() : "Échec"));
                boolean accord2 = demandeParoleManager.accordParole(demande2.getId(), animateurParole.getId());
                System.out.println("Accord de parole à " + demandeur2.getLogin() + " : " + (accord2 ? "Succès" : "Échec"));
            } catch (SQLException e) {
                System.err.println("Erreur lors de la demande/gestion de parole (deuxième demandeur) : " + e.getMessage());
            }
        }

        if (reunionParole != null) {
            try {
                List<DemandeParole> toutesDemandes = demandeParoleManager.obtenirDemandesPourReunion(reunionParole.getId());
                System.out.println("Récupération de toutes les demandes de parole pour la réunion : " + (toutesDemandes != null ? "Succès - Nombre: " + toutesDemandes.size() : "Échec"));
            } catch (SQLException e) {
                System.err.println("Erreur lors de la récupération de toutes les demandes de parole : " + e.getMessage());
            }
        }
    }

    public void testerMessageManager() {
        System.out.println("\n--- Tests MessageManager ---");

        Personne auteurMessage = null;
        Reunion reunionMessage = null;
        try {
            auteurMessage = personneManager.enregistrerPersonne("AuteurMsg", "Test", "auteur_msg", "pwd");
            Personne organisateurMsg = personneManager.enregistrerPersonne("OrgaMsg", "Test", "orga_msg", "pwd");
            Personne animateurMsg = personneManager.enregistrerPersonne("AnimMsg", "Test", "anim_msg", "pwd");
            LocalDateTime debut = LocalDateTime.now().plusDays(6);
            reunionMessage = reunionManager.planifierReunion("Réunion Message Test", "Sujet Msg", "Agenda Msg", debut, 15, Reunion.Type.STANDARD, organisateurMsg.getId(), animateurMsg.getId());
            participationManager.entrerDansReunion(auteurMessage.getId(), reunionMessage.getId());
        } catch (SQLException e) {
            System.err.println("Erreur lors de la création des personnes/réunion pour les messages : " + e.getMessage());
            return;
        }

        if (auteurMessage != null && reunionMessage != null) {
            try {
                Message message1 = messageManager.envoyerMessage(auteurMessage.getId(), reunionMessage.getId(), "Bonjour à tous !");
                System.out.println("Envoi du premier message : " + (message1 != null ? "Succès - ID: " + message1.getId() : "Échec"));
                Message message2 = messageManager.envoyerMessage(auteurMessage.getId(), reunionMessage.getId(), "Comment ça va ?");
                System.out.println("Envoi du deuxième message : " + (message2 != null ? "Succès - ID: " + message2.getId() : "Échec"));

                List<Message> messages = messageManager.obtenirMessagesReunion(reunionMessage.getId());
                System.out.println("Récupération des messages de la réunion : " + (messages != null && messages.size() == 2 ? "Succès - Nombre: " + messages.size() : "Échec"));
                if (messages != null) {
                    for (Message msg : messages) {
                        System.out.println("  Message ID: " + msg.getId() + ", Contenu: " + msg.getContenu() + ", Auteur ID: " + msg.getIdPersonne());
                    }
                }
            } catch (SQLException e) {
                System.err.println("Erreur lors de l'envoi/récupération des messages : " + e.getMessage());
            }
        }
    }

    public void resetDatabase() {
        System.out.println("\n--- Réinitialisation de la base de données ---");
        try {
            // Désactiver les contraintes de clés étrangères temporairement
            try (PreparedStatement pstmt = connection.prepareStatement("SET FOREIGN_KEY_CHECKS = 0")) {
                pstmt.executeUpdate();
            }

            // Tronquer les tables
            truncateTable("message");
            truncateTable("demande_parole");
            truncateTable("autorisation_reunion_privee");
            truncateTable("participation");
            truncateTable("reunion");
            truncateTable("personne");

            // Réactiver les contraintes de clés étrangères
            try (PreparedStatement pstmt = connection.prepareStatement("SET FOREIGN_KEY_CHECKS = 1")) {
                pstmt.executeUpdate();
            }
            System.out.println("Base de données réinitialisée avec succès.");

        } catch (SQLException e) {
            System.err.println("Erreur lors de la réinitialisation de la base de données : " + e.getMessage());
        }
    }

    private void truncateTable(String tableName) throws SQLException {
        try (PreparedStatement pstmt = connection.prepareStatement("TRUNCATE TABLE " + tableName)) {
            pstmt.executeUpdate();
            System.out.println("Table " + tableName + " vidée.");
        }
    }
}