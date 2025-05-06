# 贡献追踪器 API 文档

## 基础信息

- 基础URL: `http://localhost:8080`
- 所有响应均为JSON格式
- 所有接口支持CORS

## 接口列表

### 1. 获取贡献者列表

```a
GET /api/contributors
```

响应示例：

```json
[
  {
    "id": 1,
    "playerUuid": "550e8400-e29b-41d4-a716-446655440000",
    "playerName": "Player1",
    "contributionCount": 5
  }
]
```

### 2. 获取所有贡献列表

```a
GET /api/contributions
```

响应示例：

```json
[
  {
    "id": 1,
    "name": "红石农场",
    "type": "红石",
    "gameId": null,
    "x": 100.5,
    "y": 64.0,
    "z": -200.3,
    "world": "minecraft:overworld",
    "createdAt": "2024-03-20T10:30:00Z",
    "creatorUuid": "550e8400-e29b-41d4-a716-446655440000",
    "creatorName": "Player1",
    "contributors": "Player1,Player2"
  }
]
```

### 3. 获取单个贡献者贡献列表

```a
GET /api/contributions/:playerId
```

参数：

- playerId: 玩家UUID

响应示例：

```json
[
  {
    "id": 1,
    "name": "红石农场",
    "type": "红石",
    "createdAt": "2024-03-20T10:30:00Z",
    "creatorName": "Player1"
  }
]
```

### 4. 获取单个资源详细信息

```a
GET /api/contributions/details/:contributionId
```

参数：

- contributionId: 贡献ID

响应示例：

```json
{
  "id": 1,
  "name": "红石农场",
  "type": "红石",
  "gameId": null,
  "x": 100.5,
  "y": 64.0,
  "z": -200.3,
  "world": "minecraft:overworld",
  "createdAt": "2024-03-20T10:30:00Z",
  "creatorUuid": "550e8400-e29b-41d4-a716-446655440000",
  "creatorName": "Player1",
  "contributors": [
    {
      "playerUuid": "550e8400-e29b-41d4-a716-446655440000",
      "playerName": "Player1",
      "note": "主要设计者"
    },
    {
      "playerUuid": "550e8400-e29b-41d4-a716-446655440001",
      "playerName": "Player2",
      "note": "建造了农场结构"
    }
  ]
}
```

## 错误响应

所有接口在发生错误时会返回以下格式的响应：

```json
{
  "error": "错误信息",
  "code": 错误代码
}
```

常见错误代码：

- 400: 请求参数错误
- 403: 权限不足
- 404: 资源未找到
- 500: 服务器内部错误
