package cn.popcraft.villagerpro.util;

import cn.popcraft.villagerpro.VillagerPro;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class Messages {
    private static FileConfiguration messagesConfig = null;
    
    /**
     * 初始化消息配置
     */
    public static void initialize() {
        loadMessages();
    }
    
    /**
     * 加载消息配置
     */
    private static void loadMessages() {
        // 保存默认的messages.yml文件
        VillagerPro.getInstance().saveResource("messages.yml", false);
        
        // 加载messages.yml文件
        File messagesFile = new File(VillagerPro.getInstance().getDataFolder(), "messages.yml");
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        
        // 加载默认的messages.yml作为备份
        try (InputStream inputStream = VillagerPro.getInstance().getResource("messages.yml");
             InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(inputStream), StandardCharsets.UTF_8)) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(reader);
            messagesConfig.setDefaults(defaultConfig);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 重新加载消息配置
     */
    public static void reloadMessages() {
        loadMessages();
    }
    
    /**
     * 获取消息
     * @param path 消息路径
     * @return 消息内容
     */
    public static String getMessage(String path) {
        if (messagesConfig == null) {
            return "Message not found: " + path;
        }
        
        String message = messagesConfig.getString(path, "Message not found: " + path);
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    
    /**
     * 获取带参数的消息
     * @param path 消息路径
     * @param params 参数
     * @return 消息内容
     */
    public static String getMessage(String path, String... params) {
        String message = getMessage(path);
        
        // 替换参数
        for (int i = 0; i < params.length; i += 2) {
            if (i + 1 < params.length) {
                message = message.replace("{" + params[i] + "}", params[i + 1]);
            }
        }
        
        return message;
    }
}