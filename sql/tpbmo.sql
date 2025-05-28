-- 1. Création des tables (sans contraintes complexes)

-- Table Personne
CREATE TABLE IF NOT EXISTS personne
(
    id    INT AUTO_INCREMENT PRIMARY KEY,
    nom   VARCHAR(50) NOT NULL,
    prenom VARCHAR(50) NOT NULL,
    login VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    connecte BOOLEAN DEFAULT FALSE
);

-- Table Reunion
CREATE TABLE IF NOT EXISTS reunion
(
    id              INT AUTO_INCREMENT PRIMARY KEY,
    nom             VARCHAR(100)                              NOT NULL,
    sujet           VARCHAR(255),
    agenda          TEXT,
    debut           DATETIME                                  NOT NULL,
    duree           INT                                       NOT NULL,
    type            ENUM ('STANDARD','PRIVEE','DEMOCRATIQUE') NOT NULL,
    organisateur_id INT                                       NOT NULL,
    animateur_id    INT
);

-- Table pour l'association entre personnes et réunions (participation)
CREATE TABLE IF NOT EXISTS participation
(
    personne_id INT NOT NULL,
    reunion_id  INT NOT NULL,
    PRIMARY KEY (personne_id, reunion_id)
);

-- Table pour les personnes autorisées dans les réunions privées
CREATE TABLE IF NOT EXISTS autorisation_reunion_privee
(
    personne_id INT NOT NULL,
    reunion_id  INT NOT NULL,
    PRIMARY KEY (personne_id, reunion_id)
);

-- Table pour les demandes de parole
CREATE TABLE IF NOT EXISTS demande_parole
(
    id            INT AUTO_INCREMENT PRIMARY KEY,
    personne_id   INT NOT NULL,
    reunion_id    INT NOT NULL,
    heure_demande TIMESTAMP                                DEFAULT CURRENT_TIMESTAMP,
    statut        ENUM ('EN_ATTENTE','ACCORDEE','REFUSEE') DEFAULT 'EN_ATTENTE',
    UNIQUE (personne_id, reunion_id, statut)
);

-- Table pour stocker les messages/interventions
CREATE TABLE IF NOT EXISTS message
(
    id          INT AUTO_INCREMENT PRIMARY KEY,
    personne_id INT  NOT NULL,
    reunion_id  INT  NOT NULL,
    contenu     TEXT NOT NULL,
    heure_envoi TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Ajout des contraintes de clés étrangères

-- Contraintes pour la table reunion
ALTER TABLE reunion
    ADD CONSTRAINT fk_reunion_organisateur
        FOREIGN KEY (organisateur_id) REFERENCES personne (id),
    ADD CONSTRAINT fk_reunion_animateur
        FOREIGN KEY (animateur_id) REFERENCES personne (id);

-- Contraintes pour la table participation
ALTER TABLE participation
    ADD CONSTRAINT fk_participation_personne
        FOREIGN KEY (personne_id) REFERENCES personne (id),
    ADD CONSTRAINT fk_participation_reunion
        FOREIGN KEY (reunion_id) REFERENCES reunion (id) ON DELETE CASCADE;

-- Contraintes pour la table autorisation_reunion_privee
ALTER TABLE autorisation_reunion_privee
    ADD CONSTRAINT fk_autorisation_personne
        FOREIGN KEY (personne_id) REFERENCES personne (id),
    ADD CONSTRAINT fk_autorisation_reunion
        FOREIGN KEY (reunion_id) REFERENCES reunion (id) ON DELETE CASCADE;

-- Contraintes pour la table demande_parole
ALTER TABLE demande_parole
    ADD CONSTRAINT fk_demande_personne
        FOREIGN KEY (personne_id) REFERENCES personne (id),
    ADD CONSTRAINT fk_demande_reunion
        FOREIGN KEY (reunion_id) REFERENCES reunion (id) ON DELETE CASCADE;

-- Contraintes pour la table message
ALTER TABLE message
    ADD CONSTRAINT fk_message_personne
        FOREIGN KEY (personne_id) REFERENCES personne (id),
    ADD CONSTRAINT fk_message_reunion
        FOREIGN KEY (reunion_id) REFERENCES reunion (id) ON DELETE CASCADE;

