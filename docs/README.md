# ContribTracker

ContribTracker是一个Minecraft Fabric模组，用于追踪和管理服务器上的玩家贡献。它允许玩家记录建筑、红石设备、农场等贡献，并管理贡献者权限。

## 功能特点

### 贡献管理
- 创建和删除贡献点
- 按类型分类贡献（建筑、红石、农场等）
- 记录贡献的位置和创建者信息
- 查找附近的贡献

### 贡献者系统
- 多级贡献者权限管理
- 邀请其他玩家成为贡献者
- 贡献者层级显示
- 权限继承和管理

### 命令系统
- `/contribtracker add type {type} {name}` - 创建新贡献
- `/contribtracker add player {playerName} {contribId}` - 添加玩家到贡献
- `/contribtracker delete {contribId}` - 删除贡献
- `/contribtracker list` - 列出所有贡献
- `/contribtracker near [radius]` - 查找附近贡献
- `/contribtracker accept` - 接受贡献邀请
- `/contribtracker reject` - 拒绝贡献邀请
- `/contribtracker remove {playerName} {contribId}` - 移除贡献者

### WebSocket API
- 实时数据同步
- 支持外部应用集成
- 提供贡献数据查询接口

## 技术特点

- 多线程架构设计，避免阻塞主线程
- 使用HikariCP数据库连接池优化数据库访问
- 智能缓存系统减少数据库查询
- WebSocket批量处理提升网络性能
- 完善的错误处理和恢复机制

## 安装要求

- Minecraft 1.20.1或更高版本
- Fabric Loader 0.14.0或更高版本
- Fabric API 0.42.0或更高版本

## 安装步骤

1. 确保已安装Fabric Loader和Fabric API
2. 下载最新的ContribTracker发布版本
3. 将jar文件放入服务器的mods文件夹
4. 重启服务器

## 配置说明

配置文件位于`config/null_city/contributions/config.json`，可调整以下参数：

```json
{
  "websocket": {
    "port": 8080,
    "enabled": true
  },
  "database": {
    "cacheExpireTime": 5000,
    "maxConnections": 5
  },
  "system": {
    "workerThreads": 4,
    "logLevel": "WARN"
  }
}
```

## 使用指南

### 创建贡献
1. 站在你想要记录贡献的位置
2. 执行命令：`/contribtracker add type building 主城大厅`
3. 系统会记录当前位置和创建者信息

### 添加贡献者
1. 执行命令：`/contribtracker add player Steve 1`
2. 玩家会收到邀请通知
3. 玩家可以使用`/contribtracker accept`接受邀请

### 查找附近贡献
1. 执行命令：`/contribtracker near 50`
2. 系统会显示50格范围内的所有贡献

## 权限说明

- `contribtracker.command.basic` - 基础命令权限
- `contribtracker.command.admin` - 管理员命令权限
- `contribtracker.manage` - 管理贡献权限

## 性能考量

- 模组使用多线程架构，将耗时操作移至工作线程
- 数据库操作使用连接池和缓存机制
- WebSocket通信采用批处理方式
- 日志系统经过优化，减少I/O操作

## 常见问题

**Q: 服务器TPS下降怎么办？**  
A: 检查配置文件中的工作线程数和缓存过期时间，适当调整可提高性能。

**Q: WebSocket连接失败怎么办？**  
A: 检查端口是否被占用，可在配置文件中修改WebSocket端口。

**Q: 如何备份贡献数据？**  
A: 数据存储在`config/null_city/contributions/contributions.db`文件中，定期备份此文件即可。

## 贡献开发

欢迎提交Pull Request或Issue。开发时请遵循以下准则：

- 遵循Java代码规范
- 添加适当的注释
- 编写单元测试
- 更新文档

## 许可证

本项目采用MIT许可证。详见LICENSE文件。 
