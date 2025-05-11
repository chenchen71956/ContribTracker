import asyncio
import websockets
import json
import time

async def connect_websocket():
    # WebSocket服务器地址
    uri = "ws://127.0.0.1:25580/ws"
    
    try:
        async with websockets.connect(uri) as websocket:
            print(f"已连接到WebSocket服务器: {uri}")
            
            # 持续监听消息
            while True:
                try:
                    # 等待服务器消息
                    message = await websocket.recv()
                    print(f"收到服务器消息: {message}")
                    
                    # 解析消息
                    data = json.loads(message)
                    if data.get("type") == "ping":
                        # 回复pong
                        pong_message = {"type": "pong"}
                        await websocket.send(json.dumps(pong_message))
                        print("已回复pong消息")
                    
                except websockets.exceptions.ConnectionClosed:
                    print("连接已关闭，尝试重新连接...")
                    break
                    
    except Exception as e:
        print(f"连接错误: {e}")
        print("5秒后重试...")
        await asyncio.sleep(5)
        await connect_websocket()

async def main():
    print("启动WebSocket测试客户端...")
    while True:
        try:
            await connect_websocket()
        except Exception as e:
            print(f"发生错误: {e}")
            print("5秒后重试...")
            await asyncio.sleep(5)

if __name__ == "__main__":
    asyncio.run(main()) 