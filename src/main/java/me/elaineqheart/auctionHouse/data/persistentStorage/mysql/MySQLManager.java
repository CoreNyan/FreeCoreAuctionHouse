package me.elaineqheart.auctionHouse.data.persistentStorage.mysql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.data.persistentStorage.local.SettingManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MySQLManager {

    private static HikariDataSource dataSource;
    private static volatile boolean enabled = false;

    public static synchronized void init() {
        if (!SettingManager.useMySQL) {
            enabled = false;
            return;
        }

        try {
            close();

            HikariConfig config = new HikariConfig();
            String jdbcUrl = String.format(
                    "jdbc:mysql://%s:%d/%s?autoReconnect=true&useSSL=%b&allowPublicKeyRetrieval=%b&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai",
                    SettingManager.mysqlHost,
                    SettingManager.mysqlPort,
                    SettingManager.mysqlDatabase,
                    SettingManager.mysqlUseSSL,
                    SettingManager.mysqlAllowPublicKeyRetrieval
            );
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(SettingManager.mysqlUsername);
            config.setPassword(SettingManager.mysqlPassword);
            config.setMaximumPoolSize(SettingManager.mysqlMaxPoolSize);
            config.setMinimumIdle(SettingManager.mysqlMinIdle);
            config.setConnectionTimeout(5000);
            config.setValidationTimeout(3000);
            config.setMaxLifetime(1800000);
            config.setPoolName("FreeCoreAuctionHouse-MySQLPool");

            dataSource = new HikariDataSource(config);
            createTables();
            enabled = true;
            AuctionHouse.getInstance().getLogger().info("[FreeCoreAuctionHouse] MySQL 数据源初始化成功并已建表/校验表！");
        } catch (Exception e) {
            enabled = false;
            AuctionHouse.getInstance().getLogger().severe("[FreeCoreAuctionHouse] 初始化 MySQL 连接池失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createTables() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS freecore_auction_notes (" +
                "  note_id VARCHAR(36) PRIMARY KEY," +
                "  player_name VARCHAR(64) NOT NULL," +
                "  player_uuid VARCHAR(36) NOT NULL," +
                "  buyer_name VARCHAR(64)," +
                "  buyer_uuid VARCHAR(36)," +
                "  price DOUBLE NOT NULL," +
                "  date_created BIGINT NOT NULL," +
                "  item_data MEDIUMTEXT NOT NULL," +
                "  is_sold BOOLEAN NOT NULL DEFAULT FALSE," +
                "  partially_sold_amount_left INT NOT NULL DEFAULT 0," +
                "  admin_message TEXT," +
                "  auction_time BIGINT NOT NULL," +
                "  item_name VARCHAR(255) NOT NULL," +
                "  is_bid_auction BOOLEAN NOT NULL DEFAULT FALSE," +
                "  bid_history LONGTEXT," +
                "  claimed_players LONGTEXT," +
                "  updated_at BIGINT NOT NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        String notifSql = "CREATE TABLE IF NOT EXISTS freecore_auction_notifications (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  receiver_uuid VARCHAR(36) NOT NULL," +
                "  message TEXT NOT NULL," +
                "  created_at BIGINT NOT NULL," +
                "  INDEX idx_receiver (receiver_uuid)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            stmt.executeUpdate(notifSql);
        }
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource 未初始化或不可用！");
        }
        return dataSource.getConnection();
    }

    public static boolean isEnabled() {
        return enabled && dataSource != null && !dataSource.isClosed();
    }

    public static synchronized void close() {
        if (dataSource != null) {
            try {
                if (!dataSource.isClosed()) {
                    dataSource.close();
                }
            } catch (Exception ignored) {
            } finally {
                dataSource = null;
                enabled = false;
            }
        }
    }
}
