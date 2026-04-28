package erze.stock;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class StockManageCommand implements CommandExecutor {
    private final Stock plugin;
    private StockManager stockManager;

    public StockManageCommand(Stock plugin, StockManager stockManager) {
        this.plugin = plugin;
        this.stockManager = stockManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission("e-stock.manage")) {
            sender.sendMessage("이 명령어를 사용할 권한이 없습니다.");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        if (args.length > 0) {
            switch (args[0].toLowerCase()) {
                case "추가":
                    if (args.length < 2) {
                        sender.sendMessage("사용법: /주식관리 추가 <주식명>");
                        return true;
                    }
                    addStock(sender, args[1]);
                    break;
                case "삭제":
                    if (args.length < 2) {
                        sender.sendMessage("사용법: /주식관리 삭제 <주식명>");
                        return true;
                    }
                    deleteStock(sender, args[1]);
                    break;
                case "로드":
                    reloadConfig(sender);
                    break;
                case "리스트":
                    listStocks(sender);
                    break;
                case "변동":
                    if (args.length < 2) {
                        sender.sendMessage("사용법: /주식관리 변동 <분>");
                        return true;
                    }
                    try {
                        int newInterval = Integer.parseInt(args[1]);
                        stockManager.setUpdateInterval(newInterval);
                        sender.sendMessage("주식 가격 변동 주기가 " + newInterval + "분으로 설정되었습니다.");
                    } catch (NumberFormatException e) {
                        sender.sendMessage("올바른 숫자를 입력해주세요.");
                    }
                    break;
                case "상장":
                    if (args.length < 2) {
                        sender.sendMessage("사용법: /주식관리 상장 <주식명>");
                        return true;
                    }
                    stockListing(sender, args[1]);
                    break;
                default:
                    sender.sendMessage("사용 가능한 명령어: 추가, 삭제, 로드, 리스트, 변동, 상장");
                    break;
            }
        } else {
            sender.sendMessage("사용 가능한 명령어: 추가, 삭제, 로드, 리스트, 변동, 상장");
        }
        return true;
    }

    private void addStock(CommandSender sender, String stockName) {
        FileConfiguration config = plugin.getConfig();
        if (config.contains("stocks." + stockName)) {
            sender.sendMessage("이미 존재하는 주식입니다.");
        } else {
            String basePath = "stocks." + stockName;
            config.set(basePath + ".defaultPrice", 100); // 기본값
            config.set(basePath + ".upperLimit", 10000); // 상한가
            config.set(basePath + ".lowerLimit", 1); // 하한가
            config.set(basePath + ".minPercent", 5); // 최소 변동치
            config.set(basePath + ".maxPercent", 50); // 최대 변동치
            config.set(basePath + ".fluctuationProbability", 50); // 변동 확률
            config.set(basePath + ".riseProbability", 50); // 상승 확률
            config.set(basePath + ".shareIssuance", 0); // 주식 발행 수
            config.set(basePath + ".purchaseLimit", 0); // 개인당 구매 제한
            config.set(basePath + ".previousPrice", 100); // 이전가격
            config.set(basePath + ".currentPrice", 100); // 현재 가격
            config.set(basePath + ".isDelisted", false); // 상장폐지 여부
            config.set(basePath + ".remainingStocks", 100); // 남은 주식 발행 수

            plugin.saveConfig();
            sender.sendMessage(stockName + " 주식을 추가했습니다.");
            plugin.reloadConfig();
        }
    }

    private void deleteStock(CommandSender sender, String stockName) {
        FileConfiguration config = plugin.getConfig();
        if (!config.contains("stocks." + stockName)) {
            sender.sendMessage("존재하지 않는 주식입니다.");
        } else {
            config.set("stocks." + stockName, null);
            plugin.saveConfig();
            sender.sendMessage(stockName + " 주식을 삭제했습니다.");
        }
    }

    private void reloadConfig(CommandSender sender) {
        plugin.reloadConfig();
        sender.sendMessage("주식 데이터를 리로드했습니다.");
    }

    private void listStocks(CommandSender sender) {
        FileConfiguration config = plugin.getConfig();
        if (config.getConfigurationSection("stocks") == null || config.getConfigurationSection("stocks").getKeys(false).isEmpty()) {
            sender.sendMessage("등록된 주식이 없습니다.");
            return;
        }

        sender.sendMessage("등록된 주식 목록:");
        for (String stockName : config.getConfigurationSection("stocks").getKeys(false)) {
            sender.sendMessage("- 주식명: " + stockName);
        }
    }

    private void stockListing(CommandSender sender, String stockName) {
        FileConfiguration config = plugin.getConfig();

        boolean isDelisted = config.getBoolean("stocks." + stockName + ".isDelisted");

        if (!config.contains("stocks." + stockName) || !isDelisted) {
            sender.sendMessage("존재하지 않는 주식이거나 상장폐지되지 않은 주식입니다.");
        } else {

            int currentPrice = config.getInt("stocks." + stockName + ".defaultPrice");
            int shareIssuance = config.getInt("stocks." + stockName + ".shareIssuance");

            String basePath = "stocks." + stockName;
            config.set(basePath + ".previousPrice", currentPrice);
            config.set(basePath + ".currentPrice", currentPrice);
            config.set(basePath + ".isDelisted", false);
            config.set(basePath + ".remainingStocks", shareIssuance);

            plugin.saveConfig();
            sender.sendMessage(stockName + " 주식을 상장했습니다.");
            plugin.reloadConfig();
        }
    }

}
