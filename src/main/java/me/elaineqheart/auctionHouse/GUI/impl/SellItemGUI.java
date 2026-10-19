package me.elaineqheart.auctionHouse.GUI.impl;

import me.elaineqheart.auctionHouse.AuctionHouse;
import me.elaineqheart.auctionHouse.GUI.InventoryButton;
import me.elaineqheart.auctionHouse.GUI.InventoryGUI;
import me.elaineqheart.auctionHouse.GUI.other.Sounds;
import me.elaineqheart.auctionHouse.GUI.other.input.InputHandler;
import me.elaineqheart.auctionHouse.data.persistentStorage.ItemNoteStorage;
import me.elaineqheart.auctionHouse.data.persistentStorage.local.SettingManager;
import me.elaineqheart.auctionHouse.data.persistentStorage.local.configs.Blacklist;
import me.elaineqheart.auctionHouse.data.persistentStorage.local.configs.M;
import me.elaineqheart.auctionHouse.data.persistentStorage.local.data.ConfigManager;
import me.elaineqheart.auctionHouse.data.ram.AhConfiguration;
import me.elaineqheart.auctionHouse.data.ram.ItemManager;
import me.elaineqheart.auctionHouse.data.ram.ItemNote;
import me.elaineqheart.auctionHouse.pluginDependencies.DiscordSRVHook;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SellItemGUI extends InventoryGUI {

    private final AhConfiguration c;
    private ItemStack pendingItem;
    private int amount = 1;
    private double price = 100.0;
    private boolean isBIDAuction = false;

    private int selectedPlayerSlot = -1; // 记录玩家背包槽位 (0-35)

    public SellItemGUI(AhConfiguration configuration, ItemStack initialItem) {
        super();
        this.c = configuration;
        if (initialItem != null && initialItem.getType() != Material.AIR) {
            this.pendingItem = initialItem.clone();
            this.amount = initialItem.getAmount();
        }
    }

    public SellItemGUI(AhConfiguration configuration) {
        this(configuration, null);
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 6 * 9, M.getFormatted("inventory-titles.sell-menu"));
    }

    @Override
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        event.setCancelled(true);
        int rawSlot = event.getRawSlot();
        if (rawSlot >= getInventory().getSize()) {
            // 点击了玩家自身的背包
            int playerSlot = event.getSlot();
            ItemStack clickedItem = event.getCurrentItem();
            Player p = (Player) event.getWhoClicked();

            if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                return;
            }

            if (Blacklist.isBlacklisted(clickedItem)) {
                p.sendMessage(M.getFormatted("command-feedback.item-blacklisted"));
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.1f);
                return;
            }

            this.pendingItem = clickedItem.clone();
            this.amount = clickedItem.getAmount();
            this.selectedPlayerSlot = playerSlot;
            Sounds.click(event);
            decorate(p);
            return;
        }

        super.onClick(event);
    }

    @Override
    public void decorate(Player player) {
        fillOutPlaces(new String[]{
                "# # # # # # # # #",
                "# . # t # a # p #",
                "# # # # # # # # #",
                "# # # # # # # # #",
                "# # # # # # # # #",
                "b # # # # # # # c"
        }, player);
        super.decorate(player);
    }

    private void fillOutPlaces(String[] places, Player player) {
        for (int i = 0; i < places.length; i++) {
            for (int j = 0; j < places[i].length(); j += 2) {
                int slot = i * 9 + j / 2;
                char ch = places[i].charAt(j);
                switch (ch) {
                    case '#' -> this.addButton(slot, fillerItem());
                    case '.' -> this.addButton(slot, itemSlotButton(player));
                    case 't' -> this.addButton(slot, modeSwitchButton());
                    case 'a' -> this.addButton(slot, amountButton());
                    case 'p' -> this.addButton(slot, priceButton());
                    case 'b' -> this.addButton(slot, backButton());
                    case 'c' -> this.addButton(slot, confirmSellButton(player));
                }
            }
        }
    }

    private InventoryButton fillerItem() {
        return new InventoryButton()
                .creator(player -> ItemManager.fillerItem)
                .consumer(event -> {});
    }

    private InventoryButton itemSlotButton(Player player) {
        return new InventoryButton()
                .creator(p -> {
                    if (pendingItem == null || pendingItem.getType() == Material.AIR) {
                        ItemStack placeholder = new ItemStack(Material.HOPPER);
                        ItemMeta meta = placeholder.getItemMeta();
                        if (meta != null) {
                            meta.setItemName("§e放入待上架物品");
                            List<String> lore = new ArrayList<>();
                            lore.add("§a[直接点击下方背包物品] 快速放入");
                            lore.add("§7或点击此处放入当前主手持有的物品");
                            meta.setLore(lore);
                            placeholder.setItemMeta(meta);
                        }
                        return placeholder;
                    } else {
                        ItemStack display = pendingItem.clone();
                        display.setAmount(amount);
                        return display;
                    }
                })
                .consumer(event -> {
                    ItemStack handItem = player.getInventory().getItemInMainHand();
                    if (pendingItem == null) {
                        if (handItem == null || handItem.getType() == Material.AIR) {
                            player.sendMessage(M.getFormatted("command-feedback.no-item-in-hand"));
                            Sounds.villagerDeny(event);
                            return;
                        }
                        if (Blacklist.isBlacklisted(handItem)) {
                            player.sendMessage(M.getFormatted("command-feedback.item-blacklisted"));
                            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.1f);
                            return;
                        }
                        this.pendingItem = handItem.clone();
                        this.amount = handItem.getAmount();
                        this.selectedPlayerSlot = player.getInventory().getHeldItemSlot();
                        Sounds.click(event);
                        decorate(player);
                    } else {
                        // 取消选择或替换
                        this.pendingItem = null;
                        this.amount = 1;
                        this.selectedPlayerSlot = -1;
                        Sounds.click(event);
                        decorate(player);
                    }
                });
    }

    private InventoryButton modeSwitchButton() {
        return new InventoryButton()
                .creator(player -> {
                    ItemStack item = new ItemStack(isBIDAuction ? Material.GOLD_BLOCK : Material.EMERALD);
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.setItemName(isBIDAuction ? "§6当前模式: 竞价拍卖 (BID)" : "§a当前模式: 一口价直购 (BIN)");
                        List<String> lore = new ArrayList<>();
                        lore.add("§7点击在 一口价 和 竞拍 之间切换");
                        lore.add(isBIDAuction ? "§e买家参与竞价，价高者得" : "§e买家直接支付该金额购买");
                        meta.setLore(lore);
                        item.setItemMeta(meta);
                    }
                    return item;
                })
                .consumer(event -> {
                    if (isBIDAuction) {
                        if (!SettingManager.BINAuctions) {
                            c.getPlayer().sendMessage(M.getFormatted("chat.non-existent2"));
                            return;
                        }
                        isBIDAuction = false;
                    } else {
                        if (!SettingManager.BIDAuctions) {
                            c.getPlayer().sendMessage(M.getFormatted("chat.non-existent2"));
                            return;
                        }
                        isBIDAuction = true;
                    }
                    Sounds.click(event);
                    decorate(c.getPlayer());
                });
    }

    private InventoryButton amountButton() {
        return new InventoryButton()
                .creator(player -> {
                    ItemStack item = new ItemStack(Material.REPEATER);
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.setItemName("§b出售数量: §e" + amount);
                        List<String> lore = new ArrayList<>();
                        lore.add("§7左键: 数量 +1");
                        lore.add("§7右键: 数量 -1");
                        lore.add("§eShift+左键: 设为最大可售数量");
                        lore.add("§eShift+右键: 手动在聊天栏输入");
                        meta.setLore(lore);
                        item.setItemMeta(meta);
                    }
                    return item;
                })
                .consumer(event -> {
                    Player p = c.getPlayer();
                    int maxAllowed = (pendingItem != null) ? pendingItem.getAmount() : 64;
                    if (event.isShiftClick() && event.isLeftClick()) {
                        amount = maxAllowed;
                        Sounds.click(event);
                        decorate(p);
                        return;
                    }
                    if (event.isShiftClick() && event.isRightClick()) {
                        Sounds.click(event);
                        AuctionHouse.getInputManager().open(p, "inventory-titles.anvil-set-amount", new InputHandler() {
                            @Override
                            public void execute(Player player, String typedText) {
                                try {
                                    int val = Integer.parseInt(typedText.trim());
                                    if (val >= 1 && val <= maxAllowed) {
                                        amount = val;
                                    } else {
                                        player.sendMessage(M.getFormatted("command-feedback.invalid-number7"));
                                    }
                                } catch (Exception e) {
                                    player.sendMessage(M.getFormatted("command-feedback.invalid-number7"));
                                }
                                AuctionHouse.getGuiManager().openGUI(SellItemGUI.this, player);
                            }

                            @Override
                            public void onClose(Player player) {
                                AuctionHouse.getGuiManager().openGUI(SellItemGUI.this, player);
                            }
                        });
                        return;
                    }
                    if (event.isLeftClick()) {
                        if (amount < maxAllowed) amount++;
                    } else if (event.isRightClick()) {
                        if (amount > 1) amount--;
                    }
                    Sounds.click(event);
                    decorate(p);
                });
    }

    private InventoryButton priceButton() {
        return new InventoryButton()
                .creator(player -> {
                    ItemStack item = new ItemStack(Material.SUNFLOWER);
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.setItemName(isBIDAuction ? "§6起拍价格: §e" + price + " H币" : "§6一口价: §e" + price + " H币");
                        List<String> lore = new ArrayList<>();
                        lore.add("§a[左键点击] 直接输入设定金额");
                        lore.add("§7右键: 快捷 +10 H币");
                        lore.add("§7Shift+左键: +100 H币 | Shift+右键: -100 H币");
                        meta.setLore(lore);
                        item.setItemMeta(meta);
                    }
                    return item;
                })
                .consumer(event -> {
                    Player p = c.getPlayer();
                    // 左键或中键点击直接调出输入框
                    if (event.isLeftClick() && !event.isShiftClick() || event.getClick().isCreativeAction()) {
                        promptCustomPrice(p);
                        return;
                    }
                    if (event.isShiftClick()) {
                        if (event.isLeftClick()) price += 100;
                        else if (event.isRightClick() && price > 100) price -= 100;
                    } else if (event.isRightClick()) {
                        price += 10;
                    }
                    if (price < 1) price = 1;
                    Sounds.click(event);
                    decorate(p);
                });
    }

    private void promptCustomPrice(Player p) {
        AuctionHouse.getInputManager().open(p, isBIDAuction ? "inventory-titles.anvil-set-bid" : "inventory-titles.anvil-set-amount", new InputHandler() {
            @Override
            public void execute(Player player, String typedText) {
                try {
                    double val = Double.parseDouble(typedText.trim());
                    if (val > 0) {
                        price = Math.round(val * 100.0) / 100.0;
                    } else {
                        player.sendMessage(M.getFormatted("command-feedback.invalid-number2"));
                    }
                } catch (Exception e) {
                    player.sendMessage(M.getFormatted("command-feedback.invalid-number2"));
                }
                AuctionHouse.getGuiManager().openGUI(SellItemGUI.this, player);
            }

            @Override
            public void onClose(Player player) {
                AuctionHouse.getGuiManager().openGUI(SellItemGUI.this, player);
            }
        });
    }

    private InventoryButton backButton() {
        return new InventoryButton()
                .creator(player -> ItemManager.backToMainMenu)
                .consumer(event -> {
                    Sounds.click(event);
                    AuctionHouse.getGuiManager().openGUI(new AuctionHouseGUI(c), c.getPlayer());
                });
    }

    private InventoryButton confirmSellButton(Player player) {
        return new InventoryButton()
                .creator(p -> {
                    ItemStack item = new ItemStack(Material.LIME_CONCRETE);
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.setItemName("§a✔ 确认上架");
                        List<String> lore = new ArrayList<>();
                        lore.add("§7上架模式: " + (isBIDAuction ? "§6竞价拍卖" : "§a一口价直购"));
                        lore.add("§7出售数量: §e" + amount);
                        lore.add("§7设定价格: §e" + price + " H币");
                        lore.add("");
                        lore.add(pendingItem == null ? "§c[请先放入需要上架的物品]" : "§e[点击立即完成上架]");
                        meta.setLore(lore);
                        item.setItemMeta(meta);
                    }
                    return item;
                })
                .consumer(event -> {
                    if (pendingItem == null || pendingItem.getType() == Material.AIR) {
                        player.sendMessage(M.getFormatted("command-feedback.no-item-in-hand"));
                        Sounds.villagerDeny(event);
                        return;
                    }

                    // 检查上架上限
                    if (ItemNoteStorage.getNumberOfAuctions(player.getUniqueId()) >= ConfigManager.permissions.getAuctionSlots(player)) {
                        player.sendMessage(M.getFormatted("command-feedback.reached-max-auctions",
                                "%limit%", String.valueOf(ConfigManager.permissions.getAuctionSlots(player))));
                        Sounds.villagerDeny(event);
                        return;
                    }

                    // 校验价格
                    if (isBIDAuction) {
                        if (price < SettingManager.minBIDPrice) {
                            player.sendMessage(M.getFormatted("command-feedback.min-bid", SettingManager.minBIDPrice));
                            return;
                        }
                        if (SettingManager.maxBIDPrice != -1 && price > SettingManager.maxBIDPrice) {
                            player.sendMessage(M.getFormatted("command-feedback.max-bid", SettingManager.maxBIDPrice));
                            return;
                        }
                    } else {
                        if (price < SettingManager.minBINPrice) {
                            player.sendMessage(M.getFormatted("command-feedback.min-bin", SettingManager.minBINPrice));
                            return;
                        }
                        if (SettingManager.maxBINPrice != -1 && price > SettingManager.maxBINPrice) {
                            player.sendMessage(M.getFormatted("command-feedback.max-bin", SettingManager.maxBINPrice));
                            return;
                        }
                    }

                    // 校验并扣除玩家背包中的对应物品
                    org.bukkit.inventory.PlayerInventory playerInv = player.getInventory();
                    ItemStack targetSlotItem = null;
                    if (selectedPlayerSlot >= 0 && selectedPlayerSlot < 36) {
                        targetSlotItem = playerInv.getItem(selectedPlayerSlot);
                    }

                    // 如果特定槽位不匹配，则在背包中智能寻找同种物品
                    if (targetSlotItem == null || !targetSlotItem.isSimilar(pendingItem) || targetSlotItem.getAmount() < amount) {
                        selectedPlayerSlot = -1;
                        for (int s = 0; s < 36; s++) {
                            ItemStack it = playerInv.getItem(s);
                            if (it != null && it.isSimilar(pendingItem) && it.getAmount() >= amount) {
                                targetSlotItem = it;
                                selectedPlayerSlot = s;
                                break;
                            }
                        }
                    }

                    if (targetSlotItem == null || !targetSlotItem.isSimilar(pendingItem) || targetSlotItem.getAmount() < amount) {
                        player.sendMessage(" §b§l拍卖 §f§l>>> §c背包中未找到对应物品或数量不足！");
                        Sounds.villagerDeny(event);
                        return;
                    }

                    ItemStack toAuction = pendingItem.clone();
                    toAuction.setAmount(amount);

                    ItemNote note;
                    try {
                        note = ItemNoteStorage.createNote(player, toAuction, price, isBIDAuction);
                    } catch (Exception ex) {
                        AuctionHouse.getInstance().getLogger().severe("GUI上架物品异常: " + ex.getMessage());
                        ex.printStackTrace();
                        player.sendMessage(" §b§l拍卖 §f§l>>> §c上架失败，数据保存异常！");
                        return;
                    }

                    // 成功保存后再扣除背包对应槽位的数量
                    targetSlotItem.setAmount(targetSlotItem.getAmount() - amount);
                    Sounds.createAuction(player);
                    player.sendMessage(M.getFormatted("command-feedback.auction", price));

                    // 全服广播
                    String itemName = note.getItemName();
                    if (SettingManager.auctionAnnouncementsEnabled) {
                        String announcement = M.getFormatted(
                                isBIDAuction ? "chat.bid-announcement" : "chat.auction-announcement",
                                price,
                                "%player%", M.formatPlayer(player.getDisplayName(), player.getUniqueId()),
                                "%item%", itemName,
                                "%amount%", String.valueOf(amount));
                        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                            if (ConfigManager.playerPreferences.hasAnnouncementsEnabled(onlinePlayer.getUniqueId()) && !onlinePlayer.equals(player)) {
                                onlinePlayer.sendMessage(announcement);
                            }
                        }
                    }

                    // DiscordSRV
                    String key = isBIDAuction ? "discord-srv.new-bid-auction-title" : "discord-srv.new-bin-auction-title";
                    String title = Pattern.compile("§.").splitAsStream(M.getFormatted(key,
                            price,
                            "%player%", M.formatPlayer(player.getDisplayName(), player.getUniqueId()),
                            "%item%", itemName,
                            "%amount%", String.valueOf(amount))).collect(Collectors.joining());
                    DiscordSRVHook.sendAuctionListingToDiscord(player, toAuction, title);

                    // 返回拍卖主界面
                    AuctionHouse.getGuiManager().openGUI(new AuctionHouseGUI(c), player);
                });
    }
}
