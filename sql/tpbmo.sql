-- ----------------------------------------------------------------
-- Script de création de base de données pour gestion de réunions
-- Version révisée avec suggestions pour l'encodage et la structure
-- ----------------------------------------------------------------

-- Il est crucial de s'assurer que la base de données elle-même est créée avec le bon encodage.
-- Exemple (à exécuter avant de lancer ce script si la base n'existe pas ou pour la modifier) :
-- CREATE DATABASE IF NOT EXISTS votre_nom_de_base_de_donnees
-- CHARACTER SET utf8mb4
-- COLLATE utf8mb4_unicode_ci;
-- USE votre_nom_de_base_de_donnees;

-- ----------------------------------------------------------------
-- 1. Création des tables
-- Ajout de DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci pour une meilleure gestion des caractères.
-- ----------------------------------------------------------------

-- Table Personne
CREATE TABLE IF NOT EXISTS personne
(
    id       INT AUTO_INCREMENT PRIMARY KEY,
    nom      VARCHAR(50)  NOT NULL,
    prenom   VARCHAR(50)  NOT NULL,
    login    VARCHAR(50)  NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL, -- Note: Pour la sécurité, les mots de passe devraient être hashés avant stockage.
    connecte BOOLEAN DEFAULT FALSE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table Reunion
CREATE TABLE IF NOT EXISTS reunion
(
    id              INT AUTO_INCREMENT PRIMARY KEY,
    nom             VARCHAR(100)                              NOT NULL,
    sujet           VARCHAR(255),
    agenda          TEXT,
    debut           DATETIME                                  NOT NULL,
    duree           INT                                       NOT NULL, -- Durée en minutes, par exemple
    type            ENUM ('STANDARD','PRIVEE','DEMOCRATIQUE') NOT NULL,
    organisateur_id INT                                       NOT NULL,
    animateur_id    INT,                                               -- Peut être NULL si pas d'animateur désigné initialement
    statut          ENUM ('PLANIFIEE','OUVERTE','FERMEE')     DEFAULT 'OUVERTE',
    heure_ouverture DATETIME NULL,
    heure_fermeture DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table pour l'association entre personnes et réunions (participation)
CREATE TABLE IF NOT EXISTS participation
(
    personne_id INT NOT NULL,
    reunion_id  INT NOT NULL,
    PRIMARY KEY (personne_id, reunion_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table pour les personnes autorisées dans les réunions privées
CREATE TABLE IF NOT EXISTS autorisation_reunion_privee
(
    personne_id INT NOT NULL,
    reunion_id  INT NOT NULL,
    PRIMARY KEY (personne_id, reunion_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table pour les demandes de parole
CREATE TABLE IF NOT EXISTS demande_parole
(
    id            INT AUTO_INCREMENT PRIMARY KEY,
    personne_id   INT NOT NULL,
    reunion_id    INT NOT NULL,
    heure_demande TIMESTAMP                                DEFAULT CURRENT_TIMESTAMP,
    statut        ENUM ('EN_ATTENTE','ACCORDEE','REFUSEE') DEFAULT 'EN_ATTENTE'
    -- La contrainte UNIQUE (personne_id, reunion_id, statut) a été retirée ici.
    -- Elle empêchait une personne de faire une nouvelle demande 'EN_ATTENTE'
    -- si une demande précédente pour la même réunion avait été 'REFUSEE' ou 'ACCORDEE'.
    -- Une contrainte UNIQUE (personne_id, reunion_id) pourrait être envisagée si une personne
    -- ne peut avoir qu'une seule demande (quel que soit son statut) active à la fois pour une réunion.
    -- La gestion de plusieurs demandes ou de la réactivation devrait alors être gérée par la logique applicative.
    -- Pour l'instant, on permet plusieurs enregistrements si les statuts diffèrent ou si on veut historiser.
    -- Si vous souhaitez qu'une personne ne puisse avoir qu'UNE SEULE demande EN_ATTENTE par réunion,
    -- cette logique est souvent mieux gérée au niveau de l'application ou par des triggers.
    -- Une alternative serait: UNIQUE KEY uk_demande_active (personne_id, reunion_id) si on ne veut qu'une demande par personne/réunion.
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table pour stocker les messages/interventions
CREATE TABLE IF NOT EXISTS message
(
    id          INT AUTO_INCREMENT PRIMARY KEY,
    personne_id INT  NOT NULL,
    reunion_id  INT  NOT NULL,
    contenu     TEXT NOT NULL,
    heure_envoi TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Table pour les invitations en attente
CREATE TABLE IF NOT EXISTS invitation_reunion
(
    id                  INT AUTO_INCREMENT PRIMARY KEY,
    reunion_id          INT NOT NULL,
    personne_invitee_id INT NOT NULL,
    inviteur_id         INT NOT NULL,
    statut              ENUM ('EN_ATTENTE', 'ACCEPTEE', 'REFUSEE') DEFAULT 'EN_ATTENTE',
    date_invitation     TIMESTAMP                                  DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_invitation (reunion_id, personne_invitee_id) -- Assure qu'une personne n'est invitée qu'une fois par réunion
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------
-- 2. Ajout des contraintes de clés étrangères
-- ----------------------------------------------------------------

-- Contraintes pour la table reunion
ALTER TABLE reunion
    ADD CONSTRAINT fk_reunion_organisateur
        FOREIGN KEY (organisateur_id) REFERENCES personne (id)
        ON DELETE RESTRICT ON UPDATE CASCADE, -- Empêche la suppression d'une personne si elle est organisatrice
    ADD CONSTRAINT fk_reunion_animateur
        FOREIGN KEY (animateur_id) REFERENCES personne (id)
        ON DELETE SET NULL ON UPDATE CASCADE; -- Si l'animateur est supprimé, le champ devient NULL

-- Contraintes pour la table participation
ALTER TABLE participation
    ADD CONSTRAINT fk_participation_personne
        FOREIGN KEY (personne_id) REFERENCES personne (id)
        ON DELETE CASCADE ON UPDATE CASCADE, -- Si une personne est supprimée, ses participations le sont aussi
    ADD CONSTRAINT fk_participation_reunion
        FOREIGN KEY (reunion_id) REFERENCES reunion (id)
        ON DELETE CASCADE ON UPDATE CASCADE; -- Si une réunion est supprimée, les participations le sont aussi

-- Contraintes pour la table autorisation_reunion_privee
ALTER TABLE autorisation_reunion_privee
    ADD CONSTRAINT fk_autorisation_personne
        FOREIGN KEY (personne_id) REFERENCES personne (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT fk_autorisation_reunion
        FOREIGN KEY (reunion_id) REFERENCES reunion (id)
        ON DELETE CASCADE ON UPDATE CASCADE;

-- Contraintes pour la table demande_parole
ALTER TABLE demande_parole
    ADD CONSTRAINT fk_demande_personne
        FOREIGN KEY (personne_id) REFERENCES personne (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT fk_demande_reunion
        FOREIGN KEY (reunion_id) REFERENCES reunion (id)
        ON DELETE CASCADE ON UPDATE CASCADE;

-- Contraintes pour la table message
ALTER TABLE message
    ADD CONSTRAINT fk_message_personne
        FOREIGN KEY (personne_id) REFERENCES personne (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT fk_message_reunion
        FOREIGN KEY (reunion_id) REFERENCES reunion (id)
        ON DELETE CASCADE ON UPDATE CASCADE;

-- Contraintes pour la table invitation_reunion
ALTER TABLE invitation_reunion
    ADD CONSTRAINT fk_invitation_reunion
        FOREIGN KEY (reunion_id) REFERENCES reunion (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT fk_invitation_personne_invitee
        FOREIGN KEY (personne_invitee_id) REFERENCES personne (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT fk_invitation_inviteur
        FOREIGN KEY (inviteur_id) REFERENCES personne (id)
        ON DELETE CASCADE ON UPDATE CASCADE; -- Ou ON DELETE RESTRICT si un inviteur ne peut être supprimé tant qu'il a des invitations envoyées

-- ----------------------------------------------------------------
-- 3. Données de test
-- Les caractères accentués devraient être correctement gérés si l'encodage est UTF-8 partout.
-- ----------------------------------------------------------------
INSERT INTO personne (nom, prenom, login, password, connecte)
VALUES ('Assy', 'Eliel Onésime', 'eassy', 'eassy', FALSE), -- Mots de passe en clair sont un risque de sécurité
       ('Ouattara', 'Katié Myriam', 'okatie', 'okatie', FALSE),
       ('Logbo', 'Zoukou Axelle', 'azoukou', 'azoukou', FALSE),
       ('Diby', 'Eunice', 'ediby', 'ediby', FALSE),
       ('Demo', 'User', 'demo', 'demo', FALSE);

-- ----------------------------------------------------------------
-- 4. Remarques sur les "Mises à jour pour installations existantes"
-- Les commandes ALTER TABLE que vous aviez pour ajouter les colonnes
-- statut, heure_ouverture, heure_fermeture à la table 'reunion'
-- sont redondantes si ces colonnes sont déjà définies dans le CREATE TABLE initial.
-- Je les ai donc omises ici car la table 'reunion' est créée avec ces colonnes.
-- Si ce script est destiné à mettre à jour une structure existante *sans* ces colonnes,
-- alors ces ALTER TABLE seraient nécessaires, mais placés conditionnellement ou dans un script de migration séparé.
-- ----------------------------------------------------------------

-- Exemple : Si vous deviez ajouter une contrainte UNIQUE spécifique pour demande_parole :
-- ALTER TABLE demande_parole
-- ADD UNIQUE KEY uk_personne_reunion_demande_en_attente (personne_id, reunion_id)
-- WHERE statut = 'EN_ATTENTE'; -- Note: La syntaxe de condition de contrainte partielle (WHERE) n'est pas supportée par toutes les versions/moteurs SQL (ex: MySQL avant 8.0).
-- Pour MySQL, cela nécessiterait un trigger ou une gestion applicative.

