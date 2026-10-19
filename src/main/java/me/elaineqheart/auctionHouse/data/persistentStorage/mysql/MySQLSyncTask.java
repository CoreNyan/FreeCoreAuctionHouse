package me.elaineqheart.auctionHouse.data.persistentStorage.mysql;

import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.data.persistentStorage.local.SettingManager;
import me.elaineqheart.auctionHouse.data.ram.AuctionHouseStorage;
import me.elaineqheart.auctionHouse.data.ram.ItemNote;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MySQLSyncTask implements Runnable {

    private static final AtomicBoolean isSyncing = new AtomicBoolean(false);

    public static void start() {
        if (!MySQLManager.isEnabled()) return;
        int intervalSeconds = Math.max(1, SettingManager.syncIntervalSeconds);
        AuctionHouse.getScheduler().asyncScheduler().runAtFixedRate(new MySQLSyncTask(), Duration.ofSeconds(2), Duration.ofSeconds(intervalSeconds));
        AuctionHouse.getInstance().getLogger().info("[FreeCoreAuctionHouse] 已启动 MySQL 多子服商品同步定时器，间隔: " + intervalSeconds + "秒");
    }

    @Override
    public void run() {
        if (!MySQLManager.isEnabled()) return;
        if (!isSyncing.compareAndSet(false, true)) {
            return;
        }

        try {
            List<ItemNote> notes = MySQLNoteDAO.loadAllNotes();
            AuctionHouseStorage.set(notes.toArray(new ItemNote[0]));

            // 检查当前子服所有在线玩家是否有待领取的跨服通知（如在其他子服被买走的物品）
            for (org.bukkit.entity.Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
                MySQLNotificationDAO.checkAndDeliverNotifications(online);
            }
        } catch (Throwable t) {
            // 兜底捕获所有包括 LinkageError/NoClassDefFoundError 在内的异常，严禁向上抛给服务端调度器引起刷屏
            AuctionHouse.getInstance().getLogger().fine("[FreeCoreAuctionHouse] 拍卖同步任务跳过异常: " + t.getMessage());
        } finally {
            isSyncing.set(false);
        }
    }

    public static void syncNow() {
        if (!MySQLManager.isEnabled()) return;
        try {
            List<ItemNote> notes = MySQLNoteDAO.loadAllNotes();
            AuctionHouseStorage.set(notes.toArray(new ItemNote[0]));
        } catch (Exception e) {
            AuctionHouse.getInstance().getLogger().warning("[FreeCoreAuctionHouse] 立即同步拍卖列表异常: " + e.getMessage());
        }
    }
}
