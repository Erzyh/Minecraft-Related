package erze.stock;

import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;

public class Stock extends JavaPlugin {

    private Economy econ = null;
    private StockManager stockManager;

    @Override
    public void onEnable() {
        if (!setupEconomy() ) {
            System.out.println(String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getConfig().options().copyDefaults(true);
        saveDefaultConfig();

        this.stockManager = new StockManager(this);

        getCommand("주식").setExecutor(new StockCommand(this, stockManager, econ));
        getCommand("주식관리").setExecutor(new StockManageCommand(this, stockManager));
    }

    @Override
    public void onDisable() {
        saveConfig();
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }
}
