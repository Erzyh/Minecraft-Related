package erze.stock;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.time.Instant;

public class StockManager {
    private final JavaPlugin plugin;
    private int updateInterval;
    private long nextUpdateTime;
    private BukkitRunnable priceUpdateTask;

    public StockManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadUpdateInterval();
        loadNextUpdateTime();
        startPriceUpdateTask();
    }

    private void loadUpdateInterval() {
        this.updateInterval = plugin.getConfig().getInt("updateInterval", 720);
    }

    private void loadNextUpdateTime() {
        calculateNextUpdateTime();
        saveNextUpdateTime();
    }

    private void calculateNextUpdateTime() {
        this.nextUpdateTime = Instant.now().toEpochMilli() + updateInterval * 60000;
    }

    public long getNextUpdateTime() {
        return this.nextUpdateTime;
    }

    private void startPriceUpdateTask() {
        if (this.priceUpdateTask != null) {
            this.priceUpdateTask.cancel();
        }
        this.priceUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateStockPrices();
            }
        };
        priceUpdateTask.runTaskTimer(plugin, 0L, 20L * 60 * updateInterval);
    }

    private void updateStockPrices() {
        FileConfiguration config = plugin.getConfig();
        for (String key : config.getConfigurationSection("stocks").getKeys(false)) {
            int currentPrice = config.getInt("stocks." + key + ".currentPrice");
            int fluctuationProbability = config.getInt("stocks." + key + ".fluctuationProbability", 50);
            int riseProbability = config.getInt("stocks." + key + ".riseProbability", 50);
            double minPercent = config.getDouble("stocks." + key + ".minPercent", 5);
            double maxPercent = config.getDouble("stocks." + key + ".maxPercent", 20);
            int upperLimit = config.getInt("stocks." + key + ".upperLimit");
            int lowerLimit = config.getInt("stocks." + key + ".lowerLimit");

            config.set("stocks." + key + ".previousPrice", currentPrice);

            // 가격 변동
            if (Math.random() * 100 < fluctuationProbability) {
                double changePercent = minPercent + (Math.random() * (maxPercent - minPercent));
                boolean isRise = Math.random() * 100 < riseProbability;
                double changeAmount = currentPrice * (changePercent / 100.0);
                int finalChangeAmount = (int) Math.round(changeAmount * (isRise ? 1 : -1));
                currentPrice += finalChangeAmount;

                // 상한가 확인
                if (upperLimit > 0) {
                    currentPrice = Math.min(upperLimit, currentPrice);
                }
                // 하한가 확인
                if (currentPrice <= lowerLimit) {
                    currentPrice = lowerLimit;
                    config.set("stocks." + key + ".isDelisted", true);
                    delistStockAndUpdatePlayerData(key);
                }
            }
            config.set("stocks." + key + ".currentPrice", currentPrice);
        }

        plugin.saveConfig();
        calculateNextUpdateTime();
        saveNextUpdateTime();
    }

    public void setUpdateInterval(int updateInterval) {
        this.updateInterval = updateInterval;
        plugin.getConfig().set("updateInterval", updateInterval);
        plugin.saveConfig();
        startPriceUpdateTask();
    }

    private void saveNextUpdateTime() {
        plugin.getConfig().set("nextUpdateTime", nextUpdateTime);
        plugin.saveConfig();
    }

    private void delistStockAndUpdatePlayerData(String stockKey) {
        File playerDataFile = new File(plugin.getDataFolder(), "playerdata.yml");
        FileConfiguration playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);

        for (String playerUUID : playerDataConfig.getKeys(false)) {
            String path = playerUUID + ".stocks." + stockKey;
            if (playerDataConfig.contains(path)) {
                playerDataConfig.set(path + ".amount", 0);
            }
        }

        try {
            playerDataConfig.save(playerDataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
