package erze.stock;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import java.text.SimpleDateFormat;

import java.io.File;
import java.io.IOException;

public class StockCommand implements CommandExecutor, Listener {
    private final JavaPlugin plugin;
    private StockManager stockManager;
    private File playerDataFile;
    private FileConfiguration playerData;
    private Economy econ;
    private Set<UUID> openGuiPlayers = ConcurrentHashMap.newKeySet();
    private Map<UUID, Integer> playerPages = new ConcurrentHashMap<>();

    public StockCommand(JavaPlugin plugin, StockManager stockManager, Economy econ) {
        this.plugin = plugin;
        this.stockManager = stockManager;
        this.econ = econ;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        playerDataFile = new File(plugin.getDataFolder(), "playerdata.yml");
        if (!playerDataFile.exists()) {
            try {
                playerDataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        playerData = YamlConfiguration.loadConfiguration(playerDataFile);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        Player player = (Player) sender;
        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                player.sendMessage("페이지 번호가 유효하지 않습니다.");
                return true;
            }
        }
        openStockGui(player, page);
        return true;
    }

    private void openStockGui(Player player, int page) {
        openGuiPlayers.add(player.getUniqueId());
        playerPages.put(player.getUniqueId(), page);

        Inventory gui = Bukkit.createInventory(null, 54, "주식 구매창 [페이지: " + page + "]");

        addStockItems(player, gui, page);

        addPageNavigationItems(gui, page);

        ItemStack clockItem = new ItemStack(Material.CLOCK);
        ItemMeta clockMeta = clockItem.getItemMeta();
        if (clockMeta != null) {
            String timeUntilNextChange = calculateNextChangeFormatted();
            clockMeta.setDisplayName(ChatColor.GOLD + "변동: " + timeUntilNextChange);
            clockItem.setItemMeta(clockMeta);
        }
        gui.setItem(4, clockItem);

        player.openInventory(gui);
    }


    public void refreshOpenGuis() {
        for (UUID playerId : openGuiPlayers) {
            Player player = Bukkit.getServer().getPlayer(playerId);
            if (player != null && player.isOnline()) {
                Integer page = playerPages.getOrDefault(playerId, 1);
                openStockGui(player, page);

                openGuiPlayers.add(player.getUniqueId());
                playerPages.put(player.getUniqueId(), page);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            UUID playerId = event.getPlayer().getUniqueId();
            openGuiPlayers.remove(playerId);
            playerPages.remove(playerId);
        }
    }

    public String calculateNextChangeFormatted() {
        long nextUpdateTime = stockManager.getNextUpdateTime();

        Date nextUpdateDate = new Date(nextUpdateTime);
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm [MM/dd]");
        return dateFormat.format(nextUpdateDate);
    }

    private void addStockItems(Player player, Inventory gui, int page) {
        String playerUUID = player.getUniqueId().toString();
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection stocksSection = config.getConfigurationSection("stocks");
        if (stocksSection == null) return;

        List<String> stockNames = new ArrayList<>(stocksSection.getKeys(false));
        int startIndex = (page - 1) * 36;
        int endIndex = Math.min(stockNames.size(), startIndex + 36);
        for (int i = startIndex; i < endIndex; i++) {
            String stockName = stockNames.get(i);
            boolean isDelisted = config.getBoolean("stocks." + stockName + ".isDelisted", false);

            ItemStack stockItem;
            if (isDelisted) {
                stockItem = new ItemStack(Material.BARRIER);
            } else {
                stockItem = new ItemStack(Material.BOOK);
            }

            ItemMeta stockMeta = stockItem.getItemMeta();
            if (stockMeta != null) {
                stockMeta.setDisplayName(isDelisted ? ChatColor.RED + "상장폐지: " + stockName : "주식: " + stockName);

                List<String> lore = new ArrayList<>();
                if (!isDelisted) {
                    int currentPrice = config.getInt("stocks." + stockName + ".currentPrice");
                    int previousPrice = config.getInt("stocks." + stockName + ".previousPrice", currentPrice);
                    double changePercentage = previousPrice > 0 ? ((double) (currentPrice - previousPrice) / previousPrice) * 100.0 : 0;
                    int shareIssuance = config.getInt("stocks." + stockName + ".shareIssuance");
                    int purchaseLimit = config.getInt("stocks." + stockName + ".purchaseLimit");
                    int remainingStocks = config.getInt("stocks." + stockName + ".remainingStocks");

                    int ownedStocks = playerData.getInt(playerUUID + ".stocks." + stockName, 0);

                    lore.add(ChatColor.WHITE + "현재가: " + currentPrice + " " +
                            (changePercentage == 0 ? ChatColor.GRAY + "(─)" :
                                    (changePercentage > 0 ? ChatColor.RED + "(↑" : ChatColor.BLUE + "(↓") +
                                            String.format("%.2f%%)", Math.abs(changePercentage))));
                    lore.add(ChatColor.WHITE + "주식 발행: " + (shareIssuance == 0 ? "제한 없음" : remainingStocks + " / " + shareIssuance));
                    lore.add(ChatColor.WHITE + "구매 제한: " + (purchaseLimit == 0 ? "제한 없음 [보유: " + ownedStocks + "]" : purchaseLimit-ownedStocks + " / " + purchaseLimit));
                    lore.add("");
                    lore.add(ChatColor.AQUA + "매수 (1주): 좌클릭  (10주): 쉬프트+좌클릭");
                    lore.add(ChatColor.AQUA + "매도 (1주): 우클릭  (10주): 쉬프트+우클릭");
                } else {
                    lore.add(ChatColor.RED + "이 주식은 상장폐지되었습니다.");
                }
                stockMeta.setLore(lore);
                stockItem.setItemMeta(stockMeta);
            }
            gui.setItem(9 + i - startIndex, stockItem);
        }
    }

    private void addPageNavigationItems(Inventory gui, int page) {
        ItemStack prevPage = new ItemStack(Material.BLUE_DYE);
        ItemMeta prevMeta = prevPage.getItemMeta();
        if (prevMeta != null) {
            prevMeta.setDisplayName("이전 페이지");
            prevPage.setItemMeta(prevMeta);
        }
        gui.setItem(47, prevPage);

        ItemStack nextPage = new ItemStack(Material.RED_DYE);
        ItemMeta nextMeta = nextPage.getItemMeta();
        if (nextMeta != null) {
            nextMeta.setDisplayName("다음 페이지");
            nextPage.setItemMeta(nextMeta);
        }
        gui.setItem(51, nextPage);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().startsWith("주식 구매창")) return;
        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getItemMeta() == null) return;

