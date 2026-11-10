-- Table des clés virtuelles de SHCrates avec SHNetwork. Le plugin la crée seul au démarrage si l'utilisateur a le droit CREATE ;
-- ce fichier sert à la créer à la main et à ne donner à l'utilisateur que les droits de lecture et d'écriture.
--
-- Utilisation (base bo_net pour les essais multi-Opale) :
--   mysql -u root < sql/schema.sql   puis remplacer CHANGE_ME par le mot de passe de config.yml

CREATE DATABASE IF NOT EXISTS bo_net;

CREATE TABLE IF NOT EXISTS bo_net.shcrates_keys (
    uuid   CHAR(36)    NOT NULL,                 -- uuid « hors ligne » du joueur, le même sur tous les serveurs
    crate  VARCHAR(64) NOT NULL,                 -- identifiant de la caisse, en minuscules
    amount INT         NOT NULL DEFAULT 0,       -- clés virtuelles, modifiées par « amount = amount + n »
    PRIMARY KEY (uuid, crate)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE USER IF NOT EXISTS 'shcrates'@'127.0.0.1' IDENTIFIED BY 'CHANGE_ME';
GRANT SELECT, INSERT, UPDATE ON bo_net.shcrates_keys TO 'shcrates'@'127.0.0.1';
