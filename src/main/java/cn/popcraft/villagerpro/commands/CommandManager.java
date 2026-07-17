package cn.popcraft.villagerpro.commands;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.database.DatabaseDialect;
import cn.popcraft.villagerpro.database.DatabaseMigrationManager;
import cn.popcraft.villagerpro.gui.GUIManager;
import cn.popcraft.villagerpro.managers.VillageManager;
import cn.popcraft.villagerpro.managers.VillagerManager;
import cn.popcraft.villagerpro.managers.BuildingManager;
import cn.popcraft.villagerpro.managers.MarketManager;
import cn.popcraft.villagerpro.managers.PolicyManager;
import cn.popcraft.villagerpro.managers.CrisisManager;
import cn.popcraft.villagerpro.managers.CaravanManager;
import cn.popcraft.villagerpro.managers.VillageOrderManager;
import cn.popcraft.villagerpro.models.BuildingType;
import cn.popcraft.villagerpro.models.Village;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CommandManager implements CommandExecutor, TabCompleter {
    
    private static final List<String> VILLAGE_SUBCOMMANDS = Arrays.asList(
            "create", "info", "warehouse", "orders", "stats", "rules", "building", "market", "policy", "crisis", "caravan", "upgrade", "reload", "database");
    private static final List<String> VILLAGER_SUBCOMMANDS = Arrays.asList(
            "list", "recruit", "info", "remove", "upgrade", "specialize", "needs", "workstation");
    private static final List<String> UPGRADE_SUBCOMMANDS = Arrays.asList("village", "villager");

    /**
     * 初始化命令管理器
     */
    public static void initialize() {
        // 注册所有命令执行器
        VillagerPro.getInstance().getCommand("village").setExecutor(new CommandManager());
        VillagerPro.getInstance().getCommand("villager").setExecutor(new CommandManager());
        VillagerPro.getInstance().getCommand("upgrade").setExecutor(new CommandManager());
        
        // 注册Tab补全器
        VillagerPro.getInstance().getCommand("village").setTabCompleter(new CommandManager());
        VillagerPro.getInstance().getCommand("villager").setTabCompleter(new CommandManager());
        VillagerPro.getInstance().getCommand("upgrade").setTabCompleter(new CommandManager());
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if ("village".equalsIgnoreCase(command.getName()) && args.length > 0
                && "database".equalsIgnoreCase(args[0])) {
            return handleDatabaseCommand(sender, args);
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("§c该命令只能由玩家执行！");
            return true;
        }
        
        Player player = (Player) sender;
        
        switch (command.getName().toLowerCase()) {
            case "village":
                return handleVillageCommand(player, args);
            case "villager":
                return handleVillagerCommand(player, args);
            case "upgrade":
                return handleUpgradeCommand(player, args);
            default:
                return false;
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if ("village".equalsIgnoreCase(command.getName())) {
            if (args.length == 1 && !(sender instanceof Player)) {
                List<String> database = new ArrayList<>();
                if (sender.hasPermission("villagerpro.database.migrate")) {
                    StringUtil.copyPartialMatches(args[0], Collections.singletonList("database"), database);
                }
                return database;
            }
            if (args.length == 2 && "database".equalsIgnoreCase(args[0])) {
                if (!sender.hasPermission("villagerpro.database.migrate")) {
                    return Collections.emptyList();
                }
                List<String> database = new ArrayList<>();
                StringUtil.copyPartialMatches(args[1], Collections.singletonList("migrate"), database);
                return database;
            }
            if (args.length == 3 && "database".equalsIgnoreCase(args[0])
                    && "migrate".equalsIgnoreCase(args[1])) {
                if (!sender.hasPermission("villagerpro.database.migrate")) {
                    return Collections.emptyList();
                }
                List<String> databases = new ArrayList<>();
                String target = DatabaseDialect.MYSQL == cn.popcraft.villagerpro.database.DatabaseManager.getDialect()
                        ? "sqlite" : "mysql";
                StringUtil.copyPartialMatches(args[2], Collections.singletonList(target), databases);
                return databases;
            }
            if (args.length == 4 && "database".equalsIgnoreCase(args[0])
                    && "migrate".equalsIgnoreCase(args[1])
                    && sender.hasPermission("villagerpro.database.migrate")) {
                List<String> confirmation = new ArrayList<>();
                StringUtil.copyPartialMatches(args[3], Collections.singletonList("confirm"), confirmation);
                return confirmation;
            }
        }

        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }
        
        Player player = (Player) sender;
        List<String> completions = new ArrayList<>();
        
        switch (command.getName().toLowerCase()) {
            case "village":
                if (args.length == 1) {
                    StringUtil.copyPartialMatches(args[0], VILLAGE_SUBCOMMANDS, completions);
                    if (!sender.hasPermission("villagerpro.database.migrate")) {
                        completions.remove("database");
                    }
                } else if (args.length == 2) {
                    switch (args[0].toLowerCase()) {
                        case "info":
                        case "warehouse":
                        case "upgrade":
                        case "specialize":
                        case "needs":
                        case "workstation":
                        case "stats":
                        case "rules":
                        case "reload":
                            // 这些子命令没有额外参数
                            break;
                        case "orders":
                            if (player.hasPermission("villagerpro.village.orders.resolve")) {
                                StringUtil.copyPartialMatches(args[1],
                                        Collections.singletonList("resolve"), completions);
                            }
                            break;
                        case "database":
                            completions.add("migrate");
                            break;
                        case "building":
                            StringUtil.copyPartialMatches(args[1],
                                    Arrays.asList("list", "register", "inspect", "remove"), completions);
                            break;
                        case "market":
                            StringUtil.copyPartialMatches(args[1],
                                    Arrays.asList("prices", "buy", "sell"), completions);
                            break;
                        case "policy":
                            StringUtil.copyPartialMatches(args[1],
                                    Arrays.asList("list", "set"), completions);
                            break;
                        case "crisis":
                            StringUtil.copyPartialMatches(args[1],
                                    Arrays.asList("status", "contribute", "trigger"), completions);
                            if (!player.hasPermission("villagerpro.village.crisis.trigger")) {
                                completions.remove("trigger");
                            }
                            break;
                        case "caravan":
                            StringUtil.copyPartialMatches(args[1],
                                    Arrays.asList("list", "dispatch", "claim"), completions);
                            break;
                        case "create":
                            // 创建命令可以建议村庄名称
                            completions.add("<村庄名称>");
                            break;
                    }
                } else if (args.length == 3 && "building".equalsIgnoreCase(args[0])
                        && ("register".equalsIgnoreCase(args[1]) || "remove".equalsIgnoreCase(args[1]))) {
                    StringUtil.copyPartialMatches(args[2],
                            Arrays.asList("granary", "workshop", "clinic"), completions);
                } else if (args.length == 3 && "market".equalsIgnoreCase(args[0])
                        && ("buy".equalsIgnoreCase(args[1]) || "sell".equalsIgnoreCase(args[1]))) {
                    StringUtil.copyPartialMatches(args[2], MarketManager.getConfiguredItems(), completions);
                } else if (args.length == 4 && "market".equalsIgnoreCase(args[0])
                        && ("buy".equalsIgnoreCase(args[1]) || "sell".equalsIgnoreCase(args[1]))) {
                    completions.add("<数量>");
                } else if (args.length == 3 && "policy".equalsIgnoreCase(args[0])
                        && "set".equalsIgnoreCase(args[1])) {
                    StringUtil.copyPartialMatches(args[2],
                            Arrays.asList("overtime", "welfare", "export", "reserve"), completions);
                } else if (args.length == 3 && "crisis".equalsIgnoreCase(args[0])
                        && "contribute".equalsIgnoreCase(args[1])) {
                    completions.add("<数量>");
                } else if (args.length == 3 && "crisis".equalsIgnoreCase(args[0])
                        && "trigger".equalsIgnoreCase(args[1])
                        && player.hasPermission("villagerpro.village.crisis.trigger")) {
                    StringUtil.copyPartialMatches(args[2], Arrays.asList(
                            "harvest_failure", "epidemic", "fire", "trade_blockade"), completions);
                } else if (args.length == 3 && "caravan".equalsIgnoreCase(args[0])
                        && "dispatch".equalsIgnoreCase(args[1])) {
                    StringUtil.copyPartialMatches(args[2], CaravanManager.getDestinations(), completions);
                } else if (args.length == 4 && "caravan".equalsIgnoreCase(args[0])
                        && "dispatch".equalsIgnoreCase(args[1])) {
                    completions.add("<物品>");
                } else if (args.length == 5 && "caravan".equalsIgnoreCase(args[0])
                        && "dispatch".equalsIgnoreCase(args[1])) {
                    completions.add("<数量>");
                } else if (args.length == 3 && "caravan".equalsIgnoreCase(args[0])
                        && "claim".equalsIgnoreCase(args[1])) {
                    completions.add("<商队ID>");
                } else if (args.length == 3 && "orders".equalsIgnoreCase(args[0])
                        && "resolve".equalsIgnoreCase(args[1])
                        && player.hasPermission("villagerpro.village.orders.resolve")) {
                    completions.add("<订单ID>");
                } else if (args.length == 4 && "orders".equalsIgnoreCase(args[0])
                        && "resolve".equalsIgnoreCase(args[1])
                        && player.hasPermission("villagerpro.village.orders.resolve")) {
                    StringUtil.copyPartialMatches(args[3],
                            Arrays.asList("completed", "pending"), completions);
                }
                break;
                
            case "villager":
                if (args.length == 1) {
                    StringUtil.copyPartialMatches(args[0], VILLAGER_SUBCOMMANDS, completions);
                } else if (args.length == 2) {
                    switch (args[0].toLowerCase()) {
                        case "list":
                        case "recruit":
                            // 这些子命令没有额外参数
                            break;
                        case "info":
                        case "remove":
                        case "upgrade":
                        case "specialize":
                        case "needs":
                        case "workstation":
                            addOwnedVillagerIdCompletions(player, args[1], completions);
                            break;
                    }
                }
                break;
                
            case "upgrade":
                if (args.length == 1) {
                    StringUtil.copyPartialMatches(args[0], UPGRADE_SUBCOMMANDS, completions);
                } else if (args.length == 2) {
                    if ("villager".equals(args[0].toLowerCase())) {
                        addOwnedVillagerIdCompletions(player, args[1], completions);
                    }
                }
                break;
        }
        
        Collections.sort(completions);
        return completions;
    }

    private void addOwnedVillagerIdCompletions(Player player, String input,
                                               List<String> completions) {
        Village village = VillageManager.getVillage(player.getUniqueId());
        if (village == null) return;
        List<String> ids = VillagerManager.getVillagers(village.getId()).stream()
                .map(villager -> Integer.toString(villager.getId()))
                .toList();
        StringUtil.copyPartialMatches(input, ids, completions);
    }

    private boolean handleDatabaseCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("villagerpro.database.migrate")) {
            sender.sendMessage("§c你没有权限执行数据库迁移");
            return true;
        }
        if ((args.length != 3 && args.length != 4) || !"migrate".equalsIgnoreCase(args[1])
                || args.length == 4 && !"confirm".equalsIgnoreCase(args[3])) {
            sender.sendMessage("§c用法: /village database migrate <mysql|sqlite> [confirm]");
            return true;
        }

        final DatabaseDialect target;
        try {
            target = DatabaseDialect.fromConfig(args[2]);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage("§c" + exception.getMessage());
            return true;
        }

        sender.sendMessage("§e开始迁移数据库。目标库必须为空，迁移期间服务器可能短暂停顿……");
        try {
            boolean replaceTarget = args.length == 4;
            if (replaceTarget) {
                sender.sendMessage("§c已确认覆盖：目标库中现有的 VillagerPro 数据将被当前数据库替换。");
            }
            DatabaseMigrationManager.MigrationResult result = DatabaseMigrationManager.migrate(target, replaceTarget);
            sender.sendMessage("§a数据库迁移完成：已复制 " + result.getTableCount()
                    + " 张表、" + result.getTotalRows() + " 行数据。");
            sender.sendMessage("§a已将 database.type 切换为 " + target.name().toLowerCase()
                    + "。VillagerPro 现在停止运行，请重启服务器。");
            VillagerPro.getInstance().getLogger().info("数据库迁移完成，已切换至 " + target.name()
                    + "，复制 " + result.getTotalRows() + " 行数据；等待服务器重启");
            VillagerPro.getInstance().getServer().getPluginManager().disablePlugin(VillagerPro.getInstance());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            sender.sendMessage("§c无法迁移数据库：" + exception.getMessage());
        } catch (Exception exception) {
            sender.sendMessage("§c数据库迁移失败，源数据库和当前配置保持不变：" + exception.getMessage());
            VillagerPro.getInstance().getLogger().severe("数据库迁移失败: " + exception.getMessage());
        }
        return true;
    }
    
    private boolean handleVillageCommand(Player player, String[] args) {
        // 检查基础权限
        if (!player.hasPermission("villagerpro.village.info") && 
            !player.hasPermission("villagerpro.village.create") && 
            !player.hasPermission("villagerpro.village.warehouse") && 
            !player.hasPermission("villagerpro.village.upgrade") &&
            !player.hasPermission("villagerpro.village.building") &&
            !player.hasPermission("villagerpro.village.market") &&
            !player.hasPermission("villagerpro.village.policy") &&
            !player.hasPermission("villagerpro.village.crisis") &&
            !player.hasPermission("villagerpro.village.caravan") &&
            !player.hasPermission("villagerpro.village.reload")) {
            player.sendMessage("§c你没有权限执行任何村庄相关命令");
            return true;
        }
        
        if (args.length == 0) {
            player.sendMessage("§c用法: /village <create|info|warehouse|orders|stats|rules|building|market|policy|crisis|caravan|upgrade|reload>");
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "create":
                // 检查权限
                if (!player.hasPermission("villagerpro.village.create")) {
                    player.sendMessage("§c你没有权限执行此命令");
                    return true;
                }
                
                if (args.length < 2) {
                    player.sendMessage("§c用法: /village create <村庄名称>");
                    return true;
                }
                
                // 检查玩家是否已经有村庄
                Village existingVillage = VillageManager.getVillage(player.getUniqueId());
                if (existingVillage != null) {
                    player.sendMessage("§c你已经有一个村庄了！");
                    return true;
                }
                
                // 合并剩余参数作为村庄名称
                StringBuilder nameBuilder = new StringBuilder();
                for (int i = 1; i < args.length; i++) {
                    nameBuilder.append(args[i]);
                    if (i < args.length - 1) {
                        nameBuilder.append(" ");
                    }
                }
                String villageName = nameBuilder.toString();
                
                Village village = VillageManager.createVillage(player.getUniqueId(), villageName, player.getLocation());
                if (village != null) {
                    player.sendMessage("§a成功创建村庄: " + villageName);
                } else {
                    player.sendMessage("§c创建村庄失败");
                }
                return true;
                
            case "info":
                if (!player.hasPermission("villagerpro.village.info")) {
                    player.sendMessage("§c你没有权限执行此命令");
                    return true;
                }
                GUIManager.openVillageGUI(player);
                return true;
                
            case "warehouse":
                if (!player.hasPermission("villagerpro.village.warehouse")) {
                    player.sendMessage("§c你没有权限执行此命令");
                    return true;
                }
                GUIManager.openWarehouseGUI(player);
                return true;

            case "orders":
                if (args.length == 4 && "resolve".equalsIgnoreCase(args[1])) {
                    if (!player.hasPermission("villagerpro.village.orders.resolve")) {
                        player.sendMessage("§c你没有权限人工确认订单结算");
                        return true;
                    }
                    try {
                        VillageOrderManager.resolvePayoutState(
                                player, Integer.parseInt(args[2]), args[3]);
                    } catch (NumberFormatException exception) {
                        player.sendMessage("§c订单 ID 必须是整数");
                    }
                    return true;
                }
                if (args.length != 1) {
                    player.sendMessage("§c用法: /village orders [resolve <订单ID> <completed|pending>]");
                    return true;
                }
                if (!player.hasPermission("villagerpro.village.info")) {
                    player.sendMessage("§c你没有权限执行此命令");
                    return true;
                }
                cn.popcraft.villagerpro.gui.VillageOperationsGUI.openOrderBoard(player);
                return true;

            case "stats":
                if (!player.hasPermission("villagerpro.village.info")) {
                    player.sendMessage("§c你没有权限执行此命令");
                    return true;
                }
                cn.popcraft.villagerpro.gui.VillageOperationsGUI.openProductionStats(player);
                return true;

            case "rules":
                if (!player.hasPermission("villagerpro.village.warehouse")) {
                    player.sendMessage("§c你没有权限执行此命令");
                    return true;
                }
                cn.popcraft.villagerpro.gui.VillageOperationsGUI.openWarehouseRules(player);
                return true;

            case "building":
                return handleBuildingCommand(player, args);

            case "market":
                return handleMarketCommand(player, args);

            case "policy":
                return handlePolicyCommand(player, args);

            case "crisis":
                return handleCrisisCommand(player, args);

            case "caravan":
                return handleCaravanCommand(player, args);
                
            case "upgrade":
                if (!player.hasPermission("villagerpro.village.upgrade")) {
                    player.sendMessage("§c你没有权限执行此命令");
                    return true;
                }
                GUIManager.openVillageUpgradeGUI(player);
                return true;
                
            case "reload":
                // 检查管理员权限
                if (!player.hasPermission("villagerpro.village.reload")) {
                    player.sendMessage("§c你没有权限执行此命令");
                    return true;
                }
                // 重新加载配置文件
                VillagerPro.getInstance().reloadConfig();
                cn.popcraft.villagerpro.managers.EcoChainManager.getInstance().reloadConfiguration();
                player.sendMessage("§a配置文件已重新加载！");
                return true;
                
            default:
                player.sendMessage("§c未知的子命令: " + args[0]);
                player.sendMessage("§c用法: /village <create|info|warehouse|orders|stats|rules|building|market|policy|crisis|caravan|upgrade|reload>");
                return true;
        }
    }

    private boolean handlePolicyCommand(Player player, String[] args) {
        if (!player.hasPermission("villagerpro.village.policy")) {
            player.sendMessage("§c你没有权限管理村庄政策");
            return true;
        }
        if (args.length < 2 || "list".equalsIgnoreCase(args[1])) {
            PolicyManager.showPolicies(player);
            return true;
        }
        if (args.length != 3 || !"set".equalsIgnoreCase(args[1])) {
            player.sendMessage("§c用法: /village policy <list|set> [overtime|welfare|export|reserve]");
            return true;
        }
        PolicyManager.PolicyType type = PolicyManager.PolicyType.fromInput(args[2]);
        if (type == null) {
            player.sendMessage("§c未知政策，可选 overtime、welfare、export、reserve");
            return true;
        }
        PolicyManager.selectPolicy(player, type);
        return true;
    }

    private boolean handleCrisisCommand(Player player, String[] args) {
        if (!player.hasPermission("villagerpro.village.crisis")) {
            player.sendMessage("§c你没有权限处理村庄危机");
            return true;
        }
        if (args.length < 2 || "status".equalsIgnoreCase(args[1])) {
            CrisisManager.showStatus(player);
            return true;
        }
        if (args.length == 3 && "contribute".equalsIgnoreCase(args[1])) {
            try {
                CrisisManager.contribute(player, Integer.parseInt(args[2]));
            } catch (NumberFormatException exception) {
                player.sendMessage("§c提交数量必须是整数");
            }
            return true;
        }
        if (args.length == 3 && "trigger".equalsIgnoreCase(args[1])) {
            if (!player.hasPermission("villagerpro.village.crisis.trigger")) {
                player.sendMessage("§c你没有权限手动触发危机");
                return true;
            }
            CrisisManager.CrisisType type = CrisisManager.CrisisType.fromInput(args[2]);
            if (type == null) {
                player.sendMessage("§c未知危机类型");
                return true;
            }
            CrisisManager.forceStart(player, type);
            return true;
        }
        player.sendMessage("§c用法: /village crisis <status|contribute|trigger> [数量|类型]");
        return true;
    }

    private boolean handleCaravanCommand(Player player, String[] args) {
        if (!player.hasPermission("villagerpro.village.caravan")) {
            player.sendMessage("§c你没有权限使用商队路线");
            return true;
        }
        if (args.length < 2 || "list".equalsIgnoreCase(args[1])) {
            CaravanManager.showRoutes(player);
            return true;
        }
        if (args.length == 5 && "dispatch".equalsIgnoreCase(args[1])) {
            try {
                CaravanManager.dispatch(player, args[2], args[3], Integer.parseInt(args[4]));
            } catch (NumberFormatException exception) {
                player.sendMessage("§c货物数量必须是整数");
            }
            return true;
        }
        if (args.length == 3 && "claim".equalsIgnoreCase(args[1])) {
            try {
                CaravanManager.claim(player, Integer.parseInt(args[2]));
            } catch (NumberFormatException exception) {
                player.sendMessage("§c商队 ID 必须是整数");
            }
            return true;
        }
        player.sendMessage("§c用法: /village caravan <list|dispatch|claim> [目的地 物品 数量|商队ID]");
        return true;
    }

    private boolean handleMarketCommand(Player player, String[] args) {
        if (!player.hasPermission("villagerpro.village.market")) {
            player.sendMessage("§c你没有权限使用村庄市场");
            return true;
        }
        if (args.length < 2 || "prices".equalsIgnoreCase(args[1])) {
            MarketManager.showPrices(player);
            return true;
        }
        if (args.length != 4
                || !("buy".equalsIgnoreCase(args[1]) || "sell".equalsIgnoreCase(args[1]))) {
            player.sendMessage("§c用法: /village market <prices|buy|sell> [物品] [数量]");
            return true;
        }
        try {
            int amount = Integer.parseInt(args[3]);
            if ("buy".equalsIgnoreCase(args[1])) {
                MarketManager.buy(player, args[2], amount);
            } else {
                MarketManager.sell(player, args[2], amount);
            }
        } catch (NumberFormatException exception) {
            player.sendMessage("§c数量必须是整数");
        }
        return true;
    }

    private boolean handleBuildingCommand(Player player, String[] args) {
        if (!player.hasPermission("villagerpro.village.building")) {
            player.sendMessage("§c你没有权限管理功能建筑");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§c用法: /village building <list|register|inspect|remove> [granary|workshop|clinic]");
            return true;
        }
        switch (args[1].toLowerCase()) {
            case "list":
                BuildingManager.listBuildings(player);
                return true;
            case "inspect": {
                org.bukkit.block.Block target = player.getTargetBlockExact(8);
                if (target == null) {
                    player.sendMessage("§c请看向八格内的建筑核心");
                } else {
                    BuildingManager.inspectBuilding(player, target);
                }
                return true;
            }
            case "register": {
                BuildingType type = parseBuildingType(player, args);
                if (type == null) return true;
                org.bukkit.block.Block target = player.getTargetBlockExact(8);
                if (target == null) {
                    player.sendMessage("§c请看向八格内的建筑核心方块");
                } else {
                    BuildingManager.registerBuilding(player, type, target);
                }
                return true;
            }
            case "remove": {
                BuildingType type = parseBuildingType(player, args);
                if (type != null) BuildingManager.removeBuilding(player, type);
                return true;
            }
            default:
                player.sendMessage("§c用法: /village building <list|register|inspect|remove> [granary|workshop|clinic]");
                return true;
        }
    }

    private BuildingType parseBuildingType(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§c请指定建筑类型: granary、workshop 或 clinic");
            return null;
        }
        BuildingType type = BuildingType.fromInput(args[2]);
        if (type == null) {
            player.sendMessage("§c未知建筑类型: " + args[2]);
        }
        return type;
    }
    
    // 已在onTabComplete中实现统一的Tab补全逻辑，移除旧方法
    
    private boolean handleVillagerCommand(Player player, String[] args) {
        // 检查基础权限
        if (!player.hasPermission("villagerpro.villager.list") && 
            !player.hasPermission("villagerpro.villager.recruit") && 
            !player.hasPermission("villagerpro.villager.info") && 
            !player.hasPermission("villagerpro.villager.remove") &&
            !player.hasPermission("villagerpro.villager.upgrade")) {
            player.sendMessage("§c你没有权限执行任何村民相关命令");
            return true;
        }
        
        if (args.length == 0) {
            player.sendMessage("§c用法: /villager <list|recruit|info|remove|upgrade|specialize|needs|workstation>");
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "list":
                if (!player.hasPermission("villagerpro.villager.list")) {
                    player.sendMessage("§c你没有权限执行此命令");
                    return true;
                }
                GUIManager.openVillagerListGUI(player);
                return true;
                
            case "recruit":
                if (!player.hasPermission("villagerpro.villager.recruit")) {
                    player.sendMessage("§c你没有权限执行此命令");
                    return true;
                }
                GUIManager.openRecruitGUI(player);
                return true;
                
            case "info":
                if (!player.hasPermission("villagerpro.villager.info")) {
                    player.sendMessage("§c你没有权限执行此命令");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§c用法: /villager info <村民ID>");
                    return true;
                }
                try {
                    int villagerId = Integer.parseInt(args[1]);
                    GUIManager.openVillagerInfoGUI(player, villagerId);
                } catch (NumberFormatException e) {
                    player.sendMessage("§c无效的村民ID: " + args[1]);
                }
                return true;
                
            case "remove":
                if (!player.hasPermission("villagerpro.villager.remove")) {
                    player.sendMessage("§c你没有权限执行此命令");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§c用法: /villager remove <村民ID>");
                    return true;
                }
                try {
                    int villagerId = Integer.parseInt(args[1]);
                    Village village = VillageManager.getVillage(player.getUniqueId());
                    cn.popcraft.villagerpro.models.VillagerData villager =
                            VillagerManager.getVillagerById(villagerId);
                    if (village == null || villager == null || villager.getVillageId() != village.getId()) {
                        player.sendMessage("§c该村民不属于你的村庄");
                    } else if (VillagerManager.removeVillager(villagerId)) {
                        org.bukkit.entity.Villager entity = villager.getEntity();
                        if (entity != null && entity.isValid()) {
                            entity.setCustomName(null);
                            entity.setCustomNameVisible(false);
                            entity.setProfession(org.bukkit.entity.Villager.Profession.NONE);
                        }
                        player.sendMessage("§a成功移除村民");
                    } else {
                        player.sendMessage("§c移除村民失败");
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage("§c无效的村民ID");
                }
                return true;
                
            case "upgrade":
                if (!player.hasPermission("villagerpro.villager.upgrade")) {
                    player.sendMessage("§c你没有权限执行此命令");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§c用法: /villager upgrade <村民ID>");
                    return true;
                }
                try {
                    int villagerId = Integer.parseInt(args[1]);
                    GUIManager.openVillagerUpgradeGUI(player, villagerId);
                } catch (NumberFormatException e) {
                    player.sendMessage("§c无效的村民ID: " + args[1]);
                }
                return true;

            case "specialize":
                return openVillagerDevelopment(player, args,
                        cn.popcraft.villagerpro.gui.VillagerDevelopmentGUI::openSpecialization,
                        "specialize");

            case "needs":
                return openVillagerDevelopment(player, args,
                        cn.popcraft.villagerpro.gui.VillagerDevelopmentGUI::openNeeds,
                        "needs");

            case "workstation":
                return openVillagerDevelopment(player, args,
                        cn.popcraft.villagerpro.gui.VillagerDevelopmentGUI::openWorkstation,
                        "workstation");
                
            default:
                player.sendMessage("§c未知的子命令: " + args[0]);
                player.sendMessage("§c用法: /villager <list|recruit|info|remove|upgrade|specialize|needs|workstation>");
                return true;
        }
    }

    private boolean openVillagerDevelopment(Player player, String[] args,
                                             java.util.function.BiConsumer<Player, Integer> opener,
                                             String subcommand) {
        if (!player.hasPermission("villagerpro.villager.info")) {
            player.sendMessage("§c你没有权限执行此命令");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§c用法: /villager " + subcommand + " <村民ID>");
            return true;
        }
        try {
            opener.accept(player, Integer.parseInt(args[1]));
        } catch (NumberFormatException e) {
            player.sendMessage("§c无效的村民ID: " + args[1]);
        }
        return true;
    }
    
    // 已在onTabComplete中实现统一的Tab补全逻辑，移除旧方法
    
    private boolean handleUpgradeCommand(Player player, String[] args) {
        // 检查权限
        if (!player.hasPermission("villagerpro.village.upgrade")) {
            player.sendMessage("§c你没有权限执行此命令");
            return true;
        }
        
        if (args.length == 0) {
            player.sendMessage("§c用法: /upgrade <village|villager>");
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "village":
                GUIManager.openVillageUpgradeGUI(player);
                return true;
                
            case "villager":
                if (args.length < 2) {
                    player.sendMessage("§c用法: /upgrade villager <村民ID>");
                    return true;
                }
                try {
                    int villagerId = Integer.parseInt(args[1]);
                    GUIManager.openVillagerUpgradeGUI(player, villagerId);
                } catch (NumberFormatException e) {
                    player.sendMessage("§c无效的村民ID: " + args[1]);
                }
                return true;
                
            default:
                player.sendMessage("§c未知的子命令: " + args[0]);
                player.sendMessage("§c用法: /upgrade <village|villager>");
                return true;
        }
    }
    
    // 已在onTabComplete中实现统一的Tab补全逻辑，移除旧方法和filterCompletions方法
    
    // 添加命令处理相关方法
}
