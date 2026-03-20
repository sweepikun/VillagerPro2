package cn.popcraft.villagerpro.managers;

import cn.popcraft.villagerpro.models.Village;
import cn.popcraft.villagerpro.models.VillagerData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存管理器 - 减少数据库查询次数
 * 使用简单的LRU策略和TTL（生存时间）机制
 */
public class CacheManager {
    // 村庄缓存：玩家UUID -> 村庄数据
    private static final Map<UUID, CacheEntry<Village>> villageCache = new ConcurrentHashMap<>();
    
    // 村民缓存：村民ID -> 村民数据
    private static final Map<Integer, CacheEntry<VillagerData>> villagerByIdCache = new ConcurrentHashMap<>();
    
    // 村民缓存：实体UUID -> 村民数据
    private static final Map<UUID, CacheEntry<VillagerData>> villagerByEntityCache = new ConcurrentHashMap<>();
    
    // 村庄村民列表缓存：村庄ID -> 村民列表
    private static final Map<Integer, CacheEntry<List<VillagerData>>> villageVillagersCache = new ConcurrentHashMap<>();
    
    // 缓存TTL（毫秒）：5分钟
    private static final long CACHE_TTL = 5 * 60 * 1000;
    
    /**
     * 缓存条目包装类
     */
    private static class CacheEntry<T> {
        private final T data;
        private final long timestamp;
        
        public CacheEntry(T data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL;
        }
        
        public T getData() {
            return data;
        }
    }
    
    // ========== 村庄缓存操作 ==========
    
    /**
     * 获取缓存的村庄数据
     * @param ownerUUID 玩家UUID
     * @return 村庄数据，如果缓存不存在或已过期则返回null
     */
    public static Village getCachedVillage(UUID ownerUUID) {
        CacheEntry<Village> entry = villageCache.get(ownerUUID);
        if (entry != null && !entry.isExpired()) {
            return entry.getData();
        }
        // 清理过期缓存
        if (entry != null) {
            villageCache.remove(ownerUUID);
        }
        return null;
    }
    
    /**
     * 缓存村庄数据
     * @param ownerUUID 玩家UUID
     * @param village 村庄数据
     */
    public static void cacheVillage(UUID ownerUUID, Village village) {
        if (village != null) {
            villageCache.put(ownerUUID, new CacheEntry<>(village));
        }
    }
    
    /**
     * 清除村庄缓存
     * @param ownerUUID 玩家UUID
     */
    public static void invalidateVillage(UUID ownerUUID) {
        villageCache.remove(ownerUUID);
    }
    
    /**
     * 清除村庄缓存（通过村庄ID）
     * @param villageId 村庄ID
     */
    public static void invalidateVillageById(int villageId) {
        villageCache.entrySet().removeIf(entry -> 
            entry.getValue().getData() != null && entry.getValue().getData().getId() == villageId);
    }
    
    // ========== 村民缓存操作 ==========
    
    /**
     * 获取缓存的村民数据（通过村民ID）
     * @param villagerId 村民ID
     * @return 村民数据，如果缓存不存在或已过期则返回null
     */
    public static VillagerData getCachedVillagerById(int villagerId) {
        CacheEntry<VillagerData> entry = villagerByIdCache.get(villagerId);
        if (entry != null && !entry.isExpired()) {
            return entry.getData();
        }
        if (entry != null) {
            villagerByIdCache.remove(villagerId);
        }
        return null;
    }
    
    /**
     * 获取缓存的村民数据（通过实体UUID）
     * @param entityUUID 实体UUID
     * @return 村民数据，如果缓存不存在或已过期则返回null
     */
    public static VillagerData getCachedVillagerByEntity(UUID entityUUID) {
        CacheEntry<VillagerData> entry = villagerByEntityCache.get(entityUUID);
        if (entry != null && !entry.isExpired()) {
            return entry.getData();
        }
        if (entry != null) {
            villagerByEntityCache.remove(entityUUID);
        }
        return null;
    }
    
    /**
     * 缓存村民数据
     * @param villager 村民数据
     */
    public static void cacheVillager(VillagerData villager) {
        if (villager != null) {
            CacheEntry<VillagerData> entry = new CacheEntry<>(villager);
            villagerByIdCache.put(villager.getId(), entry);
            if (villager.getEntityUUID() != null) {
                villagerByEntityCache.put(villager.getEntityUUID(), entry);
            }
        }
    }
    
    /**
     * 清除村民缓存
     * @param villagerId 村民ID
     */
    public static void invalidateVillager(int villagerId) {
        CacheEntry<VillagerData> entry = villagerByIdCache.remove(villagerId);
        if (entry != null && entry.getData() != null && entry.getData().getEntityUUID() != null) {
            villagerByEntityCache.remove(entry.getData().getEntityUUID());
        }
        // 同时清除相关的村庄村民列表缓存
        if (entry != null && entry.getData() != null) {
            villageVillagersCache.remove(entry.getData().getVillageId());
        }
    }
    
    /**
     * 清除村民缓存（通过实体UUID）
     * @param entityUUID 实体UUID
     */
    public static void invalidateVillagerByEntity(UUID entityUUID) {
        CacheEntry<VillagerData> entry = villagerByEntityCache.remove(entityUUID);
        if (entry != null && entry.getData() != null) {
            villagerByIdCache.remove(entry.getData().getId());
            villageVillagersCache.remove(entry.getData().getVillageId());
        }
    }
    
    // ========== 村庄村民列表缓存操作 ==========
    
    /**
     * 获取缓存的村庄村民列表
     * @param villageId 村庄ID
     * @return 村民列表，如果缓存不存在或已过期则返回null
     */
    public static List<VillagerData> getCachedVillageVillagers(int villageId) {
        CacheEntry<List<VillagerData>> entry = villageVillagersCache.get(villageId);
        if (entry != null && !entry.isExpired()) {
            return entry.getData();
        }
        if (entry != null) {
            villageVillagersCache.remove(villageId);
        }
        return null;
    }
    
    /**
     * 缓存村庄村民列表
     * @param villageId 村庄ID
     * @param villagers 村民列表
     */
    public static void cacheVillageVillagers(int villageId, List<VillagerData> villagers) {
        if (villagers != null) {
            villageVillagersCache.put(villageId, new CacheEntry<>(new ArrayList<>(villagers)));
            // 同时缓存每个村民
            for (VillagerData villager : villagers) {
                cacheVillager(villager);
            }
        }
    }
    
    /**
     * 清除村庄村民列表缓存
     * @param villageId 村庄ID
     */
    public static void invalidateVillageVillagers(int villageId) {
        villageVillagersCache.remove(villageId);
    }
    
    // ========== 通用缓存操作 ==========
    
    /**
     * 清除所有缓存
     */
    public static void clearAll() {
        villageCache.clear();
        villagerByIdCache.clear();
        villagerByEntityCache.clear();
        villageVillagersCache.clear();
    }
    
    /**
     * 清理过期缓存
     */
    public static void cleanupExpired() {
        villageCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        villagerByIdCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        villagerByEntityCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        villageVillagersCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    /**
     * 获取缓存统计信息
     * @return 统计信息字符串
     */
    public static String getStats() {
        return String.format("Cache Stats - Villages: %d, Villagers(ID): %d, Villagers(Entity): %d, VillageVillagers: %d",
            villageCache.size(), villagerByIdCache.size(), villagerByEntityCache.size(), villageVillagersCache.size());
    }
}