        String itemName = clickedItem.getItemMeta().getDisplayName();
        Player player = (Player) event.getWhoClicked();
        int currentPage = getCurrentPage(event.getView().getTitle());
        int totalPages = calculateTotalPages();

        if (itemName.equals("이전 페이지")) {
            if (currentPage > 1) {
                openStockGui(player, currentPage - 1);
                openGuiPlayers.add(player.getUniqueId());
                playerPages.put(player.getUniqueId(), currentPage -1);
            } else {
                player.sendMessage("첫 번째 페이지입니다.");
            }
        }
        else if (itemName.equals("다음 페이지")) {
            if (currentPage < totalPages) {
                openStockGui(player, currentPage + 1);
                openGuiPlayers.add(player.getUniqueId());
                playerPages.put(player.getUniqueId(), currentPage + 1);
            } else {
                player.sendMessage("마지막 페이지입니다.");
            }
        }

        if (clickedItem.getType() == Material.BOOK && !isDelisted(clickedItem)) {
            String stockName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName()).substring(4);
            int amount = event.getClick().isShiftClick() ? 10 : 1;

            if (event.isLeftClick()) {
                buyStock(player, stockName, amount);
            } else if (event.isRightClick()) {
                sellStock(player, stockName, amount);
            }
            refreshOpenGuis();
        }
    }

    private boolean isDelisted(ItemStack item) {
        return item.getType() == Material.BARRIER;
    }

    // 매수
    public void buyStock(Player player, String stockName, int amount) {
        String playerUUID = player.getUniqueId().toString();
        FileConfiguration config = plugin.getConfig();
        int stockPrice = config.getInt("stocks." + stockName + ".currentPrice");
        int totalCost = stockPrice * amount;
        int shareIssuance = config.getInt("stocks." + stockName + ".shareIssuance");
        int remainingStocks = config.getInt("stocks." + stockName + ".remainingStocks", shareIssuance);
        int purchaseLimit = config.getInt("stocks." + stockName + ".purchaseLimit");

        if (!econ.has(player, totalCost)) {
            player.sendMessage(ChatColor.RED + "주식을 구매하기에 충분한 자금이 없습니다.");
            return;
        }

        if (shareIssuance != 0 && amount > remainingStocks) {
            player.sendMessage(ChatColor.RED + "남은 재고가 부족합니다.");
            return;
        }

        int ownedStocks = playerData.getInt(playerUUID + ".stocks." + stockName, 0);
        if (purchaseLimit != 0 && (ownedStocks + amount) > purchaseLimit) {
            player.sendMessage(ChatColor.RED + "개인당 구매 제한을 초과합니다.");
            return;
        }

        econ.withdrawPlayer(player, totalCost);
        playerData.set(playerUUID + ".stocks." + stockName, ownedStocks + amount);
        if (shareIssuance != 0) {
            config.set("stocks." + stockName + ".remainingStocks", remainingStocks - amount);
            plugin.saveConfig();
        }
        savePlayerData();
        player.sendMessage(ChatColor.GREEN + "성공적으로 " + amount + "주의 " + stockName + "을(를) 구매했습니다.");
    }

    // 매도
    public void sellStock(Player player, String stockName, int amount) {
        String playerUUID = player.getUniqueId().toString();
        FileConfiguration config = plugin.getConfig();
        int remainingStocks = config.getInt("stocks." + stockName + ".remainingStocks");
        int stockPrice = plugin.getConfig().getInt("stocks." + stockName + ".currentPrice");
        int totalRevenue = stockPrice * amount;

        int ownedStocks = playerData.getInt(playerUUID + ".stocks." + stockName, 0);
        if (ownedStocks < amount) {
            player.sendMessage(ChatColor.RED + "판매할 충분한 주식이 없습니다.");
            return;
        }

        econ.depositPlayer(player, totalRevenue);
        playerData.set(playerUUID + ".stocks." + stockName, ownedStocks - amount);
        config.set("stocks." + stockName + ".remainingStocks", remainingStocks + amount);
        plugin.saveConfig();
        savePlayerData();
        player.sendMessage(ChatColor.GREEN + "성공적으로 " + amount + "주의 " + stockName + "을(를) 판매했습니다.");
    }

    private void savePlayerData() {
        try {
            playerData.save(playerDataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int calculateTotalPages() {
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection stocksSection = config.getConfigurationSection("stocks");
        if (stocksSection == null) return 1;
        int totalStocks = stocksSection.getKeys(false).size();
        return (int) Math.ceil((double) totalStocks / 36);
    }

    private int getCurrentPage(String title) {
        String[] parts = title.split(" ");
        try {
            return Integer.parseInt(parts[parts.length - 1].replace("]", ""));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

}
