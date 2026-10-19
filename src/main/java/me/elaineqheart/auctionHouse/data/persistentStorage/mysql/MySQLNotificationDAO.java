package me.elaineqheart.auctionHouse.data.persistentStorage.mysql;

import me.elaineqheart.auctionHouse.AuctionHouse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MySQLNotificationDAO {

    public static void sendNotification(UUID receiverUUID, String message) {
        if (!MySQLManager.isEnabled()) {
            Player p = Bukkit.getPlayer(receiverUUID);
            if (p != null && p.isOnline()) {
                p.sendMessage(message);
            }
            return;
        }

        // 异步写入数据库通知队列
        AuctionHouse.getScheduler().asyncScheduler().run(() -> {
            String sql = "INSERT INTO freecore_auction_notifications (receiver_uuid, message, created_at) VALUES (?, ?, ?)";
            try (Connection conn = MySQLManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, receiverUUID.toString());
                ps.setString(2, message);
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (Exception e) {
                AuctionHouse.getInstance().getLogger().warning("[FreeCoreAuctionHouse] 写入跨服通知失败: " + e.getMessage());
            }
        });
    }

    public static void checkAndDeliverNotifications(Player player) {
        if (!MySQLManager.isEnabled() || player == null || !player.isOnline()) return;

        UUID uuid = player.getUniqueId();
        String selectSql = "SELECT id, message FROM freecore_auction_notifications WHERE receiver_uuid = ? ORDER BY id ASC";
        String deleteSql = "DELETE FROM freecore_auction_notifications WHERE id = ?";

        List<Long> ids = new ArrayList<>();
        List<String> messages = new ArrayList<>();

        try (Connection conn = MySQLManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong("id"));
                    messages.add(rs.getString("message"));
                }
            }
        } catch (Exception e) {
            AuctionHouse.getInstance().getLogger().warning("[FreeCoreAuctionHouse] 拉取玩家通知失败: " + e.getMessage());
            return;
        }

        if (ids.isEmpty()) return;

        // 在玩家所在线程/主线程发送消息
        for (String msg : messages) {
            player.sendMessage(msg);
        }

        // 异步删除已投递消息
        AuctionHouse.getScheduler().asyncScheduler().run(() -> {
            try (Connection conn = MySQLManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                for (Long id : ids) {
                    ps.setLong(1, id);
                    ps.executeUpdate();
                }
            } catch (Exception e) {
                AuctionHouse.getInstance().getLogger().warning("[FreeCoreAuctionHouse] 清理已读通知失败: " + e.getMessage());
            }
        });
    }
}
