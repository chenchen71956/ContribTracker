# ContribTracker

ContribTracker是一个Minecraft Fabric服务器模组，用于追踪和管理玩家在服务器中建造的各种贡献（如建筑、红石装置等）。

## 项目概述

本项目为服务器管理员提供了一套完整的工具，用于记录、管理和展示玩家贡献。通过权限分级和位置追踪等功能，服务器可以更好地组织玩家创作并进行展示。

## 主要功能

- **贡献追踪管理**：记录玩家贡献及其位置信息
- **权限分级系统**：多层级权限控制，贡献者可管理下级贡献者
- **位置搜索功能**：快速查找附近贡献
- **WebSocket API**：实时数据同步接口，支持外部系统集成
- **高性能设计**：多线程处理、连接池、缓存机制确保服务器性能

## 技术特性

- 多线程架构，将耗时操作从主线程移至工作线程
- HikariCP数据库连接池实现
- 智能数据缓存机制减少数据库访问
- 批量消息处理减少网络资源消耗
- 标准化日志输出系统

## 项目结构

```
ContribTracker/
├── src/main/java/com/example/contribtracker/
│   ├── command/          # 命令实现
│   ├── config/           # 配置系统
│   ├── database/         # 数据库相关
│   ├── util/             # 工具类
│   │   └── LogHelper.java # 日志工具类
│   ├── websocket/        # WebSocket服务实现
│   └── ContribTrackerMod.java # 主类
├── docs/
│   ├── README.md         # 项目概述
│   ├── CHANGELOG.md      # 变更日志
│   └── PROGRESS.md       # 项目进度
└── README.md             # 安装和使用说明
```

## 更多信息

详细的安装说明和API文档请参考项目根目录中的README.md文件。
版本历史和更新内容请查看CHANGELOG.md。 