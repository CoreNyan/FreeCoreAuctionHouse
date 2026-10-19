package me.elaineqheart.auctionHouse.data.persistentStorage.mysql;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.data.ram.Bid;
import me.elaineqheart.auctionHouse.data.ram.ItemNote;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class MySQLNoteDAO {

    private static final Gson gson = new Gson();
    private static final Type BID_LIST_TYPE = new TypeToken<List<BidData>>() {}.getType();
    private static final Type UUID_SET_TYPE = new TypeToken<Set<String>>() {}.getType();

    private static class BidData {
        String player;
        String playerName;
        long date;
        double bid;
    }

    public static List<ItemNote> loadAllNotes() {
        List<ItemNote> result = new ArrayList<>();
        if (!MySQLManager.isEnabled()) return result;

        String sql = "SELECT * FROM freecore_auction_notes";
        try (Connection conn = MySQLManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                ItemNote note = parseNote(rs);
                if (note != null) {
                    result.add(note);
                }
            }
        } catch (SQLException e) {
            AuctionHouse.getInstance().getLogger().severe("[MySQL] loadAllNotes 失败: " + e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    public static ItemNote getNote(UUID noteID) {
        if (!MySQLManager.isEnabled() || noteID == null) return null;

        String sql = "SELECT * FROM freecore_auction_notes WHERE note_id = ?";
        try (Connection conn = MySQLManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, noteID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return parseNote(rs);
                }
            }
        } catch (SQLException e) {
            AuctionHouse.getInstance().getLogger().severe("[MySQL] getNote 失败: " + e.getMessage());
        }
        return null;
    }

    public static void saveNote(ItemNote note) {
        if (!MySQLManager.isEnabled() || note == null) return;

        String sql = "INSERT INTO freecore_auction_notes (" +
                "note_id, player_name, player_uuid, buyer_name, buyer_uuid, price, " +
                "date_created, item_data, is_sold, partially_sold_amount_left, admin_message, " +
                "auction_time, item_name, is_bid_auction, bid_history, claimed_players, updated_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "player_name = VALUES(player_name), " +
                "buyer_name = VALUES(buyer_name), " +
                "buyer_uuid = VALUES(buyer_uuid), " +
                "price = VALUES(price), " +
                "item_data = VALUES(item_data), " +
                "is_sold = VALUES(is_sold), " +
                "partially_sold_amount_left = VALUES(partially_sold_amount_left), " +
                "admin_message = VALUES(admin_message), " +
                "auction_time = VALUES(auction_time), " +
                "item_name = VALUES(item_name), " +
                "bid_history = VALUES(bid_history), " +
                "claimed_players = VALUES(claimed_players), " +
                "updated_at = VALUES(updated_at)";

        try (Connection conn = MySQLManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            bindNoteToStatement(ps, note);
            ps.executeUpdate();
        } catch (SQLException e) {
            AuctionHouse.getInstance().getLogger().severe("[MySQL] saveNote 失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void deleteNote(UUID noteID) {
        if (!MySQLManager.isEnabled() || noteID == null) return;

        String sql = "DELETE FROM freecore_auction_notes WHERE note_id = ?";
        try (Connection conn = MySQLManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, noteID.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            AuctionHouse.getInstance().getLogger().severe("[MySQL] deleteNote 失败: " + e.getMessage());
        }
    }

    public static void purge() {
        if (!MySQLManager.isEnabled()) return;

        String sql = "TRUNCATE TABLE freecore_auction_notes";
        try (Connection conn = MySQLManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            AuctionHouse.getInstance().getLogger().severe("[MySQL] purge 失败: " + e.getMessage());
        }
    }

    public static boolean tryBuyNote(UUID noteID, String buyerName, UUID buyerUUID, int buyAmount, double buyPrice) {
        if (!MySQLManager.isEnabled() || noteID == null) return false;

        String selectSql = "SELECT * FROM freecore_auction_notes WHERE note_id = ? FOR UPDATE";
        String updateSql = "UPDATE freecore_auction_notes SET " +
                "buyer_name = ?, buyer_uuid = ?, is_sold = ?, partially_sold_amount_left = ?, updated_at = ? " +
                "WHERE note_id = ?";

        try (Connection conn = MySQLManager.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement psSelect = conn.prepareStatement(selectSql)) {
                psSelect.setString(1, noteID.toString());
                try (ResultSet rs = psSelect.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return false;
                    }
                    boolean isSold = rs.getBoolean("is_sold");
                    int partialLeft = rs.getInt("partially_sold_amount_left");
                    boolean isBid = rs.getBoolean("is_bid_auction");

                    if (isBid) {
                        conn.rollback();
                        return false;
                    }

                    if (isSold && partialLeft == 0) {
                        conn.rollback();
                        return false;
                    }

                    int newPartialLeft = 0;
                    boolean newIsSold = true;
                    if (partialLeft > 0) {
                        if (partialLeft < buyAmount) {
                            conn.rollback();
                            return false;
                        }
                        newPartialLeft = partialLeft - buyAmount;
                        newIsSold = (newPartialLeft == 0);
                    }

                    try (PreparedStatement psUpdate = conn.prepareStatement(updateSql)) {
                        psUpdate.setString(1, buyerName);
                        psUpdate.setString(2, buyerUUID != null ? buyerUUID.toString() : null);
                        psUpdate.setBoolean(3, newIsSold);
                        psUpdate.setInt(4, newPartialLeft);
                        psUpdate.setLong(5, System.currentTimeMillis());
                        psUpdate.setString(6, noteID.toString());
                        psUpdate.executeUpdate();
                    }
                    conn.commit();
                    return true;
                }
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            AuctionHouse.getInstance().getLogger().severe("[MySQL] tryBuyNote 事务失败: " + e.getMessage());
            return false;
        }
    }

    public static boolean tryPlaceBid(UUID noteID, String bidderName, UUID bidderUUID, double bidPrice, long extraTimeSeconds) {
        if (!MySQLManager.isEnabled() || noteID == null) return false;

        String selectSql = "SELECT * FROM freecore_auction_notes WHERE note_id = ? FOR UPDATE";
        String updateSql = "UPDATE freecore_auction_notes SET " +
                "price = ?, auction_time = ?, bid_history = ?, updated_at = ? " +
                "WHERE note_id = ?";

        try (Connection conn = MySQLManager.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement psSelect = conn.prepareStatement(selectSql)) {
                psSelect.setString(1, noteID.toString());
                try (ResultSet rs = psSelect.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return false;
                    }

                    boolean isSold = rs.getBoolean("is_sold");
                    if (isSold) {
                        conn.rollback();
                        return false;
                    }

                    double currentPrice = rs.getDouble("price");
                    String bidHistoryJson = rs.getString("bid_history");
                    List<BidData> bidDataList = deserializeBidData(bidHistoryJson);

                    double minRequired = bidDataList.isEmpty() ? currentPrice : Math.ceil(0.25 * currentPrice + currentPrice);
                    if (bidPrice < minRequired) {
                        conn.rollback();
                        return false;
                    }

                    BidData newBid = new BidData();
                    newBid.player = bidderUUID.toString();
                    newBid.playerName = bidderName;
                    newBid.date = System.currentTimeMillis();
                    newBid.bid = bidPrice;
                    bidDataList.add(newBid);

                    long dateCreated = rs.getLong("date_created");
                    long auctionTime = rs.getLong("auction_time");
                    long timeLeft = auctionTime + 30 - (System.currentTimeMillis() - dateCreated) / 1000;
                    if (timeLeft < extraTimeSeconds) {
                        auctionTime = extraTimeSeconds - 30 + (System.currentTimeMillis() - dateCreated) / 1000;
                    }

                    try (PreparedStatement psUpdate = conn.prepareStatement(updateSql)) {
                        psUpdate.setDouble(1, bidPrice);
                        psUpdate.setLong(2, auctionTime);
                        psUpdate.setString(3, gson.toJson(bidDataList));
                        psUpdate.setLong(4, System.currentTimeMillis());
                        psUpdate.setString(5, noteID.toString());
                        psUpdate.executeUpdate();
                    }
                    conn.commit();
                    return true;
                }
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            AuctionHouse.getInstance().getLogger().severe("[MySQL] tryPlaceBid 事务失败: " + e.getMessage());
            return false;
        }
    }

    private static void bindNoteToStatement(PreparedStatement ps, ItemNote note) throws SQLException {
        ps.setString(1, note.getNoteID().toString());
        ps.setString(2, note.getPlayerName());
        ps.setString(3, note.getPlayerUUID().toString());
        ps.setString(4, note.getBuyerName());
        ps.setString(5, note.getBuyerUUID() != null ? note.getBuyerUUID().toString() : null);
        ps.setDouble(6, note.getPrice());
        ps.setLong(7, note.getDateCreated() != null ? note.getDateCreated().getTime() : System.currentTimeMillis());
        ps.setString(8, note.getItemData());
        ps.setBoolean(9, note.isSold());
        ps.setInt(10, note.getPartiallySoldAmountLeft());
        ps.setString(11, note.getAdminMessage());
        ps.setLong(12, note.getAuctionTime());
        ps.setString(13, note.getItemName());
        ps.setBoolean(14, note.isBIDAuction());

        List<BidData> bidDataList = new ArrayList<>();
        if (note.getBidHistoryList() != null) {
            for (Bid b : note.getBidHistoryList()) {
                BidData bd = new BidData();
                bd.player = b.getPlayerID().toString();
                bd.playerName = b.getPlayerName();
                bd.date = System.currentTimeMillis();
                bd.bid = b.getPrice();
                bidDataList.add(bd);
            }
        }
        ps.setString(15, gson.toJson(bidDataList));

        Set<String> claimed = new HashSet<>();
        if (note.getClaimedPlayers() != null) {
            for (UUID u : note.getClaimedPlayers()) {
                claimed.add(u.toString());
            }
        }
        ps.setString(16, gson.toJson(claimed));
        ps.setLong(17, System.currentTimeMillis());
    }

    private static ItemNote parseNote(ResultSet rs) throws SQLException {
        UUID noteID = UUID.fromString(rs.getString("note_id"));
        String playerName = rs.getString("player_name");
        UUID playerUUID = UUID.fromString(rs.getString("player_uuid"));
        String buyerName = rs.getString("buyer_name");
        String bUuidStr = rs.getString("buyer_uuid");
        UUID buyerUUID = bUuidStr != null && !bUuidStr.isEmpty() ? UUID.fromString(bUuidStr) : null;
        double price = rs.getDouble("price");
        Date dateCreated = new Date(rs.getLong("date_created"));
        String itemData = rs.getString("item_data");
        boolean isSold = rs.getBoolean("is_sold");
        int partiallySoldAmountLeft = rs.getInt("partially_sold_amount_left");
        String adminMessage = rs.getString("admin_message");
        long auctionTime = rs.getLong("auction_time");
        String itemName = rs.getString("item_name");
        boolean isBIDAuction = rs.getBoolean("is_bid_auction");

        String bidHistoryJson = rs.getString("bid_history");
        List<Bid> bids = new ArrayList<>();
        List<BidData> bidDataList = deserializeBidData(bidHistoryJson);
        for (BidData bd : bidDataList) {
            bids.add(new Bid(UUID.fromString(bd.player), bd.playerName, new Date(bd.date), bd.bid));
        }

        String claimedJson = rs.getString("claimed_players");
        Set<UUID> claimed = new HashSet<>();
        if (claimedJson != null && !claimedJson.isEmpty()) {
            Set<String> set = gson.fromJson(claimedJson, UUID_SET_TYPE);
            if (set != null) {
                for (String s : set) claimed.add(UUID.fromString(s));
            }
        }

        return new ItemNote(noteID, playerName, playerUUID, buyerName, buyerUUID, price,
                dateCreated, itemData, isSold, partiallySoldAmountLeft, adminMessage,
                auctionTime, itemName, isBIDAuction, bids, claimed);
    }

    private static List<BidData> deserializeBidData(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();
        List<BidData> list = gson.fromJson(json, BID_LIST_TYPE);
        return list != null ? list : new ArrayList<>();
    }
}
