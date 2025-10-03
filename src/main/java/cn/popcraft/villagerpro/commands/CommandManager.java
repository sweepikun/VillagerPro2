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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommandManager implements CommandExecutor, TabCompleter {
    
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
            sender.sendMessage("§c只有玩家可以执行此命令");
            return true;
        }
        
        Player player = (Player) sender;
        
        switch (command.getName().toLowerCase()) {
            case "village":
                handleVillageCommand(player, args);
                break;
            case "villager":
                handleVillagerCommand(player, args);
                break;
            case "upgrade":
                handleUpgradeCommand(player, args);
                break;
            default:
                player.sendMessage("§c未知命令");
                break;
        }
        
        return true;
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
                completions = handleVillageTabComplete(player, args);
                break;
            case "villager":
                completions = handleVillagerTabComplete(player, args);
                break;
            case "upgrade":
                completions = handleUpgradeTabComplete(player, args);
                break;
        }
        
        return completions != null ? completions : Collections.emptyList();
    }
    
    private void handleVillageCommand(Player player, String[] args) {
        // 检查权限
        if (!player.hasPermission("villagerpro.village.info")) {
            player.sendMessage("§c你没有权限执行此命令");
            return;
        }
        
        if (args.length == 0) {
            // 打开村庄主界面
            GUIManager.openVillageGUI(player);
            return;
        }
        
        switch (args[0].toLowerCase()) {
            case "create":
                // 检查权限
                if (!player.hasPermission("villagerpro.village.create")) {
                    player.sendMessage("§c你没有权限执行此命令");
                    return;
                }
                
                if (args.length < 2) {
                    player.sendMessage("§c用法: /village create <村庄名称>");
                    return;
                }
                
                // 检查玩家是否已经有村庄
                Village existingVillage = VillageManager.getVillage(player.getUniqueId());
                if (existingVillage != null) {
                    player.sendMessage("§c你已经有一个村庄了！");
                    return;
                }
                
                // 创建村庄
                StringBuilder nameBuilder = new StringBuilder();
                for (int i = 1; i < args.length; i++) {
                    nameBuilder.append(args[i]).append(" ");
                }
                String villageName = nameBuilder.toString().trim();
                
                Village village = VillageManager.createVillage(player.getUniqueId(), villageName);
                if (village != null) {
                    player.sendMessage("§a成功创建村庄: " + villageName);
                } else {
                    player.sendMessage("§c创建村庄失败");
                }
                break;
                
            case "info":
                GUIManager.openVillageGUI(player);
                break;
                
            case "warehouse":
                // 检查权限
                if (!player.hasPermission("villagerpro.village.warehouse")) {
                    player.sendMessage("§c你没有权限执行此命令");
                    return;
                }
                
                GUIManager.openWarehouseGUI(player);
                break;
                
            default:
                player.sendMessage("§c未知子命令。用法: /village [create|info|warehouse]");
                break;
        }
    }
    
    private List<String> handleVillageTabComplete(Player player, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // 第一个参数补全
            if (player.hasPermission("villagerpro.village.create")) {
                completions.add("create");
            }
            completions.add("info");
            if (player.hasPermission("villagerpro.village.warehouse")) {
                completions.add("warehouse");
            }
        } else if (args.length == 2 && "create".equals(args[0])) {
            // 创建村庄命令的村庄名称参数，这里不提供补全
            return Collections.emptyList();
        }
        
        return filterCompletions(completions, args[args.length - 1]);
    }
    
    private void handleVillagerCommand(Player player, String[] args) {
        // 检查权限
        if (!player.hasPermission("villagerpro.villager.list")) {
            player.sendMessage("§c你没有权限执行此命令");
            return;
        }
        
        if (args.length == 0) {
            // 打开村民列表界面
            GUIManager.openVillagerListGUI(player);
            return;
        }
        
        switch (args[0].toLowerCase()) {
            case "list":
                GUIManager.openVillagerListGUI(player);
                break;
                
            case "recruit":
                // 检查权限
                if (!player.hasPermission("villagerpro.villager.recruit")) {
                    player.sendMessage("§c你没有权限执行此命令");
                    return;
                }
                
                GUIManager.openRecruitGUI(player);
                break;
                
            case "info":
                if (args.length < 2) {
                    player.sendMessage("§c用法: /villager info <村民ID>");
                    return;
                }
                
                try {
                    int villagerId = Integer.parseInt(args[1]);
                    GUIManager.openVillagerInfoGUI(player, villagerId);
                } catch (NumberFormatException e) {
                    player.sendMessage("§c无效的村民ID");
                }
                break;
                
            case "remove":
                // 检查权限
                if (!player.hasPermission("villagerpro.villager.remove")) {
                    player.sendMessage("§c你没有权限执行此命令");
                    return;
                }
                
                if (args.length < 2) {
                    player.sendMessage("§c用法: /villager remove <村民ID>");
                    return;
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
                break;
                
            default:
                player.sendMessage("§c未知子命令。用法: /villager [list|recruit|info|remove]");
                break;
        }
    }
    
    private List<String> handleVillagerTabComplete(Player player, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // 第一个参数补全
            completions.add("list");
            if (player.hasPermission("villagerpro.villager.recruit")) {
                completions.add("recruit");
            }
            completions.add("info");
            if (player.hasPermission("villagerpro.villager.remove")) {
                completions.add("remove");
            }
        } else if (args.length == 2) {
            // 第二个参数补全
            if ("info".equals(args[0]) || "remove".equals(args[0])) {
                // 这里可以添加村民ID的补全逻辑，但通常ID是动态的，所以不提供补全
                return Collections.emptyList();
            }
        }
        
        return filterCompletions(completions, args[args.length - 1]);
    }
    
    private void handleUpgradeCommand(Player player, String[] args) {
        // 检查权限
        if (!player.hasPermission("villagerpro.village.upgrade")) {
            player.sendMessage("§c你没有权限执行此命令");
            return;
        }
        
        if (args.length == 0) {
            // 打开村庄升级界面
            GUIManager.openVillageUpgradeGUI(player);
            return;
        }
        
        switch (args[0].toLowerCase()) {
            case "village":
                GUIManager.openVillageUpgradeGUI(player);
                break;
                
            case "villager":
                if (args.length < 2) {
                    player.sendMessage("§c用法: /upgrade villager <村民ID>");
                    return;
                }
                
                try {
                    int villagerId = Integer.parseInt(args[1]);
                    // 实现村民升级界面
                    GUIManager.openVillagerUpgradeGUI(player, villagerId);
                } catch (NumberFormatException e) {
                    player.sendMessage("§c无效的村民ID");
                }
                break;
                
            default:
                player.sendMessage("§c未知子命令。用法: /upgrade [village|villager]");
                break;
        }
    }
    
    private List<String> handleUpgradeTabComplete(Player player, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // 第一个参数补全
            completions.add("village");
            completions.add("villager");
        } else if (args.length == 2) {
            // 第二个参数补全
            if ("villager".equals(args[0])) {
                // 这里可以添加村民ID的补全逻辑，但通常ID是动态的，所以不提供补全
                return Collections.emptyList();
            }
        }
        
        return filterCompletions(completions, args[args.length - 1]);
    }
    
    /**
     * 过滤补全选项
     * @param completions 所有可能的补全选项
     * @param arg 当前输入的参数
     * @return 过滤后的补全选项
     */
    private List<String> filterCompletions(List<String> completions, String arg) {
        if (arg.isEmpty()) {
            return completions;
        }
        
        List<String> filtered = new ArrayList<>();
        for (String completion : completions) {
            if (completion.toLowerCase().startsWith(arg.toLowerCase())) {
                filtered.add(completion);
            }
        }
        
        return filtered;
    }
    
    // 添加命令处理相关方法
}