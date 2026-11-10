package org.Cry_Legende.sHCrates.network;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Clés virtuelles en MariaDB, partagées par tous les serveurs. Chargée seulement avec SHNetwork
 * (HikariCP et le pilote MariaDB viennent de {@code libraries:} dans plugin.yml).
 *
 * <p>Toutes les méthodes bloquent : à appeler hors du fil principal.</p>
 */
public final class KeyDatabase {

    private static final String TABLE = "shcrates_keys";

    private final HikariDataSource pool;

    public KeyDatabase(String url, String user, String password, int timeoutMs) {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("SHCrates");
        hikari.setJdbcUrl(url);
        hikari.setUsername(user);
        hikari.setPassword(password);
        hikari.setDriverClassName("org.mariadb.jdbc.Driver");
        hikari.addDataSourceProperty("connectTimeout", String.valueOf(timeoutMs));
        hikari.addDataSourceProperty("socketTimeout", String.valueOf(timeoutMs));
        hikari.setMaximumPoolSize(3);
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(timeoutMs);
        // le serveur démarre même si la base est absente : le plugin reste alors en mode local
        hikari.setInitializationFailTimeout(-1);
        hikari.setKeepaliveTime(60_000);
        this.pool = new HikariDataSource(hikari);
    }

    /** Crée la table si elle manque. Une table déjà là ne demande aucun droit CREATE à l'utilisateur. */
    public void createTable() throws SQLException {
        try (Connection c = pool.getConnection(); Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT 1 FROM " + TABLE + " LIMIT 1")) {
                return;
            } catch (SQLException missing) {
                // la table n'existe pas encore : création ci-dessous
            }
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + "uuid CHAR(36) NOT NULL, "
                + "crate VARCHAR(64) NOT NULL, "
                + "amount INT NOT NULL DEFAULT 0, "
                + "PRIMARY KEY (uuid, crate)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
        }
    }

    /** Clés virtuelles du joueur : identifiant de caisse -> nombre (seulement les positifs). */
    public Map<String, Integer> load(UUID uuid) throws SQLException {
        Map<String, Integer> result = new HashMap<>();
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT crate, amount FROM " + TABLE + " WHERE uuid = ? AND amount > 0")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString(1), rs.getInt(2));
                }
            }
        }
        return result;
    }

    /**
     * Ajoute (ou retire, si négatif) des clés. Une seule instruction atomique : deux serveurs qui touchent
     * au même joueur s'additionnent, et le total ne descend jamais sous zéro. Jamais rejouée.
     */
    public void add(UUID uuid, String crate, int delta) throws SQLException {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO " + TABLE + " (uuid, crate, amount) VALUES (?, ?, GREATEST(0, ?)) "
                 + "ON DUPLICATE KEY UPDATE amount = GREATEST(0, amount + ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, crate);
            ps.setInt(3, delta);
            ps.setInt(4, delta);
            ps.executeUpdate();
        }
    }

    /**
     * Reprend les clés de l'ancien dossier playerdata. Une ligne déjà présente (joueur, caisse) n'est pas touchée,
     * donc l'importation peut être relancée sans rien compter deux fois.
     *
     * @return {importées, déjà présentes}
     */
    public int[] importKeys(Map<UUID, Map<String, Integer>> keysByPlayer) throws SQLException {
        int imported = 0;
        int present = 0;
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT IGNORE INTO " + TABLE + " (uuid, crate, amount) VALUES (?, ?, ?)")) {
            for (Map.Entry<UUID, Map<String, Integer>> player : keysByPlayer.entrySet()) {
                for (Map.Entry<String, Integer> key : player.getValue().entrySet()) {
                    if (key.getValue() == null || key.getValue() <= 0) {
                        continue;
                    }
                    ps.setString(1, player.getKey().toString());
                    ps.setString(2, key.getKey().toLowerCase());
                    ps.setInt(3, key.getValue());
                    if (ps.executeUpdate() > 0) {
                        imported++;
                    } else {
                        present++;
                    }
                }
            }
        }
        return new int[]{imported, present};
    }

    public void close() {
        pool.close();
    }
}
