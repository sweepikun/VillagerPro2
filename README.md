# VillagerPro

VillagerPro 是一个功能强大的 Minecraft Spigot 插件，专为增强游戏中的村民系统而设计。通过这个插件，玩家可以创建和管理自己的村庄，招募和升级村民，以及使用高级经济系统。

## 功能特性

### 村庄系统
- 创建和管理个人村庄
- 村庄升级系统，提升村庄等级解锁新功能
- 村庄仓库系统，集中管理资源
- 村庄经验系统，通过村民工作获得经验

### 村民系统
- 招募附近未被招募的村民
- 多种职业选择（农民、渔夫、牧羊人、图书管理员、牧师、制图师）
- 村民升级系统，提升村民技能和效率
- 村民跟随系统，支持三种模式：自由、跟随、停留

### 经济系统集成
- 支持 Vault 经济插件（金币系统）
- 支持 PlayerPoints 插件（点券系统）
- 支持 ItemsAdder 自定义物品
- 灵活的成本配置系统

### 用户界面
- 直观的图形用户界面（GUI）
- 右键点击村民打开操作界面
- 清晰的信息展示和操作指引

## 安装说明

1. 将生成的 `VillagerPro2-2.0.0.jar` 文件放入服务器的 `plugins` 文件夹中
2. 确保服务器已安装以下依赖插件：
   - Vault
   - PlayerPoints（可选）
   - ItemsAdder（可选）
3. 启动服务器，插件将自动生成配置文件
4. 根据需要修改 `plugins/VillagerPro/` 目录下的配置文件

## 依赖库

插件使用以下库来提供核心功能：

- HikariCP 5.0.1 (数据库连接池)
- SQLite JDBC 3.42.0.0 (SQLite数据库驱动)

这些库会在插件加载时自动下载（需要 Paper 服务端）或需要手动添加到服务器的 classpath 中。

## 命令

### 基本命令

- `/village` 或 `/vill` 或 `/vl` - 管理村庄
  - `create <name>` - 创建村庄
  - `info` - 查看村庄信息
  - `warehouse` - 访问村庄仓库
  - `upgrade` - 升级村庄

- `/villager` 或 `/villagers` 或 `/vlr` - 管理村民
  - `list` - 查看村民列表
  - `recruit` - 招募村民
  - `info <id>` - 查看村民信息
  - `remove <id>` - 移除村民
  - `upgrade <id>` - 升级村民

- `/upgrade` 或 `/upg` 或 `/up` - 升级村庄或村民
  - `village` - 升级村庄
  - `villager <id>` - 升级村民

### 权限节点

- `villagerpro.village` - 使用村庄命令
- `villagerpro.villager` - 使用村民命令
- `villagerpro.upgrade` - 使用升级命令
- `villagerpro.*` - 所有权限

## 配置文件

### config.yml

主要配置文件，包含村庄和村民的设置：

```yaml
# 村庄设置
village:
  # 村庄最大等级
  max_level: 5
  # 村庄升级成本
  upgrade_costs:
    # 示例：升级到2级需要100金币
    2: 
      - type: vault
        amount: 100
    # 升级到3级需要200金币和50点券
    3:
      - type: vault
        amount: 200
      - type: playerpoints
        amount: 50

# 村民设置
villager:
  # 招募成本
  recruit_cost:
    - type: vault
      amount: 50
  # 村民最大等级
  max_level: 5
  # 村民升级经验公式（level为当前等级）
  level_exp_formula: "level * 100"
  
  # 各职业配置
  professions:
    farmer:
      display_name: "农民"
      # 农民技能树
      skills:
        efficient_harvest:
          display_name: "高效收割"
          description: "提高农作物收获效率"
          max_level: 5
          costs:
            1:
              - type: vault
                amount: 100
            2:
              - type: vault
                amount: 200
              - type: playerpoints
                amount: 50
```

### messages.yml

消息配置文件，支持多语言：

```yaml
# 村庄相关消息
village:
  created: "§a村庄 {name} 创建成功！"
  not_found: "§c你还没有创建村庄！"
  level_up: "§a村庄 {name} 升级到 {level} 级！"
  
# 村民相关消息
villager:
  recruited: "§a成功招募了一名{profession}！"
  not_found: "§c找不到该村民！"
  level_up: "§a{profession} 升级到 {level} 级！"
```

## 数据库

插件使用 SQLite 数据库存储所有数据，包括：

- 村庄信息（拥有者、名称、等级、经验、繁荣度）
- 村民信息（所属村庄、实体UUID、职业、等级、经验、跟随模式）
- 仓库物品（村庄ID、物品类型、数量）
- 升级信息（村庄和村民的升级状态）

数据库文件位于 `plugins/VillagerPro/database.db`。

## API 集成

### Vault 经济系统

插件支持所有与 Vault 兼容的经济插件，如 EssentialsX Economy、Gringotts 等。

### PlayerPoints

支持 PlayerPoints 插件的点券系统作为额外的支付方式。

### ItemsAdder

支持 ItemsAdder 自定义物品作为招募和升级的成本。

## 开发者信息

### 构建项目

使用 Gradle 构建项目：

```bash
./gradlew build
```

生成的 jar 文件位于 `build/libs/` 目录中。

### 项目结构

```
src/main/java/cn/popcraft/villagerpro/
├── commands/        # 命令处理
├── database/        # 数据库管理
├── economy/         # 经济系统
├── events/          # 事件监听
├── gui/             # 图形界面
├── managers/        # 业务逻辑管理器
├── models/          # 数据模型
├── scheduler/       # 调度任务
└── util/            # 工具类
```

## 故障排除

### 插件无法加载

1. 确保服务器已安装 Vault 插件
2. 检查服务端日志中的错误信息
3. 确认服务器版本兼容性（推荐使用 Spigot/Paper 1.19.4）

### 村民无法招募

1. 确保附近有未被招募的村民
2. 检查是否有足够的资源支付招募成本
3. 确认玩家有相应的权限节点

### 数据库问题

1. 检查 `plugins/VillagerPro/` 目录是否有写入权限
2. 确认 SQLite JDBC 驱动正确加载
3. 如有需要，可以删除 `database.db` 文件重置数据库（会丢失所有数据）

## 许可证

本项目采用 MIT 许可证，详情请参阅 [LICENSE](LICENSE) 文件。

## 联系方式

如有问题或建议，请联系项目维护者或在 GitHub 上提交 issue。