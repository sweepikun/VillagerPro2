package cn.popcraft.villagerpro.commands;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.gui.GUIManager;
import cn.popcraft.villagerpro.managers.VillageManager;
import cn.popcraft.villagerpro.managers.VillagerManager;
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
    
    private static final List<String> VILLAGE_SUBCOMMANDS = Arrays.asList("create", "info", "warehouse", "upgrade", "reload");
    private static final List<String> VILLAGER_SUBCOMMANDS = Arrays.asList("list", "recruit", "info", "remove", "upgrade");
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
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }
        
        Player player = (Player) sender;
        List<String> completions = new ArrayList<>();
        
        switch (command.getName().toLowerCase()) {
            case "village":
                if (args.length == 1) {
                    StringUtil.copyPartialMatches(args[0], VILLAGE_SUBCOMMANDS, completions);
                } else if (args.length == 2) {
                    switch (args[0].toLowerCase()) {
                        case "info":
                        case "warehouse":
                        case "upgrade":
                        case "reload":
                            // 这些子命令没有额外参数
                            break;
                        case "create":
                            // 创建命令可以建议村庄名称
                            completions.add("<村庄名称>");
                            break;
                    }
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
                            // 这些子命令需要村民ID
                            completions.add("<村民ID>");
                            break;
                    }
                }
                break;
                
            case "upgrade":
                if (args.length == 1) {
                    StringUtil.copyPartialMatches(args[0], UPGRADE_SUBCOMMANDS, completions);
                } else if (args.length == 2) {
                    if ("villager".equals(args[0].toLowerCase())) {
                        completions.add("<村民ID>");
                    }
                }
                break;
        }
        
        Collections.sort(completions);
        return completions;
    }
    
    private boolean handleVillageCommand(Player player, String[] args) {
        // 检查基础权限
        if (!player.hasPermission("villagerpro.village.info") && 
            !player.hasPermission("villagerpro.village.create") && 
            !player.hasPermission("villagerpro.village.warehouse") && 
            !player.hasPermission("villagerpro.village.upgrade") &&
            !player.hasPermission("villagerpro.admin.reload")) {
            player.sendMessage("§c你没有权限执行任何村庄相关命令");
            return true;
        }
        
        if (args.length == 0) {
            player.sendMessage("§c用法: /village <create|info|warehouse|upgrade|reload>");
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
                
                Village village = VillageManager.createVillage(player.getUniqueId(), villageName);
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
                
            case "upgrade":
                if (!player.hasPermission("villagerpro.village.upgrade")) {
                    player.sendMessage("§c你没有权限执行此命令");
                    return true;
                }
                GUIManager.openVillageUpgradeGUI(player);
                return true;
                
            case "reload":
                // 检查管理员权限
                if (!player.hasPermission("villagerpro.admin.reload")) {
                    player.sendMessage("§c你没有权限执行此命令");
                    return true;
                }
                // 重新加载配置文件
                VillagerPro.getInstance().reloadConfig();
                player.sendMessage("§a配置文件已重新加载！");
                return true;
                
            default:
                player.sendMessage("§c未知的子命令: " + args[0]);
                player.sendMessage("§c用法: /village <create|info|warehouse|upgrade|reload>");
                return true;
        }
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
            player.sendMessage("§c用法: /villager <list|recruit|info|remove|upgrade>");
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
                    // 实现移除村民逻辑
                    if (VillagerManager.removeVillager(villagerId)) {
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
                
            default:
                player.sendMessage("§c未知的子命令: " + args[0]);
                player.sendMessage("§c用法: /villager <list|recruit|info|remove|upgrade>");
                return true;
        }
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