<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHCrates

*Caisses à récompenses avec clés physiques ou virtuelles, aperçu des lots, ouverture multiple, paliers de rareté, jauge de garantie et lots du mois.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Caisses à récompenses avec clés physiques ou virtuelles, aperçu des lots et ouverture multiple.

Utilisé sur : **Opale**.

## Paliers, jauge et garanties

- **Paliers de rareté** (`paliers` dans `config.yml`) : chaque récompense peut désigner son palier avec `Palier:`. Le palier donne la ligne « Rareté » de l'aperçu, la couleur du cadre quand la roulette ralentit, le son, le feu d'artifice, l'animation du totem, le titre et l'annonce au serveur. Une récompense sans palier garde le son `SoundWin` de la caisse et `FireworkOnWin`.
- **Roulette pondérée** : les lots qui défilent sont tirés selon leurs vraies chances.
- **Jauge de garantie** (`Jauge:` dans une caisse) : après `Seuil - 1` clés sans lot d'un des paliers visés, la clé suivante tire seulement parmi eux. Elle repart à zéro dès qu'un de ces lots sort. L'état s'affiche en barre d'action après chaque ouverture et à la réception de clés.
- **Première clé** (`PremiereOuverture:`) : la toute première clé d'un joueur tire parmi les lots du palier donné ou mieux.
- **Lots du mois** (`Rotation:`) : un seul des lots listés est gagnable chaque mois, à tour de rôle.
- **Conditions** (`Condition:` d'une récompense) : lot gagnable et visible seulement si la comparaison PlaceholderAPI est vraie. Les commandes des récompenses passent aussi par PlaceholderAPI.
- **Ouverture multiple** : `/crate openall` et Shift+clic droit sur le bloc d'une QuickCrate ouvrent toutes les clés (virtuelles puis physiques), avec un récapitulatif du plus courant au plus rare, un seul son et le dévoilement du meilleur lot. Refusée sur une caisse qui a `OuvertureMultiple: false`. S'arrête quand l'inventaire est plein.
- **Réception de clés** (`AnnonceReception: true`) : message, son et état de la jauge quand le joueur reçoit des clés de la caisse.
- Une animation en GUI donne le lot dès la fin du défilement quand le joueur ne peut pas le relancer ; avec une relance possible, il est donné à la fermeture, une fois le choix fait.

Les compteurs (jauge, clés ouvertes, première clé, meilleur palier) sont rangés avec les clés virtuelles du joueur : section `progression` de `playerdata/<uuid>.yml`, ou lignes `#<compteur>` de la table `shcrates_keys` en mode réseau.

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
| `/crate stats [joueur]` | `/shcrates stats` | Clés ouvertes, meilleur palier, jauge et collection par caisse. | `shcrates.use` ; autre joueur : `shcrates.admin.statsothers` |
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
| `shcrates.admin.previewothers` `.openothers` `.keysothers` `.statsothers` | Viser un autre joueur avec `preview`, `open`, `keys` ou `stats`. | `op` |

## Placeholders

| Placeholder | Valeur |
|---|---|
| `%shcrates_virtual_<caisse>%` / `%shcrates_physical_<caisse>%` | Clés virtuelles / physiques du joueur. |
| `%shcrates_virtual_total%` / `%shcrates_physical_total%` | Total de clés virtuelles / physiques. |
| `%shcrates_jauge_<caisse>%` / `%shcrates_jauge_seuil_<caisse>%` / `%shcrates_jauge_reste_<caisse>%` | Jauge du joueur, seuil, clés restantes avant la garantie. |
| `%shcrates_jauge_barre_<caisse>%` | Barre de 20 segments. |
| `%shcrates_ouvertures_<caisse>%` | Clés ouvertes par le joueur. |
| `%shcrates_premiere_<caisse>%` | Texte de la garantie de première clé tant qu'elle n'est pas utilisée, vide ensuite. |
| `%shcrates_rotation_<caisse>%` | Nom du lot en rotation ce mois-ci. |

## Configuration

Fichiers par défaut, avec des valeurs d'exemple. Cliquez sur un fichier pour l'ouvrir :

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
