package com.example.contribtracker.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.ServerCommandSource;

/**
 * 命令基础接口，所有命令都必须实现此接口
 */
public interface BaseCommand {
    /**
     * 注册命令
     * @return 命令构建器
     */
    LiteralArgumentBuilder<ServerCommandSource> register();
} 