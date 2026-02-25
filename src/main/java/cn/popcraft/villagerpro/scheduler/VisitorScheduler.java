package cn.popcraft.villagerpro.scheduler;

import cn.popcraft.villagerpro.VillagerPro;
import cn.popcraft.villagerpro.managers.VillageManager;
import cn.popcraft.villagerpro.managers.VisitorManager;
import cn.popcraft.villagerpro.models.Village;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Random;

/**
 * 访客调度器
 * 负责定期检查并生成访客
 */
public class VisitorScheduler {
    
    private final VillagerPro plugin;
    private final VisitorManager visitorManager;
    private final Random random;
    private BukkitRunnable task;
    private boolean running = false;
    
    public VisitorScheduler() {
        this.plugin = VillagerPro.getInstance();
        // VillageManager没有单例模式，使用静态方法直接调用
        this.visitorManager = VisitorManager.getInstance();
        this.random = new Random();
    }
    
    /**
     * 启动调度器
     */
    public void start() {
        if (running) return;
        
        // 获取检查间隔
        long checkIntervalTicks = plugin.getConfig().getLong("visitors.check_interval_ticks", 24000);
        
        task = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    checkAndSpawnVisitors();
                } catch (Exception e) {
                    plugin.getLogger().warning("访客检查过程中出现错误: " + e.getMessage());
                    VillagerPro.getInstance().getLogger().warning("操作失败：" + e.getMessage());
                }
            }
        };
        
        // 延迟启动，然后按间隔重复执行
        task.runTaskTimer(plugin, checkIntervalTicks, checkIntervalTicks);
        running = true;
        
        plugin.getLogger().info("访客调度器已启动，检查间隔: " + (checkIntervalTicks / 1200) + " 分钟");
    }
    
    /**
     * 停止调度器
     */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        running = false;
        plugin.getLogger().info("访客调度器已停止");
    }
    
    /**
     * 检查并生成访客
     */
    private void checkAndSpawnVisitors() {
        // 获取所有村庄
        List<Village> villages = VillageManager.getAllVillages();
        if (villages.isEmpty()) return;
        
        // 遍历村庄，检查是否需要生成访客
        for (Village village : villages) {
            if (shouldSpawnVisitor(village)) {
                spawnVisitorForVillage(village);
            }
        }
    }
    
    /**
     * 判断是否应该为村庄生成访客
     */
    private boolean shouldSpawnVisitor(Village village) {
        // 检查村庄是否有活跃访客
        if (visitorManager.hasActiveVisitor(village.getId())) {
            return false;
        }
        
        // 检查繁荣度阈值
        int prosperityThreshold = plugin.getConfig().getInt("visitors.prosperity_threshold", 100);
        if (village.getProsperity() < prosperityThreshold) {
            return false;
        }
        
        // 检查生成概率
        double spawnProbability = plugin.getConfig().getDouble("visitors.spawn_probability", 0.3);
        return random.nextDouble() < spawnProbability;
    }
    
    /**
     * 为村庄生成访客
     */
    private void spawnVisitorForVillage(Village village) {
        // 随机选择访客类型
        String[] availableTypes = getAvailableVisitorTypes(village);
        if (availableTypes.length == 0) return;
        
        String visitorType = availableTypes[random.nextInt(availableTypes.length)];
        
        // 生成访客
        visitorManager.spawnVisitor(village, visitorType);
    }
    
    /**
     * 获取可用的访客类型
     */
    private String[] getAvailableVisitorTypes(Village village) {
        // 检查每个访客类型是否启用
        java.util.List<String> availableTypes = new java.util.ArrayList<>();
        
        // 商人
        if (plugin.getConfig().getBoolean("visitors.merchant.enabled", true)) {
            availableTypes.add("merchant");
        }
        
        // 旅行者
        if (plugin.getConfig().getBoolean("visitors.traveler.enabled", true)) {
            availableTypes.add("traveler");
        }
        
        // 节日使者
        if (plugin.getConfig().getBoolean("visitors.festival.enabled", true) && isFestivalDay()) {
            availableTypes.add("festival");
        }
        
        return availableTypes.toArray(new String[0]);
    }
    
    /**
     * 检查是否是节日日
     */
    private boolean isFestivalDay() {
        // 这里可以实现节日逻辑，比如根据Minecraft世界时间或现实日期
        // 目前简单实现：每7天有一个节日
        long worldTime = Bukkit.getWorlds().get(0).getTime();
        return (worldTime % 168000) < 24000; // 168000 ticks = 7天，24000 ticks = 1天
    }
    
    /**
     * 强制为指定村庄生成访客（用于命令或测试）
     */
    public boolean forceSpawnVisitor(int villageId) {
        Village village = VillageManager.getVillageById(villageId);
        if (village == null) {
            plugin.getLogger().warning("找不到村庄 ID: " + villageId);
            return false;
        }
        
        return visitorManager.spawnVisitor(village, "merchant") != null;
    }
    
    /**
     * 获取调度器状态
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * 手动触发一次访客检查
     */
    public void triggerCheckNow() {
        if (!running) {
            plugin.getLogger().warning("访客调度器未运行");
            return;
        }
        
        // 在主线程中执行检查
        Bukkit.getScheduler().runTask(plugin, this::checkAndSpawnVisitors);
    }
}