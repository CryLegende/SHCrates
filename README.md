<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHCrates

*Plugin du serveur SkyHand.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Plugin du serveur SkyHand.

Utilisé sur : **Opale**.

## Dépendances

- Optionnelles : `PlaceholderAPI`, `Vault`

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/shcrates` | `/crate`, `/shcr` | Commande principale pour SHCrates. | `shcrates.command.base` |

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shcrates.*` | Donne accès à toutes les fonctionnalités de SHCrates. | `op` |
| `shcrates.admin` | Permet d'utiliser les commandes d'administration (reload, setblock, removeblock, givekey). | `op` |
| `shcrates.command.base` | Permet d'utiliser les commandes de base de SHCrates (list, preview, open, openall, keys). | `true` |

## Configuration

Fichiers par défaut repris du serveur (secrets remplacés par `CHANGE_ME`) :

- `config.yml`
- `crates/ordinaire.yml`
- `messages.yml`

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
