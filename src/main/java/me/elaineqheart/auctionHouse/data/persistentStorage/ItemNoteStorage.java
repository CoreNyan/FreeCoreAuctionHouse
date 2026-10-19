package me.elaineqheart.auctionHouse.data.persistentStorage;

import me.elaineqheart.auctionHouse.data.persistentStorage.local.SettingManager;
import me.elaineqheart.auctionHouse.data.persistentStorage.local.data.ConfigManager;
import me.elaineqheart.auctionHouse.data.persistentStorage.local.data.JsonNoteStorage;
import me.elaineqheart.auctionHouse.data.persistentStorage.mysql.MySQLManager;
import me.elaineqheart.auctionHouse.data.persistentStorage.mysql.MySQLNoteDAO;
import me.elaineqheart.auctionHouse.data.persistentStorage.mysql.MySQLSyncTask;
import me.elaineqheart.auctionHouse.data.ram.AuctionHouseStorage;
import me.elaineqheart.auctionHouse.data.ram.ItemManager;
import me.elaineqheart.auctionHouse.data.ram.ItemNote;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class ItemNoteStorage {

    private static HashMap<UUID, ItemStack> items = new HashMap<>();
    public static ItemStack getItem(UUID itemNoteID) {
        return items.get(itemNoteID);
    }
    public static void addItem(UUID itemNoteID, ItemStack item) {
        items.put(itemNoteID, item);
    }
    public static void removeItem(UUID itemNoteID) {
        items.remove(itemNoteID);
    }

    public static int getNumberOfAuctions(UUID playerUUID) {
        return AuctionHouseStorage.getNumberOfAuctions(playerUUID);
    }


    public static ItemNote createNote(Player p, ItemStack item, double price, boolean isBIDAuction) {
        ItemNote itemNote = new ItemNote(p, item, price, isBIDAuction);
        ConfigManager.transactionLogger.logSetUpAuction(
                p.getDisplayName(),
                itemNote.getItemName(),
                price,
                item.getAmount(),
                isBIDAuction);

        if (m()) {
            MySQLNoteDAO.saveNote(itemNote);
            AuctionHouseStorage.add(itemNote);
        } else {
            JsonNoteStorage.createNote(itemNote);
        }
        return itemNote;
    }

    public static void saveNotes() throws IOException {
        if (!m()) {
            JsonNoteStorage.saveNotes();
        }
    }

    public static void loadNotes() throws IOException {
        if (m()) {
            List<ItemNote> list = MySQLNoteDAO.loadAllNotes();
            AuctionHouseStorage.set(list.toArray(new ItemNote[0]));
        } else {
            JsonNoteStorage.loadNotes();
        }
    }

    private static void deleteNote(ItemNote note) {
        if (m()) {
            MySQLNoteDAO.deleteNote(note.getNoteID());
            AuctionHouseStorage.remove(note);
        } else {
            JsonNoteStorage.deleteNote(note);
        }
        removeItem(note.getNoteID());
    }

    public static void deleteCancelNote(ItemNote note) {
        ConfigManager.transactionLogger.logCancelAuction(
                note.getPlayerName(),
                note.getItemName(),
                note.getCurrentPrice(),
                note.getCurrentAmount(),
                note.isBIDAuction());
        deleteNote(note);
    }

    public static void deleteExpiredNote(ItemNote note) {
        ConfigManager.transactionLogger.logExpiredAuction(
                note.getPlayerName(),
                note.getItemName(),
                note.getCurrentPrice(),
                note.getCurrentAmount(),
                note.isBIDAuction());
        deleteNote(note);
    }
    public static void deleteAdminExpiredNote(ItemNote note) {
        ConfigManager.transactionLogger.logAdminExpiredAuction(
                note.getPlayerName(),
                note.getItemName(),
                note.getCurrentPrice(),
                note.getCurrentAmount(),
                note.isBIDAuction());
        deleteNote(note);
    }
    public static void deleteAdminDeletedNote(ItemNote note) {
        ConfigManager.transactionLogger.logAdminDeletedAuction(
                note.getPlayerName(),
                note.getItemName(),
                note.getCurrentPrice(),
                note.getCurrentAmount(),
                note.isBIDAuction());
        deleteNote(note);
    }

    public static void setBuyerName(ItemNote note, String buyerName, UUID playerID) {
        note.setBuyerName(buyerName, playerID);
        if (m()) MySQLNoteDAO.saveNote(note);
    }
    public static void setSold(ItemNote note, boolean isSold) {
        note.setSold(isSold);
        if (m()) MySQLNoteDAO.saveNote(note);
    }
    public static void setAdminMessage(ItemNote note, String adminMessage) {
        note.setAdminMessage(adminMessage);
        if (m()) MySQLNoteDAO.saveNote(note);
    }
    public static void setItem(ItemNote note, ItemStack item) {
        note.setItem(item);
        if (m()) MySQLNoteDAO.saveNote(note);
    }
    public static void setAuctionTime(ItemNote note, long time) {
        note.setAuctionTime(time);
        if (m()) MySQLNoteDAO.saveNote(note);
    }
    public static void setPartiallySoldAmountLeft(ItemNote note, int amount) {
        note.setPartiallySoldAmountLeft(amount);
        if (m()) MySQLNoteDAO.saveNote(note);
    }
    public static void setPrice(ItemNote note, double amount) {
        note.setPrice(amount);
        if (m()) MySQLNoteDAO.saveNote(note);
    }
    public static void addBid(ItemNote note, Player player, double amount) {
        note.addBid(player, amount);
        if (m()) MySQLNoteDAO.saveNote(note);
    }
    public static void removeBidFromMemory(Player player, ItemNote note) {
        note.removeBid(player);
        AuctionHouseStorage.removeBidFromMemory(player.getUniqueId(), note.getNoteID());
        if (m()) MySQLNoteDAO.saveNote(note);
    }

    public static boolean m() {
        return SettingManager.useMySQL && MySQLManager.isEnabled();
    }

    public enum SortMode {
        NAME,
        PRICE_ASC,
        PRICE_DESC,
        DATE
    }

    public static void purge() {
        for (ItemNote note : AuctionHouseStorage.getAll()) {
            ConfigManager.transactionLogger.logPurge(
                    note.getPlayerName(),
                    note.getItemName(),
                    note.getCurrentPrice(),
                    note.getCurrentAmount(),
                    note.isBIDAuction()
            );
        }
        if (m()) {
            MySQLNoteDAO.purge();
            AuctionHouseStorage.set(new ItemNote[0]);
            reload();
        } else {
            JsonNoteStorage.purge();
        }
    }

    public static void reload() {
        items = new HashMap<>();
    }


    public static boolean setSoldIfOnAuction(ItemNote note, Player p, int amount, double price) {
        if (m()) {
            boolean success = MySQLNoteDAO.tryBuyNote(note.getNoteID(), p.getDisplayName(), p.getUniqueId(), amount, price);
            if (!success) {
                MySQLSyncTask.syncNow();
                return false;
            }
        }

        setSold(note, true);
        setBuyerName(note, p.getDisplayName(), p.getUniqueId());
        if (price != note.getPrice()) {
            if (note.getPartiallySoldAmountLeft() == 0) {
                setPartiallySoldAmountLeft(note, note.getItem().getAmount() - amount);
            } else {
                setPartiallySoldAmountLeft(note, note.getPartiallySoldAmountLeft() - amount);
            }
        }
        saveNotesWithoutCheck();
        ConfigManager.transactionLogger.logTransaction(
                p.getDisplayName(),
                note.getPlayerName(),
                note.getItemName(),
                price,
                amount,
                note.isBIDAuction());
        return true;
    }

    public static boolean collectExpiredAuctionItem(ItemNote note) {
        deleteExpiredNote(note);
        saveNotesWithoutCheck();
        return true;
    }
    public static boolean collectAdminExpiredAuctionItem(ItemNote note) {
        deleteAdminExpiredNote(note);
        saveNotesWithoutCheck();
        return true;
    }
    public static boolean collectAdminDeletedAuctionItem(ItemNote note) {
        deleteAdminDeletedNote(note);
        saveNotesWithoutCheck();
        return true;
    }

    public static boolean collectSoldAuctionItem(ItemNote note, int itemAmount, double price) {
        if (note == null) return false;
        if (note.isBIDAuction() && note.isSold()) return false;
        if (note.getPartiallySoldAmountLeft() != 0) {
            setPrice(note, note.getPrice() - price);
            ItemStack temp = note.getItem();
            temp.setAmount(note.getItem().getAmount() - itemAmount);
            setItem(note, temp);
            if (note.getPartiallySoldAmountLeft() == note.getItem().getAmount()) {
                setPartiallySoldAmountLeft(note, 0);
                setSold(note, false);
                setBuyerName(note, null, null);
            }
        } else {
            if (!note.isBIDAuction()) deleteNote(note);
            else {
                note.setSold(true);
                if (m()) MySQLNoteDAO.saveNote(note);
                AuctionHouseStorage.checkRemove(note.getNoteID());
            }
        }
        saveNotesWithoutCheck();
        return true;
    }

    public static boolean addBidIfOnAuction(ItemNote note, Player p, double price) {
        if (m()) {
            boolean success = MySQLNoteDAO.tryPlaceBid(note.getNoteID(), p.getDisplayName(), p.getUniqueId(), price, SettingManager.lastBIDExtraTime);
            if (!success) {
                MySQLSyncTask.syncNow();
                return false;
            }
        }
        addBid(note, p, price);
        saveNotesWithoutCheck();
        return true;
    }

    public static boolean claimEndedAuctionItem(Player p, ItemNote note) {
        removeBidFromMemory(p, note);
        saveNotesWithoutCheck();
        return true;
    }

    public static boolean adminConfirmDeleteItem(ItemNote note, String reason) {
        setAuctionTime(note, -1);
        setAdminMessage(note, reason);
        setItem(note, ItemManager.createDirt());
        saveNotesWithoutCheck();
        return true;
    }

    public static boolean adminConfirmExpireItem(ItemNote note, String reason) {
        setAuctionTime(note, -1);
        setAdminMessage(note, reason);
        saveNotesWithoutCheck();
        return true;
    }



    private static void saveNotesWithoutCheck() {
        try {
            saveNotes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}

