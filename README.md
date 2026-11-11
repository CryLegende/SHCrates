<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHCrates

*Caisses à récompenses avec clés physiques ou virtuelles, aperçu des lots et ouverture multiple.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Caisses à récompenses avec clés physiques ou virtuelles, aperçu des lots et ouverture multiple.

Utilisé sur : **Opale**.

## Dépendances

- Optionnelles : `PlaceholderAPI`, `Vault`, `SHLogs`, `SHNetwork` (mode réseau)

## Mode réseau

Avec **SHNetwork** installé (et `reseau.actif: true`, valeur par défaut), plusieurs serveurs de jeu partagent les clés virtuelles :

- elles sont en MariaDB (table `shcrates_keys` : `uuid`, `crate`, `amount`) au lieu de `playerdata/<uuid>.yml` ;
- à la connexion, une fois que l'autre serveur a fini de sauvegarder le joueur, ses clés sont lues en base et gardées en mémoire ;
- chaque clé ajoutée, ouverte ou retirée change la mémoire tout de suite et la base reçoit l'écart (`+n` ou `-n`), jamais le total ;
- `givekey`, `takekey`, `open` et `openall` visant un joueur d'un autre serveur sont exécutés là où il se trouve ; `giveall` donne aussi les clés aux joueurs des autres serveurs ;
- `keys` et `preview` visant un joueur d'un autre serveur répondent qu'il faut les lancer depuis son serveur ;
- les clés déjà dans `playerdata/` se copient une fois avec `/shcrates import`, sans rien écraser (permission `shcrates.admin.import`).

Les blocs de caisse (`locations.yml`) restent propres à chaque serveur. Sur Opale ils sont dans les mondes `spawn` et `Easter`, qui n'existent que sur le serveur du spawn : les caisses physiques ne se trouvent que là. Les clés physiques sont des objets (données de l'objet) et suivent le joueur avec son inventaire.

Sans SHNetwork, ou sans les clés `reseau` dans la configuration, le plugin se comporte comme avant : fichiers `playerdata/`. Si la base est injoignable au démarrage, il reste en mode local. La table se crée toute seule, ou à la main avec `sql/schema.sql`.

## Logs

Chaque récompense gagnée est écrite dans SHLogs, avec un fichier par caisse (`LogFile` dans le fichier de la caisse, par défaut le nom du fichier). Le texte de la ligne se règle avec `reward_log_format`. Sans SHLogs, la ligne est écrite dans la console.

## Commandes

`/crate` est la commande des joueurs, `/shcrates` (alias `/shcr`) celle de l'administration. Les deux acceptent toutes les sous-commandes, sous réserve des permissions : `/shcrates preview` et `/shcrates open` continuent de marcher. Sans argument, ou avec une sous-commande inconnue, une ligne d'usage ne liste que les sous-commandes permises à l'expéditeur.

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/crate preview <caisse> [joueur]` | `/shcrates preview` | Ouvre l'aperçu d'une caisse. | `shcrates.use` et `shcrates.preview.<caisse>` |
| `/crate open <caisse> [joueur]` | `/shcrates open` | Ouvre une caisse. | `shcrates.use` et `shcrates.open.<caisse>` |
| `/crate openall <caisse> [joueur]` | `/shcrates openall` | Ouvre toutes les clés d'une caisse. | idem `open` |
| `/crate keys [joueur]` | `/shcrates keys` | Affiche les clés d'un joueur. | `shcrates.keys` ; autre joueur : `shcrates.admin.keysothers` |
| `/crate list` | `/shcrates list` | Liste les caisses. | `shcrates.list` |
| `/shcrates givekey <joueur> <caisse> <nombre> [physical\|virtual]` | `/shcr` | Donne des clés. | `shcrates.admin.givekey` |
| `/shcrates takekey <joueur> <caisse> <nombre> [physical\|virtual]` | `/shcr` | Retire des clés. | `shcrates.admin.takekey` |
| `/shcrates giveall <caisse> <nombre> [physical\|virtual]` | `/shcr` | Donne des clés à tous les joueurs en ligne. | `shcrates.admin.giveall` |
| `/shcrates setblock <caisse>` | `/shcr` | Définit le bloc visé comme caisse. | `shcrates.admin.setblock` |
| `/shcrates removeblock` | `/shcr` | Retire la caisse du bloc visé. | `shcrates.admin.removeblock` |
| `/shcrates info <caisse>` | `/shcr` | Nom, bloc, clé, animation, récompenses et blocs posés. | `shcrates.admin.info` |
| `/shcrates reload` | `/shcr` | Relit `config.yml`, `messages.yml`, `crates/` et `locations.yml`. Un fichier illisible ou une caisse inutilisable annule tout. | `shcrates.admin.reload` |
| `/shcrates import` | `/shcr` | Mode réseau : copie les clés virtuelles de `playerdata/` dans la base. | `shcrates.admin.import` |

Les sous-commandes d'administration marchent depuis la console ; `setblock` et `removeblock` ont besoin du bloc visé par un joueur.

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shcrates.*` | Tous les droits (parent de `shcrates.admin` et `shcrates.use`). | `op` |
| `shcrates.use` | Utiliser `/crate` et les sous-commandes joueur. | `true` |
| `shcrates.list` | `/crate list`. | `op` |
| `shcrates.keys` | `/crate keys`. | `op` |
| `shcrates.open.<caisse>` / `shcrates.open.*` | Ouvrir une caisse (`shcrates.open.ordinaire`). | `op` |
| `shcrates.preview.<caisse>` / `shcrates.preview.*` | Aperçu d'une caisse. | `op` |
| `shcrates.reroll.<caisse>` / `shcrates.reroll.*` | Relancer une récompense. | `op` |
| `shcrates.removeblock.<caisse>` | Casser le bloc d'une caisse. | `op` |
| `shcrates.bypass.creative` | Ouvrir une caisse en mode créatif (ne se déduit pas de `shcrates.admin`). | `op` |
| `shcrates.admin` | Toute l'administration, aperçu et ouverture de toutes les caisses (parent des nœuds suivants). | `op` |
| `shcrates.admin.givekey` `.takekey` `.giveall` `.setblock` `.removeblock` `.reload` `.import` `.info` | Une sous-commande chacun. | `op` |
| `shcrates.admin.previewothers` `.openothers` `.keysothers` | Viser un autre joueur avec `preview`, `open` ou `keys`. | `op` |

## Configuration

Fichiers par défaut repris du serveur (secrets remplacés par `CHANGE_ME`). Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHCrates/
├── <a href="src/main/resources/config.yml">config.yml</a>
├── <a href="src/main/resources/messages.yml">messages.yml</a>
└── crates/
    └── <a href="src/main/resources/crates/ordinaire.yml">ordinaire.yml</a>
</pre>

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
